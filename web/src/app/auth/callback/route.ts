import {
  browserContext,
  cookie,
  getIdentityProvider,
  sessionCookieName,
  setCookie,
  transactionCookieName,
} from "../../../server/auth";
import { getStore, randomId } from "../../../server/session-store";
import { getConfig } from "../../../server/config";
import { errorResponse, noStore, PortalFailure } from "../../../server/errors";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  let name: string | undefined;
  try {
    const params = new URL(request.url).searchParams;
    const state = params.get("state") ?? "";
    name = transactionCookieName("login", state);
    if (
      params.getAll("state").length !== 1 ||
      params.getAll("code").length !== 1 ||
      params.has("error")
    )
      throw new PortalFailure(
        400,
        "INVALID_TRANSACTION",
        "Sign-in was not completed. Please start again.",
      );
    const store = await getStore();
    const tx = await store.consumeLoginTransaction(
      state,
      cookie(request, name),
    );
    const actual = browserContext(request);
    if (
      actual.userAgent !== tx.context.userAgent ||
      actual.acceptLanguage !== tx.context.acceptLanguage ||
      actual.externalOrigin !== tx.context.externalOrigin
    )
      throw new PortalFailure(
        400,
        "INVALID_TRANSACTION",
        "Sign-in was not completed. Please start again.",
      );
    const identity = await getIdentityProvider().exchange(
      params.get("code")!,
      tx,
    );
    const now = Date.now();
    const session = await store.rotateSession(
      {
        ...identity,
        id: randomId(),
        createdAt: now,
        lastSeenAt: now,
        browserContext: tx.context,
        csrfSecret: randomId(),
      },
      cookie(request, sessionCookieName()),
    );
    const response = new Response(null, {
      status: 303,
      headers: { ...noStore, Location: getConfig().origin + tx.returnTo },
    });
    setCookie(response, name, "", 0);
    setCookie(
      response,
      sessionCookieName(),
      session.id,
      Math.max(
        0,
        Math.floor(
          (Math.min(identity.expiresAt, now + 28800_000) - now) / 1000,
        ),
      ),
    );
    return response;
  } catch (error) {
    const unavailable = error instanceof PortalFailure && error.status === 503;
    const response = new Response(null, {
      status: 303,
      headers: {
        ...noStore,
        Location:
          getConfig().origin +
          "/auth/error" +
          (unavailable ? "?unavailable=1" : ""),
      },
    });
    if (name) setCookie(response, name, "", 0);
    return response;
  }
}
