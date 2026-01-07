"use client";

import { useState } from "react";
import useSWR from "swr";
import { authServerApi } from "@/lib/api";
import { Application } from "@/types/api";
import { FiPlus, FiEdit, FiKey, FiTrash2, FiCopy } from "react-icons/fi";
import styles from "../common.module.css";

const fetcher = () => authServerApi.getApplications().then(res => res.data);

export default function ApplicationsPage() {
  const { data: applications, error, mutate } = useSWR<Application[]>("/v1/applications", fetcher);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showSecretModal, setShowSecretModal] = useState(false);
  const [selectedApp, setSelectedApp] = useState<Application | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleGenerateSecret = async (app: Application) => {
    setSelectedApp(app);
    setShowSecretModal(true);
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    alert("Copied to clipboard!");
  };

  if (error) {
    return (
      <div className={styles.container}>
        <div className={styles.error}>Failed to load applications</div>
      </div>
    );
  }

  if (!applications) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>Loading applications...</div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Applications</h1>
          <p className={styles.subtitle}>Manage OAuth2 clients and application settings</p>
        </div>
        <button onClick={() => setShowCreateModal(true)} className={styles.primaryButton}>
          <FiPlus /> Create Application
        </button>
      </div>

      <div className={styles.table}>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Client ID</th>
              <th>Type</th>
              <th>Auth Flow</th>
              <th>URI</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {applications.map((app) => (
              <tr key={app.applicationId}>
                <td>{app.name}</td>
                <td>
                  <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                    <code style={{ fontSize: "0.85rem" }}>{app.clientId}</code>
                    <button
                      onClick={() => copyToClipboard(app.clientId)}
                      className={styles.iconButton}
                      title="Copy Client ID"
                    >
                      <FiCopy size={14} />
                    </button>
                  </div>
                </td>
                <td>{app.applicationType || "N/A"}</td>
                <td>{app.authFlow || "N/A"}</td>
                <td style={{ maxWidth: "200px", overflow: "hidden", textOverflow: "ellipsis" }}>
                  {app.uri}
                </td>
                <td>
                  <div className={styles.actions}>
                    <button
                      className={styles.iconButton}
                      onClick={() => handleGenerateSecret(app)}
                      title="Manage Secrets"
                    >
                      <FiKey />
                    </button>
                    <button className={styles.iconButton} title="Edit">
                      <FiEdit />
                    </button>
                    <button className={styles.iconButton} title="Delete">
                      <FiTrash2 />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreateModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h2>Create Application</h2>
            <form>
              <div className={styles.formGroup}>
                <label htmlFor="name">Application Name *</label>
                <input id="name" type="text" required placeholder="Enter application name" />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="description">Description</label>
                <textarea id="description" placeholder="Optional description" rows={3} />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="uri">Application URI *</label>
                <input id="uri" type="url" required placeholder="https://example.com" />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="appType">Application Type *</label>
                <select id="appType" required>
                  <option value="">Select type...</option>
                  <option value="1">Web Application</option>
                  <option value="2">Native Application</option>
                  <option value="3">Single Page Application</option>
                </select>
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="authFlow">Authentication Flow *</label>
                <select id="authFlow" required>
                  <option value="">Select flow...</option>
                  <option value="1">Authorization Code</option>
                  <option value="2">Implicit</option>
                  <option value="3">Client Credentials</option>
                </select>
              </div>
              <div className={styles.modalActions}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className={styles.secondaryButton}
                >
                  Cancel
                </button>
                <button type="submit" className={styles.primaryButton}>
                  Create
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showSecretModal && selectedApp && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h2>Client Secrets - {selectedApp.name}</h2>
            <p style={{ marginBottom: "1.5rem", color: "#718096" }}>
              Generate and manage client secrets for this application.
            </p>
            <div style={{ marginBottom: "1.5rem" }}>
              <strong>Client ID:</strong>
              <div
                style={{
                  background: "#f7fafc",
                  padding: "0.75rem",
                  borderRadius: "0.5rem",
                  marginTop: "0.5rem",
                  fontFamily: "monospace",
                }}
              >
                {selectedApp.clientId}
              </div>
            </div>
            <div className={styles.modalActions}>
              <button
                onClick={() => setShowSecretModal(false)}
                className={styles.secondaryButton}
              >
                Close
              </button>
              <button className={styles.primaryButton}>
                <FiPlus /> Generate New Secret
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
