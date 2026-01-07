"use client";

import { useSession, signOut } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useEffect, ReactNode } from "react";
import { FiHome, FiUsers, FiSettings, FiLogOut, FiDatabase } from "react-icons/fi";
import Link from "next/link";
import styles from "./dashboard.module.css";

interface DashboardLayoutProps {
  children: ReactNode;
}

export default function DashboardLayout({ children }: DashboardLayoutProps) {
  const { data: session, status } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (status === "unauthenticated") {
      router.push("/auth/signin");
    }
  }, [status, router]);

  if (status === "loading") {
    return (
      <div className={styles.loading}>
        <div className={styles.spinner}></div>
        <p>Loading...</p>
      </div>
    );
  }

  if (!session) {
    return null;
  }

  return (
    <div className={styles.container}>
      <nav className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <h2>Admin Portal</h2>
          <p className={styles.userInfo}>{session.user?.name}</p>
        </div>
        <ul className={styles.navList}>
          <li>
            <Link href="/dashboard" className={styles.navLink}>
              <FiHome /> Dashboard
            </Link>
          </li>
          <li>
            <Link href="/dashboard/organizations" className={styles.navLink}>
              <FiDatabase /> Organizations
            </Link>
          </li>
          <li>
            <Link href="/dashboard/applications" className={styles.navLink}>
              <FiSettings /> Applications
            </Link>
          </li>
          <li>
            <Link href="/dashboard/users" className={styles.navLink}>
              <FiUsers /> Users
            </Link>
          </li>
        </ul>
        <div className={styles.sidebarFooter}>
          <button onClick={() => signOut({ callbackUrl: "/" })} className={styles.logoutButton}>
            <FiLogOut /> Sign Out
          </button>
        </div>
      </nav>
      <main className={styles.main}>{children}</main>
    </div>
  );
}
