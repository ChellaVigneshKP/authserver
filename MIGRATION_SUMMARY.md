# Database Migration Summary - Missing Stored Procedures

## Overview
This migration adds all missing stored procedures and required schema changes to support the auth server functionality.

## Files Created

### 1. V2__AddMissingSchemaChanges.sql (16KB)
**Purpose:** Add all missing columns and tables required by stored procedures

**Schema Changes:**
- Token.AuthSession: Added ClientFingerprint, ClientId, Branding, RedirectUri columns
- Token.Token: Added SigningKey, DataHash columns  
- Token.Pkce: Added RedirectUri column
- Client.Application: Added AllowForgotUsername, UsernameType, PinTimeToLive, JwkSetUrl, RequirePKCE, JwsAlgorithmId, Active columns
- Client.Credential: Added Status, SecretId, CertificateId columns
- Person.Profile: Added SecondaryPhoneNumber, MemberId, LoginId columns
- Person.Credential: Added ExternalId, DisallowedRecentPasswordCount, Version columns

**New Tables:**
- Token.SsoCookie - Single Sign-On cookie management
- dbo.Range - Range management for ID generation
- dbo.GlobalConfig - Global configuration settings
- dbo.ResourceLibrary - Resource library definitions
- Partner.Certificate - Certificate management
- Resource.Resource - Resource assignments to applications
- Client.TokenSetting - Token lifetime and format settings
- Client.MFARealm - Multi-factor authentication realms
- dbo.ExternalSource - External authentication sources
- Person.PasswordHistory - Password history tracking
- dbo.PasswordBlacklist - Blacklisted passwords
- dbo.ForgetUserNameType - Table type for forgot username feature

### 2. V3__AddMissingStoredProcedures_Part1.sql (8.4KB)
**Purpose:** Token schema stored procedures (21 procedures)

**Procedures:**
- Session Management: CreateAuthSession, GetAuthSessionById, GetAuthSessionBySessionId, SetAuthSessionInactive, GetMostRecentActiveSession, SetBrandingAndRedirectUri
- Auth Code: CreateAuthCode, GetAuthCodeById, GetSessionIdByAuthCode, SetAuthCodeConsumedOn
- PKCE: CreatePkce, GetPkceById, GetPkceBySessionId
- Token: CreateTokenWithHash, GetTokenById, GetTokenByClientId, GetTokenByValueHash, GetAllActiveTokensBySessionId, GetTokensByClientIdAndRequestDateTime
- SSO: InsertSsoCookie, FindSsoCookieByEncryptedSessionId

### 3. V4__AddMissingStoredProcedures_Part2.sql (12KB)
**Purpose:** Client schema stored procedures (29 procedures)

**Procedures:**
- Application: GetApplicationByClientId, UpdateApplication
- Credentials: CreateCredential, GetCredentials, GetActiveCredentials, GetCredentialByGuid, GetCredentialsByAuthFLow, UpdateCredentialStatus
- Secrets: CreateSecret, GetSecrets, GetSecretById, DeleteSecret  
- Token Settings: CreateTokenSetting, UpdateTokenSetting, GetTokenSettingForApp, GetTokenSettingById, GetSettingsByApplicationId
- Certificates: SaveCertificate
- Redirect URIs: CreateRedirectUri, UpdateRedirectUri, DeleteRedirectUri, GetRedirectUrisByApplicationId, GetPostLogoutRedirectUrisByApplicationId, DeletePostLogoutRedirectUris
- Resources: GetMFARealms, getAllResourcesByAppId, getAllResourcesByClientId

### 4. V5__AddMissingStoredProcedures_Part3.sql (20KB)
**Purpose:** Person schema stored procedures (30 procedures)

**Procedures:**
- User Management: CreateUser, GetUserById, GetUserByUsername, GetUserByUsernameAndBranding
- Credentials: GetUserCredentialsByUsername, GetUserCredentialsByUsernameAndBranding, GetUserCredentialsByGuid
- Updates: UpdateUserProfile, UpdateUserEmail, UpdateUserPassword, UpdateUserPassword_V2, UpdateUsername, UpdateUserSecuritySettings, UpdateUserStatus, UpdateUserForChangeProfilePage
- Details: GetUserDetailsByUserId, GetUserDetailsByUserIdAndBrandingV2, GetUserAuthDetailsByUserName, GetUserAuthDetailsByUserNameAndExternalSourceCode
- Permissions: GetUserPermissions, GetUsers, GetProfileOrganizationByProfileId
- Security: UpdateAccessFailedCount, UpdateAccessFailedCountWithExternalSourceCode, LockAccount, UnlockAccount, ReactivateUserWithPassword
- Metadata: CreateMetadata, GetMetadata, DeleteMetadata
- Password: getHistoricPasswords, validatePasswordBlacklisted

### 5. V6__AddMissingStoredProcedures_Part4.sql (13KB)
**Purpose:** Partner, Resource, and dbo schema stored procedures (41 procedures)

**Partner Procedures (15):**
- Certificates: GetCertificate, GetCertificateById, GetCertificatesByOrgId, GetCertificatesByCertTypeId, GetCertificatesByClientIdAndCertTypeId, UpdateCertificateStatus
- Organizations: GetOrganizationGroup, GetOrganizationGroups, GetOrganizationGroupPermissions, UpdateOrganization, UpdateOrganizationPrimaryContact, UpdateOrganizationSecondaryContact
- Resources: GetResourceById

**Resource Procedures (5):**
- CreateResource, GetResource, GetResourceById, GetResourceByResourceLibraryId, DeleteResource

**dbo Procedures (21):**
- Enums: GetEnumsByType
- Config: GetGlobalConfig, GetRanges, UpdateRanges
- Resource Library: CreateResourceLibrary, UpdateResourceLibrary, GetResourceLibraries, GetResourceLibrary, GetResourceLibraryById, GetResourceLibraryByUriMethodAndUrn
- Applications: ApplicationExists, GetApplications, GetApplicationByName, UpdateApplicationActivation, UpdateApplicationUri
- Post-Logout URIs: CreatePostLogoutRedirectUris, UpdatePostLogoutRedirectUri
- Token Settings: TokenSettingExistsForApp
- External Sources: GetExternalSource, GetExternalSourceById

## Total Count
- **Schema Changes:** 35 modifications (columns + tables)
- **Stored Procedures:** 118 procedures
- **Files:** 5 migration files (V2 through V6)
- **Total Size:** ~70KB of SQL code

## Testing Recommendations

### 1. Schema Validation
- Verify all columns are created with correct data types
- Ensure all foreign key relationships are valid
- Check that all table types are created

### 2. Procedure Validation  
- Test each procedure with sample data
- Verify return values match expected formats
- Check transaction handling in complex procedures
- Test error handling for edge cases

### 3. Integration Testing
- Verify procedures work with existing Java repository code
- Test complete workflows (login, token generation, etc.)
- Validate performance with realistic data volumes

### 4. Migration Testing
- Run migrations on a test database first
- Verify Flyway version history is updated correctly
- Test rollback procedures if needed
- Ensure idempotent behavior (can be run multiple times safely)

## Migration Order
The migrations must be run in order:
1. V2 - Schema changes (creates tables and columns)
2. V3 - Token procedures
3. V4 - Client procedures  
4. V5 - Person procedures
5. V6 - Partner/Resource/dbo procedures

## Notes
- All procedures use CREATE OR ALTER to be idempotent
- Schema changes check for existence before creating/altering
- Transaction handling is included in complex multi-table operations
- Error handling with TRY/CATCH blocks where appropriate
- Procedures follow naming conventions from existing codebase

## Known Issues / TODOs
- Some procedures have placeholder implementations for branding filter logic
- UpdateRanges procedure needs business logic implementation  
- External source code filtering needs full implementation
- Some procedures may need performance tuning based on data volumes

## Compatibility
- SQL Server (MSSQL) compatible
- Uses temporal tables (SYSTEM_VERSIONING) where appropriate
- Compatible with Flyway migration tool
- Follows existing database schema patterns
