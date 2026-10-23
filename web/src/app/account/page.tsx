import { headers } from "next/headers";
import { requireSession } from "../../server/auth";
import { getConfig } from "../../server/config";
import { getPortalContext } from "../../server/portal-context";
import { PortalFailure } from "../../server/errors";
import { SignOut } from "./SignOut";
export const dynamic = "force-dynamic";
export default async function Account() {
  try {
    const session = await requireSession(
      new Request(getConfig().origin + "/account", {
        headers: await headers(),
      }),
    );
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
    const message =
      error instanceof PortalFailure
        ? error.message
        : "The service is temporarily unavailable. Please try again.";
    return (
      <>
        <h1>Account unavailable</h1>
        <p role="alert">{message}</p>
        <a href="/auth/login">Sign in</a>
      </>
    );
  }
}
