import { afterAll, beforeAll, expect, it, vi } from "vitest";
import { createServer, connect, type Server, type Socket } from "node:net";
import { randomUUID } from "node:crypto";
import { createClient } from "redis";
import { getStore } from "../../src/server/session-store";
const namespace = "outage-" + randomUUID();
const prefix = "portal:v1:" + namespace + ":";
const context = {
  userAgent: "RedisOutageTest",
  acceptLanguage: "en-US",
  externalOrigin: "http://127.0.0.1:3100",
};
let proxy: Server;
let stalled = false;
const sockets = new Set<Socket>();
beforeAll(async () => {
  proxy = createServer((incoming) => {
    const upstream = connect(16379, "127.0.0.1");
    sockets.add(incoming);
    sockets.add(upstream);
    incoming.on("data", (data) => upstream.write(data));
    upstream.on("data", (data) => {
      if (!stalled) incoming.write(data);
    });
    for (const socket of [incoming, upstream]) socket.on("error", () => {});
    incoming.on("close", () => {
      upstream.destroy();
      sockets.delete(incoming);
      sockets.delete(upstream);
    });
    upstream.on("close", () => incoming.destroy());
  });
  await new Promise<void>((resolve) => proxy.listen(0, "127.0.0.1", resolve));
  for (const [name, value] of Object.entries({
    PORTAL_ORIGIN: context.externalOrigin,
    AUTH_ISSUER: "http://localhost:9080/services",
    AUTH_CLIENT_ID: "portal",
    PORTAL_REDIS_URL:
      "redis://127.0.0.1:" + (proxy.address() as { port: number }).port + "/0",
    PORTAL_REDIS_NAMESPACE: namespace,
    PORTAL_ALLOW_INSECURE_LOOPBACK: "true",
  }))
    vi.stubEnv(name, value);
});
afterAll(async () => {
  for (const socket of sockets) socket.destroy();
  await new Promise<void>((resolve) => proxy.close(() => resolve()));
  const cleanup = createClient({
    url: "redis://127.0.0.1:16379/0",
    socket: { reconnectStrategy: false },
  });
  await cleanup.connect();
  for await (const keys of cleanup.scanIterator({ MATCH: prefix + "*" }))
    if (keys.length) await cleanup.del(keys);
  await cleanup.quit();
});
it("an established but stalled Redis connection returns controlled503 within the dependency deadline", async () => {
  const store = await getStore();
  await store.createLoginTransaction("/account", context);
  stalled = true;
  try {
    const status = await Promise.race([
      store.createLoginTransaction("/account", context).then(
        () => 200,
        (error) => error.status,
      ),
      new Promise<number>((resolve) => setTimeout(() => resolve(0), 2500)),
    ]);
    expect(status).toBe(503);
  } finally {
    stalled = false;
    for (const socket of sockets) socket.destroy();
  }
});
it("a lost owned Redis connection can recover on a subsequent request without restarting Next", async () => {
  await new Promise((resolve) => setTimeout(resolve, 50));
  const store = await getStore();
  const tx = await store.createLoginTransaction("/account", context);
  expect(
    (await store.consumeLoginTransaction(tx.state, tx.binding)).nonce ===
      tx.nonce,
  ).toBe(true);
});
