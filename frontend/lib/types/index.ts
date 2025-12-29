export interface User {
  id: string;
  rowGuid: string;
  loginId: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber?: string;
  status: UserStatus;
  organizationId: string;
  organizationName?: string;
  groupId?: string;
  groupName?: string;
  metadata?: Record<string, any>;
  createdAt?: string;
  updatedAt?: string;
  results?: number;
}

export enum UserStatus {
  INACTIVE = 0,
  ACTIVE = 1,
}

export interface Application {
  id: string;
  rowGuid: string;
  clientId: string;
  clientName: string;
  applicationUrl?: string;
  organizationId: string;
  organizationGuid: string;
  status: ApplicationStatus;
  createdAt?: string;
  updatedAt?: string;
}

export enum ApplicationStatus {
  INACTIVE = 0,
  ACTIVE = 1,
}

export interface ApplicationDetail extends Application {
  clientSecret?: string;
  requireAuthorizationConsent: boolean;
  requireProofKey: boolean;
  authorizationGrantTypes: string[];
  clientAuthenticationMethods: string[];
  scopes: string[];
  redirectUris?: string[];
  postLogoutRedirectUris?: string[];
  tokenSettings?: TokenSettings;
}

export interface TokenSettings {
  accessTokenTimeToLive: number;
  refreshTokenTimeToLive: number;
  authorizationCodeTimeToLive: number;
  reuseRefreshTokens: boolean;
}

export interface Organization {
  id: number;
  rowGuid: string;
  name: string;
  description?: string;
  status: OrganizationStatus;
  primaryContactName?: string;
  primaryContactEmail?: string;
  primaryContactPhone?: string;
  secondaryContactName?: string;
  secondaryContactEmail?: string;
  secondaryContactPhone?: string;
  createdAt?: string;
  updatedAt?: string;
}

export enum OrganizationStatus {
  INACTIVE = 0,
  ACTIVE = 1,
}

export interface OrganizationGroup {
  id: number;
  rowGuid: string;
  name: string;
  description?: string;
  organizationId: number;
}

export interface OrganizationGroupPermission {
  id: number;
  groupId: number;
  permissionId: number;
  permissionName: string;
  resourceName: string;
  action: string;
}

export interface Resource {
  id: number;
  rowGuid: string;
  name: string;
  description?: string;
  endpoint: string;
  method: string;
  organizationId?: number;
}

export interface Certificate {
  id: number;
  rowGuid: string;
  name: string;
  organizationId: number;
  validFrom: string;
  validTo: string;
  thumbprint: string;
}

export interface Credential {
  id: number;
  rowGuid: string;
  name: string;
  type: string;
  organizationId: number;
  createdAt: string;
}

export interface PaginatedResponse<T> {
  data: T[];
  offset: number;
  limit: number;
  total: number;
}

export interface DashboardMetrics {
  totalUsers: number;
  activeUsers: number;
  totalApplications: number;
  activeApplications: number;
  totalOrganizations: number;
  activeOrganizations: number;
}
