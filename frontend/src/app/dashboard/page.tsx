"use client";

import { useSession } from "next-auth/react";
import { FiDatabase, FiSettings, FiUsers, FiActivity } from "react-icons/fi";
import styles from "./page.module.css";

export default function DashboardPage() {
  const { data: session } = useSession();

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Welcome back, {session?.user?.name}!</h1>
      <p className={styles.subtitle}>Manage your authentication server from this dashboard</p>

      <div className={styles.cardGrid}>
        <div className={styles.card}>
          <div className={styles.cardIcon} style={{ background: "#667eea" }}>
            <FiDatabase size={24} />
          </div>
          <h3>Organizations</h3>
          <p>Manage partner organizations and their configurations</p>
          <a href="/dashboard/organizations" className={styles.cardLink}>
            Manage Organizations →
          </a>
        </div>

        <div className={styles.card}>
          <div className={styles.cardIcon} style={{ background: "#764ba2" }}>
            <FiSettings size={24} />
          </div>
          <h3>Applications</h3>
          <p>Configure OAuth2 clients and application settings</p>
          <a href="/dashboard/applications" className={styles.cardLink}>
            Manage Applications →
          </a>
        </div>

        <div className={styles.card}>
          <div className={styles.cardIcon} style={{ background: "#f093fb" }}>
            <FiUsers size={24} />
          </div>
          <h3>Users</h3>
          <p>Create and manage user accounts and permissions</p>
          <a href="/dashboard/users" className={styles.cardLink}>
            Manage Users →
          </a>
        </div>

        <div className={styles.card}>
          <div className={styles.cardIcon} style={{ background: "#4facfe" }}>
            <FiActivity size={24} />
          </div>
          <h3>Activity</h3>
          <p>Monitor authentication activity and system logs</p>
          <span className={styles.cardLink} style={{ opacity: 0.5 }}>
            Coming Soon
          </span>
        </div>
      </div>

      <div className={styles.infoSection}>
        <h2>Quick Start Guide</h2>
        <ol className={styles.stepsList}>
          <li>
            <strong>Create an Organization:</strong> Start by setting up a partner organization
          </li>
          <li>
            <strong>Register an Application:</strong> Configure an OAuth2 client for your application
          </li>
          <li>
            <strong>Add Users:</strong> Create user accounts and assign them to organizations
          </li>
          <li>
            <strong>Configure Settings:</strong> Set up redirect URIs, scopes, and grant types
          </li>
        </ol>
      </div>
    </div>
  );
}
