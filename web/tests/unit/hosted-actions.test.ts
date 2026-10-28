import { beforeEach, describe, expect, it } from "vitest";
import {
  buildHostedAction,
  parseHostedContinuation,
} from "../../src/server/hosted-actions";
beforeEach(() => {
  process.env.PORTAL_ORIGIN = "http://127.0.0.1:3100";
  process.env.AUTH_ISSUER = "http://localhost:8080/services";
  process.env.AUTH_CLIENT_ID = "public-client";
  process.env.PORTAL_REDIS_URL = "redis://127.0.0.1:16379";
  process.env.PORTAL_ALLOW_INSECURE_LOOPBACK = "true";
});
const encode = (s: string) => Buffer.from(s).toString("base64");
describe("Java hosted continuation contract", () => {
  it("builds bounded issuer transition with stable public login ID and UTC date", () => {
    const login = "11111111-1111-4111-8111-111111111111";
    const url = buildHostedAction("change-profile", "/account", login);
    expect(url.origin + url.pathname).toBe(
      "http://localhost:8080/services/portal/account-action",
    );
    expect(url.searchParams.get("action")).toBe("profile");
    expect(url.searchParams.get("clientId")).toBe("public-client");
    expect(url.searchParams.get("expectedLoginId")).toBe(login);
    expect(url.searchParams.get("returnTo")).toBe(
      "http://127.0.0.1:3100/account",
    );
    expect(url.searchParams.get("x-request-datetime")).toMatch(/Z$/);
    expect([...url.searchParams.keys()].sort()).toEqual([
      "action",
      "clientId",
      "expectedLoginId",
      "returnTo",
      "x-request-datetime",
    ]);
  });
  it.each(["subject-name", "not-a-uuid", ""])(
    "rejects non-stable expected identity",
    (login) =>
      expect(() =>
        buildHostedAction("change-password", "/account/security", login),
      ).toThrow(),
  );
  it("uses existing recovery path without invented continuation", () => {
    const u = buildHostedAction(
      "recovery",
      "/account/security",
      "11111111-1111-4111-8111-111111111111",
    );
    expect(u.pathname).toBe("/services/forgot-password");
    expect(u.search).toBe("");
  });
  it.each([
    "//evil.test/account",
    "https://evil.test/account",
    "/account?next=evil",
    "/account/../admin",
    "/admin",
  ])("rejects unsafe return %s", (p) =>
    expect(() =>
      buildHostedAction(
        "change-password",
        p,
        "11111111-1111-4111-8111-111111111111",
      ),
    ).toThrow(),
  );
  it("accepts exact fixed-origin account cancellation without claiming success", () => {
    expect(
      parseHostedContinuation(
        new URLSearchParams({
          p: encode(
            "surl=http%3A%2F%2F127.0.0.1%3A3100%2Faccount%2Fsecurity&errors.codes=auths.304.001",
          ),
        }),
      ),
    ).toEqual({ returnTo: "/account/security", notice: "cancelled" });
    expect(
      parseHostedContinuation(
        new URLSearchParams({
          p: encode("surl=http://127.0.0.1:3100/account"),
        }),
      ),
    ).toEqual({ returnTo: "/account", notice: "returned" });
  });
  it.each([
    "surl=https://evil.test/account",
    "surl=http://127.0.0.1:3100/account?token=secret",
    "surl=/account",
    "surl=http://127.0.0.1:3100/account&surl=http://127.0.0.1:3100/account",
    "surl=http://127.0.0.1:3100/account&access_token=secret",
  ])("rejects untrusted decoded schema", (s) =>
    expect(() =>
      parseHostedContinuation(new URLSearchParams({ p: encode(s) })),
    ).toThrow(),
  );
  it("rejects malformed, oversized and mixed OAuth continuations", () => {
    for (const query of [
      new URLSearchParams({ p: "!!!" }),
      new URLSearchParams({ p: "A".repeat(4100) }),
      new URLSearchParams({
        p: encode("surl=http://127.0.0.1:3100/account"),
        code: "x",
      }),
      new URLSearchParams({
        p: encode("surl=http://127.0.0.1:3100/account"),
        state: "x",
      }),
    ])
      expect(() => parseHostedContinuation(query)).toThrow();
  });
});
