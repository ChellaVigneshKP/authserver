import { headers } from "next/headers";
import { requireSession } from "../../server/auth";
import { getConfig } from "../../server/config";
import { getPortalContext } from "../../server/portal-context";
import { PortalFailure } from "../../server/errors";
import type { PortalSession } from "../../server/types";
import { SignOut } from "./SignOut";
export const dynamic = "force-dynamic";

function unavailable(error: unknown, canSignOut: boolean) {
  const message =
    error instanceof PortalFailure
      ? error.message
      : "The service is temporarily unavailable. Please try again.";
  return (
    <>
      <h1>Account unavailable</h1>
      <p role="alert">{message}</p>
      {canSignOut ? <SignOut /> : <a href="/auth/login">Sign in</a>}
    </>
  );
}

export default async function Account() {
  let session: PortalSession;
  try {
    session = await requireSession(
      new Request(getConfig().origin + "/account", {
        headers: await headers(),
      }),
    );
  } catch (error) {
    return unavailable(error, false);
  }
  try {
    const context = await getPortalContext(session);
    return (
      <>
        <h1>Your account</h1>
        <p>
          Signed in as <strong>{context.subject.username}</strong>.
        </p>
        <p>Your identity service verified this session.</p>
        <SignOut />
      </>
    );
  } catch (error) {
    // A Java 401 invalidates the Redis session. Other context failures must
    // leave the local sign-out path available to the authenticated browser.
    return unavailable(
      error,
      !(error instanceof PortalFailure && error.status === 401),
    );
  }
}
