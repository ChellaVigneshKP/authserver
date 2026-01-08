"use client";

import { useState } from "react";
import useSWR from "swr";
import { authServerApi } from "@/lib/api";
import { User, CreateUserDto } from "@/types/api";
import { FiPlus, FiEdit, FiLock, FiTrash2 } from "react-icons/fi";
import styles from "../common.module.css";

const fetcher = () => authServerApi.getUsers().then(res => res.data);

export default function UsersPage() {
  const { data: users, error, mutate } = useSWR<User[]>("/v1/users", fetcher);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [formData, setFormData] = useState<CreateUserDto>({
    orgId: 1,
    firstName: "",
    lastName: "",
    username: "",
    email: "",
    password: "",
    phoneNumber: "",
    secondaryPhoneNumber: "",
    branding: "default",
  });
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await authServerApi.createUser(formData);
      setShowCreateModal(false);
      setFormData({
        orgId: 1,
        firstName: "",
        lastName: "",
        username: "",
        email: "",
        password: "",
        phoneNumber: "",
        secondaryPhoneNumber: "",
        branding: "default",
      });
      mutate();
    } catch (error) {
      console.error("Failed to create user:", error);
      alert("Failed to create user. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (error) {
    return (
      <div className={styles.container}>
        <div className={styles.error}>Failed to load users</div>
      </div>
    );
  }

  if (!users) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>Loading users...</div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Users</h1>
          <p className={styles.subtitle}>Manage user accounts and permissions</p>
        </div>
        <button onClick={() => setShowCreateModal(true)} className={styles.primaryButton}>
          <FiPlus /> Create User
        </button>
      </div>

      <div className={styles.table}>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Username</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Organization</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{`${user.firstName} ${user.lastName}`}</td>
                <td>{user.username}</td>
                <td>{user.email || "-"}</td>
                <td>{user.phoneNumber || "-"}</td>
                <td>{user.orgId}</td>
                <td>
                  <div className={styles.actions}>
                    <button className={styles.iconButton} title="Reset Password">
                      <FiLock />
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
            <h2>Create User</h2>
            <form onSubmit={handleCreate}>
              <div className={styles.formGroup}>
                <label htmlFor="firstName">First Name *</label>
                <input
                  id="firstName"
                  type="text"
                  value={formData.firstName}
                  onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                  required
                  placeholder="Enter first name"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="lastName">Last Name *</label>
                <input
                  id="lastName"
                  type="text"
                  value={formData.lastName}
                  onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                  required
                  placeholder="Enter last name"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="username">Username *</label>
                <input
                  id="username"
                  type="text"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  required
                  placeholder="Enter username"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="email">Email *</label>
                <input
                  id="email"
                  type="email"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  required
                  placeholder="user@example.com"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="password">Password *</label>
                <input
                  id="password"
                  type="password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  required
                  placeholder="Enter password"
                  minLength={12}
                />
                <small style={{ color: "#718096", fontSize: "0.85rem" }}>
                  Must be at least 12 characters with uppercase, lowercase, number, and special character
                </small>
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="phoneNumber">Phone Number</label>
                <input
                  id="phoneNumber"
                  type="tel"
                  value={formData.phoneNumber}
                  onChange={(e) => setFormData({ ...formData, phoneNumber: e.target.value })}
                  placeholder="1234567890"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="branding">Branding *</label>
                <input
                  id="branding"
                  type="text"
                  value={formData.branding}
                  onChange={(e) => setFormData({ ...formData, branding: e.target.value })}
                  required
                  placeholder="default"
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
