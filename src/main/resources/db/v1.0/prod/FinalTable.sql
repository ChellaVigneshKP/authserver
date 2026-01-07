ALTER TABLE [Client].[Setting]
    DROP COLUMN JWSAlgorithm;
GO

ALTER TABLE [Client].[Setting]
    ADD JWSAlgorithmId INTEGER;
GO

ALTER TABLE [Client].[Setting]
    ADD CONSTRAINT [FK_SettingJWSAlgorithmId_EnumId]
        FOREIGN KEY ([JWSAlgorithmId]) REFERENCES [dbo].[Enum] ([EnumId]);
GO

ALTER TABLE [Token].[Pkce]
    ALTER COLUMN Expiration DATETIME2(7) NULL;
GO

ALTER TABLE [Partner].[Certificate]
    ADD CertificateName VARCHAR(255);
GO

EXEC sp_rename
     'Client.Credential.CredentialStatusId',
     'CredentialStatus',
     'COLUMN';
GO

ALTER TABLE [Client].[Credential]
    ADD SecretId INTEGER,
        CertificateId INTEGER;
GO

ALTER TABLE [Client].[Credential]
    ADD CONSTRAINT [FK_CredentialSecretId_SecretId]
        FOREIGN KEY ([SecretId]) REFERENCES [Client].[Secret] ([SecretId]);
GO

GO

ALTER TABLE [Client].[Credential]
    ADD CONSTRAINT [FK_CredentialCertificateId_CertificateId]
        FOREIGN KEY ([CertificateId])
            REFERENCES [Partner].[Certificate] ([CertificateId]);

GO


ALTER TABLE [Client].[Credential]
    DROP CONSTRAINT [FK_ClientCredential_AuthFlowId];

GO

ALTER TABLE [Client].[Credential]
    WITH CHECK ADD CONSTRAINT [FK_ClientCredential_AuthFlowId]
        FOREIGN KEY ([AuthFlowId])
            REFERENCES [dbo].[Enum] ([EnumId]);

GO

ALTER TABLE [Client].[Credential]
    CHECK CONSTRAINT [FK_ClientCredential_AuthFlowId];

GO


IF EXISTS (SELECT *
           FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
           WHERE CONSTRAINT_NAME = 'FK_ClientTokenSetting_OidcTokenSignatureAlgorithmId')
    BEGIN
        ALTER TABLE [Client].[TokenSetting]
            DROP CONSTRAINT [FK_ClientTokenSetting_OidcTokenSignatureAlgorithmId];
    END

GO

IF EXISTS (SELECT *
           FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = 'Client'
             AND TABLE_NAME = 'TokenSetting'
             AND COLUMN_NAME = 'EnumId')
    BEGIN
        ALTER TABLE [Client].[TokenSetting]
            DROP COLUMN [EnumId];
    END

GO


DECLARE @typeId INT;

SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'TokenType';

BEGIN
    INSERT INTO [dbo].[Enum]
        ([EnumTypeId], [Code], [Description])
    VALUES (@typeId, N'Auth Code Token', N'Auth Code Token');
END

GO


BEGIN
    ALTER TABLE [Client].[Application]
        ADD Active BIT DEFAULT 1 NOT NULL;
END

GO

USE [AGSAuth]
GO


ALTER TABLE [dbo].[GroupPermissionTemplate]
    ALTER COLUMN GroupId INT NULL;
GO

ALTER TABLE [dbo].[GroupPermissionTemplate]
    ADD [PermissionKey] NVARCHAR(64) NOT NULL;
GO


ALTER TABLE [Partner].[OrganizationGroupPermission]
    ADD [PermissionKey] NVARCHAR(64) NOT NULL;
GO


ALTER TABLE [Partner].[OrganizationGroup]
    ADD [GroupTemplateId] INT NULL;
GO

ALTER TABLE [Partner].[OrganizationGroup]
    ADD CONSTRAINT [FK_PartnerOrganizationGroupPermission_GroupTemplateId]
        FOREIGN KEY ([GroupTemplateId])
            REFERENCES [dbo].[GroupTemplate] ([GroupTemplateId]);
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

IF EXISTS (SELECT TABLE_NAME
           FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_NAME = 'ResourceLibrary'
             AND COLUMN_NAME = 'DisplayName'
             AND TABLE_SCHEMA = 'dbo')
    BEGIN
        ALTER TABLE [dbo].[ResourceLibrary]
            DROP COLUMN DisplayName;

        ALTER TABLE [dbo].[ResourceLibrary]
            ADD
                Name NVARCHAR(255) NOT NULL,
                AllowedMethod NVARCHAR(128) NOT NULL,
                Urn NVARCHAR(255);

        ALTER TABLE [dbo].[ResourceLibrary]
            ADD CONSTRAINT UQ_ResourceLibrary_Yrl_Am_Urn
                UNIQUE (Uri, AllowedMethod, Urn);
    END
GO

IF NOT EXISTS (SELECT TABLE_NAME
               FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_NAME = 'Range'
                 AND TABLE_SCHEMA = 'dbo')
    BEGIN
        CREATE TABLE [dbo].[Range]
        (
            [RangeId]     INT IDENTITY (1,1) NOT NULL,
            [Name]        NVARCHAR(1024)     NOT NULL,
            [Description] NVARCHAR(1024)     NOT NULL,
            [Min]         INT                NOT NULL,
            [Max]         INT                NOT NULL,
            [Status]      TINYINT            NOT NULL
                CONSTRAINT [DF_Range_Status] DEFAULT (1),
            [CreatedOn]   DATETIME2(0)       NOT NULL
                CONSTRAINT [DF_Range_CreatedOn] DEFAULT (GETUTCDATE()),
            [ModifiedOn]  DATETIME2(0)       NULL,
            [ModifiedBy]  NVARCHAR(255)      NULL,
            CONSTRAINT [PK_Range]
                PRIMARY KEY CLUSTERED ([RangeId] ASC)
                    ON 'DATA'
        )
            ON 'DATA'
    END
GO



IF NOT EXISTS (SELECT 1
               FROM [dbo].[Range]
               WHERE [Name] = N'AuthCodeTimeToLive')
    BEGIN
        INSERT INTO [dbo].[Range]
            ([Name], [Description], [Min], [Max])
        VALUES (N'AuthCodeTimeToLive', N'Allowed Time To Live Range for Auth Codes', 1, 5),
               (N'AccessTokenTimeToLive', N'Allowed Time To Live Range for Access Tokens', 1, 900),
               (N'DeviceCodeTimeToLive', N'Allowed Time To Live Range for Device Codes', 1, 5),
               (N'RefreshTokenTimeToLive', N'Allowed Time To Live Range for Refresh Tokens', 1, 900)
    END
GO



IF EXISTS (SELECT TABLE_NAME
           FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_NAME = 'Resource'
             AND COLUMN_NAME = 'Name'
             AND TABLE_SCHEMA = 'Resource')
    BEGIN
        ALTER TABLE [Resource].[Resource]
            DROP COLUMN Name, DisplayName, Description, Uri;

        ALTER TABLE [Resource].[Resource]
            ADD ResourceLibraryId INT NOT NULL;

        ALTER TABLE [Resource].[Resource]
            ADD CONSTRAINT [FK_ApiResource_ResourceLibraryId]
                FOREIGN KEY ([ResourceLibraryId])
                    REFERENCES [dbo].[ResourceLibrary] ([ResourceLibraryId]);

        ALTER TABLE [Resource].[Resource]
            ADD CONSTRAINT [UQ_ApiResource_org_app_resource]
                UNIQUE (OrganizationId, ApplicationId, ResourceLibraryId);
    END
GO


ALTER TABLE [Token].[Token]
    ADD SigningKey VARBINARY(MAX) NULL;
GO



IF NOT EXISTS (SELECT *
               FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = 'Person'
                 AND TABLE_NAME = 'Credential'
                 AND COLUMN_NAME = 'EncoderId')
    BEGIN
        ALTER TABLE [Person].[Credential]
            ADD [EncoderId] NVARCHAR(128);
    END
GO

UPDATE [Person].[Credential]
SET EncoderId = 'Ascensus-1'
WHERE EncoderId IS NULL;
GO



ALTER TABLE [Token].[AuthSession]
    ADD ClientFingerprint VARBINARY(MAX) NULL;
GO



DECLARE @typeId INT;

SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'CredentialStatus';

BEGIN
    INSERT INTO [dbo].[Enum]
        ([EnumTypeId], [Code], [Description])
    VALUES (@typeId, N'Inactive', N'Client credential is inactive.');
END
GO



IF NOT EXISTS (SELECT TABLE_NAME
               FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_NAME = 'AuthSession'
                 AND COLUMN_NAME = 'Pin'
                 AND TABLE_SCHEMA = 'Token')
    BEGIN
        ALTER TABLE [Token].[AuthSession]
            ADD Pin VARCHAR(512) NULL;
    END
GO



IF NOT EXISTS (SELECT *
               FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = 'Person'
                 AND TABLE_NAME = 'Credential'
                 AND COLUMN_NAME = 'Version')
    BEGIN
        ALTER TABLE [Person].[Credential]
            ADD [Version] INT;
    END
GO



IF EXISTS (SELECT *
           FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = 'Person'
             AND TABLE_NAME = 'Credential'
             AND COLUMN_NAME = 'EncoderId')
    BEGIN
        ALTER TABLE [Person].[Credential]
            DROP COLUMN [EncoderId];
    END
GO



UPDATE [Person].[Credential]
SET Version = 1
WHERE Version IS NULL;
GO


UPDATE [dbo].[Range]
SET Max = 1800
WHERE Name = 'AccessTokenTimeToLive';
GO

ALTER TABLE [Token].[Pkce]
    ADD [RedirectUri] NVARCHAR(2000);
GO



ALTER TABLE [Client].[TokenSetting]
    ADD MaxRequestTransitTime INTEGER NOT NULL
        CONSTRAINT [DF_ClientTokenSetting_MaxRequestTransitTime] DEFAULT 1;
GO

IF NOT EXISTS (SELECT 1
               FROM [dbo].[Range]
               WHERE [Name] = N'MaxRequestTransitTime')
    BEGIN
        INSERT INTO [dbo].[Range]
            ([Name], [Description], [Min], [Max])
        VALUES (N'MaxRequestTransitTime',
                N'Allowed Range for Max Request Transit Time',
                1,
                30);
    END
GO



ALTER TABLE [Token].[AuthSession]
    ADD [ClientId] NCHAR(32);
GO



ALTER TABLE [Token].[AuthCode]
    ADD DataHash BINARY(32) NULL;
GO

SET ANSI_PADDING ON;
GO

CREATE NONCLUSTERED INDEX [IX_AuthCode_DataHash]
    ON [Token].[AuthCode]
        (
         [DataHash] ASC
            )
    INCLUDE ([SessionId])
    WITH (FILLFACTOR = 70)
    ON [TOKEN_DATA];
GO



ALTER TABLE [Token].[Token]
    ADD DataHash BINARY(32) NULL;
GO

SET ANSI_PADDING ON;
GO

CREATE NONCLUSTERED INDEX [IX_Token_DataHash]
    ON [Token].[Token]
        (
         [DataHash] ASC,
         [TokenTypeId] ASC
            )
    INCLUDE ([TokenId])
    WITH (FILLFACTOR = 70)
    ON [TOKEN_DATA];
GO



ALTER TABLE [Token].[Token]
    SET (SYSTEM_VERSIONING = OFF);
GO

ALTER TABLE [Token].[Token]
    DROP PERIOD FOR SYSTEM_TIME;
GO

ALTER TABLE [Token].[Token]
    DROP COLUMN IF EXISTS [AuditStartOn];
GO

ALTER TABLE [Token].[Token]
    DROP COLUMN IF EXISTS [AuditEndOn];
GO

DROP TABLE Token.TokenHistory;
GO


BEGIN
    ALTER TABLE [Client].[Application]
        ADD UsernameType INTEGER,
            AllowForgotUsername BIT;
END
GO

CREATE TABLE [Token].[SSO]
(
    [ID]                 INT IDENTITY (1,1) NOT NULL,
    [SessionID]          UNIQUEIDENTIFIER   NOT NULL,
    [EncryptedSessionID] VARBINARY(4000)    NOT NULL,
    [EncryptionKey]      VARBINARY(4000)    NOT NULL,
    [CreatedOn]          DATETIME2(0)       NOT NULL
        CONSTRAINT [DF_PartnerOrganization_CreatedOn]
            DEFAULT (GETUTCDATE())
);
GO



IF NOT EXISTS (SELECT *
               FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = 'Token'
                 AND TABLE_NAME = 'SSO'
                 AND COLUMN_NAME = 'HashedEncryptedSessionId')
    BEGIN
        ALTER TABLE [Token].[SSO]
            ADD [HashedEncryptedSessionId] BINARY(32) NOT NULL
                DEFAULT NEWID();
    END
GO

UPDATE [Token].[SSO]
SET [HashedEncryptedSessionId] =
        HASHBYTES('SHA2_256', EncryptedSessionId);
GO

IF NOT EXISTS (SELECT *
               FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
               WHERE CONSTRAINT_NAME = 'PK_Token_SSO_HashedEncryptedSessionId')
    BEGIN
        ALTER TABLE [Token].[SSO]
            ADD CONSTRAINT [PK_Token_SSO_HashedEncryptedSessionId]
                PRIMARY KEY CLUSTERED ([HashedEncryptedSessionId]);
    END
GO



UPDATE [dbo].[Range]
SET Max = 3600
WHERE Name = 'AccessTokenTimeToLive';
GO



CREATE TABLE [Client].[ForgetUserNameSetting]
(
    [ForgetUserNameSettingId] INT IDENTITY (1,1) NOT NULL,
    [OrganizationId]          INT                NOT NULL,
    [ApplicationId]           INT                NOT NULL,
    [ParamPriority]           INT                NOT NULL,
    [ParamJson]               NVARCHAR(4000),
    [Active]                  BIT                NOT NULL
        CONSTRAINT [DF_ForgetUserNameSetting_Active] DEFAULT 1,
    [CreatedOn]               DATETIME2(0)       NOT NULL
        CONSTRAINT [DF_ForgetUserNameSetting_CreatedOn]
            DEFAULT GETUTCDATE(),
    [ModifiedOn]              DATETIME2(0)       NULL,
    [ModifiedBy]              NVARCHAR(255)      NULL,
    [AuditStartOn]            DATETIME2(2)
        GENERATED ALWAYS AS ROW START HIDDEN     NOT NULL,
    [AuditEndOn]              DATETIME2(2)
        GENERATED ALWAYS AS ROW END HIDDEN       NOT NULL,
    CONSTRAINT [PK_Client_ForgetUserNameSetting]
        PRIMARY KEY CLUSTERED ([ForgetUserNameSettingId] ASC)
            ON [CLIENT_DATA],
    PERIOD FOR SYSTEM_TIME ([AuditStartOn], [AuditEndOn])
)
    ON [CLIENT_DATA]
    WITH (
        SYSTEM_VERSIONING = ON
        (HISTORY_TABLE = [Client].[ForgetUserNameSettingHistory])
    );
GO



ALTER TABLE [Client].[ForgetUserNameSetting]
    WITH CHECK ADD CONSTRAINT [FK_ForgetUserNameSetting_ApplicationId]
        FOREIGN KEY ([ApplicationId])
            REFERENCES [Client].[Application] ([ApplicationId]);
GO

ALTER TABLE [Client].[ForgetUserNameSetting]
    WITH CHECK ADD CONSTRAINT [FK_ForgetUserNameSetting_OrganizationId]
        FOREIGN KEY ([OrganizationId])
            REFERENCES [Partner].[Organization] ([OrganizationId]);
GO



CREATE TYPE [dbo].[ForgetUserNameType]
AS TABLE
(
    OrganizationId INT,
    ApplicationId  INT,
    ParamPriority  INT,
    ParamJson      NVARCHAR(4000)
);
GO

GRANT EXECUTE ON TYPE::[dbo].[ForgetUserNameType] TO [db_spexec];
GO



BEGIN
    INSERT INTO [dbo].[EnumType]
        ([Name], [Description], [Status])
    VALUES ('UsernameLookupField', 'Username lookup field.', 1);
END
GO


DECLARE @typeId INT;

SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'UsernameLookupField';

BEGIN
    INSERT INTO [dbo].[Enum]
        ([EnumTypeId], [Code], [Description])
    VALUES (@typeId, N'AccountEmail', N'Email');

    INSERT INTO [dbo].[Enum]
        ([EnumTypeId], [Code], [Description])
    VALUES (@typeId, N'AccountNumber', N'Account number');

    INSERT INTO [dbo].[Enum]
        ([EnumTypeId], [Code], [Description])
    VALUES (@typeId, N'LastFourSSN', N'Last 4 Digits of SSN');
END
GO


CREATE TABLE [dbo].[ExternalSource]
(
    [Id]         INT IDENTITY (1,1) PRIMARY KEY,
    [ExternalId] UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    [Branding]   NVARCHAR(100)    NOT NULL,
    [CreatedOn]  DATETIME2(0)     NOT NULL
        CONSTRAINT [DF_ExternalSource_CreatedOn]
            DEFAULT (GETUTCDATE()),
    [ModifiedOn] DATETIME2(0)     NULL,
    [ModifiedBy] NVARCHAR(255)    NULL
);
GO

IF NOT EXISTS (SELECT TABLE_NAME
               FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_NAME = 'BlockedPassword'
                 AND TABLE_SCHEMA = 'Person')
    BEGIN
        CREATE TABLE [Person].[BlockedPassword]
        (
            [BlockedPasswordId] INT IDENTITY (1,1) NOT NULL,
            [Password]          VARBINARY(64)      NOT NULL,
            [Status]            TINYINT            NOT NULL
                CONSTRAINT [DF_BlockedPassword_Status] DEFAULT (1),
            [CreatedOn]         DATETIME2(0)       NOT NULL
                CONSTRAINT [DF_BlockedPassword_CreatedOn]
                    DEFAULT (GETUTCDATE()),
            [ModifiedOn]        DATETIME2(0)       NULL,
            [ModifiedBy]        NVARCHAR(255)      NULL,
            CONSTRAINT [PK_BlockedPassword]
                PRIMARY KEY CLUSTERED ([BlockedPasswordId] ASC)
                    ON "USER_DATA"
        )
            ON "USER_DATA"
    END
GO


CREATE TABLE [dbo].[ExternalType]
(
    [ExternalTypeId]   [int] IDENTITY (1,1)                           NOT NULL,
    [ExternalTypeName] [nvarchar](255)                                NOT NULL,
    [Status]           [tinyint]
        CONSTRAINT [DF_ExternalType_Status] DEFAULT ((1))             NOT NULL,
    [ForgetUserSchema] [nvarchar](100)                                NULL,
    [SyncUserSchema]   [nvarchar](100)                                NULL,
    [CreatedOn]        [datetime2](0)
        CONSTRAINT [DF_ExternalType_CreatedOn] DEFAULT (getutcdate()) NOT NULL,
    [ModifiedOn]       [datetime2](0)                                 NULL,
    [ModifiedBy]       [nvarchar](255)                                NULL,
    CONSTRAINT [PK_ExternalType] PRIMARY KEY CLUSTERED ([ExternalTypeId] ASC) ON [DATA]
) ON [DATA]
GO
IF EXISTS (SELECT *
           FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = 'dbo'
             AND TABLE_NAME = 'ExternalSource')
    BEGIN
        DROP TABLE [dbo].[ExternalSource]
    END
GO


CREATE TABLE [dbo].[ExternalSource]
(
    [SourceId]       [uniqueidentifier]                                 NOT NULL,
    [SourceCode]     [nvarchar](255)                                    NOT NULL,
    [ExternalTypeId] [int]                                              NOT NULL,
    [CreatedOn]      [datetime2](6)
        CONSTRAINT [DF_ExternalSource_CreatedOn] DEFAULT (getutcdate()) NOT NULL,
    [ModifiedOn]     [datetime2](6)                                     NULL,
    [ModifiedBy]     [nvarchar](255)                                    NULL,
    CONSTRAINT [PK_ExternalSource] PRIMARY KEY CLUSTERED ([SourceId] ASC) ON [DATA]
) ON [DATA]
GO
ALTER TABLE [dbo].[ExternalSource]
    WITH CHECK ADD CONSTRAINT [FK_ExternalSource_ExternalTypeId] FOREIGN KEY ([ExternalTypeId]) REFERENCES [dbo].[ExternalType] ([ExternalTypeId])
GO

INSERT INTO [dbo].[ExternalType]
(
    [ExternalTypeName],
    [ForgetUserSchema],
    [SyncUserSchema]
)
VALUES
    ('IDP', NULL, NULL),
    ('529', 'urn:ascensus:529:1.0:accountsearch', 'urn:ascensus:529:1.0:accountsync'),
    ('SFRP', 'urn:ascensus:sfrp:1.0:accountsearch', 'urn:ascensus:sfrp:1.0:accountsync'),
    ('ABLE', 'urn:ascensus:able:1.0:accountsearch', 'urn:ascensus:able:1.0:accountsync'),
    ('ADVISOR', 'urn:ascensus:advisor:1.0:accountsearch', 'urn:ascensus:advisor:1.0:accountsync');
GO

DECLARE @ExtTypeId int
SELECT @ExtTypeId = ExternalTypeId
FROM [dbo].[ExternalType]
WHERE ExternalTypeName = 'IDP'
INSERT INTO [dbo].[ExternalSource] (SourceId, SourceCode, ExternalTypeId)
VALUES (0x1111111111111111111111111111, 'idp', @ExtTypeId)
GO

IF NOT EXISTS (SELECT *
               FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = 'Person'
                 AND TABLE_NAME = 'Credential'
                 AND COLUMN_NAME = 'ExternalId')
    BEGIN
        ALTER TABLE [Person].[Credential]
            ADD ExternalId UNIQUEIDENTIFIER NOT NULL
                DEFAULT 0x11111111111111111111111111111111;
    END
GO

ALTER TABLE [Person].[Credential]
    ADD CONSTRAINT FK_Credential_ExternalSource
        FOREIGN KEY (ExternalId)
            REFERENCES [dbo].[ExternalSource] (SourceId);
GO



IF NOT EXISTS (SELECT TABLE_NAME
               FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_NAME = 'CredentialAudit'
                 AND TABLE_SCHEMA = 'Person')
    BEGIN
        CREATE TABLE [Person].[CredentialAudit]
        (
            [CredentialAuditId] INT IDENTITY (1,1) NOT NULL,
            [CredentialId]      INT                NOT NULL,
            [Password]          VARBINARY(4000)    NOT NULL,
            [Version]           INT                NOT NULL,
            [Status]            TINYINT            NOT NULL
                CONSTRAINT [DF_CredentialAudit_Status]
                    DEFAULT (1),
            [CreatedOn]         DATETIME2(0)       NOT NULL
                CONSTRAINT [DF_CredentialAudit_CreatedOn]
                    DEFAULT (GETUTCDATE()),
            [ModifiedOn]        DATETIME2(0)       NULL,
            [ModifiedBy]        NVARCHAR(255)      NULL,
            CONSTRAINT [PK_CredentialAudit]
                PRIMARY KEY CLUSTERED ([CredentialAuditId] ASC)
                    ON [USER_DATA],
            CONSTRAINT [FK_CredentialAudit_CredentialId]
                FOREIGN KEY ([CredentialId])
                    REFERENCES [Person].[Credential] ([Id])
        )
            ON [USER_DATA]
    END
GO

ALTER TABLE [Person].[Credential]
    ADD PasswordExpirationDate DATETIME NULL;
GO

ALTER TABLE [Person].[Profile]
    ADD [SecondaryPhoneNumber] NVARCHAR(128) NULL,
        [SecondaryPhoneNumberConfirmed] BIT NULL;
GO

UPDATE [dbo].[Range]
SET [Max] = 15
WHERE Name = 'AuthCodeTimeToLive';
GO

UPDATE Person.Profile
SET TwoFactorEnabled = 1
WHERE TwoFactorEnabled = 0
  AND ProfileId != 1;
GO



ALTER TABLE [Person].[BlockedPassword]
    ALTER COLUMN Password NVARCHAR(255) NOT NULL;
GO

ALTER TABLE [Person].[BlockedPassword]
    ADD CONSTRAINT [Password_Unique_BlockedPassword]
        UNIQUE (Password);
GO

ALTER TABLE [Person].[Credential]
    ADD [LastLogin] DATETIME2(2) NULL;
GO

ALTER TABLE [Person].[Credential]
    DROP COLUMN [LockoutEnd];
GO

ALTER TABLE [Token].[AuthSession]
    ADD [Branding] NVARCHAR(100),
        [RedirectUriId] INT;
GO


IF NOT EXISTS (
    SELECT TABLE_NAME
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_NAME = 'GlobalConfig'
      AND TABLE_SCHEMA = 'dbo'
)
    BEGIN
        CREATE TABLE [dbo].[GlobalConfig]
        (
            [GlobalConfigId] INT IDENTITY (1,1) NOT NULL,
            [Name] NVARCHAR(1024) NOT NULL,
            [Description] NVARCHAR(1024) NOT NULL,
            [Value] INT NOT NULL,
            [Status] TINYINT NOT NULL
                CONSTRAINT [DF_GlobalConfig_Status]
                    DEFAULT (1),
            [CreatedOn] DATETIME2(0) NOT NULL
                CONSTRAINT [DF_GlobalConfig_CreatedOn]
                    DEFAULT (GETUTCDATE()),
            [ModifiedOn] DATETIME2(0) NULL,
            [ModifiedBy] NVARCHAR(255) NULL,
            CONSTRAINT [PK_GlobalConfig]
                PRIMARY KEY CLUSTERED ([GlobalConfigId] ASC)
                    ON [DATA]
        )
            ON [DATA]
    END
GO

IF NOT EXISTS (
    SELECT 1
    FROM [dbo].[GlobalConfig]
    WHERE [Name] = N'MaxPasswordFailureCount'
)
    BEGIN
        INSERT INTO [dbo].[GlobalConfig]
        ([Name], [Description], [Value])
        VALUES
            (N'MaxPasswordFailureCount',
             N'Maximum failed login attempts before account is locked',
             3),
            (N'DisallowedRecentPasswordCount',
             N'Number of recent passwords user cannot use',
             12)
    END
GO

IF NOT EXISTS (SELECT 1
               FROM [dbo].[GlobalConfig]
               WHERE [Name] = N'MaxMfaPinFailureCount')
    BEGIN
        INSERT INTO [dbo].[GlobalConfig]
            ([Name], [Description], [Value])
        VALUES (N'MaxMfaPinFailureCount',
                'Maximum failed MFA PIN attempts before restarting MFA process',
                3);
    END
GO

UPDATE GlobalConfig
SET Value = 7
WHERE Name = N'MaxPasswordFailureCount';
GO


CREATE TABLE [Client].[MFARealm]
(
    [MFARealmId]     INT IDENTITY (2,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [Name]           NVARCHAR(255)                                     NOT NULL,
    [Description]    NVARCHAR(1024)                                    NULL,
    [Uri]            NVARCHAR(1024)                                    NOT NULL,
    [Active]         BIT
        CONSTRAINT [DF_MFARealm_Active] DEFAULT ((1))                  NOT NULL,
    [CreatedOn]      DATETIME2(0)
        CONSTRAINT [DF_MFARealm_CreatedOn] DEFAULT (GETUTCDATE())      NOT NULL,
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL
        CONSTRAINT [DF_MFARealm_RowGuid] DEFAULT (NEWSEQUENTIALID())   NOT NULL,
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_MFARealm] PRIMARY KEY CLUSTERED ([MFARealmId] ASC)
        ON [CLIENT_DATA],
    PERIOD FOR SYSTEM_TIME ([AuditStartOn], [AuditEndOn])
)
    ON [CLIENT_DATA]
    WITH
        (
        SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[MFARealmHistory])
    );
GO

ALTER TABLE [Client].[MFARealm]
    WITH CHECK
        ADD CONSTRAINT [FK_MFARealm_OrganizationId]
        FOREIGN KEY ([OrganizationId])
            REFERENCES [Partner].[Organization] ([OrganizationId]);
GO

ALTER TABLE [Client].[Application]
    CHECK CONSTRAINT [FK_Application_OrganizationId];
GO

ALTER TABLE [Client].[Application]
    ADD [MFARealmId] [int]
        CONSTRAINT [DF_Client_MFARealmId] DEFAULT (1) NOT NULL;
GO

ALTER TABLE [Client].[Application]
    WITH CHECK
        ADD CONSTRAINT [FK_Application_MFARealmId]
        FOREIGN KEY ([MFARealmId])
            REFERENCES [Client].[MFARealm] ([MFARealmId]);
GO

ALTER TABLE [Client].[Application]
    CHECK CONSTRAINT [FK_Application_MFARealmId];
GO

ALTER TABLE [Client].[Application]
    ADD [CMSContext] NVARCHAR(255) NULL;
GO

CREATE FULLTEXT CATALOG IdPFullTextCatalog;
GO

CREATE FULLTEXT STOPLIST IdPStopList FROM SYSTEM STOPLIST;
GO

CREATE FULLTEXT INDEX ON Person.Profile
    (
     Email,
     FirstName,
     LastName,
     PhoneNumber,
     SecondaryPhoneNumber
        )
    KEY INDEX PK_PersonProfile ON IdPFullTextCatalog -- Unique index
    WITH CHANGE_TRACKING AUTO;                      -- Population type

ALTER FULLTEXT INDEX ON [Person].[Profile]
SET STOPLIST = [IdPStopList];
GO


ALTER TABLE [dbo].[ExternalSource]
    ADD [SyncFlag] TINYINT NOT NULL DEFAULT 1;
GO



UPDATE [Person].[Profile]
SET SyncFlag = 1
WHERE SyncFlag = 0;
GO

ALTER TABLE [Person].[Profile]
    DROP CONSTRAINT [DF_PersonProfile_SyncFlag];
GO

ALTER TABLE [Person].[Profile]
    ADD CONSTRAINT [DF_PersonProfile_SyncFlag]
        DEFAULT ((1)) FOR [SyncFlag];
GO



IF NOT EXISTS (SELECT 1
               FROM [dbo].[Range]
               WHERE [Name] = N'PinTimeToLive')
    BEGIN
        INSERT INTO [dbo].[Range]
            ([Name], [Description], [Min], [Max])
        VALUES (N'PinTimeToLive',
                'Allowed Time To Live Range for MFA Expiry Pin',
                300,
                1800);
    END
GO

IF NOT EXISTS (SELECT 1
               FROM [dbo].[GlobalConfig]
               WHERE [Name] = N'PinTimeToLive')
    BEGIN
        INSERT INTO [dbo].[GlobalConfig]
            ([Name], [Description], [Value], [Status])
        VALUES (N'PinTimeToLive',
                'Allowed Time To Live Range for MFA Expiry Pin',
                300,
                1);
    END
GO

BEGIN
    ALTER TABLE [Client].[Application]
        ADD PinTimeToLive INT NULL;
END
GO

DECLARE @MobileEnumId INT;
DECLARE @WebEnumId INT;
DECLARE @GlobalPinTimeToLive INT;

SELECT @MobileEnumId = EnumId
FROM Enum e
WHERE e.code = 'mobile';

SELECT @WebEnumId = EnumId
FROM Enum e
WHERE e.code = 'web';

SELECT @GlobalPinTimeToLive = Value
FROM GlobalConfig gc
WHERE gc.Name = 'PinTimeToLive';

UPDATE [Client].[Application]
SET PinTimeToLive = @GlobalPinTimeToLive
WHERE ApplicationTypeId IN (@MobileEnumId, @WebEnumId);
GO
