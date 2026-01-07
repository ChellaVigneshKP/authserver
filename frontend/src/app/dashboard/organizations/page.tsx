"use client";

import { useState } from "react";
import useSWR from "swr";
import { authServerApi } from "@/lib/api";
import { Organization } from "@/types/api";
import { FiPlus, FiEdit, FiTrash2 } from "react-icons/fi";
import styles from "../common.module.css";

const fetcher = () => authServerApi.getOrganizations().then(res => res.data);

export default function OrganizationsPage() {
  const { data: organizations, error, mutate } = useSWR<Organization[]>("/v1/organizations", fetcher);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [formData, setFormData] = useState({ name: "", note: "" });
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await authServerApi.createOrganization(formData);
      setShowCreateModal(false);
      setFormData({ name: "", note: "" });
      mutate();
    } catch (error) {
      console.error("Failed to create organization:", error);
      alert("Failed to create organization. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (error) {
    return (
      <div className={styles.container}>
        <div className={styles.error}>Failed to load organizations</div>
      </div>
    );
  }

  if (!organizations) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>Loading organizations...</div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Organizations</h1>
          <p className={styles.subtitle}>Manage partner organizations</p>
        </div>
        <button onClick={() => setShowCreateModal(true)} className={styles.primaryButton}>
          <FiPlus /> Create Organization
        </button>
      </div>

      <div className={styles.table}>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Note</th>
              <th>Created On</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {organizations.map((org) => (
              <tr key={org.organizationId}>
                <td>{org.name}</td>
                <td>{org.note || "-"}</td>
                <td>{new Date(org.createdOn).toLocaleDateString()}</td>
                <td>
                  <span className={org.status === 1 ? styles.statusActive : styles.statusInactive}>
                    {org.status === 1 ? "Active" : "Inactive"}
                  </span>
                </td>
                <td>
                  <div className={styles.actions}>
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
            <h2>Create Organization</h2>
            <form onSubmit={handleCreate}>
              <div className={styles.formGroup}>
                <label htmlFor="name">Organization Name *</label>
                <input
                  id="name"
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  required
                  placeholder="Enter organization name"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="note">Note</label>
                <textarea
                  id="note"
                  value={formData.note}
                  onChange={(e) => setFormData({ ...formData, note: e.target.value })}
                  placeholder="Optional description"
                  rows={3}
                />
              </div>
              <div className={styles.modalActions}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className={styles.secondaryButton}
                  disabled={isSubmitting}
                >
                  Cancel
                </button>
                <button type="submit" className={styles.primaryButton} disabled={isSubmitting}>
                  {isSubmitting ? "Creating..." : "Create"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
