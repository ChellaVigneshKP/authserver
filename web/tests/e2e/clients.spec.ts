import {
  test,
  expect,
  type Page,
  type BrowserContext,
  type Locator,
} from "@playwright/test";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHmac, createHash, randomBytes, randomUUID } from "node:crypto";
import { createClient } from "redis";
import { writeReport } from "./report";
import { SignJWT } from "jose";
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
// A locator/assertion failure must never trigger an automatic page/ARIA capture.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
test.use({ trace: "off", screenshot: "off", video: "off" });
const fixture = JSON.parse(
  readFileSync(process.env.PORTAL_E2E_CONFIG!, "utf8"),
);
const checks: { id: string; passed: boolean }[] = [];
const redis = createClient({
  url: "redis://127.0.0.1:16379/0",
  socket: { reconnectStrategy: false },
});
function check(id: string, passed: boolean) {
  checks.push({ id, passed });
  writeFileSync(
    process.env.PORTAL_SCREENSHOT_DIR + "/clients-progress.json",
    JSON.stringify(checks),
    { mode: 0o600 },
  );
  expect(passed, id).toBe(true);
}
async function sanitizedScreenshot(
  page: Page,
  name: string,
  privateValues: string[],
) {
  const html = await page.content();
  check(
    name + " screenshot contains no secret UI or session material",
    (await page.evaluate(
      () => !document.querySelector('[data-qa="secret-once"]'),
    )) &&
      privateValues.every((v) => !v || !html.includes(v)) &&
      new URL(page.url()).searchParams.size === 0,
  );
  await expect
    .poll(
      () =>
        page.evaluate(
          () =>
            document.querySelectorAll('button[type="submit"]:disabled').length,
        ),
      { message: "capture waits for pending form actions" },
    )
    .toBe(0);
  await page.evaluate(async () => {
    window.scrollTo(0, 0);
    await new Promise<void>((r) =>
      requestAnimationFrame(() => requestAnimationFrame(() => r())),
    );
  });
  await page.screenshot({
    path: process.env.PORTAL_SCREENSHOT_DIR + "/" + name + ".png",
    fullPage: false,
  });
}
test.beforeAll(() => redis.connect());
test.afterEach(async ({ page }) => {
  await page
    .evaluate(() =>
      document
        .querySelector<HTMLButtonElement>('[data-qa="secret-dismiss"]')
        ?.click(),
    )
    .catch(() => {});
});
test.afterAll(async () => {
  await redis.quit();
  writeReport(process.env.PORTAL_E2E_REPORT!, checks);
});
async function login(page: Page) {
  await page.goto(fixture.portal);
  await page.getByRole("link", { name: "Sign in", exact: true }).click();
  await page.locator("#username").fill(fixture.username);
  await page.locator("#password").fill(fixture.password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.waitForURL(fixture.portal + "/account");
}
// React attaches its fiber to a host node while hydrating it. Interacting
// before that point submits the native form instead of the RHF handler.
async function hydrated(area: Locator) {
  await expect
    .poll(
      () =>
        area.evaluate((el) =>
          Object.keys(el).some((k) => k.startsWith("__reactFiber$")),
        ),
      { message: "form hydrated", timeout: 15000 },
    )
    .toBe(true);
}
function wizard(page: Page) {
  return page
    .locator("form")
    .filter({
      has: page.getByRole("button", { name: "Create identity", exact: true }),
    });
}
function catalogForm(page: Page) {
  return page
    .locator("form")
    .filter({
      has: page.getByRole("button", { name: /^(Create|Save) resource$/ }),
    });
}
async function session(context: BrowserContext) {
  const c = (await context.cookies(fixture.portal)).find(
    (v) => v.name === "portal-session",
  )!;
  return JSON.parse(
    (await redis.get(fixture.redisPrefix + "session:" + c.value))!,
  );
}
function signed(s: any, raw = "") {
  return {
    Authorization: "Bearer " + s.accessToken,
    "User-Agent": s.browserContext.userAgent,
    "Accept-Language": s.browserContext.acceptLanguage,
    Referer: s.browserContext.externalOrigin + "/",
    "x-request-datetime": new Date().toISOString(),
    "x-signature": createHmac("sha256", Buffer.from(s.signingKey, "base64"))
      .update(raw)
      .digest("base64"),
    "Content-Type": "application/json",
  };
}
async function java(
  page: Page,
  s: any,
  path: string,
  method = "GET",
  data?: unknown,
) {
  const raw = data === undefined ? "" : JSON.stringify(data);
  return page.request.fetch(fixture.issuer + path, {
    method,
    headers: signed(s, raw),
    ...(data === undefined ? {} : { data: raw }),
  });
}
function sql(query: string) {
  return JSON.parse(
    execFileSync(
      "python3",
      [
        "-c",
        `import sys,json,re;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d);q=json.load(sys.stdin);print(sql_text(c,d,q))`,
      ],
      {
        cwd: process.env.PORTAL_REPO_ROOT!,
        input: JSON.stringify(query),
        encoding: "utf8",
        timeout: 20000,
      },
    ),
  );
}
async function bff(
  page: Page,
  path: string,
  method: string,
  data: unknown = {},
) {
  const state = await (
    await page.request.get(fixture.portal + "/api/session")
  ).json();
  return page.request.fetch(fixture.portal + path, {
    method,
    headers: {
      Origin: fixture.portal,
      "Content-Type": "application/json",
      "x-csrf-token": state.csrfToken,
    },
    data: JSON.stringify(data),
  });
}
test("real assignment wire identifier corresponds to catalog GUID, distinct from assignment SQL GUID", async ({
  page,
  context,
}) => {
  await login(page);
  const s = await session(context);
  const home = await (await java(page, s, "/api/v1/portal/context")).json();
  const org = home.subject.organizationId;
  const appResponse = await java(
    page,
    s,
    "/api/v1/organizations/" + org + "/applications",
    "POST",
    {
      name: "Identifier proof",
      description: "Owned fixture",
      type: "SERVER",
      authMethod: "CLIENT_SECRET_JWT",
    },
  );
  check("identifier fixture client created", appResponse.status() === 201);
  const app = await appResponse.json();
  const r = await java(page, s, "/api/v1/endpoints", "POST", {
    name: "Identifier proof",
    description: "Owned API",
    uri: "https://api.example.test/owned-proof",
    allowedMethod: "GET",
    urn: "urn:owned:proof",
  });
  check("catalog fixture created", r.status() === 201);
  const resource = await r.json();
  check(
    "real assignment created",
    (
      await java(
        page,
        s,
        "/api/v1/organizations/" +
          org +
          "/applications/" +
          app.id +
          "/endpoints",
        "POST",
        { resourceId: resource.id },
      )
    ).status() === 201,
  );
  const list = await (
    await java(
      page,
      s,
      "/api/v1/organizations/" + org + "/applications/" + app.id + "/endpoints",
    )
  ).json();
  const rows = sql(
    "SELECT CONVERT(VARCHAR(36),r.RowGuid) AS assignment,CONVERT(VARCHAR(36),c.RowGuid) AS catalog FROM Resource.Resource r JOIN dbo.ResourceLibrary c ON c.ResourceLibraryId=r.ResourceLibraryId JOIN Client.Application a ON a.ApplicationId=r.ApplicationId WHERE a.RowGuid='" +
      app.id +
      "' FOR JSON PATH",
  );
  check(
    "SQL distinct catalog and assignment GUIDs",
    rows.length === 1 &&
      rows[0].assignment.toLowerCase() !== rows[0].catalog.toLowerCase(),
  );
  check(
    "HTTP list id is catalog GUID",
    list.length === 1 &&
      list[0].id.toLowerCase() === rows[0].catalog.toLowerCase() &&
      list[0].id.toLowerCase() !== rows[0].assignment.toLowerCase(),
  );
});
test("client onboarding, readback, credentials, exact URLs and revocation through browser and SQL", async ({
  page,
  context,
}) => {
  await login(page);
  const s = await session(context);
  const home = await (await java(page, s, "/api/v1/portal/context")).json();
  const org = home.subject.organizationId;
  await page.goto(fixture.portal + "/admin/organizations/" + org + "/clients");
  await expect(
    page.getByRole("heading", { name: "Clients", exact: true }),
  ).toBeVisible();
  await sanitizedScreenshot(page, "clients-list", [
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
  await page.getByRole("link", { name: "Create client", exact: true }).click();
  await expect(page.getByLabel("Client name")).toBeVisible();
  await hydrated(wizard(page));
  await page.getByLabel("Client name").fill("Browser server");
  await page.getByLabel("Client type").selectOption("SERVER");
  await page
    .getByLabel("Authentication method")
    .selectOption("CLIENT_SECRET_JWT");
  check(
    "server PKCE combination absent",
    (await page
      .getByLabel("Authentication method")
      .locator("option[value=PKCE]")
      .count()) === 0,
  );
  await sanitizedScreenshot(page, "clients-wizard", [
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
  await page
    .getByRole("button", { name: "Create identity", exact: true })
    .click();
  await expect(
    page.getByRole("link", { name: "Continue configuration", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Continue configuration", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Browser server", exact: true }),
  ).toBeVisible();
  const appId = new URL(page.url()).pathname.split("/").at(-1)!;
  check(
    "SQL client created once",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
        appId +
        "' AND Name='Browser server' FOR JSON PATH",
    )[0].n === 1,
  );
  check(
    "type and authentication immutable in detail",
    (await page.getByLabel("Client type").count()) === 0 &&
      (await page.getByLabel("Authentication method").count()) === 0,
  );
  check(
    "no arbitrary grants or scopes",
    (await page.getByRole("checkbox", { name: /scope|grant/i }).count()) === 0,
  );
  await hydrated(page.locator("#client-urls"));
  await page.getByLabel("Application URL").fill("http://localhost:3199");
  await page
    .getByLabel("Redirect URLs")
    .fill("http://localhost:3199/callback\nhttp://localhost:3199/callback");
  await page.getByLabel("Logout URLs").fill("http://localhost:3199/logout");
  await page.getByRole("button", { name: "Save URLs", exact: true }).click();
  await expect(
    page.getByRole("alert").filter({ hasText: /duplicate/i }),
  ).toBeVisible();
  await page
    .getByLabel("Redirect URLs")
    .fill("http://localhost:3199/callback?fixed=1");
  await page.getByRole("button", { name: "Save URLs", exact: true }).click();
  await expect(
    page.getByText("URLs saved and verified.", { exact: true }),
  ).toBeVisible();
  await hydrated(page.locator("#client-settings"));
  await page.getByLabel("Access token lifetime (seconds)").fill("599");
  await page.getByLabel("Reuse refresh tokens").check();
  await page
    .getByRole("button", { name: "Save token settings", exact: true })
    .click();
  await expect(
    page.getByText("Token settings saved and verified.", { exact: true }),
  ).toBeVisible();
  check(
    "SQL token seconds and reuse persisted",
    sql(
      "SELECT t.AccessTokenTimeToLive AS ttl,CAST(t.ReuseRefreshTokens AS INT) AS reuse FROM Client.TokenSetting t JOIN Client.Application a ON a.ApplicationId=t.ApplicationId WHERE a.RowGuid='" +
        appId +
        "' FOR JSON PATH",
    )[0].ttl === 599,
  );
  await page.reload();
  await expect(page.getByLabel("Reuse refresh tokens")).toBeChecked();
  await hydrated(page.locator("#client-credentials"));
  await page.getByLabel("Credential name").fill("first");
  await page
    .getByRole("button", { name: "Create credential", exact: true })
    .click();
  // Do not use locator assertions while a secret can be rendered: diagnostic DOM may contain it.
  let secret = "";
  try {
    await expect
      .poll(
        () =>
          page.evaluate(
            () => !!document.querySelector('[data-qa="secret-once"]'),
          ),
        { message: "one-time secret control appeared" },
      )
      .toBe(true);
    secret = await page.evaluate(
      () =>
        document.querySelector<HTMLInputElement>('[data-qa="secret-once"]')
          ?.value ?? "",
    );
    check("one-time secret nonempty", secret.length > 20);
    await page.evaluate(() =>
      document
        .querySelector<HTMLInputElement>('[data-qa="secret-ack"] input')
        ?.click(),
    );
    await page.evaluate(() =>
      document
        .querySelector<HTMLButtonElement>('[data-qa="secret-done"]')
        ?.click(),
    );
    await expect
      .poll(
        () =>
          page.evaluate(
            () => !document.querySelector('[data-qa="secret-once"]'),
          ),
        { message: "secret removed after acknowledgement" },
      )
      .toBe(true);
  } finally {
    await page.evaluate(() =>
      document
        .querySelector<HTMLButtonElement>('[data-qa="secret-dismiss"]')
        ?.click(),
    );
  }
  check(
    "secret erased from DOM on dismissal",
    !(await page.content()).includes(secret),
  );
  const metadata = await (
    await page.request.get(
      fixture.portal +
        "/api/admin/organizations/" +
        org +
        "/clients/" +
        appId +
        "/credentials",
    )
  ).json();
  check(
    "metadata has no secret fields or values",
    !JSON.stringify(metadata).includes(secret) &&
      !JSON.stringify(metadata).includes("secretValue"),
  );
  await page.reload();
  check(
    "secret absent after reload",
    !(await page.content()).includes(secret) &&
      (await page.getByRole("dialog").count()) === 0,
  );
  await sanitizedScreenshot(page, "clients-details", [
    secret,
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
  const documentResponse = await page.request.get(page.url());
  const documentText = await documentResponse.text();
  const rscResponse = await page.request.get(page.url(), {
    headers: { RSC: "1" },
  });
  const rscText = await rscResponse.text();
  check(
    "normal HTML and RSC omit credential secret and server session material",
    [secret, s.accessToken, s.idToken, s.signingKey].every(
      (v) => !documentText.includes(v) && !rscText.includes(v),
    ),
  );
  check(
    "normal reads are no-store",
    (documentResponse.headers()["cache-control"] ?? "").includes("no-store") &&
      (rscResponse.headers()["cache-control"] ?? "").includes("no-store"),
  );
  check(
    "browser storage never persisted secret or tokens",
    await page.evaluate(
      () => localStorage.length === 0 && sessionStorage.length === 0,
    ),
  );
  check(
    "secret absent from URL and private runtime logs",
    !page.url().includes(secret) &&
      [
        process.env.PORTAL_NEXT_LOG!,
        process.env.PORTAL_SCREENSHOT_DIR + "/java-repeat.log",
        process.env.PORTAL_SCREENSHOT_DIR + "/browser.log",
        fixture.faultEvents,
      ].every(
        (path) =>
          !existsSync(path) ||
          !readFileSync(path).includes(Buffer.from(secret)),
      ),
  );
  const app = await (
    await java(
      page,
      s,
      "/api/v1/organizations/" + org + "/applications/" + appId,
    )
  ).json();
  const issue = async () => {
    const assertion = await new SignJWT({})
      .setProtectedHeader({ alg: "HS256" })
      .setIssuer(app.clientId)
      .setSubject(app.clientId)
      .setAudience(fixture.issuer + "/oauth2/token")
      .setIssuedAt()
      .setExpirationTime("2m")
      .setJti(randomUUID())
      .sign(Buffer.from(secret));
    return page.request.post(fixture.issuer + "/oauth2/token", {
      form: {
        grant_type: "client_credentials",
        client_id: app.clientId,
        scope: "confidential-client",
        client_assertion_type:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        client_assertion: assertion,
      },
      headers: { "x-request-datetime": new Date().toISOString() },
    });
  };
  check("confidential actual token issuance", (await issue()).status() === 200);
  // Actual platform catalog and client-assignment controls, with SQL effects.
  await page.goto(fixture.portal + "/admin/resources");
  await hydrated(catalogForm(page));
  await page.getByLabel("Resource name").fill("Browser API");
  await page.getByLabel("Resource description").fill("Browser-owned API");
  await page
    .getByLabel("Resource URI")
    .fill(fixture.issuer + "/api/v1/organizations/" + org + "/applications");
  await page
    .getByRole("button", { name: "Create resource", exact: true })
    .click();
  await expect(
    page.getByText("Resource saved and read back.", { exact: true }),
  ).toBeVisible();
  await sanitizedScreenshot(page, "resource-catalog", [
    secret,
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
  const resource = sql(
    "SELECT CONVERT(VARCHAR(36),RowGuid) AS id FROM dbo.ResourceLibrary WHERE Name='Browser API' FOR JSON PATH",
  )[0];
  check("browser resource create SQL", !!resource?.id);
  await page
    .getByRole("button", { name: "Edit Browser API", exact: true })
    .click();
  await expect(page.getByLabel("Resource description")).toHaveValue(
    "Browser-owned API",
  );
  await page.getByLabel("Resource description").fill("Browser update");
  const updated = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname.toLowerCase() ===
        ("/api/admin/resources/" + resource.id).toLowerCase() &&
      response.request().method() === "PUT",
  );
  await page
    .getByRole("button", { name: "Save resource", exact: true })
    .click();
  check(
    "browser catalog update actual HTTP200",
    (await updated).status() === 200,
  );
  await expect(
    page.getByText("Resource saved and read back.", { exact: true }),
  ).toBeVisible();
  check(
    "browser resource update SQL",
    sql(
      "SELECT COUNT(*) AS n FROM dbo.ResourceLibrary WHERE RowGuid='" +
        resource.id +
        "' AND Description='Browser update' FOR JSON PATH",
    )[0].n === 1,
  );
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/" + appId,
  );
  const resources = page.locator("#client-resources");
  await hydrated(resources);
  await resources
    .getByLabel("Catalog resource")
    .selectOption(resource.id.toLowerCase());
  const assignPost = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname.toLowerCase().endsWith("/resources") &&
      response.request().method() === "POST",
  );
  await resources
    .getByRole("button", { name: "Assign resource", exact: true })
    .click();
  check("browser assignment sent one POST", (await assignPost).ok());
  await expect(
    resources.getByRole("button", { name: "Remove Browser API", exact: true }),
  ).toBeVisible();
  check(
    "browser assignment SQL",
    sql(
      "SELECT COUNT(*) AS n FROM Resource.Resource r JOIN dbo.ResourceLibrary l ON l.ResourceLibraryId=r.ResourceLibraryId WHERE l.RowGuid='" +
        resource.id +
        "' FOR JSON PATH",
    )[0].n === 1,
  );
  await page
    .getByRole("button", { name: "Remove Browser API", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Remove assignment", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Remove Browser API", exact: true }),
  ).toHaveCount(0);
  check(
    "browser assignment removal SQL",
    sql(
      "SELECT COUNT(*) AS n FROM Resource.Resource r JOIN dbo.ResourceLibrary l ON l.ResourceLibraryId=r.ResourceLibraryId WHERE l.RowGuid='" +
        resource.id +
        "' FOR JSON PATH",
    )[0].n === 0,
  );
  await page
    .getByRole("button", { name: "Disable first", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Disable credential", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Enable first", exact: true }),
  ).toBeVisible();
  check(
    "disabled secret rejects new real confidential authentication",
    (await issue()).status() === 401,
  );
  await page.getByRole("button", { name: "Delete first", exact: true }).click();
  await page
    .getByRole("button", { name: "Delete credential", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Enable first", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Deactivate client", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText("Browser server");
  await page
    .getByRole("button", { name: "Deactivate client", exact: true })
    .last()
    .click();
  await expect(
    page.getByRole("alert").filter({ hasText: "This client is inactive." }),
  ).toBeVisible();
  check(
    "browser deactivation SQL",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
        appId +
        "' AND Active=0 FOR JSON PATH",
    )[0].n === 1,
  );
});

test("timeout after committed identity and lost credential response reconcile without automatic write retries", async ({
  page,
  context,
}) => {
  await login(page);
  const s = await session(context);
  const home = await (await java(page, s, "/api/v1/portal/context")).json();
  const org = home.subject.organizationId;
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/new",
  );
  await hydrated(wizard(page));
  await page.getByLabel("Client name").fill("Uncertain server");
  await page.getByLabel("Client type").selectOption("SERVER");
  const creationBefore = existsSync(fixture.faultEvents)
    ? JSON.parse(readFileSync(fixture.faultEvents, "utf8")).filter(
        (v: any) =>
          v.method === "POST" &&
          v.path === "/services/api/v1/organizations/" + org + "/applications",
      ).length
    : 0;
  writeFileSync(
    fixture.faultControl,
    JSON.stringify({
      method: "POST",
      path: "/services/api/v1/organizations/" + org + "/applications",
    }),
    { mode: 0o600 },
  );
  await page
    .getByRole("button", { name: "Create identity", exact: true })
    .click();
  await expect(
    page.getByRole("alert").filter({ hasText: "Creation is uncertain." }),
  ).toBeVisible({ timeout: 20000 });
  const events = JSON.parse(readFileSync(fixture.faultEvents, "utf8"));
  check(
    "uncertain creation forwarded exactly once",
    events.filter(
      (v: any) =>
        v.method === "POST" &&
        v.path === "/services/api/v1/organizations/" + org + "/applications",
    ).length ===
      creationBefore + 1 &&
      sql(
        "SELECT COUNT(*) AS n FROM Client.Application WHERE Name='Uncertain server' FOR JSON PATH",
      )[0].n === 1,
  );
  const original = (
    await (
      await java(page, s, "/api/v1/organizations/" + org + "/applications")
    ).json()
  ).find((v: any) => v.name === "Uncertain server");
  const duplicate = await (
    await java(
      page,
      s,
      "/api/v1/organizations/" + org + "/applications",
      "POST",
      {
        name: "Imported candidate",
        description: "Owned ambiguity fixture",
        type: "SERVER",
        authMethod: "CLIENT_SECRET_JWT",
      },
    )
  ).json();
  sql(
    "UPDATE Client.Application SET Name='Uncertain server' WHERE RowGuid='" +
      duplicate.id +
      "'; SELECT 1 AS n FOR JSON PATH",
  );
  await page
    .getByRole("button", { name: "Read current onboarding state", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: /Use this client/ }),
  ).toHaveCount(2);
  check(
    "ambiguous candidates cannot trigger retry",
    (await page
      .getByRole("button", { name: "Review retry", exact: true })
      .count()) === 0,
  );
  await page
    .getByRole("button", {
      name: "Use this client " + original.clientId,
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page
    .getByRole("link", { name: "Continue configuration", exact: true })
    .click();
  const appId = new URL(page.url()).pathname.split("/").at(-1)!;
  const before = JSON.parse(readFileSync(fixture.faultEvents, "utf8")).filter(
    (v: any) => v.method === "POST" && v.path.endsWith("/applications"),
  ).length;
  await page.reload();
  check(
    "reload after reconciliation does not repeat identity POST",
    JSON.parse(readFileSync(fixture.faultEvents, "utf8")).filter(
      (v: any) => v.method === "POST" && v.path.endsWith("/applications"),
    ).length === before,
  );
  writeFileSync(
    fixture.faultControl,
    JSON.stringify({
      method: "POST",
      path:
        "/services/api/v1/organizations/" +
        org +
        "/applications/" +
        appId +
        "/credentials",
    }),
    { mode: 0o600 },
  );
  await hydrated(page.locator("#client-credentials"));
  await page.getByLabel("Credential name").fill("lost response");
  await page
    .getByRole("button", { name: "Create credential", exact: true })
    .click();
  await expect(
    page
      .getByRole("alert")
      .filter({ hasText: "A lost secret cannot be recovered" }),
  ).toBeVisible({ timeout: 20000 });
  check(
    "lost creation has metadata and no secret dialog",
    (await page.getByRole("dialog").count()) === 0 &&
      sql(
        "SELECT COUNT(*) AS n FROM Client.Credential c JOIN Client.Application a ON a.ApplicationId=c.ApplicationId WHERE a.RowGuid='" +
          appId +
          "' AND c.Name='lost response' FOR JSON PATH",
      )[0].n === 1,
  );
  check(
    "credential request never automatically retried",
    JSON.parse(readFileSync(fixture.faultEvents, "utf8")).filter(
      (v: any) =>
        v.method === "POST" && v.path.endsWith("/" + appId + "/credentials"),
    ).length === 1,
  );
  await page
    .getByRole("button", { name: "Disable lost response", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Disable credential", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Enable lost response", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Credential name").fill("replacement");
  await page
    .getByRole("button", { name: "Create credential", exact: true })
    .click();
  try {
    await expect
      .poll(
        () =>
          page.evaluate(
            () => !!document.querySelector('[data-qa="secret-once"]'),
          ),
        { message: "replacement secret appeared once" },
      )
      .toBe(true);
  } finally {
    await page.evaluate(() =>
      document
        .querySelector<HTMLButtonElement>('[data-qa="secret-dismiss"]')
        ?.click(),
    );
  }
  const rotated = await (
    await page.request.get(
      fixture.portal +
        "/api/admin/organizations/" +
        org +
        "/clients/" +
        appId +
        "/credentials",
    )
  ).json();
  check(
    "lost response rotated with actual metadata readback",
    rotated.some(
      (v: any) => v.name === "lost response" && v.status === "Disabled",
    ) &&
      rotated.some(
        (v: any) => v.name === "replacement" && v.status === "Active",
      ) &&
      sql(
        "SELECT COUNT(*) AS n FROM Client.Credential c JOIN Client.Application a ON a.ApplicationId=c.ApplicationId WHERE a.RowGuid='" +
          appId +
          "' FOR JSON PATH",
      )[0].n === 2,
  );
});

test("every client/resource mutation has real SQL readback and independent Java+BFF read-only denial", async ({
  page,
  context,
  browser,
}) => {
  await login(page);
  const admin = await session(context);
  const home = await (await java(page, admin, "/api/v1/portal/context")).json();
  const org = home.subject.organizationId;
  const b = "/api/admin/organizations/" + org + "/clients",
    j = "/api/v1/organizations/" + org + "/applications";
  const response = await bff(page, b, "POST", {
    name: "Matrix client",
    description: "Owned matrix",
    type: "SERVER",
    authMethod: "CLIENT_SECRET_JWT",
  });
  check("BFF client create201", response.status() === 201);
  const created = await response.json(),
    app = created.state.applicationId;
  const a = b + "/" + app,
    ja = j + "/" + app;
  check(
    "client create SQL row",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
        app +
        "' AND Active=1 FOR JSON PATH",
    )[0].n === 1,
  );
  check(
    "BFF identity update200",
    (
      await bff(page, a, "PUT", {
        name: "Matrix renamed",
        description: "Updated",
      })
    ).status() === 200,
  );
  check(
    "identity update SQL",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
        app +
        "' AND Name='Matrix renamed' AND Description='Updated' FOR JSON PATH",
    )[0].n === 1,
  );
  const urls = {
    applicationUrl: "https://matrix.example.test",
    returnUrls: ["https://matrix.example.test/callback?fixed=1"],
    logoutUrls: ["https://matrix.example.test/logout"],
  };
  check(
    "BFF exact URLs update200",
    (await bff(page, a + "/urls", "PUT", urls)).status() === 200,
  );
  check(
    "redirect SQL exact URL",
    sql(
      "SELECT COUNT(*) AS n FROM Client.RedirectUri r JOIN Client.Application a ON a.ApplicationId=r.ApplicationId WHERE a.RowGuid='" +
        app +
        "' AND r.RedirectUri='https://matrix.example.test/callback?fixed=1' FOR JSON PATH",
    )[0].n === 1,
  );
  const settings = {
    authCodeTimeToLive: 120,
    accessTokenTimeToLive: 500,
    refreshTokenTimeToLive: 3600,
    deviceCodeTimeToLive: 300,
    maxRequestTransitTime: 30,
    reuseRefreshTokens: false,
  };
  for (const reuseRefreshTokens of [false, true, false]) {
    check(
      "BFF token settings " + reuseRefreshTokens,
      (
        await bff(page, a + "/settings", "PUT", {
          ...settings,
          reuseRefreshTokens,
        })
      ).status() === 200,
    );
    const read = await (await page.request.get(fixture.portal + a)).json();
    const row = sql(
      "SELECT CAST(t.ReuseRefreshTokens AS INT) AS reuse,t.AccessTokenTimeToLive AS ttl FROM Client.TokenSetting t JOIN Client.Application a ON a.ApplicationId=t.ApplicationId WHERE a.RowGuid='" +
        app +
        "' FOR JSON PATH",
    )[0];
    check(
      "HTTP SQL persisted refresh reuse " + reuseRefreshTokens,
      read.detail.reuseRefreshTokens === reuseRefreshTokens &&
        row.reuse === Number(reuseRefreshTokens) &&
        row.ttl === 500,
    );
  }
  const credentialResponse = await bff(page, a + "/credentials", "POST", {
    name: "matrix first",
    type: "SHARED_SECRET",
    algorithm: "HS256",
  });
  check("BFF credential create201", credentialResponse.status() === 201);
  const first = await credentialResponse.json();
  check(
    "credential SQL created",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Credential WHERE RowGuid='" +
        first.id +
        "' FOR JSON PATH",
    )[0].n === 1,
  );
  check(
    "BFF active credential delete denied400",
    (await bff(page, a + "/credentials/" + first.id, "DELETE")).status() ===
      400,
  );
  check(
    "BFF credential disable200",
    (
      await bff(page, a + "/credentials/" + first.id, "PUT", {
        status: "Disabled",
      })
    ).status() === 200,
  );
  check(
    "disable SQL+HTTP",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Credential WHERE RowGuid='" +
        first.id +
        "' AND ModifiedOn IS NOT NULL FOR JSON PATH",
    )[0].n === 1 &&
      (
        await (
          await page.request.get(fixture.portal + a + "/credentials")
        ).json()
      ).some((v: any) => v.id === first.id && v.status === "Disabled"),
  );
  check(
    "BFF credential enable200",
    (
      await bff(page, a + "/credentials/" + first.id, "PUT", {
        status: "Active",
      })
    ).status() === 200,
  );
  check(
    "enable SQL+HTTP",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Credential WHERE RowGuid='" +
        first.id +
        "' AND ModifiedOn IS NOT NULL FOR JSON PATH",
    )[0].n === 1 &&
      (
        await (
          await page.request.get(fixture.portal + a + "/credentials")
        ).json()
      ).some((v: any) => v.id === first.id && v.status === "Active"),
  );
  const secondResponse = await bff(page, a + "/credentials", "POST", {
    name: "matrix second",
    type: "SHARED_SECRET",
    algorithm: "HS256",
  });
  check("second active credential create201", secondResponse.status() === 201);

  check(
    "two active ceiling Java+BFF",
    (
      await bff(page, a + "/credentials", "POST", {
        name: "third",
        type: "SHARED_SECRET",
        algorithm: "HS256",
      })
    ).status() === 409 &&
      (
        await java(page, admin, ja + "/credentials", "POST", {
          name: "third",
          type: "SHARED_SECRET",
          algorithm: "HS256",
        })
      ).status() === 409,
  );
  check(
    "two credential SQL rows only",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Credential c JOIN Client.Application a ON a.ApplicationId=c.ApplicationId WHERE a.RowGuid='" +
        app +
        "' FOR JSON PATH",
    )[0].n === 2,
  );
  const resourceInput = {
    name: "Matrix resource",
    description: "Owned",
    uri: fixture.issuer + j,
    allowedMethod: "GET",
    urn: "urn:owned:matrix",
  };
  const resourceResponse = await bff(
    page,
    "/api/admin/resources",
    "POST",
    resourceInput,
  );
  check("BFF resource create201", resourceResponse.status() === 201);
  const resource = await resourceResponse.json();
  check(
    "resource create SQL",
    sql(
      "SELECT COUNT(*) AS n FROM dbo.ResourceLibrary WHERE RowGuid='" +
        resource.id +
        "' AND Name='Matrix resource' FOR JSON PATH",
    )[0].n === 1,
  );
  check(
    "BFF resource update200",
    (
      await bff(page, "/api/admin/resources/" + resource.id, "PUT", {
        ...resourceInput,
        description: "Updated matrix",
      })
    ).status() === 200,
  );
  check(
    "resource update SQL",
    sql(
      "SELECT COUNT(*) AS n FROM dbo.ResourceLibrary WHERE RowGuid='" +
        resource.id +
        "' AND Description='Updated matrix' FOR JSON PATH",
    )[0].n === 1,
  );
  check(
    "BFF assign resource201",
    (
      await bff(page, a + "/resources", "POST", { resourceId: resource.id })
    ).status() === 201,
  );
  check(
    "assignment SQL+browser public DTO",
    sql(
      "SELECT COUNT(*) AS n FROM Resource.Resource r JOIN dbo.ResourceLibrary l ON l.ResourceLibraryId=r.ResourceLibraryId JOIN Client.Application a ON a.ApplicationId=r.ApplicationId WHERE a.RowGuid='" +
        app +
        "' AND l.RowGuid='" +
        resource.id +
        "' FOR JSON PATH",
    )[0].n === 1 &&
      (
        await (await page.request.get(fixture.portal + a + "/resources")).json()
      ).every(
        (v: any) =>
          v.catalogResourceId === resource.id &&
          !("resourceId" in v) &&
          !("resourceLibraryId" in v),
      ),
  );
  const role = await (
    await java(
      page,
      admin,
      "/api/v1/organizations/" + org + "/groups",
      "POST",
      {
        name: "F3 reader",
        description: "Owned",
        permissionKeys: ["idp-admin-app:read"],
      },
    )
  ).json();
  const username = "reader_" + randomUUID().replaceAll("-", "").slice(0, 12);
  const user = await (
    await java(page, admin, "/api/v1/users", "POST", {
      firstName: "Reader",
      lastName: "Owned",
      username,
      email: username + "@example.test",
      password: Buffer.from(fixture.secondPassword).toString("base64"),
      phoneNumber: "+19999999998",
      orgGuid: org,
      branding: "idp",
      syncFlag: false,
    })
  ).json();
  check(
    "reader role assigned",
    (
      await java(
        page,
        admin,
        "/api/v1/organizations/" + org + "/users/" + user.id + "/group",
        "PUT",
        { groupId: role.id },
      )
    ).status() === 204,
  );
  await java(
    page,
    admin,
    "/api/v1/users/" + user.id + "/security-settings",
    "PUT",
    { twoFactorEnabled: false },
  );
  const viewer = await browser.newContext({
    userAgent: "PortalAcceptance/1.0",
    locale: "en-US",
    extraHTTPHeaders: { "Accept-Language": "en-US" },
    timezoneId: "Asia/Kolkata",
  });
  const vp = await viewer.newPage();
  try {
    await vp.goto(fixture.portal);
    await vp.getByRole("link", { name: "Sign in", exact: true }).click();
    await vp.locator("#username").fill(username);
    await vp.locator("#password").fill(fixture.secondPassword);
    await vp.getByRole("button", { name: "Sign in", exact: true }).click();
    await vp.waitForURL(fixture.portal + "/account");
    const vs = await session(viewer);
    const mutations: [string, string, string, unknown][] = [
      [
        "POST",
        b,
        j,
        {
          name: "Denied",
          description: "",
          type: "SERVER",
          authMethod: "CLIENT_SECRET_JWT",
        },
      ],
      ["PUT", a, ja, { name: "Denied", description: "" }],
      ["PUT", a + "/urls", ja + "/urls", urls],
      ["PUT", a + "/settings", ja + "/settings", settings],
      [
        "POST",
        a + "/credentials",
        ja + "/credentials",
        { name: "Denied", type: "SHARED_SECRET", algorithm: "HS256" },
      ],
      [
        "PUT",
        a + "/credentials/" + first.id,
        ja + "/credentials/" + first.id,
        { status: "Disabled" },
      ],
      [
        "DELETE",
        a + "/credentials/" + first.id,
        ja + "/credentials/" + first.id,
        undefined,
      ],
      [
        "POST",
        a + "/resources",
        ja + "/endpoints",
        { resourceId: resource.id },
      ],
      [
        "DELETE",
        a + "/resources/" + resource.id,
        ja + "/endpoints/" + resource.id,
        undefined,
      ],
      ["DELETE", a, ja, undefined],
      ["POST", "/api/admin/resources", "/api/v1/endpoints", resourceInput],
      [
        "PUT",
        "/api/admin/resources/" + resource.id,
        "/api/v1/endpoints/" + resource.id,
        resourceInput,
      ],
    ];
    for (const [method, bpath, jpath, input] of mutations) {
      check(
        "read-only BFF denied " +
          method +
          " " +
          bpath.replace(/[0-9a-f-]{36}/g, "{id}"),
        (await bff(vp, bpath, method, input)).status() === 403,
      );
      check(
        "read-only Java independently denied " +
          method +
          " " +
          jpath.replace(/[0-9a-f-]{36}/g, "{id}"),
        (await java(vp, vs, jpath, method, input)).status() === 403,
      );
    }
    check(
      "denials preserved SQL client/resource/credential state",
      sql(
        "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
          app +
          "' AND Active=1 AND Name='Matrix renamed' FOR JSON PATH",
      )[0].n === 1 &&
        sql(
          "SELECT COUNT(*) AS n FROM dbo.ResourceLibrary WHERE RowGuid='" +
            resource.id +
            "' AND Description='Updated matrix' FOR JSON PATH",
        )[0].n === 1 &&
        sql(
          "SELECT COUNT(*) AS n FROM Client.Credential c JOIN Client.Application a ON a.ApplicationId=c.ApplicationId WHERE a.RowGuid='" +
            app +
            "' FOR JSON PATH",
        )[0].n === 2,
    );
  } finally {
    await viewer.close();
  }
  check(
    "BFF remove assignment204 readback",
    (await bff(page, a + "/resources/" + resource.id, "DELETE")).status() ===
      200,
  );
  check(
    "assignment removal SQL",
    sql(
      "SELECT COUNT(*) AS n FROM Resource.Resource r JOIN dbo.ResourceLibrary l ON l.ResourceLibraryId=r.ResourceLibraryId JOIN Client.Application a ON a.ApplicationId=r.ApplicationId WHERE a.RowGuid='" +
        app +
        "' AND l.RowGuid='" +
        resource.id +
        "' FOR JSON PATH",
    )[0].n === 0,
  );
  await bff(page, a + "/credentials/" + first.id, "PUT", {
    status: "Disabled",
  });
  check(
    "BFF disabled credential delete",
    (await bff(page, a + "/credentials/" + first.id, "DELETE")).status() ===
      200,
  );
  check(
    "credential deleted SQL status preserved history",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Credential WHERE RowGuid='" +
        first.id +
        "' AND ModifiedOn IS NOT NULL FOR JSON PATH",
    )[0].n === 1 &&
      (
        await (
          await page.request.get(fixture.portal + a + "/credentials")
        ).json()
      ).some((v: any) => v.id === first.id && v.status === "Inactive"),
  );
  check(
    "BFF client deactivate200",
    (await bff(page, a, "DELETE")).status() === 200,
  );
  check(
    "inactive client HTTP SQL list semantics",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
        app +
        "' AND Active=0 FOR JSON PATH",
    )[0].n === 1 &&
      (await (await page.request.get(fixture.portal + a)).json()).detail
        .active === false &&
      !(await (await page.request.get(fixture.portal + b)).json()).some(
        (v: any) => v.id === app,
      ),
  );
});

test("configured web PKCE client really issues a human token and deactivation rejects its existing bearer", async ({
  page,
  context,
}) => {
  await login(page);
  const admin = await session(context),
    home = await (await java(page, admin, "/api/v1/portal/context")).json(),
    org = home.subject.organizationId;
  const base = "/api/admin/organizations/" + org + "/clients";
  const createdResponse = await bff(page, base, "POST", {
    name: "Actual PKCE",
    description: "Owned real flow",
    type: "WEB",
    authMethod: "PKCE",
  });
  check("PKCE identity create201", createdResponse.status() === 201);
  const created = await createdResponse.json(),
    app = created.detail;
  let code = "";
  const callback = createServer((req, res) => {
    const url = new URL(req.url!, "http://127.0.0.1");
    if (url.pathname === "/callback") {
      code = url.searchParams.get("code") ?? "";
      res.writeHead(303, { Location: "/done", "Cache-Control": "no-store" });
    } else
      res.writeHead(200, {
        "Content-Type": "text/html",
        "Cache-Control": "no-store",
      });
    res.end("<!doctype html><title>Owned callback complete</title>");
  });
  await new Promise<void>((r) => callback.listen(0, "127.0.0.1", r));
  const origin = "http://127.0.0.1:" + (callback.address() as AddressInfo).port,
    redirect = origin + "/callback";
  try {
    check(
      "PKCE URL settings readback",
      (
        await bff(page, base + "/" + app.id + "/urls", "PUT", {
          applicationUrl: origin,
          returnUrls: [redirect],
          logoutUrls: [origin + "/done"],
        })
      ).status() === 200,
    );
    const verifier = randomBytes(32).toString("base64url"),
      authorize = new URL(fixture.issuer + "/oauth2/authorize");
    authorize.search = new URLSearchParams({
      response_type: "code",
      client_id: app.clientId,
      redirect_uri: redirect,
      scope: "openid profile email",
      branding: "idp",
      state: randomUUID(),
      nonce: randomUUID(),
      code_challenge: createHash("sha256").update(verifier).digest("base64url"),
      code_challenge_method: "S256",
      "x-request-datetime": new Date().toISOString(),
    }).toString();
    await context.clearCookies({ domain: new URL(fixture.issuer).hostname });
    await page.goto(authorize.href, { referer: fixture.portal + "/" });
    await page.locator("#username").fill(fixture.username);
    await page.locator("#password").fill(fixture.password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.waitForURL(origin + "/done");
    check("actual PKCE authorization code received", code.length > 0);
    const response = await page.request.post(fixture.issuer + "/oauth2/token", {
      form: {
        grant_type: "authorization_code",
        client_id: app.clientId,
        code,
        redirect_uri: redirect,
        code_verifier: verifier,
      },
    });
    check("actual configured PKCE token issuance", response.status() === 200);
    const tokens = await response.json();
    code = "";
    const other = {
      accessToken: tokens.access_token,
      signingKey: tokens.signing_key,
      browserContext: admin.browserContext,
    };
    check(
      "new PKCE human token authenticates",
      (await java(page, other, "/api/v1/portal/context")).status() === 200,
    );
    check(
      "deactivate configured PKCE client",
      (await bff(page, base + "/" + app.id, "DELETE")).status() === 200,
    );
    check(
      "deactivated client SQL inactive",
      sql(
        "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
          app.id +
          "' AND Active=0 FOR JSON PATH",
      )[0].n === 1,
    );
    check(
      "existing bearer from deactivated client rejected",
      (await java(page, other, "/api/v1/portal/context")).status() === 401,
    );
    check(
      "deactivated client rejects new authorization",
      (await page.request.get(authorize.href, { maxRedirects: 0 })).status() !==
        200,
    );
  } finally {
    await new Promise<void>((r) => callback.close(() => r()));
  }
});

test("guided configuration uses readback status, keyboard section links and accessible PKCE progress", async ({
  page,
  context,
}) => {
  await login(page);
  const s = await session(context),
    home = await (await java(page, s, "/api/v1/portal/context")).json(),
    org = home.subject.organizationId;
  const app = await (
    await java(
      page,
      s,
      "/api/v1/organizations/" + org + "/applications",
      "POST",
      {
        name: "Guided PKCE",
        description: "Owned guided progress",
        type: "WEB",
        authMethod: "PKCE",
      },
    )
  ).json();
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/" + app.id,
  );
  const nav = page.getByRole("navigation", {
    name: "Client configuration",
    exact: true,
  });
  await expect(nav).toBeVisible();
  await hydrated(nav);
  check(
    "configuration navigation is an ordered six-step list",
    (await nav.locator("ol > li").count()) === 6,
  );
  await expect(
    page.getByText("Next needed: URLs", { exact: true }),
  ).toBeVisible();
  await expect(
    nav.getByText("Not required for PKCE", { exact: true }),
  ).toBeVisible();
  await expect(nav.getByText("0 assigned", { exact: true })).toBeVisible();
  await nav.getByRole("link", { name: "URLs", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#client-urls")).toBeFocused();
  check("keyboard configuration link focuses actual URL section", true);
  const saved = await bff(
    page,
    "/api/admin/organizations/" + org + "/clients/" + app.id + "/urls",
    "PUT",
    {
      applicationUrl: "https://guided.example.test",
      returnUrls: ["https://guided.example.test/callback"],
      logoutUrls: ["https://guided.example.test/logout"],
    },
  );
  check("guided URL save uses actual reviewed route", saved.status() === 200);
  await page.reload();
  await expect(
    page.getByText("Next needed: Readiness", { exact: true }),
  ).toBeVisible();
  await expect(
    nav.getByRole("link", { name: "Readiness", exact: true }),
  ).toHaveAttribute("aria-current", "step");
  const { default: AxeBuilder } = await import("@axe-core/playwright");
  const a11y = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  check("guided detail automated accessibility", a11y.violations.length === 0);
  await sanitizedScreenshot(page, "clients-guided-details", [
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
  await page.goto(fixture.portal + "/admin/organizations/" + org + "/clients");
  await expect(
    page.getByRole("heading", { name: "Clients", exact: true }),
  ).toBeVisible();
  await sanitizedScreenshot(page, "clients-list-current", [
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/new",
  );
  await expect(page.getByLabel("Client name")).toBeVisible();
  await sanitizedScreenshot(page, "clients-wizard-current", [
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
  await bff(page, "/api/admin/resources", "POST", {
    name: "Guided API",
    description: "Owned viewport fixture",
    uri: "https://guided.example.test/api",
    allowedMethod: "GET",
    urn: "urn:owned:guided",
  });
  await page.goto(fixture.portal + "/admin/resources");
  await expect(
    page.getByRole("heading", { name: "Resource catalog", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Create resource", exact: true }),
  ).toBeEnabled();
  await sanitizedScreenshot(page, "resource-catalog-current", [
    s.accessToken,
    s.idToken,
    s.signingKey,
  ]);
});

test("RHF forms preserve dirty unrelated edits, validate fields and reset only after owned readback or explicit saved-state reload", async ({
  page,
  context,
}) => {
  await login(page);
  const s = await session(context),
    home = await (await java(page, s, "/api/v1/portal/context")).json(),
    org = home.subject.organizationId;
  const app = await (
    await java(
      page,
      s,
      "/api/v1/organizations/" + org + "/applications",
      "POST",
      {
        name: "RHF readback",
        description: "Owned form fixture",
        type: "WEB",
        authMethod: "PKCE",
      },
    )
  ).json();
  const api = "/api/admin/organizations/" + org + "/clients/" + app.id;
  await bff(page, api + "/urls", "PUT", {
    applicationUrl: "https://saved.example.test",
    returnUrls: ["https://saved.example.test/callback"],
    logoutUrls: ["https://saved.example.test/logout"],
  });
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/" + app.id,
  );
  await hydrated(page.locator("#client-identity"));
  await hydrated(page.locator("#client-urls"));
  await hydrated(page.locator("#client-settings"));
  await page.getByLabel("Client name").fill("Unsaved local identity");
  await page.getByLabel("Application URL").fill("https://draft.example.test");
  await page.getByLabel("Access token lifetime (seconds)").fill("599");
  await page
    .getByRole("button", { name: "Save token settings", exact: true })
    .click();
  await expect(
    page.getByText("Token settings saved and verified.", { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Client name")).toHaveValue(
    "Unsaved local identity",
  );
  await expect(page.getByLabel("Application URL")).toHaveValue(
    "https://draft.example.test",
  );
  check("other section save preserves dirty identity and URL fields", true);
  check(
    "unsaved identity not written to SQL",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
        app.id +
        "' AND Name='RHF readback' FOR JSON PATH",
    )[0].n === 1,
  );
  await page.getByRole("button", { name: "Save URLs", exact: true }).click();
  await expect(
    page.getByText("URLs saved and verified.", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Unsaved URL changes.", { exact: true }),
  ).toHaveCount(0);
  check("own successful readback clears URL dirty state", true);
  await bff(page, api, "PUT", {
    name: "Authoritative saved identity",
    description: "Owned form fixture",
  });
  await page
    .getByRole("button", { name: "Reload saved configuration", exact: true })
    .click();
  await expect(page.getByLabel("Client name")).toHaveValue(
    "Authoritative saved identity",
  );
  check(
    "explicit reload uses actual saved identity instead of stale captured defaults",
    true,
  );
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/new",
  );
  await hydrated(wizard(page));
  await page.getByLabel("Client name").fill("   ");
  await page
    .getByRole("button", { name: "Create identity", exact: true })
    .click();
  await expect(page.getByLabel("Client name")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  check(
    "Zod field failure does not claim or create a wizard",
    !(
      await (
        await page.request.get(
          fixture.portal +
            "/api/admin/organizations/" +
            org +
            "/clients/wizard",
        )
      ).json()
    ).state,
  );
});

test("definite initial duplicate rejection restores editable identity and permits exactly one corrected creation", async ({
  page,
  context,
}) => {
  await login(page);
  const s = await session(context),
    home = await (await java(page, s, "/api/v1/portal/context")).json(),
    org = home.subject.organizationId;
  const existing = await java(
    page,
    s,
    "/api/v1/organizations/" + org + "/applications",
    "POST",
    {
      name: "Definite duplicate",
      description: "Owned preexisting fixture",
      type: "WEB",
      authMethod: "PKCE",
    },
  );
  check("duplicate fixture real Java201", existing.status() === 201);
  const path = "/services/api/v1/organizations/" + org + "/applications";
  const before = existsSync(fixture.faultEvents)
    ? JSON.parse(readFileSync(fixture.faultEvents, "utf8")).filter(
        (v: { method: string; path: string }) =>
          v.method === "POST" && v.path === path,
      ).length
    : 0;
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/new",
  );
  await hydrated(wizard(page));
  await page.getByLabel("Client name").fill("Definite duplicate");
  const rejected = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname ===
        "/api/admin/organizations/" + org + "/clients" &&
      r.request().method() === "POST",
  );
  await page
    .getByRole("button", { name: "Create identity", exact: true })
    .click();
  check(
    "definite existing-name rejection remains409",
    (await rejected).status() === 409,
  );
  check(
    "duplicate rejection inserts no SQL row",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE Name='Definite duplicate' FOR JSON PATH",
    )[0].n === 1,
  );
  await expect(page.getByLabel("Client name")).toBeVisible();
  await expect(page.getByLabel("Client name")).toHaveValue(
    "Definite duplicate",
  );
  await expect(
    page.getByRole("alert").filter({ hasText: /edit|different name/i }),
  ).toBeVisible();
  check(
    "definite initial rejection retires the noncommitting wizard",
    !(
      await (
        await page.request.get(
          fixture.portal +
            "/api/admin/organizations/" +
            org +
            "/clients/wizard",
        )
      ).json()
    ).state,
  );
  await page.getByLabel("Client name").fill("Corrected unique identity");
  const accepted = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname ===
        "/api/admin/organizations/" + org + "/clients" &&
      r.request().method() === "POST",
  );
  await page
    .getByRole("button", { name: "Create identity", exact: true })
    .click();
  check("corrected identity creates201", (await accepted).status() === 201);
  await expect(
    page.getByRole("link", { name: "Continue configuration", exact: true }),
  ).toBeVisible();
  check(
    "corrected identity creates exactly one SQL row",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE Name='Corrected unique identity' FOR JSON PATH",
    )[0].n === 1,
  );
  const events = JSON.parse(readFileSync(fixture.faultEvents, "utf8"))
    .filter(
      (v: { method: string; path: string }) =>
        v.method === "POST" && v.path === path,
    )
    .slice(before);
  check(
    "one rejected and exactly one successful create with no automatic replay",
    events.length === 2 && events[0].status === 409 && events[1].status === 201,
  );
});

test("definite retry rejection preserves and reconciles the earlier genuinely uncertain creation", async ({
  page,
  context,
}) => {
  await login(page);
  const s = await session(context),
    home = await (await java(page, s, "/api/v1/portal/context")).json(),
    org = home.subject.organizationId;
  const baseline = await (
    await java(
      page,
      s,
      "/api/v1/organizations/" + org + "/applications",
      "POST",
      {
        name: "Retry baseline occupant",
        description: "Owned baseline fixture",
        type: "WEB",
        authMethod: "PKCE",
      },
    )
  ).json();
  const api = "/api/admin/organizations/" + org + "/clients",
    path = "/services/api/v1/organizations/" + org + "/applications";
  await page.goto(
    fixture.portal + "/admin/organizations/" + org + "/clients/new",
  );
  await hydrated(wizard(page));
  await page.getByLabel("Client name").fill("Prior uncertain identity");
  writeFileSync(
    fixture.faultControl,
    JSON.stringify({ method: "POST", path }),
    { mode: 0o600 },
  );
  await page
    .getByRole("button", { name: "Create identity", exact: true })
    .click();
  await expect(
    page.getByRole("alert").filter({ hasText: "Creation is uncertain." }),
  ).toBeVisible({ timeout: 20000 });
  const prior = (
    await (await page.request.get(fixture.portal + api + "/wizard")).json()
  ).state;
  const original = (
    await (
      await java(page, s, "/api/v1/organizations/" + org + "/applications")
    ).json()
  ).find((v: { name: string }) => v.name === "Prior uncertain identity");
  sql(
    "UPDATE Client.Application SET Name='Previously uncertain renamed' WHERE RowGuid='" +
      original.id +
      "'; UPDATE Client.Application SET Name='Prior uncertain identity' WHERE RowGuid='" +
      baseline.id +
      "'; SELECT 1 AS n FOR JSON PATH",
  );
  await page
    .getByRole("button", { name: "Read current onboarding state", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Review retry", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Review retry", exact: true }).click();
  const rejected = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === api + "/wizard" &&
      r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Create once", exact: true }).click();
  check(
    "definite retry prewrite rejection remains409",
    (await rejected).status() === 409,
  );
  await expect(
    page
      .getByRole("alert")
      .filter({ hasText: /earlier creation is still uncertain/i }),
  ).toBeVisible();
  const restored = (
    await (await page.request.get(fixture.portal + api + "/wizard")).json()
  ).state;
  check(
    "retry rejection restores prior attempt correlation and fixed expiry",
    restored.attemptId === prior.attemptId &&
      restored.identity.name === prior.identity.name &&
      restored.expiresAt === prior.expiresAt &&
      restored.phase === "uncertain" &&
      restored.baseline.includes(baseline.id),
  );
  check(
    "prior possibly committed application survives retry rejection",
    sql(
      "SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid='" +
        original.id +
        "' AND Name='Previously uncertain renamed' AND Active=1 FOR JSON PATH",
    )[0].n === 1,
  );
  check(
    "prior real uncertainty is never cleared into a fresh editable form",
    (await page.getByLabel("Client name").count()) === 0,
  );
  sql(
    "UPDATE Client.Application SET Name='Retry baseline occupant' WHERE RowGuid='" +
      baseline.id +
      "'; UPDATE Client.Application SET Name='Prior uncertain identity' WHERE RowGuid='" +
      original.id +
      "'; SELECT 1 AS n FOR JSON PATH",
  );
  await page
    .getByRole("button", { name: "Read current onboarding state", exact: true })
    .click();
  await page
    .getByRole("button", {
      name: "Use this client " + original.clientId,
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "Continue configuration", exact: true }),
  ).toHaveAttribute(
    "href",
    "/admin/organizations/" + org + "/clients/" + original.id,
  );
  check(
    "preserved prior uncertain identity can still be explicitly reconciled",
    true,
  );
});
