"use client";

import { signIn } from "next-auth/react";
import { useSearchParams } from "next/navigation";
import styles from "./signin.module.css";

export default function SignIn() {
  const searchParams = useSearchParams();
  const callbackUrl = searchParams.get("callbackUrl") || "/dashboard";

  const handleSignIn = () => {
    signIn("authserver", { callbackUrl });
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>Auth Server Admin Portal</h1>
        <p className={styles.subtitle}>Sign in to manage your authentication server</p>
        <button onClick={handleSignIn} className={styles.button}>
          Sign in with Auth Server
        </button>
      </div>
    </div>
  );
}
