import { createServerApiClient } from "./client";
import {
  Application,
  ApplicationDetail,
  PaginatedResponse,
  TokenSettings,
} from "../types";

export async function getApplications(
  orgGuid: string
): Promise<Application[]> {
  const client = await createServerApiClient();
  const response = await client.get(`/api/v1/organizations/${orgGuid}/applications`);
  return response.data;
}

export async function getApplication(
  orgGuid: string,
  appGuid: string
): Promise<ApplicationDetail> {
  const client = await createServerApiClient();
  const response = await client.get(
    `/api/v1/organizations/${orgGuid}/applications/${appGuid}`
  );
  return response.data;
}

export async function createApplication(
  orgGuid: string,
  data: {
    clientName: string;
    applicationUrl: string;
    requireAuthorizationConsent: boolean;
    requireProofKey: boolean;
  }
): Promise<Application> {
  const client = await createServerApiClient();
  const response = await client.post(
    `/api/v1/organizations/${orgGuid}/applications`,
    data
  );
  return response.data;
}

export async function updateApplication(
  orgGuid: string,
  appGuid: string,
  data: {
    clientName?: string;
    applicationUrl?: string;
    status?: number;
  }
): Promise<Application> {
  const client = await createServerApiClient();
  const response = await client.put(
    `/api/v1/organizations/${orgGuid}/applications/${appGuid}`,
    data
  );
  return response.data;
}

export async function deleteApplication(
  orgGuid: string,
  appGuid: string
): Promise<void> {
  const client = await createServerApiClient();
  await client.delete(`/api/v1/organizations/${orgGuid}/applications/${appGuid}`);
}

export async function updateApplicationUrls(
  orgGuid: string,
  appGuid: string,
  data: {
    applicationUrl: string;
    returnUrls: string[];
    logoutUrls: string[];
  }
): Promise<void> {
  const client = await createServerApiClient();
  await client.put(
    `/api/v1/organizations/${orgGuid}/applications/${appGuid}/urls`,
    data
  );
}

export async function updateTokenSettings(
  orgGuid: string,
  appGuid: string,
  data: TokenSettings
): Promise<void> {
  const client = await createServerApiClient();
  await client.put(
    `/api/v1/organizations/${orgGuid}/applications/${appGuid}/settings`,
    data
  );
}

export async function assignResource(
  orgGuid: string,
  appGuid: string,
  resourceId: string
): Promise<void> {
  const client = await createServerApiClient();
  await client.post(
    `/api/v1/organizations/${orgGuid}/applications/${appGuid}/endpoints`,
    { resourceId }
  );
}

export async function getApplicationResources(
  orgGuid: string,
  appGuid: string
): Promise<any[]> {
  const client = await createServerApiClient();
  const response = await client.get(
    `/api/v1/organizations/${orgGuid}/applications/${appGuid}/endpoints`
  );
  return response.data;
}

export async function deleteApplicationResource(
  orgGuid: string,
  appGuid: string,
  resourceGuid: string
): Promise<void> {
  const client = await createServerApiClient();
  await client.delete(
    `/api/v1/organizations/${orgGuid}/applications/${appGuid}/endpoints/${resourceGuid}`
  );
}
