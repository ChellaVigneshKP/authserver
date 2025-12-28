// Common types for Auth Server Frontend

export interface Organization {
  id: string;
  name: string;
  description?: string;
  primaryContact?: Contact;
  secondaryContact?: Contact;
  createdAt?: string;
  updatedAt?: string;
}

export interface Contact {
  name?: string;
  email?: string;
  phone?: string;
}

export interface Application {
  id: string;
  orgGuid: string;
  name: string;
  description?: string;
  clientId?: string;
  clientSecret?: string;
  applicationUrl?: string;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApplicationDetail extends Application {
  returnUrls?: string[];
  logoutUrls?: string[];
  tokenSettings?: TokenSettings;
  resources?: Resource[];
}

export interface TokenSettings {
  accessTokenTimeToLive?: number;
  refreshTokenTimeToLive?: number;
  authorizationCodeTimeToLive?: number;
  reuseRefreshTokens?: boolean;
}

export interface Resource {
  id: string;
  resourceLibraryId: string;
  name: string;
  description?: string;
}

export interface User {
  id: string;
  orgGuid: string;
  groupGuid?: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  status?: number;
  type?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Credential {
  id: string;
  userId: string;
  type: string;
  value?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Certificate {
  id: string;
  orgGuid: string;
  name: string;
  type: string;
  content?: string;
  expiryDate?: string;
  createdAt?: string;
}

export interface ResourceLibrary {
  id: string;
  name: string;
  description?: string;
  endpoint?: string;
  method?: string;
}

export interface OrganizationGroup {
  id: string;
  name: string;
  description?: string;
}

export interface Permission {
  id: string;
  name: string;
  resource: string;
  action: string;
}

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface ApiResponse<T> {
  data?: T;
  error?: string;
  message?: string;
}

export interface PaginatedResponse<T> {
  data: T[];
  offset: number;
  resultsPerPage: number;
  totalResults: number;
}
