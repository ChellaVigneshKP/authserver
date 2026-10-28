import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHmac, randomUUID } from "node:crypto";
import { createClient } from "redis";
// A locator/assertion failure must never trigger an automatic page/ARIA capture.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
test.use({ trace: "off", screenshot: "off", video: "off" });
const fixture = JSON.parse(readFileSync(process.env.PORTAL_E2E_CONFIG!, "utf8"));
const checks: { id: string; passed: boolean }[] = [];
const blocked: { id: string; reason: string }[] = [];
function prior(path: string): { checks: { id: string; passed: boolean }[]; blocked: { id: string; reason: string }[] } {
  if (!existsSync(path)) return { checks: [], blocked: [] };
  const parsed = JSON.parse(readFileSync(path, "utf8"));
  if (Array.isArray(parsed)) return { checks: parsed, blocked: [] };
  return { checks: parsed.checks ?? [], blocked: parsed.blocked ?? [] };
}
function check(id: string, passed: boolean) {
  checks.push({ id, passed });
  writeFileSync(process.env.PORTAL_SCREENSHOT_DIR + "/outages-progress.json", JSON.stringify({ checks, blocked }), { mode: 0o600 });
  expect(passed, id).toBe(true);
}
function recordBlocked(id: string, reason: string) {
  blocked.push({ id, reason });
  writeFileSync(process.env.PORTAL_SCREENSHOT_DIR + "/outages-progress.json", JSON.stringify({ checks, blocked }), { mode: 0o600 });
}
const redis = createClient({ url: "redis://127.0.0.1:16379/0", socket: { reconnectStrategy: false } });
test.beforeAll(() => redis.connect());
test.afterAll(async () => {
  await redis.quit();
  const path = process.env.PORTAL_E2E_REPORT!;
  const merged = prior(path);
  writeFileSync(path, JSON.stringify({
    checks: [...merged.checks.filter(v => !checks.some(c => c.id === v.id)), ...checks],
    blocked: [...merged.blocked.filter(v => !blocked.some(b => b.id === v.id)), ...blocked],
  }), { mode: 0o600 });
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
async function java(page: Page, s: any, path: string, method = "GET") {
  return page.request.fetch(fixture.issuer + path, { method, headers: signed(s) });
}
function sql(query: string) {
  return JSON.parse(execFileSync("python3", ["-c", `import sys,json,re;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d);q=json.load(sys.stdin);print(sql_text(c,d,q))`], { cwd: process.env.PORTAL_REPO_ROOT!, input: JSON.stringify(query), encoding: "utf8", timeout: 20000 }));
}
function literal(value: string) { return "N'" + value.replaceAll("'", "''") + "'"; }
function ownedPid() {
  const pid = Number(process.env.PORTAL_OWNED_JAVA_PID);
  if (!Number.isInteger(pid) || pid <= 1) throw Error("Missing owned issuer process identity");
  return pid;
}

test("paused issuer refuses the mutation without writing and resumes without duplicates", async ({ page, context }) => {
  test.setTimeout(420000);
  await login(page);
  const admin = await session(context);
  const org = (await (await java(page, admin, "/api/v1/portal/context")).json()).subject.organizationId;
  const name = "F7 paused client " + randomUUID().slice(0, 8);
  await page.goto(fixture.portal + "/admin/organizations/" + org + "/clients/new");
  await expect(page.getByLabel("Client name")).toBeVisible();
  await page.getByLabel("Client name").fill(name);
  await page.getByLabel("Client type").selectOption("SERVER");
  const pid = ownedPid();
  process.kill(pid, "SIGSTOP");
  try {
    await page.getByRole("button", { name: "Create identity", exact: true }).click();
    await expect(page.getByRole("alert").filter({ hasText: /temporarily unavailable/i })).toBeVisible({ timeout: 60000 });
    check("paused issuer is reported as a retryable portal failure", true);
    check("paused issuer wrote no client row", sql("SELECT COUNT(*) AS n FROM Client.Application WHERE Name=" + literal(name) + " FOR JSON PATH")[0].n === 0);
    const health = await page.request.get(fixture.portal + "/healthz");
    check("liveness ignores an unavailable issuer", health.status() === 200 && (await health.json()).status === "ok");
  } finally {
    process.kill(pid, "SIGCONT");
  }
  await expect.poll(async () => {
    try { return (await page.request.get(fixture.portal + "/auth/login", { maxRedirects: 0 })).status(); } catch { return 0; }
  }, { timeout: 60000, message: "issuer resumed" }).toBe(302);
  check("paused issuer resumes and the portal starts a login transaction", true);
  await page.reload();
  await expect(page.getByLabel("Client name")).toBeVisible({ timeout: 30000 });
  await page.getByLabel("Client name").fill(name);
  await page.getByLabel("Client type").selectOption("SERVER");
  await page.getByRole("button", { name: "Create identity", exact: true }).click();
  await expect(page.getByRole("link", { name: "Continue configuration", exact: true })).toBeVisible({ timeout: 60000 });
  check("resumed issuer commits exactly one client", sql("SELECT COUNT(*) AS n FROM Client.Application WHERE Name=" + literal(name) + " FOR JSON PATH")[0].n === 1);
});

test("non-UTC browser keeps the portal fingerprint contract", async ({ browser }) => {
  test.setTimeout(300000);
  const local = await browser.newContext({ locale: "en-US", userAgent: "PortalAcceptance/1.0", extraHTTPHeaders: { "Accept-Language": "en-US" }, timezoneId: "America/Los_Angeles" });
  try {
    const page = await local.newPage();
    await login(page);
    const context = await page.request.get(fixture.portal + "/api/context");
    check("non-UTC browser obtains a portal context", context.status() === 200);
    const s = await session(local);
    check("non-UTC browser stores an unchanged declared fingerprint", s.browserContext.userAgent === "PortalAcceptance/1.0" && s.browserContext.acceptLanguage === "en-US" && s.browserContext.externalOrigin === fixture.portal);
    const org = (await context.json()).subject.organizationId;
    await page.goto(fixture.portal + "/admin/organizations/" + org + "/clients");
    await expect(page.getByRole("heading", { name: "Clients", exact: true })).toBeVisible({ timeout: 30000 });
    check("non-UTC browser reads management data through the real backend", true);
  } finally {
    await local.close();
  }
});

test("owned portal restart preserves the session and reports readiness", async ({ page }) => {
  test.setTimeout(420000);
  await login(page);
  check("context available before restart", (await page.request.get(fixture.portal + "/api/context")).status() === 200);
  const dir = process.env.PORTAL_SCREENSHOT_DIR!;
  writeFileSync(dir + "/restart-next.json", JSON.stringify({ reason: "owned acceptance restart" }), { mode: 0o600 });
  await expect.poll(() => {
    try { return JSON.parse(readFileSync(dir + "/restart-next-status.json", "utf8")).status; } catch { return "pending"; }
  }, { timeout: 240000, message: "owned portal restarted" }).toBe("ready");
  check("restart watcher reported a ready replacement", true);
  const health = await page.request.get(fixture.portal + "/healthz");
  check("liveness answers after restart", health.status() === 200 && (await health.json()).status === "ok");
  const ready = await page.request.get(fixture.portal + "/readyz");
  check("readiness answers after restart", ready.status() === 200 && (await ready.json()).status === "ready");
  await expect.poll(async () => {
    try { return (await page.request.get(fixture.portal + "/api/context")).status(); } catch { return 0; }
  }, { timeout: 60000, message: "existing session survives restart" }).toBe(200);
  check("existing Redis session survives an owned restart", true);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Your account", exact: true })).toBeVisible({ timeout: 30000 });
  check("browser session remains usable after restart", true);
  const fresh = await page.request.get(fixture.portal + "/auth/login", { maxRedirects: 0 });
  check("restarted portal starts a login transaction", fresh.status() === 302);
  recordBlocked("real account recovery delivery", "issuer delivery provider unconfigured; no delivery success is claimed by this campaign");
});
