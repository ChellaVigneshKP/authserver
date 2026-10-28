import {
  browserContext,
  getIdentityProvider,
  safeReturnTo,
  setCookie,
  transactionCookieName,
} from "../../../server/auth";
import { getStore } from "../../../server/session-store";
import { errorResponse, noStore } from "../../../server/errors";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    const url = new URL(request.url);
    const tx = await (
      await getStore()
    ).createLoginTransaction(
      safeReturnTo(url.searchParams.get("returnTo") ?? "/account"),
      browserContext(request),
    );
    const response = new Response(null, {
      status: 302,
      headers: {
        ...noStore,
        Location: await getIdentityProvider().authorize(tx),
        "Referrer-Policy": "origin",
      },
    });
    setCookie(
      response,
      transactionCookieName("login", tx.state),
      tx.binding,
      300,
    );
    return response;
  } catch (error) {
    return errorResponse(error);
  }
}
