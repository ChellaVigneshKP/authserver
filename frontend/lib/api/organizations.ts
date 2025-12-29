import { createServerApiClient } from "./client";
import {
  Organization,
  OrganizationGroup,
  OrganizationGroupPermission,
} from "../types";

export async function getOrganizations(): Promise<Organization[]> {
  const client = await createServerApiClient();
  const response = await client.get("/api/v1/organizations");
  return response.data;
}

export async function getOrganization(orgGuid: string): Promise<Organization> {
  const client = await createServerApiClient();
  const response = await client.get(`/api/v1/organizations/${orgGuid}`);
  return response.data;
}

export async function createOrganization(data: {
  name: string;
  description?: string;
}): Promise<Organization> {
  const client = await createServerApiClient();
  const response = await client.post("/api/v1/organizations", data);
  return response.data;
}

export async function updateOrganization(
  orgGuid: string,
  data: {
    name?: string;
    description?: string;
  }
): Promise<Organization> {
  const client = await createServerApiClient();
  const response = await client.put(`/api/v1/organizations/${orgGuid}`, data);
  return response.data;
}

export async function updatePrimaryContact(
  orgGuid: string,
  contact: {
    name: string;
    email: string;
    phone?: string;
  }
): Promise<Organization> {
  const client = await createServerApiClient();
  const response = await client.put(
    `/api/v1/organizations/${orgGuid}/primary-contact`,
    contact
  );
  return response.data;
}

export async function updateSecondaryContact(
  orgGuid: string,
  contact: {
    name: string;
    email: string;
    phone?: string;
  }
): Promise<Organization> {
  const client = await createServerApiClient();
  const response = await client.put(
    `/api/v1/organizations/${orgGuid}/secondary-contact`,
    contact
  );
  return response.data;
}

export async function getOrganizationGroups(
  orgGuid: string
): Promise<OrganizationGroup[]> {
  const client = await createServerApiClient();
  const response = await client.get(`/api/v1/organizations/${orgGuid}/groups`);
  return response.data;
}

export async function getOrganizationGroupPermissions(
  orgGuid: string,
  groupGuid: string
): Promise<OrganizationGroupPermission[]> {
  const client = await createServerApiClient();
  const response = await client.get(
    `/api/v1/organizations/${orgGuid}/groups/${groupGuid}/permissions`
  );
  return response.data;
}
