export const dynamic = "force-dynamic";
export default async function AuthError({
  searchParams,
}: {
  searchParams: Promise<{ unavailable?: string }>;
}) {
  const unavailable = (await searchParams).unavailable === "1";
  return (
    <>
      <h1>Sign-in could not be completed</h1>
      <p role="alert">
        {unavailable
          ? "The identity or session service is temporarily unavailable. Please try again shortly."
          : "This sign-in request expired or could not be verified. Please start again."}
      </p>
      <a href="/auth/login">Try sign-in again</a>
    </>
  );
}
