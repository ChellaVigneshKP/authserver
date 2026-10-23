import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
import { createClient } from "redis";
import { createHash, createHmac } from "node:crypto";
import { execFileSync } from "node:child_process";
const fixture = JSON.parse(
  readFileSync(process.env.PORTAL_E2E_CONFIG!, "utf8"),
);
const events: { path: string; status: number }[] = [];
const checks: { id: string; passed: boolean }[] = [];
const check = (id: string, value: boolean) => {
  checks.push({ id, passed: value });
  expect(value, id).toBe(true);
};
let redis: ReturnType<typeof createClient>;
test.beforeAll(async () => {
  redis = createClient({
    url: "redis://127.0.0.1:16379/0",
    socket: { reconnectStrategy: false },
  });
  await redis.connect();
});
test.afterAll(async () => {
  await redis?.quit();
  writeFileSync(
    process.env.PORTAL_E2E_REPORT!,
    JSON.stringify({ checks, events }),
    { mode: 0o600 },
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
async function privateSession(context: BrowserContext) {
  const cookies = await context.cookies(fixture.portal);
  const id = cookies.find((c) => c.name === "portal-session")?.value;
  check(
    "opaque HttpOnly SameSite portal cookie",
    cookies.some(
      (c) =>
        c.name === "portal-session" &&
        c.httpOnly &&
        c.sameSite === "Lax" &&
        c.value.length === 43,
    ),
  );
  const raw = id
    ? await redis.get(fixture.redisPrefix + "session:" + id)
    : null;
  if (!raw) throw Error("Owned server session not found");
  return JSON.parse(raw);
}
function javaHeaders(session: any) {
  return {
    Authorization: "Bearer " + session.accessToken,
    "User-Agent": session.browserContext.userAgent,
    "Accept-Language": session.browserContext.acceptLanguage,
    Referer: session.browserContext.externalOrigin + "/",
    "x-request-datetime": new Date().toISOString(),
    "x-signature": createHmac(
      "sha256",
      Buffer.from(session.signingKey, "base64"),
    )
      .update("")
      .digest("base64"),
  };
}
test("real hosted login, BFF context/client reads, CSRF, logout and expiry", async ({
  page,
  context,
}) => {
  page.on("response", (response) => {
    const url = new URL(response.url());
    if (
      [new URL(fixture.portal).origin, new URL(fixture.issuer).origin].includes(
        url.origin,
      )
    )
      events.push({ path: url.pathname, status: response.status() });
  });
  await login(page);
  const s = await privateSession(context);
  check("clean callback URL", page.url() === fixture.portal + "/account");
  const contextResponse = await page.request.get(
    fixture.portal + "/api/context",
  );
  check("actual Java portal context", contextResponse.status() === 200);
  const dto = await contextResponse.json();
  check("correct signed-in subject", dto.subject.username === fixture.username);
  const clients = await page.request.get(fixture.portal + "/api/clients");
  check(
    "actual client read",
    clients.status() === 200 && Array.isArray(await clients.json()),
  );
  const activeSso = await page.request.get(fixture.issuer + "/oauth2/sso", {
    headers: {
      "x-request-datetime": new Date().toISOString(),
      Referer: fixture.portal + "/",
    },
  });
  check(
    "Java SSO is valid before logout under its original fingerprint",
    activeSso.status() === 200,
  );
  const proxyHeaders = await page.request.get(fixture.portal + "/api/context", {
    headers: {
      "x-forwarded-host": "untrusted.example",
      "x-forwarded-proto": "https",
      Referer: "https://untrusted.example/",
    },
  });
  check(
    "untrusted proxy and Referer headers cannot alter fixed upstream fingerprint",
    proxyHeaders.status() === 200,
  );
  const cookieOnly = await page.request.get(
    fixture.issuer + "/api/v1/portal/context",
    { headers: { "x-request-datetime": new Date().toISOString() } },
  );
  check("Java rejects cookie-only management", cookieOnly.status() === 401);
  const sessionResponse = await page.request.get(
    fixture.portal + "/api/session",
  );
  const publicSession = await sessionResponse.json();
  check(
    "session API only public fields",
    Object.keys(publicSession).sort().join(",") ===
      "authenticated,csrfToken,expiresAt",
  );
  check(
    "public response no-store",
    contextResponse.headers()["cache-control"].includes("no-store") &&
      sessionResponse.headers()["cache-control"].includes("no-store"),
  );
  const denied = await page.request.post(fixture.portal + "/auth/logout", {
    headers: {
      Origin: "https://forged.example",
      "x-csrf-token": publicSession.csrfToken,
    },
    data: {},
  });
  check("forged Origin rejected", denied.status() === 403);
  check(
    "forged logout retained active session",
    !!(await redis.get(fixture.redisPrefix + "session:" + s.id)),
  );
  const noCsrf = await page.request.post(fixture.portal + "/auth/logout", {
    headers: { Origin: fixture.portal },
    data: {},
  });
  check("missing CSRF rejected", noCsrf.status() === 403);
  const browserArtifacts = await page.evaluate(() => ({
    html: document.documentElement.outerHTML,
    local: Object.values(localStorage),
    session: Object.values(sessionStorage),
  }));
  const artifacts =
    JSON.stringify(browserArtifacts) +
    (await contextResponse.text()) +
    (await sessionResponse.text()) +
    (await clients.text());
  check(
    "tokens and signing key absent from DOM storage and normal JSON",
    ![s.accessToken, s.idToken, s.signingKey].some((secret) =>
      artifacts.includes(secret),
    ),
  );
  check(
    "browser storage empty",
    browserArtifacts.local.length === 0 &&
      browserArtifacts.session.length === 0,
  );
  const rsc = await page.request.get(
    fixture.portal + "/account?_rsc=acceptance",
    { headers: { RSC: "1" } },
  );
  const rscText = await rsc.text();
  check(
    "tokens absent from RSC",
    ![s.accessToken, s.idToken, s.signingKey].some((secret) =>
      rscText.includes(secret),
    ),
  );
  const logs = readFileSync(process.env.PORTAL_NEXT_LOG!, "utf8");
  check(
    "tokens absent from Next logs",
    ![s.accessToken, s.idToken, s.signingKey].some((secret) =>
      logs.includes(secret),
    ),
  );
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await page.waitForURL(fixture.portal + "/auth/logged-out");
  check(
    "portal session deleted after logout",
    (await redis.get(fixture.redisPrefix + "session:" + s.id)) === null,
  );
  const old = await page.request.get(
    fixture.issuer + "/api/v1/portal/context",
    { headers: javaHeaders(s) },
  );
  check("Java logout revokes bearer", old.status() === 401);
  const sso = await page.request.get(fixture.issuer + "/oauth2/sso", {
    headers: {
      "x-request-datetime": new Date().toISOString(),
      Referer: fixture.portal + "/",
    },
  });
  check("Java SSO ended", sso.status() === 401);
  check(
    "signed-out session endpoint unauthorized",
    (await page.request.get(fixture.portal + "/api/session")).status() === 401,
  );
  await login(page);
  const expired = await privateSession(context);
  const digest = createHash("sha256").update(expired.accessToken).digest("hex");
  execFileSync(
    "python3",
    [
      "-c",
      "import sys,os;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();h=sys.stdin.read().strip();assert len(h)==64 and all(x in '0123456789abcdef' for x in h);sql_text(c,d,\"UPDATE Token.Token SET Expiration=DATEADD(second,-5,SYSUTCDATETIME()) WHERE DataHash=0x\"+h+';',scalar=True)",
    ],
    {
      cwd: process.env.PORTAL_REPO_ROOT,
      input: digest,
      stdio: ["pipe", "pipe", "pipe"],
    },
  );
  const expiredCall = await page.request.get(
    fixture.issuer + "/api/v1/portal/context",
    { headers: javaHeaders(expired) },
  );
  check("Java expired bearer rejected", expiredCall.status() === 401);
  const bff = await page.request.get(fixture.portal + "/api/context");
  check(
    "BFF expires revoked or expired Java bearer",
    bff.status() === 401 &&
      (await redis.get(fixture.redisPrefix + "session:" + expired.id)) === null,
  );
});

test("two pending browser tabs keep independent transactions and subjects", async ({
  page,
  context,
}) => {
  await login(page);
  const admin = await privateSession(context);
  async function mutation(path: string, body: unknown, method = "POST") {
    const raw = JSON.stringify(body);
    const response = await page.request.fetch(fixture.issuer + path, {
      method,
      headers: {
        ...javaHeaders(admin),
        "content-type": "application/json",
        "x-signature": createHmac(
          "sha256",
          Buffer.from(admin.signingKey, "base64"),
        )
          .update(raw)
          .digest("base64"),
      },
      data: raw,
    });
    check(
      "owned user fixture mutation " +
        method +
        " " +
        path.replace(/\/[0-9a-f-]{36}/g, "/{id}"),
      [200, 201, 204].includes(response.status()),
    );
    return response;
  }
  const contextDto = await (
    await page.request.get(fixture.portal + "/api/context")
  ).json();
  const user = await (
    await mutation("/api/v1/users", {
      firstName: "Portal",
      lastName: "Second",
      username: fixture.secondUsername,
      email: fixture.secondUsername + "@example.test",
      password: Buffer.from(fixture.secondPassword).toString("base64"),
      phoneNumber: "+19999999998",
      orgGuid: contextDto.subject.organizationId,
      branding: "idp",
      syncFlag: false,
    })
  ).json();
  await mutation(
    "/api/v1/users/" + user.id + "/security-settings",
    { twoFactorEnabled: false },
    "PUT",
  );
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await page.waitForURL(fixture.portal + "/auth/logged-out");
  const a = await page.request.get(fixture.portal + "/auth/login", {
    maxRedirects: 0,
  });
  const b = await page.request.get(fixture.portal + "/auth/login", {
    maxRedirects: 0,
  });
  const aUrl = a.headers().location,
    bUrl = b.headers().location;
  check(
    "two simultaneous pending transactions use different state",
    new URL(aUrl).searchParams.get("state") !==
      new URL(bUrl).searchParams.get("state"),
  );
  const txCookies = (await context.cookies()).filter((c) =>
    c.name.startsWith("portal-login-"),
  );
  check(
    "both transactions have independent HttpOnly cookies",
    txCookies.length === 2 && txCookies.every((c) => c.httpOnly),
  );
  let firstCallback = "";
  page.on("request", (r) => {
    if (new URL(r.url()).pathname === "/auth/callback") firstCallback = r.url();
  });
  await page.goto(aUrl, { referer: fixture.portal + "/" });
  await page.locator("#username").fill(fixture.username);
  await page.locator("#password").fill(fixture.password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.waitForURL(fixture.portal + "/account");
  const firstSession = await privateSession(context);
  const firstDto = await (
    await page.request.get(fixture.portal + "/api/context")
  ).json();
  check(
    "first transaction retains first subject",
    firstDto.subject.username === fixture.username,
  );
  const second = await context.newPage();
  await second.goto(bUrl, { referer: fixture.portal + "/" });
  await second.locator("#username").fill(fixture.secondUsername);
  await second.locator("#password").fill(fixture.secondPassword);
  await second.getByRole("button", { name: "Sign in", exact: true }).click();
  await second.waitForURL(fixture.portal + "/account");
  const secondSession = await privateSession(context);
  const secondDto = await (
    await second.request.get(fixture.portal + "/api/context")
  ).json();
  check(
    "second transaction retains second subject",
    secondDto.subject.username === fixture.secondUsername &&
      firstDto.subject.userId !== secondDto.subject.userId,
  );
  check(
    "login rotates cookie and deletes preceding portal session",
    secondSession.id !== firstSession.id &&
      (await redis.get(fixture.redisPrefix + "session:" + firstSession.id)) ===
        null,
  );
  const replay = await second.request.get(firstCallback, { maxRedirects: 0 });
  check(
    "callback replay rejected at clean URL",
    replay.status() === 303 &&
      new URL(replay.headers().location).pathname === "/auth/error",
  );
  check(
    "replay cannot swap current subject",
    (await (await second.request.get(fixture.portal + "/api/context")).json())
      .subject.username === fixture.secondUsername,
  );
  await page.reload();
  check(
    "old tab navigation cannot serve cached first-user data",
    (await page.getByText(fixture.secondUsername, { exact: true }).count()) ===
      1 &&
      (await page.getByText(fixture.username, { exact: true }).count()) === 0,
  );
  check(
    "account-only second user cannot read clients",
    (await second.request.get(fixture.portal + "/api/clients")).status() ===
      403,
  );
  await second.getByRole("button", { name: "Sign out", exact: true }).click();
  await second.waitForURL(fixture.portal + "/auth/logged-out");
});

test("Java outage fails closed while local sign-out succeeds with an SSO warning", async ({
  page,
  context,
}) => {
  await login(page);
  const session = await privateSession(context);
  const pid = Number(process.env.PORTAL_OWNED_JAVA_PID);
  if (!Number.isInteger(pid) || pid <= 1)
    throw Error("Missing owned Java process identity");
  process.kill(pid, "SIGTERM");
  await expect
    .poll(
      async () => {
        try {
          return (
            await page.request.get(fixture.portal + "/api/context")
          ).status();
        } catch {
          return 0;
        }
      },
      { timeout: 15000 },
    )
    .toBe(503);
  check("Java outage produces controlled BFF503", true);
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await page.waitForURL(fixture.portal + "/auth/logged-out?incomplete=1");
  check(
    "local sign-out removes Redis session despite Java outage",
    (await redis.get(fixture.redisPrefix + "session:" + session.id)) === null,
  );
  check(
    "failed identity-provider logout clearly warns about remaining SSO",
    (await page.getByText(/SSO session may still be active/).count()) === 1,
  );
});
