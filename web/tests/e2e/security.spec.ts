import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHmac, randomUUID } from "node:crypto";
import { createClient } from "redis";
import { writeReport } from "./report";
// A locator/assertion failure must never trigger an automatic page/ARIA capture.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
test.use({ trace: "off", screenshot: "off", video: "off" });
const fixture = JSON.parse(readFileSync(process.env.PORTAL_E2E_CONFIG!, "utf8"));
const checks: { id: string; passed: boolean }[] = [];
const blocked: { id: string; reason: string }[] = [];
function check(id: string, passed: boolean) {
  checks.push({ id, passed });
  writeFileSync(process.env.PORTAL_SCREENSHOT_DIR + "/security-progress.json", JSON.stringify({ checks, blocked }), { mode: 0o600 });
  expect(passed, id).toBe(true);
}
const redis = createClient({ url: "redis://127.0.0.1:16379/0", socket: { reconnectStrategy: false } });
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
function proof(s: any, overrides: Record<string, string> = {}) {
  return { Authorization: "Bearer " + s.accessToken, "User-Agent": s.browserContext.userAgent, "Accept-Language": s.browserContext.acceptLanguage, Referer: s.browserContext.externalOrigin + "/", "x-request-datetime": new Date().toISOString(), "x-signature": createHmac("sha256", Buffer.from(s.signingKey, "base64")).update("").digest("base64"), "Content-Type": "application/json", ...overrides };
}
function tamper(signature: string) {
  const bytes = Buffer.from(signature, "base64");
  bytes[0] ^= 0x01;
  return bytes.toString("base64");
}
function sql(query: string) {
  return JSON.parse(execFileSync("python3", ["-c", `import sys,json,re;sys.path.insert(0,'scripts');from e2e_target import load_target,sql_text;c,b,d=load_target();assert re.fullmatch(r'AuthserverLifecycle_[0-9a-f]{32}',d);q=json.load(sys.stdin);print(sql_text(c,d,q))`], { cwd: process.env.PORTAL_REPO_ROOT!, input: JSON.stringify(query), encoding: "utf8", timeout: 20000 }));
}
function literal(value: string) { return "N'" + value.replaceAll("'", "''") + "'"; }

test("issuer rejects tampered signature, foreign referer, stale clock and foreign fingerprint", async ({ page, context }) => {
  test.setTimeout(300000);
  await login(page);
  const s = await session(context);
  const control = await page.request.fetch(fixture.issuer + "/api/v1/portal/context", { headers: proof(s) });
  check("valid proof is accepted", control.status() === 200);
  const cases: [string, Record<string, string>, number[]][] = [
    ["tampered signature", { "x-signature": tamper(proof(s)["x-signature"]) }, [400]],
    ["foreign referer", { Referer: "https://untrusted.example/" }, [400]],
    ["stale request clock", { "x-request-datetime": new Date(Date.now() - 15 * 60 * 1000).toISOString() }, [400]],
    ["foreign user agent", { "User-Agent": "ForeignAgent/9.9" }, [400]],
  ];
  for (const [label, override, expected] of cases) {
    const denied = await page.request.fetch(fixture.issuer + "/api/v1/portal/context", { headers: proof(s, override) });
    check("issuer rejects " + label, expected.includes(denied.status()));
    check("rejected " + label + " returns no portal data", !(await denied.text()).includes('"subject"'));
  }
  const restored = await page.request.fetch(fixture.issuer + "/api/v1/portal/context", { headers: proof(s) });
  check("rejected proofs do not disable the legitimate session", restored.status() === 200);
});

test("BFF mutations require a matching Origin and CSRF token", async ({ page, context }) => {
  test.setTimeout(300000);
  await login(page);
  const s = await session(context);
  const home = await (await page.request.fetch(fixture.issuer + "/api/v1/portal/context", { headers: proof(s) })).json();
  const org = home.subject.organizationId;
  const state = await (await page.request.get(fixture.portal + "/api/session")).json();
  function post(name: string, headers: Record<string, string>) {
    return page.request.fetch(fixture.portal + "/api/admin/organizations/" + org + "/clients", { method: "POST", headers: { "Content-Type": "application/json", ...headers }, data: JSON.stringify({ name, type: "SERVER", authenticationMethod: "CLIENT_SECRET_JWT" }) });
  }
  const forgedName = "F7 forged origin " + randomUUID().slice(0, 6);
  const wrongName = "F7 wrong token " + randomUUID().slice(0, 6);
  const missingName = "F7 missing origin " + randomUUID().slice(0, 6);
  const forged = await post(forgedName, { Origin: "https://forged.example", "x-csrf-token": state.csrfToken });
  const forgedBody = await forged.json().catch(() => ({}));
  check("forged Origin is rejected with a CSRF failure", forged.status() === 403 && forgedBody.code === "CSRF_REJECTED");
  const wrongToken = "x".repeat(String(state.csrfToken).length);
  const wrong = await post(wrongName, { Origin: fixture.portal, "x-csrf-token": wrongToken });
  check("wrong CSRF token is rejected", wrong.status() === 403);
  const missing = await post(missingName, { "x-csrf-token": state.csrfToken });
  check("missing Origin is rejected", missing.status() === 403);
  check("denied mutations wrote no client rows", sql("SELECT COUNT(*) AS n FROM Client.Application WHERE Name IN (" + [forgedName, wrongName, missingName].map(literal).join(",") + ") FOR JSON PATH")[0].n === 0);
  const allowed = await post("F7 csrf control " + randomUUID().slice(0, 6), { Origin: fixture.portal, "x-csrf-token": state.csrfToken });
  check("matched Origin and token pass the CSRF gate", allowed.status() !== 403);
});

test("portal session cookie stays opaque, HttpOnly and SameSite=Lax on loopback", async ({ page, context }) => {
  test.setTimeout(120000);
  await login(page);
  const cookie = (await context.cookies(fixture.portal)).find(v => v.name === "portal-session")!;
  check("session cookie is HttpOnly and SameSite=Lax", cookie.httpOnly === true && cookie.sameSite === "Lax" && cookie.path === "/");
  check("loopback development cookie is explicitly not Secure", cookie.secure === false);
  check("session cookie value is opaque rather than token material", cookie.value.length >= 32 && !cookie.value.includes(".") && !cookie.value.includes("eyJ"));
  const html = await page.content();
  check("session cookie value never appears in page HTML", !html.includes(cookie.value));
});
