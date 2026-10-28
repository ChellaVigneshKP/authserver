import { afterEach, expect, it, vi } from "vitest";
import { requestJson } from "../../src/features/clients/browser-api";

afterEach(() => vi.unstubAllGlobals());

it("classifies a non-JSON success as an invalid response instead of succeeding", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response("<html>proxy</html>", { status: 200 })),
  );
  await expect(requestJson("/api/admin/owned")).rejects.toMatchObject({
    status: 502,
    code: "INVALID_RESPONSE",
  });
});

it("keeps the upstream status when a failure body is not JSON", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response("bad gateway", { status: 502 })),
  );
  await expect(requestJson("/api/admin/owned")).rejects.toMatchObject({
    status: 502,
  });
});
