-- Reference data only: no passwords, users, client secrets or real integration credentials.
DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE NAME = 'CertificateType'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'CertificateType',
                N'Identify certificate as Organization used for signing vs client signature validation');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE [EnumTypeId] = @typeId
                AND Code = N'Organization')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Organization',
                N'Organization certificate to generate signature for the payloads sent to client');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE [EnumTypeId] = @typeId
                AND Code = N'PublicKey')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'PublicKey',
                N'Public key certificate to validate the communication signed by the client private key');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE NAME = 'CertificateFileFormat'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'CertificateFileFormat',
                N'Certificate file format for uploading certificates');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'pem')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'pem',
                N'A .pem file format suppots multiple digital certificates, including a certificate chain.');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'pfx')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'pfx',
                N'A .pfx file format (PKCS #12) supports storing a private key with the associated public key certificate.');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'ApplicationType'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'ApplicationType',
                N'Type of application for authentication purposes');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'mobile')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'mobile', N'Mobile application');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'web')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'web', N'Web application');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'server')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'server', N'Server application');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'AuthFlow'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'AuthFlow',
                N'Type of authentication flow');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'AuthCode')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'AuthCode', N'Authorization code flow with PKCE');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'ClientSecret')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'ClientSecret', N'Client secret flow');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'ClientSecretJWT')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'ClientSecretJWT', N'Client secret flow with JWT');
    end
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'PrivateKeyJWT')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'PrivateKeyJWT', N'Private key flow with JWT');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].EnumType
WHERE Name = 'CredentialStatus'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'CredentialStatus',
                N'Status of the credential');
        SET @typeId = SCOPE_IDENTITY()
    end
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Active')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Active', N'Active credential');
    end
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Disabled')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Disabled', N'Disabled credential');
    END
IF NOT EXISTS(SELECT 1
              FROM Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Disabled')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Disabled', N'Disabled credential');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Expired')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Expired', N'Expired credential');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Suspended')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Suspended', N'Suspended credential');
    END

GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'TokenType'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'TokenType',
                N'Type of token enum.');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Access Token')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Access Token', N'Access token');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'OIDC Token')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'OIDC Token', N'OIDC Token');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Refresh Token')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Refresh Token', N'Refresh token');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'TokenFormat'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'TokenFormat',
                N'Format of the token');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'self-contained')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'self-contained',
                N'Self-contained token use a protected, time-limited data structure that contains token metadata and claims. JSON Web Tokens (JWTs) are self-contained tokens.');;
    END
IF NOT EXISTS(SELECT 1
              FROM dbo.Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'reference')
    BEGIN
        INSERT INTO [dbo].Enum (EnumTypeId, Code, Description)
        VALUES (@typeId, N'reference',
                N'Reference tokens are opaque strings that act as pointers to the token metadata and claims stored on the server.');;
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE NAME = 'TokenSignatureAlgorithm'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'TokenSignatureAlgorithm',
                N'Signature algorithm used to sign the token');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'HS256')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'HS256',
                N'HMAC using SHA-256 hash algorithm');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE EnumTypeId = @typeId
                AND Code = N'RS256')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'RS256',
                N'RSA using SHA-256 hash algorithm');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'ES256')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'ES256',
                N'ECDSA using P-256 curve and SHA-256 hash algorithm');
    END
GO

INSERT dbo.EnumType(Name,Description) VALUES ('AuthSessionStatus','Authentication session status'), ('UsernameType','Username type'), ('SuffixType','Name suffix'), ('UsernameLookupField','Username lookup field');
INSERT dbo.Enum(EnumTypeId,Code,Description)
SELECT et.EnumTypeId,v.Code,v.Code FROM (VALUES
('AuthSessionStatus','Session Active'),('AuthSessionStatus','Session Inactive'),
('UsernameType','Username'),('UsernameType','Email'),
('SuffixType','Mr'),('SuffixType','Mrs'),('SuffixType','Miss'),('SuffixType','Sr'),('SuffixType','Jr'),
('UsernameLookupField','AccountEmail'),('UsernameLookupField','AccountNumber'),('UsernameLookupField','LastFourSSN'),
('TokenType','Auth Code Token'),('CredentialStatus','Inactive'))v(TypeName,Code) JOIN dbo.EnumType et ON et.Name=v.TypeName;
INSERT dbo.ExternalType(ExternalTypeName,ForgetUserSchema,SyncUserSchema) VALUES
('IDP',NULL,NULL),('529','urn:ascensus:529:1.0:accountsearch','urn:ascensus:529:1.0:accountsync'),
('SFRP','urn:ascensus:sfrp:1.0:accountsearch','urn:ascensus:sfrp:1.0:accountsync'),
('ABLE','urn:ascensus:able:1.0:accountsearch','urn:ascensus:able:1.0:accountsync'),
('ADVISOR','urn:ascensus:advisor:1.0:accountsearch','urn:ascensus:advisor:1.0:accountsync');
INSERT dbo.ExternalSource(SourceId,SourceCode,ExternalTypeId) SELECT '11111111-1111-1111-1111-111111111111','idp',ExternalTypeId FROM dbo.ExternalType WHERE ExternalTypeName='IDP';
SET IDENTITY_INSERT dbo.DataOrigin ON;
INSERT dbo.DataOrigin(DataOriginId,DBName,TableName) VALUES (0,DB_NAME(),'Profile'),(1,DB_NAME(),'Credential');
SET IDENTITY_INSERT dbo.DataOrigin OFF;
INSERT Person.LoginProvider(ProviderKey,ProviderName,DisplayName) VALUES ('idp','IDP','Local identity provider');
SET IDENTITY_INSERT Partner.Organization ON;
INSERT Partner.Organization(OrganizationId,Name,Note) VALUES (1,'Ascensus','System identity organization');
SET IDENTITY_INSERT Partner.Organization OFF;
SET IDENTITY_INSERT Client.MFARealm ON;
INSERT Client.MFARealm(MFARealmId,OrganizationId,Name,Uri) VALUES(1,1,'Default','');
SET IDENTITY_INSERT Client.MFARealm OFF;
INSERT dbo.[Range](Name,Description,Min,Max) VALUES
('AuthCodeTimeToLive','Authorization code lifetime in seconds',1,900),
('AccessTokenTimeToLive','Access token lifetime in seconds',1,3600),
('DeviceCodeTimeToLive','Device code lifetime in seconds',1,300),
('RefreshTokenTimeToLive','Refresh token lifetime in seconds',1,86400),
('MaxRequestTransitTime','Maximum request transit in seconds',1,30),
('PinTimeToLive','MFA PIN lifetime in seconds',300,1800);
INSERT dbo.GlobalConfig(Name,Description,Value) VALUES
('MaxPasswordFailureCount','Failed password attempt limit',7),
('DisallowedRecentPasswordCount','Password history length',12),
('MaxMfaPinFailureCount','Failed MFA PIN attempt limit',3),
('PinTimeToLive','MFA PIN lifetime in seconds',300);
GO
INSERT INTO [dbo].[GroupTemplate]
    ([GroupName])
VALUES (N'Administrators'),
       (N'Developers'),
       (N'Viewers');
GO


DECLARE @GroupTemplateAdministrators INT;

SELECT TOP 1 @GroupTemplateAdministrators = [GroupTemplateId]
FROM [dbo].[GroupTemplate]
WHERE [GroupName] = N'Administrators';

DECLARE @GroupTemplateDevelopers INT;

SELECT TOP 1 @GroupTemplateDevelopers = [GroupTemplateId]
FROM [dbo].[GroupTemplate]
WHERE [GroupName] = N'Developers';

DECLARE @GroupTemplateViewers INT;

SELECT TOP 1 @GroupTemplateViewers = [GroupTemplateId]
FROM [dbo].[GroupTemplate]
WHERE [GroupName] = N'Viewers';


INSERT INTO [dbo].[GroupPermissionTemplate]
([PermissionName],
 [PermissionKey],
 [GroupId])
VALUES (N'Organizations: Create', N'idp-admin-org:create', NULL),
       (N'Organizations: Read', N'idp-admin-org:read', @GroupTemplateAdministrators),
       (N'Organizations: Read', N'idp-admin-org:read', @GroupTemplateDevelopers),
       (N'Organizations: Read', N'idp-admin-org:read', @GroupTemplateViewers),
       (N'Organizations: Update', N'idp-admin-org:update', @GroupTemplateAdministrators),
       (N'Organizations: Deactivate', N'idp-admin-org:deactivate', NULL),
       (N'Organizations: Switch', N'idp-admin-org:switch', NULL),

       (N'Groups: Read', N'idp-admin-group:read', @GroupTemplateAdministrators),

       (N'Administrators: Create', N'idp-admin-admin:create', @GroupTemplateAdministrators),
       (N'Administrators: Read', N'idp-admin-admin:read', @GroupTemplateAdministrators),
       (N'Administrators: Update', N'idp-admin-admin:update', @GroupTemplateAdministrators),
       (N'Administrators: Delete', N'idp-admin-admin:delete', @GroupTemplateAdministrators),

       (N'Users: Read', N'idp-admin-user:read', NULL),

       (N'Organization Certificates: Create', N'idp-admin-org-cert:create', NULL),
       (N'Organization Certificates: Read', N'idp-admin-org-cert:read', @GroupTemplateAdministrators),
       (N'Organization Certificates: Read', N'idp-admin-org-cert:read', @GroupTemplateDevelopers),
       (N'Organization Certificates: Read', N'idp-admin-org-cert:read', @GroupTemplateViewers),
       (N'Organization Certificates: Update', N'idp-admin-org-cert:update', NULL),
       (N'Organization Certificates: Delete', N'idp-admin-org-cert:delete', NULL),

       (N'Credential Certificates: Create', N'idp-admin-cred-cert:create', NULL),
       (N'Credential Certificates: Create', N'idp-admin-cred-cert:create', @GroupTemplateAdministrators),
       (N'Credential Certificates: Create', N'idp-admin-cred-cert:create', @GroupTemplateDevelopers),
       (N'Credential Certificates: Read', N'idp-admin-cred-cert:read', @GroupTemplateAdministrators),
       (N'Credential Certificates: Read', N'idp-admin-cred-cert:read', @GroupTemplateDevelopers),
       (N'Credential Certificates: Read', N'idp-admin-cred-cert:read', @GroupTemplateViewers),
       (N'Credential Certificates: Update', N'idp-admin-cred-cert:update', @GroupTemplateAdministrators),
       (N'Credential Certificates: Update', N'idp-admin-cred-cert:update', @GroupTemplateDevelopers),
       (N'Credential Certificates: Delete', N'idp-admin-cred-cert:delete', @GroupTemplateAdministrators),
       (N'Credential Certificates: Delete', N'idp-admin-cred-cert:delete', @GroupTemplateDevelopers),

       (N'Applications: Create', N'idp-admin-app:create', @GroupTemplateAdministrators),
       (N'Applications: Create', N'idp-admin-app:create', @GroupTemplateDevelopers),
       (N'Applications: Read', N'idp-admin-app:read', @GroupTemplateAdministrators),
       (N'Applications: Read', N'idp-admin-app:read', @GroupTemplateDevelopers),
       (N'Applications: Read', N'idp-admin-app:read', @GroupTemplateViewers),
       (N'Applications: Update', N'idp-admin-app:update', @GroupTemplateAdministrators),
       (N'Applications: Update', N'idp-admin-app:update', @GroupTemplateDevelopers),
       (N'Applications: Delete', N'idp-admin-app:delete', @GroupTemplateAdministrators),
       (N'Applications: Delete', N'idp-admin-app:delete', @GroupTemplateDevelopers),
       (N'Applications: Assign Resource', N'idp-admin-app:assign-resource', NULL),

       (N'Resource: Create', N'idp-admin-resource:create', NULL),
       (N'Resource: Read', N'idp-admin-resource:read', NULL),
       (N'Resource: Update', N'idp-admin-resource:update', NULL),
       (N'Resource: Delete', N'idp-admin-resource:delete', NULL);
GO


INSERT Partner.OrganizationGroup(OrganizationId,GroupName,Description,GroupTemplateId) VALUES(1,'Ascensus Admin','System administrators',1);
INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey,Description)
SELECT 1,1,PermissionName,PermissionKey,Description FROM dbo.GroupPermissionTemplate WHERE GroupId=1 OR GroupId IS NULL;
GO
