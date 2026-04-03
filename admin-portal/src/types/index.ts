export interface Organization {
  organizationId: number;
  rowGuid: string;
  name: string;
  note: string;
  status: number;
  primaryContactName: string | null;
  primaryContactEmail: string | null;
  primaryContactPhoneNumber: string | null;
  secondaryContactName: string | null;
  secondaryContactEmail: string | null;
  secondaryContactPhoneNumber: string | null;
  createdOn: string;
  modifiedOn: string | null;
}

export interface Application {
  applicationId: number;
  organizationId: number;
  rowGuid: string;
  clientId: string;
  name: string;
  description: string;
  uri: string;
  applicationType: string;
  authFlow: string;
  active: boolean;
  createdOn: string;
  modifiedOn: string | null;
}

export interface Credential {
  credentialId: number;
  rowGuid: string;
  name: string;
  credentialKey: string;
  authFlow: string;
  credentialStatus: string;
  fingerprint: string;
  tokenAlgorithm: string;
  expireOn: string | null;
  createdOn: string;
}

export interface User {
  profileId: number;
  rowGuid: string;
  userName: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  status: number;
  createdOn: string;
}

export interface ResourceEndpoint {
  resourceLibraryId: number;
  rowGuid: string;
  name: string;
  description: string | null;
  uri: string;
  allowedMethod: string;
  urn: string | null;
  displayEnabled: boolean;
  status: number;
}

export interface Certificate {
  certificateId: number;
  rowGuid: string;
  certificateName: string | null;
  subject: string;
  issuer: string;
  thumbprint: string;
  validFrom: string;
  validTo: string;
  status: number;
}

export interface OrganizationGroup {
  organizationGroupId: number;
  rowGuid: string;
  groupName: string;
  description: string | null;
}
