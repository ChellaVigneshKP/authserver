import { test, expect, type Page, type Browser, type BrowserContext, type Locator } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHmac, randomBytes, randomUUID } from "node:crypto";
import { createClient } from "redis";
import { writeReport } from "./report";
process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
test.use({ trace: "off", screenshot: "off", video: "off" });
const fixture = JSON.parse(readFileSync(process.env.PORTAL_E2E_CONFIG!, "utf8"));
const checks: { id: string; passed: boolean }[] = [];
const blocked: { id: string; reason: string }[] = [];
function check(id: string, passed: boolean) {
  checks.push({ id, passed });
  writeFileSync(process.env.PORTAL_SCREENSHOT_DIR + "/journeys-progress.json", JSON.stringify({ checks, blocked }), { mode: 0o600 });
  expect(passed, id).toBe(true);
}
function recordBlocked(id: string, reason: string) {
  blocked.push({ id, reason });
  writeFileSync(process.env.PORTAL_SCREENSHOT_DIR + "/journeys-progress.json", JSON.stringify({ checks, blocked }), { mode: 0o600 });
}
const redis = createClient({ url: "redis://127.0.0.1:16379/0", socket: { reconnectStrategy: false } });
async function accessibility(page: Page, label: string) {
  const AxeBuilder = (await import("@axe-core/playwright")).default;
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  if (result.violations.length > 0) {
    console.log(label + " violations: " + JSON.stringify(result.violations.map(v => ({ id: v.id, impact: v.impact, nodes: v.nodes.length }))));
  }
  check(label + " automated accessibility", result.violations.length === 0);
}
async function reflow(page: Page, label: string) {
  const original = page.viewportSize()!;
  await page.setViewportSize({ width: 320, height: 640 });
  const geometry = await page.evaluate(() => {
    const limit = window.innerWidth + 1;
    const metrics = {
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
      innerWidth: window.innerWidth,
      bodyScrollWidth: document.body.scrollWidth,
    };
    const widest = [...document.querySelectorAll<HTMLElement>("body *")]
      .map((element) => {
        const box = element.getBoundingClientRect();
        const classes = (element.getAttribute("class") ?? "").split(" ").filter((name) => name.startsWith("Mui")).slice(0, 2);
        return {
          tag: element.tagName.toLowerCase(),
          name: element.getAttribute("data-qa") ?? element.getAttribute("role") ?? element.getAttribute("aria-label") ?? "",
          classes: classes.join("."),
          left: Math.round(box.left),
          right: Math.round(box.right),
          scrollWidth: element.scrollWidth,
          clientWidth: element.clientWidth,
        };
      })
      .filter((row) => row.right > limit || row.left < 0 || row.scrollWidth > row.clientWidth + 1)
      .sort((a, b) => Math.max(b.right, b.left + b.scrollWidth) - Math.max(a.right, a.left + a.scrollWidth))
      .slice(0, 4);
    return { overflow: metrics.scrollWidth > limit, metrics, widest };
  });
  check(label + " reflows at 320 CSS pixels without horizontal scrolling" + (geometry.overflow ? " — " + JSON.stringify(geometry) : ""), !geometry.overflow);
  await page.setViewportSize(original);
}
test.beforeAll(() => redis.connect());
test.afterAll(async () => {
  await redis.quit();
  writeReport(process.env.PORTAL_E2E_REPORT!, checks, blocked);
});
async function login(page: Page, username = fixture.username, password = fixture.password) {
  await page.goto(fixture.portal);
  await page.getByRole("link", { name: "Sign in", exact: true }).click();
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.waitForURL(fixture.portal + "/account");
}
async function session(context: BrowserContext) {
  const cookie = (await context.cookies(fixture.portal)).find(v => v.name === "portal-session")!;
  return JSON.parse((await redis.get(fixture.redisPrefix + "session:" + cookie.value))!);
}
function signed(s: any, raw = "") {
  return { Authorization: "Bearer " + s.accessToken, "User-Agent": s.browserContext.userAgent, "Accept-Language": s.browserContext.acceptLanguage, Referer: s.browserContext.externalOrigin + "/", "x-request-datetime": new Date().toISOString(), "x-signature": createHmac("sha256", Buffer.from(s.signingKey, "base64")).update(raw).digest("base64"), "Content-Type": "application/json" };
}
async function java(page: Page, s: any, path: string, method = "GET", data?: unknown) {
  const raw = data === undefined ? "" : JSON.stringify(data);
  return page.request.fetch(fixture.issuer + path, { method, headers: signed(s, raw), ...(data === undefined ? {} : { data: raw }) });
}
async function bff(page: Page, path: string, method: string, data: unknown = {}) {
  const state = await (await page.request.get(fixture.portal + "/api/session")).json();
  return page.request.fetch(fixture.portal + path, { method, headers: { Origin: fixture.portal, "Content-Type": "application/json", "x-csrf-token": state.csrfToken }, data: JSON.stringify(data) });
}
function sql(query: string) {
  return JSON.parse(execFileSync("python3", ["-c", `import sys,json,re;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d);q=json.load(sys.stdin);print(sql_text(c,d,q))`], { cwd: process.env.PORTAL_REPO_ROOT!, input: JSON.stringify(query), encoding: "utf8", timeout: 20000 }));
}
function literal(value: string) { return "N'" + value.replaceAll("'", "''") + "'"; }
function password() { return "P9!" + randomBytes(18).toString("hex") + "qZ"; }
async function hydrated(area: Locator) {
  await expect.poll(() => area.evaluate(el => Object.keys(el).some(k => k.startsWith("__reactFiber$"))), { message: "form hydrated", timeout: 15000 }).toBe(true);
}
async function makeOrg(page: Page, s: any, label: string) {
  const r = await java(page, s, "/api/v1/organizations", "POST", { name: label + randomUUID(), description: "Owned journey proof" });
  check("journey organization created", r.status() === 201);
  return (await r.json()).id as string;
}
async function createUser(page: Page, s: any, org: string, name: string, secret: string) {
  const r = await java(page, s, "/api/v1/users", "POST", { firstName: "Journey", lastName: "Owned", username: name, email: name + "@example.test", password: Buffer.from(secret).toString("base64"), phoneNumber: "+19999999998", orgGuid: org, branding: "idp", syncFlag: false });
  check("journey fixture user created", r.status() === 201);
  return (await r.json()).id as string;
}
async function makeRole(page: Page, s: any, org: string, name: string, keys: string[]) {
  const r = await java(page, s, "/api/v1/organizations/" + org + "/groups", "POST", { name, description: "Owned journey role", permissionKeys: keys });
  check("journey fixture role created", r.status() === 201);
  return (await r.json()).id as string;
}
async function assign(page: Page, s: any, org: string, user: string, role: string) {
  return java(page, s, "/api/v1/organizations/" + org + "/users/" + user + "/group", "PUT", { groupId: role });
}
async function noMfa(page: Page, s: any, user: string) {
  check("journey fixture MFA disabled explicitly", (await java(page, s, "/api/v1/users/" + user + "/security-settings", "PUT", { twoFactorEnabled: false })).status() === 200);
}
function usersInOrg(org: string) {
  return sql("SELECT COUNT(*) AS n FROM Person.Profile p JOIN Person.ProfileOrganization o ON o.ProfileId=p.ProfileId JOIN Partner.Organization g ON g.OrganizationId=o.OrganizationId WHERE g.RowGuid=" + literal(org) + " FOR JSON PATH")[0].n;
}
async function contextFor(browser: Browser) {
  return browser.newContext({ locale: "en-US", userAgent: "PortalAcceptance/1.0", extraHTTPHeaders: { "Accept-Language": "en-US" }, timezoneId: "Asia/Kolkata" });
}

test("platform configures a client and delegates read-only tenant visibility without the secret", async ({ page, context, browser }) => {
  test.setTimeout(600000);
  await login(page);
  const admin = await session(context);
  const org = await makeOrg(page, admin, "F7 client ");
  const foreign = await makeOrg(page, admin, "F7 foreign ");
  await page.goto(fixture.portal + "/admin/organizations/" + org + "/clients/new");
  await expect(page.getByLabel("Client name")).toBeVisible();
  const name = "F7 delegated client " + randomUUID().slice(0, 8);
  await page.getByLabel("Client name").fill(name);
  await page.getByLabel("Client type").selectOption("SERVER");
  await page.getByLabel("Authentication method").selectOption("CLIENT_SECRET_JWT");
  await page.getByRole("button", { name: "Create identity", exact: true }).click();
  await page.getByRole("link", { name: "Continue configuration", exact: true }).click();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
  const appId = new URL(page.url()).pathname.split("/").at(-1)!;
  await hydrated(page.locator("#client-credentials"));
  await page.getByLabel("Credential name").fill("journey");
  await page.getByRole("button", { name: "Create credential", exact: true }).click();
  // Read the secret through evaluate: a locator assertion could capture a DOM containing it.
  let secret = "";
  try {
    await expect.poll(() => page.evaluate(() => !!document.querySelector('[data-qa="secret-once"]')), { message: "one-time secret appeared after credential creation", timeout: 30000 }).toBe(true);
    secret = await page.evaluate(() => document.querySelector<HTMLInputElement>('[data-qa="secret-once"]')?.value ?? "");
    check("platform client secret is nonempty once", secret.length > 20 && new URL(page.url()).searchParams.size === 0);
  } finally {
    await page.evaluate(() => document.querySelector<HTMLButtonElement>('[data-qa="secret-dismiss"]')?.click());
  }
  await expect.poll(() => page.evaluate(() => !document.querySelector('[data-qa="secret-once"]')), { message: "secret erased after dismissal", timeout: 20000 }).toBe(true);
  check("configured client persisted exactly once", sql("SELECT COUNT(*) AS n FROM Client.Application WHERE RowGuid=" + literal(appId) + " AND Name=" + literal(name) + " FOR JSON PATH")[0].n === 1);
  check("client detail never echoes the one-time secret", !(await page.content()).includes(secret) && !(await page.evaluate(() => JSON.stringify({ l: Object.entries(localStorage), s: Object.entries(sessionStorage) }))).includes(secret));
  await accessibility(page, "configured client detail");
  await reflow(page, "configured client detail");

  const role = await makeRole(page, admin, org, "F7 client viewer " + randomUUID().slice(0, 6), ["idp-admin-app:read", "idp-admin-org:read"]);
  const viewerName = "f7_viewer_" + randomUUID().replaceAll("-", "");
  const viewerSecret = password();
  const viewer = await createUser(page, admin, org, viewerName, viewerSecret);
  check("viewer role assigned", (await assign(page, admin, org, viewer, role)).status() === 204);
  await noMfa(page, admin, viewer);

  const tenantContext = await contextFor(browser);
  try {
    const own = await tenantContext.newPage();
    await login(own, viewerName, viewerSecret);
    await own.goto(fixture.portal + "/admin/organizations/" + org + "/clients");
    await expect(own.getByRole("heading", { name: "Clients", exact: true })).toBeVisible({ timeout: 20000 });
    const link = own.getByRole("link", { name, exact: true });
    check("delegated viewer sees the platform client", await link.isVisible());
    await link.click();
    await expect(own.getByRole("heading", { name, exact: true })).toBeVisible({ timeout: 20000 });
    check("viewer reads client metadata without a secret control", (await own.locator('[data-qa="secret-once"]').count()) === 0 && !(await own.content()).includes(secret));
    await accessibility(own, "delegated viewer client detail");
    const detail = await bff(own, "/api/admin/organizations/" + org + "/clients/" + appId, "GET", {});
    check("viewer BFF detail is redacted", detail.status() === 200 && !(await detail.text()).includes(secret));
    const direct = await java(own, await session(tenantContext), "/api/v1/organizations/" + org + "/applications/" + appId);
    check("viewer Java detail is redacted", direct.status() === 200 && !(await direct.text()).includes(secret));
    const foreignBff = await bff(own, "/api/admin/organizations/" + foreign + "/clients", "GET", {});
    check("viewer cannot read a foreign organization client list", [403, 404].includes(foreignBff.status()) && !(await foreignBff.text()).includes(name));
    const rotate = await bff(own, "/api/admin/organizations/" + org + "/clients/" + appId + "/credentials", "POST", { name: "F7 denied", description: "Owned denial" });
    check("viewer cannot mint client credentials", [403, 404].includes(rotate.status()));
  } finally { await tenantContext.close(); }
});

test("tenant manages its own local user and cannot cross the tenant boundary", async ({ page, context, browser }) => {
  test.setTimeout(600000);
  await login(page);
  const admin = await session(context);
  const org = await makeOrg(page, admin, "F7 tenant ");
  const foreign = await makeOrg(page, admin, "F7 other ");
  const role = await makeRole(page, admin, org, "F7 tenant manager " + randomUUID().slice(0, 6), ["idp-admin-org:read", "idp-admin-user:read", "idp-admin-user:create", "idp-admin-user:update"]);
  const managerName = "f7_manager_" + randomUUID().replaceAll("-", "");
  const managerSecret = password();
  const manager = await createUser(page, admin, org, managerName, managerSecret);
  check("tenant manager role assigned", (await assign(page, admin, org, manager, role)).status() === 204);
  await noMfa(page, admin, manager);

  const tenantContext = await contextFor(browser);
  try {
    const own = await tenantContext.newPage();
    await login(own, managerName, managerSecret);
    await own.goto(fixture.portal + "/admin/organizations/" + org + "/users");
    const form = own.getByRole("region", { name: "Create local user", exact: true });
    await hydrated(form);
    const localName = "f7_local_" + randomUUID().replaceAll("-", "");
    const localSecret = password();
    await form.getByLabel("First name", { exact: true }).fill("Tenant");
    await form.getByLabel("Last name", { exact: true }).fill("Local");
    await form.getByLabel("Username", { exact: true }).fill(localName);
    await form.getByLabel("Email address", { exact: true }).fill(localName + "@example.test");
    await form.getByLabel("Phone number", { exact: true }).fill("+19999999998");
    await form.getByLabel("Initial password", { exact: true }).fill(localSecret);
    await form.getByRole("button", { name: "Create local user", exact: true }).click();
    await own.waitForURL(/\/users\/[0-9a-f-]+$/, { timeout: 30000 });
    const local = own.url().split("/").at(-1)!;
    await accessibility(own, "tenant local user detail");
    await reflow(own, "tenant local user detail");
    check("tenant manager created its local user once", sql("SELECT COUNT(*) AS n FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId WHERE p.RowGuid=" + literal(local) + " AND c.UserName=" + literal(localName) + " FOR JSON PATH")[0].n === 1);
    check("tenant local user belongs to the home tenant", usersInOrg(org) >= 2 && usersInOrg(foreign) === 0);
    const foreignBefore = usersInOrg(foreign);
    const bffCreate = await bff(own, "/api/admin/organizations/" + foreign + "/users", "POST", { firstName: "Cross", lastName: "Tenant", username: "f7_cross_" + randomUUID().replaceAll("-", ""), email: "cross@example.test", phoneNumber: "+19999999997", initialPassword: localSecret });
    check("tenant BFF user create in a foreign organization denied", [403, 404].includes(bffCreate.status()));
    const javaCreate = await java(own, await session(tenantContext), "/api/v1/users", "POST", { firstName: "Cross", lastName: "Tenant", username: "f7_cross_" + randomUUID().replaceAll("-", ""), email: "cross2@example.test", password: Buffer.from(localSecret).toString("base64"), phoneNumber: "+19999999997", orgGuid: foreign, branding: "idp", syncFlag: false });
    check("tenant Java user create in a foreign organization denied", [403, 404].includes(javaCreate.status()));
    check("denied foreign user create wrote nothing", usersInOrg(foreign) === foreignBefore);
    const foreignRead = await bff(own, "/api/admin/organizations/" + foreign + "/users", "GET", {});
    check("tenant cannot read foreign users", [403, 404].includes(foreignRead.status()) && !(await foreignRead.text()).includes(localName));
    const ownRead = await bff(own, "/api/admin/organizations/" + org + "/users/" + local, "GET", {});
    check("tenant can read back its own user", ownRead.status() === 200 && (await ownRead.text()).includes(localName));
  } finally { await tenantContext.close(); }
});

test("hosted account profile, password, sign-out and unconfigured recovery", async ({ page, context, browser }) => {
  test.setTimeout(600000);
  await login(page);
  const admin = await session(context);
  const org = await makeOrg(page, admin, "F7 account ");
  const username = "f7_self_" + randomUUID().replaceAll("-", "");
  const original = password();
  const next = password();
  const user = await createUser(page, admin, org, username, original);
  await noMfa(page, admin, user);
  const personalContext = await contextFor(browser);
  try {
    const account = await personalContext.newPage();
    await login(account, username, original);
    await expect(account.getByRole("heading", { name: "Your account", exact: true })).toBeVisible();
    await accessibility(account, "hosted account");
    await reflow(account, "hosted account");
    async function openHosted(label: string) {
      await account.getByRole("button", { name: label, exact: true }).click({ timeout: 30000 });
      await account.getByRole("dialog").getByRole("button", { name: "Continue", exact: true }).click({ timeout: 30000 });
      await account.getByRole("link", { name: "Continue", exact: true }).click({ timeout: 30000 });
    }
    await openHosted("Change profile");
    await expect(account.getByRole("heading", { name: "Change profile", exact: true })).toBeVisible();
    await account.getByLabel("First name", { exact: true }).fill("JourneyUpdated");
    await account.getByRole("button", { name: "Update profile", exact: true }).click();
    await account.waitForURL(fixture.portal + "/account?notice=returned", { timeout: 30000 });
    check("hosted profile change persisted in SQL", sql("SELECT COUNT(*) AS n FROM Person.Profile WHERE RowGuid=" + literal(user) + " AND FirstName=N'JourneyUpdated' FOR JSON PATH")[0].n === 1);
    await account.getByRole("button", { name: "Sign out", exact: true }).click({ timeout: 30000 });
    await account.waitForURL(fixture.portal + "/auth/logged-out", { timeout: 30000 });
    check("sign-out cleared the portal session cookie", !(await personalContext.cookies(fixture.portal)).some(v => v.name === "portal-session"));
    await account.goto(fixture.portal + "/account");
    await expect(account.getByRole("heading", { name: "Sign in required", exact: true })).toBeVisible({ timeout: 20000 });
    check("browser back after sign-out cannot reopen the account", (await account.getByRole("heading", { name: "Your account", exact: true }).count()) === 0 && (await account.getByRole("navigation", { name: "Portal navigation" }).count()) === 0);
    const reopened = await account.goto(fixture.portal + "/auth/login", { referer: fixture.portal + "/" });
    check("signed-out portal serves a fresh login page", reopened !== null && reopened.status() === 200);
    await account.locator("#username").fill(username);
    await account.locator("#password").fill(original);
    await account.getByRole("button", { name: "Sign in", exact: true }).click();
    await account.waitForURL(fixture.portal + "/account", { timeout: 30000 });
    check("signed-out hosted user can sign in again", true);
    await account.getByRole("link", { name: "Security", exact: true }).click();
    await expect(account.getByRole("heading", { name: "Security", exact: true })).toBeVisible();
    await accessibility(account, "hosted security");
    await openHosted("Change password");
    await expect(account.getByRole("heading", { name: "Change password", exact: true })).toBeVisible();
    await account.getByLabel("Current password", { exact: true }).fill(original);
    await account.getByLabel("New password", { exact: true }).fill(next);
    await account.getByLabel("Confirm new password", { exact: true }).fill(next);
    await account.getByRole("button", { name: "Change password", exact: true }).click();
    await expect(account.getByRole("heading", { name: "Password changed", exact: true })).toBeVisible({ timeout: 30000 });
    check("hosted password change revoked other human sessions", sql("SELECT COUNT(*) AS n FROM Token.AuthSession s JOIN Person.Credential c ON c.UserName=s.SubjectId JOIN Person.Profile p ON p.ProfileId=c.ProfileId WHERE p.RowGuid=" + literal(user) + " AND s.AuthSessionStatusId=1 FOR JSON PATH")[0].n === 0);
    check("hosted password change ended the portal session", (await account.request.get(fixture.portal + "/api/context")).status() === 401);
    const retry = await account.goto(fixture.portal + "/auth/login", { referer: fixture.portal + "/" });
    check("post-change sign-in entry reaches the issuer sign-in form", retry !== null && retry.status() === 200 && (await account.locator("#username").count()) === 1);
    await account.locator("#username").fill(username);
    await account.locator("#password").fill(original);
    await account.getByRole("button", { name: "Sign in", exact: true }).click();
    await account.waitForURL(url => url.searchParams.has("error"), { timeout: 20000 });
    check("old password rejected after the hosted change", !account.url().startsWith(fixture.portal + "/account") && new URL(account.url()).searchParams.has("error"));
    await account.locator("#username").fill(username);
    await account.locator("#password").fill(next);
    await account.getByRole("button", { name: "Sign in", exact: true }).click();
    await account.waitForURL(fixture.portal + "/account", { timeout: 30000 });
    check("new password accepted after sign-out", true);
    await account.getByRole("link", { name: "Security", exact: true }).click();
    const recovery = await account.getByRole("button", { name: "Account recovery", exact: true });
    await recovery.click();
    const dialog = account.getByRole("dialog");
    check("recovery confirmation does not claim delivery", !/sent|delivered/i.test(await dialog.innerText()));
    const navigation = account.waitForURL(url => url.pathname.endsWith("/forgot-password"), { timeout: 20000 }).catch(() => null);
    await dialog.getByRole("button", { name: "Continue", exact: true }).click();
    const reached = await navigation;
    recordBlocked("real account recovery delivery", "provider unconfigured; recovery destination reached but no delivery success is claimed" + (reached ? "" : " (destination navigation unverified)"));
    check("recovery leaves the portal for the issuer recovery route", reached !== null);
  } finally { await personalContext.close(); }
});
