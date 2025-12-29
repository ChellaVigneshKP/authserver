import { createServerApiClient } from "./client";
import { User, PaginatedResponse } from "../types";

export async function getUsers(params: {
  resultsPerPage?: number;
  offset?: number;
  type?: string;
  search?: string;
  organizationId?: string;
  status?: string;
}): Promise<PaginatedResponse<User>> {
  const client = await createServerApiClient();
  const queryParams = new URLSearchParams();
  
  if (params.resultsPerPage) queryParams.append("resultsPerPage", params.resultsPerPage.toString());
  if (params.offset) queryParams.append("offset", params.offset.toString());
  if (params.type) queryParams.append("type", params.type);
  if (params.search) queryParams.append("search", params.search);
  if (params.organizationId) queryParams.append("organizationId", params.organizationId);
  if (params.status) queryParams.append("status", params.status);
  
  const response = await client.get(`/api/v1/users?${queryParams.toString()}`);
  return response.data;
}

export async function getUser(userGuid: string): Promise<User> {
  const client = await createServerApiClient();
  const response = await client.get(`/api/v1/users/${userGuid}`);
  return response.data;
}

export async function createUser(data: {
  loginId: string;
  password: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber?: string;
  orgGuid: string;
  groupGuid?: string;
}): Promise<User> {
  const client = await createServerApiClient();
  // Base64 encode the password as per backend requirement
  const encodedPassword = Buffer.from(data.password).toString("base64");
  
  const response = await client.post("/api/v1/users", {
    ...data,
    password: encodedPassword,
  });
  return response.data;
}

export async function updateUserProfile(
  userGuid: string,
  data: {
    firstName?: string;
    lastName?: string;
    email?: string;
    phoneNumber?: string;
  }
): Promise<User> {
  const client = await createServerApiClient();
  const response = await client.put(`/api/v1/users/${userGuid}/profile`, data);
  return response.data;
}

export async function updateUserEmail(
  userGuid: string,
  email: string,
  branding: string
): Promise<void> {
  const client = await createServerApiClient();
  await client.put(`/api/v1/users/${userGuid}/email`, { email, branding });
}

export async function updateUserPassword(
  userGuid: string,
  password: string,
  branding: string
): Promise<void> {
  const client = await createServerApiClient();
  // Base64 encode the password
  const encodedPassword = Buffer.from(password).toString("base64");
  
  await client.put(`/api/v1/users/${userGuid}/password`, {
    password: encodedPassword,
    branding,
    intent: { id: "idp" },
  });
}

export async function updateUserStatus(
  userGuid: string,
  status: number
): Promise<void> {
  const client = await createServerApiClient();
  await client.put(`/api/v1/users/${userGuid}/status`, { status });
}

export async function deleteUser(userGuid: string): Promise<void> {
  const client = await createServerApiClient();
  await client.delete(`/api/v1/users/${userGuid}`);
}

export async function getUserMetadata(
  userGuid: string
): Promise<Record<string, any>> {
  const client = await createServerApiClient();
  const response = await client.get(`/api/v1/users/${userGuid}/metadata`);
  return response.data;
}

export async function updateUserMetadata(
  userGuid: string,
  metadata: Record<string, any>
): Promise<void> {
  const client = await createServerApiClient();
  await client.put(`/api/v1/users/${userGuid}/metadata`, metadata);
}
