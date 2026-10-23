"use client";
import { useState } from "react";
export function SignOut() {
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  async function logout() {
    setPending(true);
    setError("");
    try {
      const info = await fetch("/api/session", { cache: "no-store" });
      if (!info.ok) throw Error("Your session has ended. Return to sign-in.");
      const { csrfToken } = await info.json();
      const response = await fetch("/auth/logout", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-csrf-token": csrfToken,
        },
        body: "{}",
      });
      const result = await response.json();
      if (!response.ok)
        throw Error(
          result.message ??
            "Sign-out could not be completed. Please try again.",
        );
      if (
        typeof result.redirect !== "string" ||
        !result.redirect.startsWith("/auth/")
      )
        throw Error("Sign-out could not be completed.");
      location.assign(result.redirect);
    } catch (error) {
      setError(error instanceof Error ? error.message : "Please try again.");
      setPending(false);
    }
  }
  return (
    <>
      <button type="button" onClick={logout} disabled={pending}>
        {pending ? "Signing out…" : "Sign out"}
      </button>
      {error && (
        <p role="alert">
          {error} <a href="/auth/login">Sign in</a>
        </p>
      )}
    </>
  );
}
