import {
  test,
  expect,
  type Page,
  type BrowserContext,
  type Browser,
  chromium,
} from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import {
  readFileSync,
  writeFileSync,
  existsSync,
  mkdtempSync,
  mkdirSync,
  rmSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createClient } from "redis";
import { createHmac, createHash, randomBytes } from "node:crypto";
import { execFileSync } from "node:child_process";
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";

const fixture = JSON.parse(
  readFileSync(process.env.PORTAL_E2E_CONFIG!, "utf8"),
);
const checks: { id: string; passed: boolean }[] = [];
const blocked: { id: string; reason: string }[] = [];
function check(id: string, value: boolean) {
  checks.push({ id, passed: value });
  writeFileSync(
    process.env.PORTAL_SCREENSHOT_DIR + "/navigation-progress.json",
    JSON.stringify(checks),
    { mode: 0o600 },
  );
  expect(value, id).toBe(true);
}
const redis = createClient({
  url: "redis://127.0.0.1:16379/0",
  socket: { reconnectStrategy: false },
});
test.beforeAll(() => redis.connect());
test.afterAll(async () => {
  await redis.quit();
  writeFileSync(
    process.env.PORTAL_E2E_REPORT!,
    JSON.stringify({
      checks: [
        ...(existsSync(process.env.PORTAL_E2E_REPORT!)
          ? JSON.parse(readFileSync(process.env.PORTAL_E2E_REPORT!, "utf8"))
              .checks
          : []),
        ...checks,
      ],
      blocked: [
        ...(existsSync(process.env.PORTAL_E2E_REPORT!)
          ? (JSON.parse(readFileSync(process.env.PORTAL_E2E_REPORT!, "utf8"))
              .blocked ?? [])
          : []),
        ...blocked,
      ],
    }),
    {
      mode: 0o600,
    },
  );
});
async function login(
  page: Page,
  username = fixture.username,
  password = fixture.password,
) {
  await page.goto(fixture.portal);
  await page.getByRole("link", { name: "Sign in", exact: true }).click();
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.waitForURL(fixture.portal + "/account");
  await expect(
    page.getByRole("heading", { name: "Your account", exact: true }),
  ).toBeVisible();
}
async function session(context: BrowserContext) {
  const cookie = (await context.cookies(fixture.portal)).find(
    (c) => c.name === "portal-session",
  )!;
  return JSON.parse(
    (await redis.get(fixture.redisPrefix + "session:" + cookie.value))!,
  );
}
function headers(s: any, raw = "") {
  return {
    Authorization: "Bearer " + s.accessToken,
    "User-Agent": s.browserContext.userAgent,
    "Accept-Language": s.browserContext.acceptLanguage,
    Referer: s.browserContext.externalOrigin + "/",
    "x-request-datetime": new Date().toISOString(),
    "x-signature": createHmac("sha256", Buffer.from(s.signingKey, "base64"))
      .update(raw)
      .digest("base64"),
  };
}
async function mutation(
  page: Page,
  s: any,
  path: string,
  data: unknown,
  method = "POST",
) {
  const raw = JSON.stringify(data);
  const response = await page.request.fetch(fixture.issuer + path, {
    method,
    headers: { ...headers(s, raw), "Content-Type": "application/json" },
    data: raw,
  });
  check(
    "owned real Java mutation " +
      method +
      " " +
      path.replace(/[0-9a-f-]{36}/g, "{id}"),
    [200, 201, 204].includes(response.status()),
  );
  return response;
}
async function accessibility(page: Page, name: string) {
  await expect(page.locator("main[aria-busy=true]")).toHaveCount(0);
  await expect(page.locator("main h1")).toBeVisible();
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  writeFileSync(
    process.env.PORTAL_SCREENSHOT_DIR +
      "/a11y-" +
      name.replaceAll(" ", "-") +
      ".json",
    JSON.stringify(
      result.violations.map((v) => ({
        id: v.id,
        impact: v.impact,
        targets: v.nodes.map((n) => n.target),
        summary: v.nodes.map((n) => n.failureSummary),
      })),
    ),
    { mode: 0o600 },
  );
  check(name + " automated accessibility", result.violations.length === 0);
}
async function screenshot(page: Page, name: string, fullPage = true) {
  await expect(page.locator("main[aria-busy=true]")).toHaveCount(0);
  await expect(page.locator("main h1")).toBeVisible();
  const s = await session(page.context());
  const content = await page.content();
  check(
    name + " sanitized before screenshot",
    ![s.accessToken, s.idToken, s.signingKey].some((v) =>
      content.includes(v),
    ) &&
      [...new URL(page.url()).searchParams.keys()].every(
        (key) => key === "organizationId",
      ),
  );
  await page.screenshot({
    path: process.env.PORTAL_SCREENSHOT_DIR + "/" + name + ".png",
    fullPage,
  });
}

test("enterprise navigation, keyboard, accessibility and explicit platform scope", async ({
  page,
  context,
}) => {
  await login(page);
  await expect(
    page.getByRole("navigation", { name: "Portal navigation" }),
  ).toBeVisible();
  check(
    "functional administration navigation visible",
    await page
      .getByRole("link", { name: "Administration", exact: true })
      .isVisible(),
  );
  await page.keyboard.press("Tab");
  await page.getByRole("link", { name: "Skip to content" }).focus();
  await page.keyboard.press("Enter");
  check(
    "skip link reaches main content",
    await page.locator("main").evaluate((e) => e === document.activeElement),
  );
  await screenshot(page, "account-desktop");
  await accessibility(page, "Account desktop");
  await page
    .getByRole("button", { name: "Change profile", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Cancel", exact: true }).focus();
  await page.keyboard.press("Shift+Tab");
  await expect(
    page.getByRole("button", { name: "Continue", exact: true }),
  ).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("button", { name: "Cancel", exact: true }),
  ).toBeFocused();
  check(
    "confirmation traps keyboard focus",
    await page
      .getByRole("dialog")
      .evaluate((e) => e.contains(document.activeElement)),
  );
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("button", { name: "Change profile", exact: true }),
  ).toBeFocused();
  check("confirmation restores trigger focus", true);
  await page.goto(fixture.portal + "/account/security");
  await expect(
    page.getByRole("heading", { name: "Security", exact: true }),
  ).toBeVisible();
  await accessibility(page, "Security desktop");
  await screenshot(page, "security-desktop");
  await page.getByRole("link", { name: "Administration", exact: true }).click();
  const s = await session(context);
  const orgResponse = await mutation(page, s, "/api/v1/organizations", {
    name: "Navigation fixture",
    description: "Owned test organization",
  });
  const org = await orgResponse.json();
  await page.reload();
  await page.getByLabel("Organization", { exact: true }).selectOption(org.id);
  await page.getByRole("button", { name: "Apply scope", exact: true }).click();
  await page.waitForURL(
    (url) => url.searchParams.get("organizationId") === org.id,
  );
  check(
    "platform selection explicitly changes request scope",
    new URL(page.url()).searchParams.get("organizationId") === org.id,
  );
  await expect(
    page.getByRole("heading", { name: "Navigation fixture", exact: true }),
  ).toBeVisible();
  check(
    "selected organization is visible",
    await page
      .getByRole("heading", { name: "Navigation fixture", exact: true })
      .isVisible(),
  );
  await accessibility(page, "Administration desktop");
  await screenshot(page, "administration-desktop");
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto(fixture.portal + "/account");
  check(
    "narrow viewport does not scroll horizontally",
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page
    .getByRole("button", { name: "Open navigation", exact: true })
    .click();
  await expect(
    page.getByRole("dialog", { name: "Portal navigation" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("button", { name: "Open navigation", exact: true }),
  ).toBeFocused();
  await accessibility(page, "Account narrow");
  await screenshot(page, "account-narrow");
  await page.setViewportSize({ width: 640, height: 480 });
  check(
    "200 percent equivalent layout reflows without horizontal scrolling",
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await accessibility(page, "Account 200 percent equivalent");
  await screenshot(page, "account-200-percent");
});

async function openHosted(page: Page, label: string) {
  await page.getByRole("button", { name: label, exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Continue", exact: true })
    .click();
  await expect(
    page.getByRole("heading", {
      name: "Continue to account settings",
      exact: true,
    }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Continue", exact: true }).click();
}
function ownedProfileSql(id: string, field: "firstName" | "activeSessions") {
  return execFileSync(
    "python3",
    [
      "-c",
      `import sys,json,uuid;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();v=json.loads(sys.stdin.read());i=str(uuid.UUID(v['id']));q={"firstName":"SELECT FirstName FROM Person.Profile WHERE RowGuid='"+i+"'", "activeSessions":"SELECT COUNT(*) FROM Token.AuthSession s JOIN Person.Credential c ON c.UserName=s.SubjectId JOIN Person.Profile p ON p.ProfileId=c.ProfileId JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId AND e.SourceCode=s.Branding WHERE p.RowGuid='"+i+"' AND s.AuthSessionStatusId=1"};print(sql_text(c,d,q[v['field']],scalar=True))`,
    ],
    {
      cwd: process.env.PORTAL_REPO_ROOT,
      input: JSON.stringify({ id, field }),
      stdio: ["pipe", "pipe", "pipe"],
    },
  )
    .toString()
    .trim();
}
test("real hosted account profile/password, tenant identity, SQL revocation and reauthentication", async ({
  page,
  context,
  browser,
}) => {
  await login(page);
  const admin = await session(context);
  const adminDto = await (
    await page.request.get(fixture.portal + "/api/context")
  ).json();
  const tenant = await (
    await mutation(page, admin, "/api/v1/organizations", {
      name: "Hosted tenant",
      description: "Owned self-service acceptance",
    })
  ).json();
  const username = "self_" + fixture.secondUsername;
  const password = fixture.secondPassword;
  const next = "N9!" + randomBytes(20).toString("hex") + "qZ";
  const user = await (
    await mutation(page, admin, "/api/v1/users", {
      firstName: "Hosted",
      lastName: "Owned",
      username,
      email: username + "@example.test",
      password: Buffer.from(password).toString("base64"),
      phoneNumber: "+19999999998",
      orgGuid: tenant.id,
      branding: "idp",
      syncFlag: false,
    })
  ).json();
  await mutation(
    page,
    admin,
    "/api/v1/users/" + user.id + "/security-settings",
    { twoFactorEnabled: false },
    "PUT",
  );
  const client = await (
    await mutation(
      page,
      admin,
      "/api/v1/organizations/" +
        adminDto.subject.organizationId +
        "/applications",
      {
        name: "Hosted second client",
        description: "Owned token revocation proof",
        type: "WEB",
        authMethod: "PKCE",
        usernameType: "USERNAME",
        allowForgotUsername: false,
        forgotUsernameSettings: [],
      },
    )
  ).json();
  let capturedCode = "";
  const callback = createServer((request, response) => {
    const url = new URL(request.url!, "http://127.0.0.1");
    if (url.pathname === "/callback") {
      capturedCode = url.searchParams.get("code") ?? "";
      response.writeHead(303, {
        Location: "/done",
        "Cache-Control": "no-store",
      });
    } else {
      response.writeHead(200, {
        "Content-Type": "text/html",
        "Cache-Control": "no-store",
      });
    }
    response.end("<!doctype html><title>Owned authorization complete</title>");
  });
  await new Promise<void>((resolve) =>
    callback.listen(0, "127.0.0.1", resolve),
  );
  const address = callback.address() as AddressInfo;
  const redirect = "http://127.0.0.1:" + address.port + "/callback";
  const selfContext = await browser.newContext({
    locale: "en-US",
    userAgent: "PortalAcceptance/1.0",
    extraHTTPHeaders: { "Accept-Language": "en-US" },
    timezoneId: "Asia/Kolkata",
  });
  try {
    await mutation(
      page,
      admin,
      "/api/v1/organizations/" +
        adminDto.subject.organizationId +
        "/applications/" +
        client.id +
        "/urls",
      {
        applicationUrl: "http://127.0.0.1:" + address.port,
        returnUrls: [redirect],
        logoutUrls: ["http://127.0.0.1:" + address.port + "/done"],
      },
      "PUT",
    );
    // The separate client exchanges its code only in this Node test process. No token JSON enters a page.
    await context.clearCookies({ domain: new URL(fixture.issuer).hostname });
    const verifier = randomBytes(32).toString("base64url");
    const authorize = new URL(fixture.issuer + "/oauth2/authorize");
    authorize.search = new URLSearchParams({
      response_type: "code",
      client_id: client.clientId,
      redirect_uri: redirect,
      scope: "openid profile email",
      branding: "idp",
      state: randomBytes(24).toString("hex"),
      nonce: randomBytes(24).toString("hex"),
      code_challenge: createHash("sha256").update(verifier).digest("base64url"),
      code_challenge_method: "S256",
      "x-request-datetime": new Date().toISOString(),
    }).toString();
    await page.goto(authorize.href, { referer: fixture.portal + "/" });
    await page.locator("#username").fill(username);
    await page.locator("#password").fill(password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.waitForURL("http://127.0.0.1:" + address.port + "/done");
    check(
      "second real client authorization completed",
      capturedCode.length > 0,
    );
    const exchange = await page.request.post(fixture.issuer + "/oauth2/token", {
      form: {
        grant_type: "authorization_code",
        client_id: client.clientId,
        code: capturedCode,
        redirect_uri: redirect,
        code_verifier: verifier,
      },
    });
    check("second real client token exchange", exchange.status() === 200);
    const second = await exchange.json();
    capturedCode = "";
    const secondSession = {
      accessToken: second.access_token,
      signingKey: second.signing_key,
      browserContext: admin.browserContext,
    };
    check(
      "second client token initially active",
      (
        await page.request.get(fixture.issuer + "/api/v1/portal/context", {
          headers: headers(secondSession),
        })
      ).status() === 200,
    );
    // Opaque portal A remains intact while issuer B is signed in through a different client.
    await page.goto(fixture.portal + "/account");
    await openHosted(page, "Change profile");
    await expect(
      page.getByRole("heading", {
        name: "Account action unavailable",
        exact: true,
      }),
    ).toBeVisible();
    check(
      "portal A issuer B mismatch denied before personal details",
      !(await page.content()).includes(username),
    );
    const account = await selfContext.newPage();
    await login(account, username, password);
    const owned = await session(selfContext);
    const ownDto = await (
      await account.request.get(fixture.portal + "/api/context")
    ).json();
    check(
      "roleless tenant uses shared platform portal client",
      ownDto.subject.organizationId === tenant.id &&
        ownDto.subject.organizationId !== adminDto.subject.organizationId &&
        ownDto.permissions.length === 0,
    );
    const portalA = (await context.cookies(fixture.portal)).find(
      (cookie) => cookie.name === "portal-session",
    )!;
    const portalB = (await selfContext.cookies(fixture.portal)).find(
      (cookie) => cookie.name === "portal-session",
    )!;
    // Both opaque cookies and issuer sessions are genuine owned login results; combine A/B only to exercise narrowing.
    await selfContext.addCookies([portalA]);
    await account.goto(fixture.portal + "/account");
    await openHosted(account, "Change profile");
    await expect(
      account.getByRole("heading", {
        name: "Account action unavailable",
        exact: true,
      }),
    ).toBeVisible();
    check(
      "same portal client still rejects portal A issuer B stable login mismatch",
      !(await account.content()).includes(username),
    );
    await selfContext.addCookies([portalB]);
    await account.goto(fixture.portal + "/account");
    let forbiddenSso = 0;
    account.on("request", (request) => {
      if (new URL(request.url()).pathname.endsWith("/oauth2/sso"))
        forbiddenSso++;
    });
    const privateValues = [
      owned.accessToken,
      owned.idToken,
      owned.signingKey,
      second.access_token,
      second.id_token,
      second.signing_key,
    ].filter((v): v is string => typeof v === "string" && v.length > 8);
    const responsePrivacy: Promise<{
      path: string;
      status: number;
      headersClean: boolean;
      bodyReadable: boolean;
      bodyClean: boolean;
    }>[] = [];
    account.on("response", (response) => {
      const path = new URL(response.url()).pathname;
      if (
        path.includes("/portal/account") ||
        path.endsWith("/hosted-account.js")
      ) {
        responsePrivacy.push(
          (async () => {
            const status = response.status();
            const awaitedHeaders = await response.allHeaders();
            const headersClean = !privateValues.some((value) =>
              JSON.stringify(awaitedHeaders).includes(value),
            );
            if (status >= 300 && status < 400)
              return {
                path,
                status,
                headersClean,
                bodyReadable: false,
                bodyClean: true,
              };
            try {
              const body = await response.body();
              return {
                path,
                status,
                headersClean,
                bodyReadable: true,
                bodyClean: !privateValues.some((value) =>
                  body.includes(Buffer.from(value)),
                ),
              };
            } catch {
              return {
                path,
                status,
                headersClean,
                bodyReadable: false,
                bodyClean: false,
              };
            }
          })(),
        );
      }
    });
    async function formPrivacy(label: string) {
      const html = await account.content();
      check(
        label + " no OAuth material or legacy proof script",
        !privateValues.some((value) => html.includes(value)) &&
          !html.includes("auth-forms.js"),
      );
    }
    await openHosted(account, "Change profile");
    await expect(
      account.getByRole("heading", { name: "Change profile", exact: true }),
    ).toBeVisible();
    await formPrivacy("profile");
    await accessibility(account, "Hosted profile");
    await account.getByLabel("First name", { exact: true }).focus();
    await account.keyboard.press("Tab");
    check(
      "hosted profile keyboard follows labeled fields",
      await account
        .getByLabel("Last name", { exact: true })
        .evaluate((field) => field === document.activeElement),
    );
    await account.getByRole("link", { name: "Cancel", exact: true }).click();
    await expect(
      account.getByRole("heading", { name: "Your account", exact: true }),
    ).toBeVisible();
    check(
      "hosted cancel leaves SQL profile unchanged",
      ownedProfileSql(user.id, "firstName") === "Hosted",
    );
    await openHosted(account, "Change profile");
    await account.getByLabel("First name", { exact: true }).fill("Updated");
    await account
      .getByRole("button", { name: "Update profile", exact: true })
      .click();
    await account.waitForURL(fixture.portal + "/account?notice=returned");
    check(
      "actual profile POST changed SQL",
      ownedProfileSql(user.id, "firstName") === "Updated",
    );
    await account.getByRole("link", { name: "Security", exact: true }).click();
    await openHosted(account, "Change password");
    await expect(
      account.getByRole("heading", { name: "Change password", exact: true }),
    ).toBeVisible();
    await formPrivacy("password");
    await accessibility(account, "Hosted password");
    await account.getByLabel("Current password", { exact: true }).focus();
    await account.keyboard.press("Tab");
    check(
      "hosted password keyboard follows labeled fields",
      await account
        .getByLabel("New password", { exact: true })
        .evaluate((field) => field === document.activeElement),
    );
    await account
      .getByLabel("Current password", { exact: true })
      .fill("Wrong9!owned");
    await account.getByLabel("New password", { exact: true }).fill(next);
    await account
      .getByLabel("Confirm new password", { exact: true })
      .fill(next);
    const invalid = account.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname.endsWith("/portal/account/password"),
    );
    await account
      .getByRole("button", { name: "Change password", exact: true })
      .click();
    check(
      "actual wrong current password rejected",
      (await invalid).status() === 400,
    );
    const rejectedHtml = await account.content();
    check(
      "error HTML excludes rejected password",
      ![
        next,
        Buffer.from(next).toString("base64"),
        "Wrong9!owned",
        Buffer.from("Wrong9!owned").toString("base64"),
      ].some((value) => rejectedHtml.includes(value)),
    );
    await account
      .getByRole("link", { name: "Return to account", exact: true })
      .click();
    await openHosted(account, "Change password");
    await account
      .getByLabel("Current password", { exact: true })
      .fill(password);
    await account.getByLabel("New password", { exact: true }).fill(next);
    await account
      .getByLabel("Confirm new password", { exact: true })
      .fill(next);
    const saved = account.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname.endsWith("/portal/account/password"),
    );
    await account
      .getByRole("button", { name: "Change password", exact: true })
      .click();
    check("actual new password POST committed", (await saved).status() === 200);
    await expect(
      account.getByRole("heading", { name: "Password changed", exact: true }),
    ).toBeVisible();
    check(
      "all target SQL human sessions inactive",
      ownedProfileSql(user.id, "activeSessions") === "0",
    );
    check(
      "issuer SSO cookie cleared after commit",
      !(await selfContext.cookies(fixture.issuer)).some(
        (cookie) => cookie.name === "ags-idp-session",
      ),
    );
    for (const [label, token] of [
      ["portal", owned],
      ["second client", secondSession],
    ] as const)
      check(
        label + " old Java token denied",
        (
          await account.request.get(fixture.issuer + "/api/v1/portal/context", {
            headers: headers(token),
          })
        ).status() === 401,
      );
    const portalCookie = (await selfContext.cookies(fixture.portal)).find(
      (cookie) => cookie.name === "portal-session",
    )!;
    check(
      "opaque portal session retained until authenticated Java denial",
      !!(await redis.get(
        fixture.redisPrefix + "session:" + portalCookie.value,
      )),
    );
    check(
      "BFF context detects revocation",
      (await account.request.get(fixture.portal + "/api/context")).status() ===
        401,
    );
    check(
      "BFF deletes its revoked opaque session",
      (await redis.get(
        fixture.redisPrefix + "session:" + portalCookie.value,
      )) === null,
    );
    check(
      "unrelated admin token preserved",
      (
        await page.request.get(fixture.issuer + "/api/v1/portal/context", {
          headers: headers(admin),
        })
      ).status() === 200,
    );
    await account
      .getByRole("link", { name: "Return to account", exact: true })
      .click();
    await expect(
      account.getByRole("link", { name: "Sign in", exact: true }),
    ).toBeVisible();
    await account.getByRole("link", { name: "Sign in", exact: true }).click();
    await account.locator("#username").fill(username);
    await account.locator("#password").fill(password);
    await account.getByRole("button", { name: "Sign in", exact: true }).click();
    await account.waitForURL(/\/login\?error/);
    check(
      "old password cannot sign in",
      new URL(account.url()).searchParams.has("error"),
    );
    await account.locator("#username").fill(username);
    await account.locator("#password").fill(next);
    await account.getByRole("button", { name: "Sign in", exact: true }).click();
    await account.waitForURL(fixture.portal + "/account");
    await expect(
      account.getByRole("heading", { name: "Your account", exact: true }),
    ).toBeVisible();
    check(
      "new password signs in through real BFF",
      (await account.request.get(fixture.portal + "/api/context")).status() ===
        200,
    );
    check(
      "hosted forms never call OAuth SSO token endpoint",
      forbiddenSso === 0,
    );
    const networkPrivacy = await Promise.all(responsePrivacy);
    writeFileSync(
      join(process.env.PORTAL_SCREENSHOT_DIR!, "hosted-response-privacy.json"),
      JSON.stringify(networkPrivacy),
      { mode: 0o600 },
    );
    check(
      "hosted network HTML/script bodies and redirect headers contain no OAuth tokens or signing keys",
      networkPrivacy.filter((result) => result.bodyReadable).length >= 6 &&
        networkPrivacy.every(
          (result) => result.headersClean && result.bodyClean,
        ),
    );
    check(
      "hosted cookies contain no OAuth tokens or signing keys",
      !(await selfContext.cookies()).some((cookie) =>
        privateValues.some((value) => cookie.value.includes(value)),
      ),
    );
    const storage = await account.evaluate(() =>
      JSON.stringify({
        local: Object.entries(localStorage),
        session: Object.entries(sessionStorage),
      }),
    );
    check(
      "hosted material absent from browser storage",
      ![...privateValues, password, next].some((value) =>
        storage.includes(value),
      ),
    );
    const directory = process.env.PORTAL_SCREENSHOT_DIR!;
    const logs =
      readFileSync(join(directory, "next.log"), "utf8") +
      readFileSync(join(directory, "java.log"), "utf8");
    check(
      "tokens keys and passwords absent from application logs",
      ![
        ...privateValues,
        password,
        next,
        Buffer.from(password).toString("base64"),
        Buffer.from(next).toString("base64"),
      ].some((value) => logs.includes(value)),
    );
  } finally {
    await selfContext.close();
    callback.closeAllConnections();
    await new Promise<void>((resolve, reject) =>
      callback.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test("account-only and viewer denials, tenant scope and live permission revocation", async ({
  page,
  context,
  browser,
}) => {
  await login(page);
  const s = await session(context);
  const dto = await (
    await page.request.get(fixture.portal + "/api/context")
  ).json();
  const group = await (
    await mutation(
      page,
      s,
      "/api/v1/organizations/" + dto.subject.organizationId + "/groups",
      {
        name: "Navigation viewer",
        description: "Read only fixture",
        permissionKeys: ["idp-admin-app:read"],
      },
    )
  ).json();
  const user = await (
    await mutation(page, s, "/api/v1/users", {
      firstName: "Portal",
      lastName: "Viewer",
      username: fixture.secondUsername,
      email: fixture.secondUsername + "@example.test",
      password: Buffer.from(fixture.secondPassword).toString("base64"),
      phoneNumber: "+19999999998",
      orgGuid: dto.subject.organizationId,
      branding: "idp",
      syncFlag: false,
    })
  ).json();
  await mutation(
    page,
    s,
    "/api/v1/users/" + user.id + "/security-settings",
    { twoFactorEnabled: false },
    "PUT",
  );
  const tenant = await browser.newContext({
    locale: "en-US",
    userAgent: "PortalAcceptance/1.0",
    extraHTTPHeaders: { "Accept-Language": "en-US" },
    timezoneId: "Asia/Kolkata",
  });
  const account = await tenant.newPage();
  await login(account, fixture.secondUsername, fixture.secondPassword);
  check(
    "account user sees no admin navigation",
    (await account
      .getByRole("link", { name: "Administration", exact: true })
      .count()) === 0,
  );
  await account.goto(fixture.portal + "/admin");
  await expect(
    account.getByRole("heading", { name: "Access denied", exact: true }),
  ).toBeVisible();
  check(
    "account deep admin URL denied",
    await account
      .getByRole("heading", { name: "Access denied", exact: true })
      .isVisible(),
  );
  await mutation(
    page,
    s,
    "/api/v1/organizations/" +
      dto.subject.organizationId +
      "/users/" +
      user.id +
      "/group",
    { groupId: group.id },
    "PUT",
  );
  await account.goto(fixture.portal + "/admin");
  const viewerContext = await (
    await account.request.get(fixture.portal + "/api/context")
  ).json();
  check(
    "actual context contains newly assigned viewer read permission",
    viewerContext.permissions?.includes("idp-admin-app:read") === true,
  );
  await expect(
    account.getByRole("heading", { name: "Administration", exact: true }),
  ).toBeVisible();
  check(
    "viewer obtains read administration",
    await account
      .getByRole("heading", { name: "Administration", exact: true })
      .isVisible(),
  );
  check(
    "tenant has no global organization switch",
    (await account.getByLabel("Organization", { exact: true }).count()) === 0,
  );
  check(
    "viewer has no mutation controls",
    (await account
      .getByRole("button", { name: /create|delete|save|deactivate/i })
      .count()) === 0,
  );
  const foreign = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  await account.goto(fixture.portal + "/admin?organizationId=" + foreign);
  await expect(
    account.getByRole("heading", { name: "Access denied", exact: true }),
  ).toBeVisible();
  check(
    "deep foreign scope denied",
    await account
      .getByRole("heading", { name: "Access denied", exact: true })
      .isVisible(),
  );
  check(
    "BFF foreign clients forbidden",
    (
      await account.request.get(
        fixture.portal + "/api/clients?organizationId=" + foreign,
      )
    ).status() === 403,
  );
  const ts = await session(tenant);
  check(
    "Java foreign clients independently denied",
    [403, 404].includes(
      (
        await account.request.get(
          fixture.issuer + "/api/v1/organizations/" + foreign + "/applications",
          { headers: headers(ts) },
        )
      ).status(),
    ),
  );
  await account.goto(fixture.portal + "/admin");
  await mutation(
    page,
    s,
    "/api/v1/organizations/" +
      dto.subject.organizationId +
      "/users/" +
      user.id +
      "/group",
    { groupId: null },
    "PUT",
  );
  await account.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(
    account.getByRole("link", { name: "Administration", exact: true }),
  ).toHaveCount(0);
  check("late revocation refresh removes admin navigation", true);
  check(
    "stale viewer BFF request denied",
    (await account.request.get(fixture.portal + "/api/clients")).status() ===
      403,
  );
  check(
    "Java revoked permission independently denied",
    (
      await account.request.get(
        fixture.issuer +
          "/api/v1/organizations/" +
          dto.subject.organizationId +
          "/applications",
        { headers: headers(ts) },
      )
    ).status() === 403,
  );
  await account.reload();
  await expect(
    account.getByRole("heading", { name: "Access denied", exact: true }),
  ).toBeVisible();
  check(
    "late revocation reload denies administration",
    await account
      .getByRole("heading", { name: "Access denied", exact: true })
      .isVisible(),
  );
  // Existing Redis session remains mandatory for p-only navigation; it never creates one.
  const p = Buffer.from("surl=" + fixture.portal + "/account").toString(
    "base64",
  );
  const guest = await browser.newContext();
  const forged = await guest.request.get(
    fixture.portal + "/auth/callback?" + new URLSearchParams({ p }),
    { maxRedirects: 0 },
  );
  check(
    "continuation cannot create guest session",
    forged.status() === 303 &&
      !(await guest.cookies()).some((c) => c.name === "portal-session"),
  );
  await guest.close();
  await tenant.close();
});

async function loginWithBrand(
  page: Page,
  username: string,
  password: string,
  branding: string,
) {
  if (branding === "idp") return login(page, username, password);
  await page.goto(fixture.portal);
  const start = await page.request.get(fixture.portal + "/auth/login", {
    maxRedirects: 0,
  });
  const authorize = new URL(start.headers().location);
  // Preserve the real BFF's state/PKCE/cookie transaction; branding is a public issuer input.
  authorize.searchParams.set("branding", branding);
  // This is an explicit other-brand protocol fixture, not a shipped portal source selector.
  // Native navigation from the actual portal document supplies the browser's real Referer.
  await page.evaluate(
    (url) => window.location.assign(url),
    authorize.toString(),
  );
  await expect(page.locator("#username")).toBeVisible({ timeout: 10000 });
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.waitForURL(fixture.portal + "/account");
  await expect(
    page.getByRole("heading", { name: "Your account", exact: true }),
  ).toBeVisible();
}
for (const otherSource of [false, true]) {
  test(`exact sibling credential browser login and hosted mutation ${otherSource ? "other source protocol fixture" : "same source shipped portal"}`, async ({
    page,
    context,
    browser,
  }) => {
    await login(page);
    const admin = await session(context);
    const home = await (
      await page.request.get(fixture.portal + "/api/context")
    ).json();
    const unique = randomBytes(6).toString("hex"),
      username = "sibling_" + unique;
    const targetUsername = otherSource ? username : username + "_second";
    const branding = otherSource ? "portal-sibling" : "idp";
    const user = await (
      await mutation(page, admin, "/api/v1/users", {
        firstName: "Sibling",
        lastName: "Owned",
        username,
        email: username + "@example.test",
        password: Buffer.from(fixture.secondPassword).toString("base64"),
        phoneNumber: "+19999999998",
        orgGuid: home.subject.organizationId,
        branding: "idp",
        syncFlag: false,
      })
    ).json();
    await mutation(
      page,
      admin,
      "/api/v1/users/" + user.id + "/security-settings",
      { twoFactorEnabled: false },
      "PUT",
    );
    const identity = JSON.parse(
      execFileSync(
        "python3",
        [
          "-c",
          `
import sys,json,uuid,re
sys.path.insert(0,'scripts')
from e2e_target import load_target,sql_text
c,b,d=load_target();assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d)
v=json.loads(sys.stdin.read());u=str(uuid.UUID(v['user']));name=v['username'];brand=v['branding']
assert re.fullmatch(r'sibling_[0-9a-f]{12}(?:_second)?',name)
assert brand in ('idp','portal-sibling')
q="""SET QUOTED_IDENTIFIER ON;
DECLARE @profile INT=(SELECT ProfileId FROM Person.Profile WHERE RowGuid='"""+u+"""');
DECLARE @original UNIQUEIDENTIFIER=(SELECT RowGuid FROM Person.Credential WHERE ProfileId=@profile);
DECLARE @source UNIQUEIDENTIFIER='11111111-1111-1111-1111-111111111111';
IF '"""+brand+"""'<>'idp' BEGIN
 SET @source=NEWID(); INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT @source,'"""+brand+"""',ExternalTypeId FROM dbo.ExternalType WHERE ExternalTypeName='IDP';
END;
DECLARE @target UNIQUEIDENTIFIER=NEWID();
INSERT Person.Credential(RowGuid,ProfileId,UserName,Password,Version,CredentialLocked,AccessFailedCount,DataOriginId,SyncFlag,ExternalId)
SELECT @target,ProfileId,'"""+name+"""',Password,Version,0,0,DataOriginId,0,@source FROM Person.Credential WHERE RowGuid=@original;
DECLARE @spoof NVARCHAR(36)=CONVERT(NVARCHAR(36),@original);
EXEC Person.CreateMetadata @profile,'uuid_login_id',@spoof;
EXEC Person.CreateMetadata @profile,'login_guid',@spoof;
SELECT CONVERT(VARCHAR(36),@original) AS original,CONVERT(VARCHAR(36),@target) AS target FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"""
print(sql_text(c,d,q))
`,
        ],
        {
          cwd: process.env.PORTAL_REPO_ROOT,
          input: JSON.stringify({
            user: user.id,
            username: targetUsername,
            branding,
          }),
          stdio: ["pipe", "pipe", "pipe"],
        },
      ).toString(),
    );
    const owned = await browser.newContext({
      locale: "en-US",
      userAgent: "PortalAcceptance/1.0",
      extraHTTPHeaders: { "Accept-Language": "en-US" },
      timezoneId: "Asia/Kolkata",
    });
    const target = await owned.newPage();
    const next = "B8!" + randomBytes(20).toString("hex") + "qZ";
    try {
      await loginWithBrand(
        target,
        targetUsername,
        fixture.secondPassword,
        branding,
      );
      const dto = await (
        await target.request.get(fixture.portal + "/api/context")
      ).json();
      check(
        `real ${branding === "idp" ? "same-source" : "other-source"} sibling login context is exact`,
        dto.subject.userId === user.id &&
          dto.subject.loginId.toLowerCase() === identity.target.toLowerCase() &&
          dto.subject.username === targetUsername,
      );
      const privateSession = await session(owned);
      await openHosted(target, "Change profile");
      await expect(
        target.getByRole("heading", { name: "Change profile", exact: true }),
      ).toBeVisible();
      check(
        "sibling hosted form excludes actual OAuth/key material",
        ![
          privateSession.accessToken,
          privateSession.idToken,
          privateSession.signingKey,
        ]
          .filter(Boolean)
          .some((value) => target.url().includes(value)),
      );
      const html = await target.content();
      check(
        "sibling hosted HTML excludes actual OAuth/key material",
        ![
          privateSession.accessToken,
          privateSession.idToken,
          privateSession.signingKey,
        ]
          .filter(Boolean)
          .some((value) => html.includes(value)),
      );
      await target
        .getByLabel("First name", { exact: true })
        .fill("Sibling updated");
      await target
        .getByRole("button", { name: "Update profile", exact: true })
        .click();
      await target.waitForURL(fixture.portal + "/account?notice=returned", {
        timeout: 15000,
      });
      check(
        "sibling actual hosted profile POST updates same profile",
        ownedProfileSql(user.id, "firstName") === "Sibling updated",
      );
      await target.goto(fixture.portal + "/account/security");
      await openHosted(target, "Change password");
      await expect(
        target.getByRole("heading", { name: "Change password", exact: true }),
      ).toBeVisible();
      const passwordHtml = await target.content();
      check(
        "sibling password HTML excludes actual OAuth/key material",
        ![
          privateSession.accessToken,
          privateSession.idToken,
          privateSession.signingKey,
        ]
          .filter(Boolean)
          .some((value) => passwordHtml.includes(value)),
      );
      await target
        .getByLabel("Current password", { exact: true })
        .fill(fixture.secondPassword);
      await target.getByLabel("New password", { exact: true }).fill(next);
      await target
        .getByLabel("Confirm new password", { exact: true })
        .fill(next);
      const saved = target.waitForResponse(
        (response) =>
          response.request().method() === "POST" &&
          new URL(response.url()).pathname.endsWith("/portal/account/password"),
      );
      await target
        .getByRole("button", { name: "Change password", exact: true })
        .click();
      check(
        "sibling actual hosted password POST commits",
        (await saved).status() === 200,
      );
      const ownedLogs =
        readFileSync(process.env.PORTAL_NEXT_LOG!, "utf8") +
        readFileSync(
          join(process.env.PORTAL_SCREENSHOT_DIR!, "java.log"),
          "utf8",
        );
      check(
        "sibling application logs exclude actual OAuth key and password material",
        ![
          privateSession.accessToken,
          privateSession.idToken,
          privateSession.signingKey,
          fixture.secondPassword,
          next,
        ]
          .filter(Boolean)
          .some((value) => ownedLogs.includes(value)),
      );
    } finally {
      await owned.close();
    }
    for (const credential of [
      {
        username,
        password: fixture.secondPassword,
        branding: "idp",
        login: identity.original,
      },
      {
        username: targetUsername,
        password: next,
        branding,
        login: identity.target,
      },
    ]) {
      const fresh = await browser.newContext({
        locale: "en-US",
        userAgent: "PortalAcceptance/1.0",
        extraHTTPHeaders: { "Accept-Language": "en-US" },
        timezoneId: "Asia/Kolkata",
      });
      try {
        const account = await fresh.newPage();
        await loginWithBrand(
          account,
          credential.username,
          credential.password,
          credential.branding,
        );
        const dto = await (
          await account.request.get(fixture.portal + "/api/context")
        ).json();
        check(
          "sibling password remains usable and changed target reauthenticates exact login",
          dto.subject.loginId.toLowerCase() === credential.login.toLowerCase(),
        );
      } finally {
        await fresh.close();
      }
    }
  });
}

async function partialReader(
  page: Page,
  context: BrowserContext,
  browser: Browser,
) {
  await login(page);
  const admin = await session(context);
  const dto = await (
    await page.request.get(fixture.portal + "/api/context")
  ).json();
  const createRole = async (name: string, permissionKeys: string[]) =>
    (
      await mutation(
        page,
        admin,
        "/api/v1/organizations/" + dto.subject.organizationId + "/groups",
        { name, description: "Owned partial revocation", permissionKeys },
      )
    ).json();
  const unique = randomBytes(6).toString("hex");
  const home = await createRole("Home " + unique, [
    "idp-admin-app:read",
    "idp-admin-user:read",
  ]);
  const single = await createRole("Single " + unique, ["idp-admin-app:read"]);
  const adminRead = await createRole("Admin read " + unique, [
    "idp-admin-admin:read",
  ]);
  const username = "partial_" + unique;
  const user = await (
    await mutation(page, admin, "/api/v1/users", {
      firstName: "Partial",
      lastName: "Reader",
      username,
      email: username + "@example.test",
      password: Buffer.from(fixture.secondPassword).toString("base64"),
      phoneNumber: "+19999999998",
      orgGuid: dto.subject.organizationId,
      branding: "idp",
      syncFlag: false,
    })
  ).json();
  await mutation(
    page,
    admin,
    "/api/v1/users/" + user.id + "/security-settings",
    { twoFactorEnabled: false },
    "PUT",
  );
  const assign = async (role: any) =>
    mutation(
      page,
      admin,
      "/api/v1/organizations/" +
        dto.subject.organizationId +
        "/users/" +
        user.id +
        "/group",
      { groupId: role.id },
      "PUT",
    );
  await assign(home);
  // The role API intentionally cannot create reserved switching grants. Install an imported,
  // preexisting legacy role only in this runner's owned SQL fixture; revocation uses the API.
  execFileSync(
    "python3",
    [
      "-c",
      `
import sys,json,uuid,re
sys.path.insert(0,'scripts')
from e2e_target import load_target,sql_text
c,b,d=load_target()
assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d)
v=json.loads(sys.stdin.read());u=str(uuid.UUID(v['user']));o=str(uuid.UUID(v['org']));g=str(uuid.UUID(v['home']))
q="""DECLARE @org INT=(SELECT OrganizationId FROM Partner.Organization WHERE RowGuid='"""+o+"""');
INSERT Partner.OrganizationGroup(OrganizationId,GroupName) VALUES(@org,'Owned imported partial switch """+u+"""');
DECLARE @group INT=SCOPE_IDENTITY();
INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey)
SELECT @group,@org,p.PermissionName,p.PermissionKey FROM Partner.OrganizationGroupPermission p JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=p.OrganizationGroupId WHERE g.RowGuid='"""+g+"""';
INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey) VALUES(@group,@org,'Switch','idp-admin-org:switch');
UPDATE pg SET OrganizationGroupId=@group FROM Person.ProfileGroup pg JOIN Person.Profile p ON p.ProfileId=pg.ProfileId WHERE p.RowGuid='"""+u+"""';"""
sql_text(c,d,q,scalar=True)
`,
    ],
    {
      cwd: process.env.PORTAL_REPO_ROOT,
      input: JSON.stringify({
        user: user.id,
        org: dto.subject.organizationId,
        home: home.id,
      }),
      stdio: ["pipe", "pipe", "pipe"],
    },
  );
  const owned = await browser.newContext({
    locale: "en-US",
    userAgent: "PortalAcceptance/1.0",
    extraHTTPHeaders: { "Accept-Language": "en-US" },
    timezoneId: "Asia/Kolkata",
  });
  const reader = await owned.newPage();
  await login(reader, username, fixture.secondPassword);
  return { reader, owned, admin, dto, assign, home, single, adminRead };
}
test("partial switch revocation denies selected foreign scope while home read survives", async ({
  page,
  context,
  browser,
}) => {
  const f = await partialReader(page, context, browser);
  try {
    const foreign = await (
      await mutation(page, f.admin, "/api/v1/organizations", {
        name: "Partial scope",
        description: "Owned scope revocation",
      })
    ).json();
    await f.reader.goto(fixture.portal + "/admin?organizationId=" + foreign.id);
    await expect(
      f.reader.getByRole("heading", { name: "Partial scope", exact: true }),
    ).toBeVisible();
    await f.assign(f.home);
    await f.reader.evaluate(() => window.dispatchEvent(new Event("focus")));
    await expect(
      f.reader.getByRole("heading", { name: "Access denied", exact: true }),
    ).toBeVisible();
    check(
      "switch-only revocation removes stale foreign SSR content",
      (await f.reader
        .getByRole("heading", { name: "Partial scope", exact: true })
        .count()) === 0,
    );
    check(
      "switch-only revocation retains home Administration navigation",
      (await f.reader
        .getByRole("link", { name: "Administration", exact: true })
        .count()) === 1,
    );
    check(
      "foreign API denied after switch-only revocation",
      (
        await f.reader.request.get(
          fixture.portal + "/api/clients?organizationId=" + foreign.id,
        )
      ).status() === 403,
    );
    await f.reader
      .getByRole("link", { name: "Administration", exact: true })
      .click();
    await expect(
      f.reader.getByRole("heading", { name: "Administration", exact: true }),
    ).toBeVisible();
    check(
      "valid home read survives switch removal",
      await f.reader
        .locator("main li")
        .getByText("Clients", { exact: true })
        .isVisible(),
    );
  } finally {
    await f.owned.close();
  }
});
test("one-of-many read revocation refreshes SSR access and administrator-only read is recognized", async ({
  page,
  context,
  browser,
}) => {
  const f = await partialReader(page, context, browser);
  try {
    await f.assign(f.home);
    await f.reader.goto(fixture.portal + "/admin");
    await expect(
      f.reader.locator("main li").getByText("Users", { exact: true }),
    ).toBeVisible();
    await f.assign(f.single);
    await f.reader.evaluate(() => window.dispatchEvent(new Event("focus")));
    await expect(
      f.reader.locator("main li").getByText("Users", { exact: true }),
    ).toHaveCount(0);
    check(
      "single read revocation removes only stale SSR permission",
      await f.reader
        .locator("main li")
        .getByText("Clients", { exact: true })
        .isVisible(),
    );
    await f.assign(f.adminRead);
    await f.reader.evaluate(() => window.dispatchEvent(new Event("focus")));
    await expect(
      f.reader.locator("main li").getByText("Administrators", { exact: true }),
    ).toBeVisible();
    check(
      "administrator-read-only grant retains current overview",
      (await f.reader
        .getByRole("link", { name: "Administration", exact: true })
        .count()) === 1,
    );
  } finally {
    await f.owned.close();
  }
});

test("native Chromium 200 percent zoom reflows with keyboard access", async () => {
  const directory = mkdtempSync(join(tmpdir(), "portal-native-zoom-"));
  const extension = join(directory, "extension");
  mkdirSync(extension);
  writeFileSync(
    join(extension, "manifest.json"),
    JSON.stringify({
      manifest_version: 3,
      name: "Owned portal zoom acceptance",
      version: "1.0",
      permissions: ["tabs"],
      background: { service_worker: "worker.js" },
    }),
  );
  writeFileSync(
    join(extension, "worker.js"),
    "chrome.runtime.onInstalled.addListener(() => {});",
  );
  let context: BrowserContext | undefined;
  try {
    context = await chromium.launchPersistentContext(
      join(directory, "profile"),
      {
        channel: "chromium",
        headless: true,
        args: [
          "--disable-extensions-except=" + extension,
          "--load-extension=" + extension,
        ],
        viewport: { width: 1280, height: 960 },
        locale: "en-US",
        userAgent: "PortalAcceptance/1.0",
        extraHTTPHeaders: { "Accept-Language": "en-US" },
        timezoneId: "Asia/Kolkata",
      },
    );
    const page = await context.newPage();
    await login(page);
    const background =
      context.serviceWorkers()[0] ??
      (await context.waitForEvent("serviceworker"));
    const zoom = await background.evaluate(async () => {
      const api = (
        globalThis as unknown as {
          chrome: {
            tabs: {
              query: (
                q: Record<string, unknown>,
              ) => Promise<{ id: number; url?: string }[]>;
              setZoom: (id: number, zoom: number) => Promise<void>;
              getZoom: (id: number) => Promise<number>;
            };
          };
        }
      ).chrome.tabs;
      const tab = (await api.query({})).find(
        (tab) => tab.url?.includes("127.0.0.1") && tab.url.includes("/account"),
      );
      if (!tab) throw Error("Owned account tab unavailable");
      await api.setZoom(tab.id, 2);
      return api.getZoom(tab.id);
    });
    check(
      "native browser zoom independently confirmed by chrome.tabs.getZoom",
      zoom === 2,
    );
    await expect(
      page.getByRole("button", { name: "Open navigation", exact: true }),
    ).toBeVisible();
    check(
      "native 200 percent zoom reflows without horizontal scroll",
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await page.getByRole("link", { name: "Skip to content" }).focus();
    await page.keyboard.press("Enter");
    check(
      "native zoom keyboard skip reaches main",
      await page.locator("main").evaluate((e) => e === document.activeElement),
    );
    await accessibility(page, "Account native 200 percent");
    writeFileSync(
      join(process.env.PORTAL_SCREENSHOT_DIR!, "native-zoom-layout.json"),
      JSON.stringify(
        await page.evaluate(() => ({
          innerWidth,
          innerHeight,
          scrollWidth: document.documentElement.scrollWidth,
          devicePixelRatio,
          desktopNavigation: matchMedia("(min-width:900px)").matches,
        })),
      ),
      { mode: 0o600 },
    );
    await screenshot(page, "account-native-200-percent", false);
  } finally {
    await context?.close();
    rmSync(directory, { recursive: true, force: true });
  }
});
