import {
  cookie,
  getIdentityProvider,
  requireSession,
  sessionCookieName,
  setCookie,
  transactionCookieName,
  upstreamFetch,
  transportUrl,
} from "../../../server/auth";
import { requireMutation } from "../../../server/csrf";
import { getStore } from "../../../server/session-store";
import { getConfig } from "../../../server/config";
import { errorResponse, jsonResponse, noStore } from "../../../server/errors";
export const dynamic = "force-dynamic";
export async function POST(request: Request) {
  try {
    const session = await requireSession(request);
    await requireMutation(request, session);
    const store = await getStore();
    await store.deleteSession(session.id);
    let response: Response;
    try {
      const tx = await store.createLogoutTransaction(session);
      response = jsonResponse({
        redirect: "/auth/logout?transaction=" + tx.state,
      });
      setCookie(
        response,
        transactionCookieName("logout", tx.state),
        tx.binding,
        300,
      );
    } catch {
      response = jsonResponse({ redirect: "/auth/logged-out?incomplete=1" });
    }
    setCookie(response, sessionCookieName(), "", 0);
    return response;
  } catch (error) {
    return errorResponse(error);
  }
}
export async function GET(request: Request) {
  let name: string | undefined;
  try {
    const state = new URL(request.url).searchParams.get("transaction") ?? "";
    name = transactionCookieName("logout", state);
    const tx = await (
      await getStore()
    ).consumeLogoutTransaction(state, cookie(request, name));
    let target: string;
    try {
      const config = getConfig();
      const health = await upstreamFetch(
        transportUrl(
          config,
          config.issuer + "/.well-known/openid-configuration",
        ),
      );
      if (!health.ok) throw Error();
      target = await getIdentityProvider().logoutUrl(tx.idToken);
    } catch {
      target = getConfig().origin + "/auth/logged-out?incomplete=1";
    }
    const response = new Response(null, {
      status: 303,
      headers: { ...noStore, Location: target },
    });
    setCookie(response, name, "", 0);
    return response;
  } catch (error) {
    const response = errorResponse(error);
    if (name) setCookie(response, name, "", 0);
    return response;
  }
}
