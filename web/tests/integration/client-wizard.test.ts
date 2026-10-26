import { beforeAll, afterAll, expect, it } from "vitest";
import { createClient } from "redis";
import { randomUUID } from "node:crypto";
import { SessionStore } from "../../src/server/session-store";
const redis = createClient({
  url: "redis://127.0.0.1:16379/0",
  socket: { reconnectStrategy: false },
});
const prefix = "portal:f3:" + randomUUID() + ":";
const org = randomUUID(),
  owner = "a".repeat(40),
  other = "b".repeat(40);
let now = Date.now();
let store: SessionStore;
beforeAll(async () => {
  await redis.connect();
  store = new SessionStore(redis, prefix, () => now);
});
afterAll(async () => {
  for await (const keys of redis.scanIterator({ MATCH: prefix + "*" }))
    if (keys.length) await redis.del(keys);
  await redis.quit();
});
it("one atomic wizard claim prevents concurrent duplicate creates and is scoped to its session and org", async () => {
  const state = {
    identity: {
      name: "owned",
      description: "",
      type: "SERVER" as const,
      authMethod: "CLIENT_SECRET_JWT" as const,
    },
    baseline: [],
    phase: "creating" as const,
    attemptId: randomUUID(),
    startedAt: now,
    expiresAt: now + 1800000,
  };
  const claims = await Promise.all([
    store.beginWizard(owner, org, state),
    store.beginWizard(owner, org, state),
  ]);
  expect(claims.filter(Boolean).length).toBe(1);
  expect(await store.getWizard(other, org)).toBeNull();
  const ttl = await redis.pTTL(prefix + "wizard:" + owner + ":" + org);
  expect(ttl > 1790000 && ttl <= 1800000).toBe(true);
  const saved = await store.getWizard(owner, org);
  expect(saved?.phase).toBe("creating");
  expect(
    await store.replaceWizard(owner, org, saved!, {
      ...state,
      phase: "uncertain",
    }),
  ).toBe(true);
  expect(
    await store.replaceWizard(owner, org, saved!, {
      ...state,
      phase: "created",
      applicationId: randomUUID(),
    }),
  ).toBe(false);
  await expect(
    store.beginWizard(other, org, {
      ...state,
      secretValue: "forbidden",
    } as typeof state),
  ).rejects.toThrow();
  const uncertain = await store.getWizard(owner, org);
  const attempts = await Promise.all([
    store.claimWizardRetry(owner, org, uncertain!),
    store.claimWizardRetry(owner, org, uncertain!),
  ]);
  expect(attempts.filter(Boolean).length).toBe(1);
  const creating = await store.getWizard(owner, org);
  expect(creating?.phase).toBe("creating");
  expect(await store.claimWizardRetry(owner, org, creating!)).toBeNull();
  now += 1800001;
  expect(await store.getWizard(owner, org)).toBeNull();
});
