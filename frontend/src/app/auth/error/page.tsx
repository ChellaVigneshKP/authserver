"use client";

import { useSearchParams } from "next/navigation";
import Link from "next/link";
import styles from "../signin/signin.module.css";

export default function ErrorPage() {
  const searchParams = useSearchParams();
  const error = searchParams.get("error");

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>Authentication Error</h1>
        <p className={styles.subtitle} style={{ color: "#e53e3e" }}>
          {error === "RefreshAccessTokenError"
            ? "Your session has expired. Please sign in again."
            : "An error occurred during authentication."}
        </p>
        <Link href="/auth/signin">
          <button className={styles.button}>Return to Sign In</button>
        </Link>
      </div>
    </div>
  );
}
