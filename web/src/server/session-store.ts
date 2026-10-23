import "server-only";
import { randomBytes } from "node:crypto";
import { createClient } from "redis";
import { getConfig } from "./config";
import { PortalFailure } from "./errors";
import type {
  BrowserContext,
  PortalSession,
  LoginTransaction,
  LogoutTransaction,
} from "./types";
const random = () => randomBytes(32).toString("base64url");
const invalid = () =>
  new PortalFailure(
    400,
    "INVALID_TRANSACTION",
    "This sign-in or sign-out request has expired. Please start again.",
  );
const consume = `local raw=redis.call('GET',KEYS[1]); if not raw then return nil end; local t=cjson.decode(raw); if t.binding~=ARGV[1] then return nil end; redis.call('DEL',KEYS[1]); if t.expiresAt<=tonumber(ARGV[2]) then return nil end; return raw`;
const touch = `local raw=redis.call('GET',KEYS[1]); if not raw then return nil end; local s=cjson.decode(raw); local now=tonumber(ARGV[1]); local expiry=math.min(s.expiresAt,s.createdAt+28800000,s.lastSeenAt+1800000); if expiry<=now then redis.call('DEL',KEYS[1]); return nil end; s.lastSeenAt=now; local ttl=math.min(s.expiresAt,s.createdAt+28800000,now+1800000)-now; redis.call('SET',KEYS[1],cjson.encode(s),'PX',math.floor(ttl)); return cjson.encode(s)`;
// A client command timeout may cover only its unsent queue. Bound the complete
// operation and discard a stalled connection; an uncertain write is never replayed.
async function boundedRedis<T>(
  operation: () => Promise<T>,
  close: () => void,
): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      operation(),
      new Promise<never>((_, reject) => {
        timer = setTimeout(() => {
          close();
          reject(new Error("Redis dependency deadline exceeded"));
        }, 2000);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}
export class SessionStore {
  constructor(
    private redis: ReturnType<typeof createClient<{}, {}, {}, 3>>,
    private prefix: string,
    private now = Date.now,
  ) {}
  private key(type: string, id: string) {
    if (!/^[a-zA-Z0-9_-]{20,128}$/.test(id)) throw invalid();
    return this.prefix + type + ":" + id;
  }
  private async dependency<T>(fn: () => Promise<T>): Promise<T> {
    try {
      return await boundedRedis(fn, () => {
        if (this.redis.isOpen) this.redis.destroy();
      });
    } catch (error) {
      if (error instanceof PortalFailure) throw error;
      throw new PortalFailure(
        503,
        "SESSION_UNAVAILABLE",
        "Sessions are temporarily unavailable. Please try again.",
        true,
      );
    }
  }
  async createLoginTransaction(
    returnTo: string,
    context: BrowserContext,
  ): Promise<LoginTransaction> {
    const tx = {
      state: random(),
      binding: random(),
      nonce: random(),
      verifier: randomBytes(48).toString("base64url"),
      returnTo,
      context,
      expiresAt: this.now() + 300_000,
    };
    await this.dependency(() =>
      this.redis.set(this.key("login", tx.state), JSON.stringify(tx), {
        PX: 300_000,
        NX: true,
      }),
    );
    return tx;
  }
  async consumeLoginTransaction(
    state: string,
    binding: string,
  ): Promise<LoginTransaction> {
    return this.consume("login", state, binding);
  }
  private async consume<T>(
    type: string,
    state: string,
    binding: string,
  ): Promise<T> {
    const raw = await this.dependency(() =>
      this.redis.eval(consume, {
        keys: [this.key(type, state)],
        arguments: [binding, String(this.now())],
      }),
    );
    if (typeof raw !== "string") throw invalid();
    return JSON.parse(raw);
  }
  async createLogoutTransaction(
    session: PortalSession,
  ): Promise<LogoutTransaction> {
    const tx = {
      state: random(),
      binding: random(),
      idToken: session.idToken,
      expiresAt: this.now() + 300_000,
    };
    await this.dependency(() =>
      this.redis.set(this.key("logout", tx.state), JSON.stringify(tx), {
        PX: 300_000,
        NX: true,
      }),
    );
    return tx;
  }
  async consumeLogoutTransaction(
    state: string,
    binding: string,
  ): Promise<LogoutTransaction> {
    return this.consume("logout", state, binding);
  }
  async putSession(session: PortalSession) {
    const ttl =
      Math.min(
        session.expiresAt,
        session.createdAt + 28800_000,
        session.lastSeenAt + 1800_000,
      ) - this.now();
    if (ttl <= 0) return;
    await this.dependency(() =>
      this.redis.set(
        this.key("session", session.id),
        JSON.stringify({
          ...session,
          signingKey: Buffer.from(session.signingKey).toString("base64"),
        }),
        { PX: Math.floor(ttl), NX: true },
      ),
    );
  }
  async getSession(id: string): Promise<PortalSession | null> {
    if (!/^[a-zA-Z0-9_-]{20,128}$/.test(id)) return null;
    const raw = await this.dependency(() =>
      this.redis.eval(touch, {
        keys: [this.key("session", id)],
        arguments: [String(this.now())],
      }),
    );
    if (typeof raw !== "string") return null;
    const value = JSON.parse(raw);
    return { ...value, signingKey: Buffer.from(value.signingKey, "base64") };
  }
  async touchSession(session: PortalSession) {
    return this.getSession(session.id);
  }
  async deleteSession(id: string) {
    if (!/^[a-zA-Z0-9_-]{20,128}$/.test(id)) return;
    await this.dependency(() => this.redis.del(this.key("session", id)));
  }
  async rotateSession(session: PortalSession, oldId?: string) {
    if (oldId) await this.deleteSession(oldId);
    await this.putSession(session);
    return session;
  }
}
let singleton: Promise<SessionStore> | undefined;
let activeClient: ReturnType<typeof createClient<{}, {}, {}, 3>> | undefined;
export function getStore(): Promise<SessionStore> {
  return (singleton ??= (async () => {
    const config = getConfig();
    const redis = createClient({
      url: config.redisUrl,
      socket: { connectTimeout: 2000, reconnectStrategy: false },
      disableOfflineQueue: true,
      commandOptions: { timeout: 2000 },
    });
    activeClient = redis;
    const invalidate = () => {
      // Reconnect only on a later request; never replay an uncertain command.
      if (activeClient === redis) {
        singleton = undefined;
        activeClient = undefined;
      }
    };
    redis.on("error", invalidate);
    redis.on("end", invalidate);
    try {
      await boundedRedis(
        () => redis.connect(),
        () => {
          if (redis.isOpen) redis.destroy();
        },
      );
      return new SessionStore(redis, config.redisPrefix);
    } catch {
      singleton = undefined;
      throw new PortalFailure(
        503,
        "SESSION_UNAVAILABLE",
        "Sessions are temporarily unavailable. Please try again.",
        true,
      );
    }
  })());
}
export { random as randomId };
