import { expect, it, vi } from "vitest";
import { GET as callback } from "../../src/app/auth/callback/route";
it("failed callbacks remove transient authorization parameters from the browser URL", async () => {
  vi.stubEnv("PORTAL_ORIGIN", "http://127.0.0.1:3100");
  vi.stubEnv("AUTH_ISSUER", "http://localhost:9080/services");
  vi.stubEnv("AUTH_CLIENT_ID", "portal");
  vi.stubEnv("PORTAL_REDIS_URL", "redis://127.0.0.1:16379/0");
  vi.stubEnv("PORTAL_ALLOW_INSECURE_LOOPBACK", "true");
  const result = await callback(
    new Request(
      "http://127.0.0.1:3100/auth/callback?state=invalid&code=private-code",
    ),
  );
  expect(result.status).toBe(303);
  expect(result.headers.get("location")).toBe(
    "http://127.0.0.1:3100/auth/error",
  );
  expect(await result.text()).not.toContain("private-code");
  expect(result.headers.get("cache-control")).toContain("no-store");
});
