export interface Organization {
  organizationId: number;
  name: string;
  note?: string;
  status: number;
  rowGuid: string;
  createdOn: string;
  modifiedOn?: string;
  modifiedBy?: string;
}

export interface Application {
  applicationId: number;
  organizationId: number;
  clientId: string;
  name: string;
  description?: string;
  applicationType: string;
  authFlow: string;
  uri: string;
  rowGuid: string;
  createdOn: string;
  modifiedOn?: string;
}

export interface User {
  id: number;
  firstName: string;
  lastName: string;
  username: string;
  email?: string;
  phoneNumber?: string;
  orgId: string;
  groupId: string;
  rowGuid: string;
  loginId: string;
}

export interface CreateUserDto {
  orgId: number;
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  password: string;
  phoneNumber?: string;
  secondaryPhoneNumber?: string;
  branding: string;
  memberId?: string;
  loginId?: string;
  metaData?: Record<string, any>;
}

export interface CreateOrganizationDto {
  name: string;
  note?: string;
}

export interface CreateApplicationDto {
  organizationId: number;
  name: string;
  description?: string;
  applicationTypeId: number;
  authFlowId: number;
  uri: string;
}
