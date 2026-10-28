export const dynamic = "force-dynamic";
export default async function LoggedOut({
  searchParams,
}: {
  searchParams: Promise<{ incomplete?: string }>;
}) {
  const incomplete = (await searchParams).incomplete === "1";
  return (
    <>
      <h1>Signed out of the portal</h1>
      <p>
        {incomplete
          ? "Your portal session has ended. Identity-provider sign-out could not be completed; its SSO session may still be active."
          : "Your identity-provider sign-out completed."}
      </p>
      <a href="/auth/login">Sign in</a>
    </>
  );
}
