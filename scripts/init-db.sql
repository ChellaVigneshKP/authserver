-- =============================================================================
-- AGSAuth Database - Consolidated Init Script (Dev Environment)
-- Runs all migrations in correct order for a clean local setup.
-- Usage: sqlcmd -S localhost -U sa -P "YourPassword" -i init-db.sql
-- =============================================================================

USE [master];
GO

IF EXISTS (SELECT 1 FROM sys.databases WHERE name = 'AGSAuth')
    BEGIN
        ALTER DATABASE [AGSAuth] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
        DROP DATABASE [AGSAuth];
    END
GO

CREATE DATABASE [AGSAuth];
GO

ALTER DATABASE [AGSAuth] SET ALLOW_SNAPSHOT_ISOLATION ON;
ALTER DATABASE [AGSAuth] SET READ_COMMITTED_SNAPSHOT ON;
GO

USE [AGSAuth];
GO

CREATE SCHEMA [Resource];
GO
CREATE SCHEMA [Client];
GO
CREATE SCHEMA [Person];
GO
CREATE SCHEMA [Partner];
GO
CREATE SCHEMA [Token];
GO

-- =============================================================================
-- SECTION 1: BASE TABLES (dbo schema)
-- =============================================================================

CREATE TABLE [dbo].[DataOrigin]
(
    [DataOriginId] INT IDENTITY (1,1) NOT NULL,
    [DBName]       NVARCHAR(50)       NOT NULL,
    [TableName]    NVARCHAR(50)       NOT NULL,
    [SyncEnabled]  BIT                NOT NULL CONSTRAINT [DF_DateOrigin_SyncEnabled] DEFAULT 0,
    [Status]       TINYINT            NOT NULL CONSTRAINT [DF_DataOrigin_Status] DEFAULT (1),
    [CreatedOn]    DATETIME2(0)       NOT NULL CONSTRAINT [DF_DataOrigin_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]   DATETIME2(0)       NULL,
    [ModifiedBy]   NVARCHAR(100)      NULL,
    CONSTRAINT [PK_DataOrigin] PRIMARY KEY CLUSTERED ([DataOriginId] ASC)
);
GO

CREATE TABLE [dbo].[EnumType]
(
    [EnumTypeId]  INT IDENTITY (1,1) NOT NULL,
    [Name]        NVARCHAR(255)      NOT NULL,
    [Description] NVARCHAR(1024)     NULL,
    [Status]      TINYINT            NOT NULL CONSTRAINT [DF_EnumType_Status] DEFAULT (1),
    [CreatedOn]   DATETIME2(0)       NOT NULL CONSTRAINT [DF_EnumType_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]  DATETIME2(0)       NULL,
    [ModifiedBy]  NVARCHAR(100)      NULL,
    CONSTRAINT [PK_EnumType] PRIMARY KEY CLUSTERED ([EnumTypeId] ASC)
);
GO

CREATE TABLE [dbo].[Enum]
(
    [EnumId]      INT IDENTITY (1,1) NOT NULL,
    [EnumTypeId]  INT                NOT NULL,
    [Code]        NVARCHAR(255)      NOT NULL,
    [Description] NVARCHAR(1024)     NOT NULL,
    [Status]      TINYINT            NOT NULL CONSTRAINT [DF_Enum_Status] DEFAULT (1),
    [CreatedOn]   DATETIME2(0)       NOT NULL CONSTRAINT [DF_Enum_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]  DATETIME2(0)       NULL,
    [ModifiedBy]  NVARCHAR(255)      NULL,
    CONSTRAINT [Pk_Enum] PRIMARY KEY CLUSTERED ([EnumId] ASC),
    CONSTRAINT [Fk_Enum_TypeId] FOREIGN KEY ([EnumTypeId]) REFERENCES [dbo].[EnumType] ([EnumTypeId])
);
GO

CREATE UNIQUE NONCLUSTERED INDEX [IX_Enum_TypeId_Code] ON [dbo].[Enum]
    ([EnumTypeId] ASC, [Code] ASC) WITH (FILLFACTOR = 70);
GO

CREATE TABLE [dbo].[ExternalType]
(
    [ExternalTypeId]   INT IDENTITY (1,1) NOT NULL,
    [ExternalTypeName] NVARCHAR(255)      NOT NULL,
    [Status]           TINYINT            NOT NULL CONSTRAINT [DF_ExternalType_Status] DEFAULT (1),
    [ForgetUserSchema] NVARCHAR(100)      NULL,
    [SyncUserSchema]   NVARCHAR(100)      NULL,
    [CreatedOn]        DATETIME2(0)       NOT NULL CONSTRAINT [DF_ExternalType_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]       DATETIME2(0)       NULL,
    [ModifiedBy]       NVARCHAR(255)      NULL,
    CONSTRAINT [PK_ExternalType] PRIMARY KEY CLUSTERED ([ExternalTypeId] ASC)
);
GO

CREATE TABLE [dbo].[ExternalSource]
(
    [SourceId]       UNIQUEIDENTIFIER   NOT NULL,
    [SourceCode]     NVARCHAR(255)      NOT NULL,
    [ExternalTypeId] INT                NOT NULL,
    [SyncFlag]       TINYINT            NOT NULL DEFAULT 1,
    [CreatedOn]      DATETIME2(6)       NOT NULL CONSTRAINT [DF_ExternalSource_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(6)       NULL,
    [ModifiedBy]     NVARCHAR(255)      NULL,
    CONSTRAINT [PK_ExternalSource] PRIMARY KEY CLUSTERED ([SourceId] ASC),
    CONSTRAINT [FK_ExternalSource_ExternalTypeId] FOREIGN KEY ([ExternalTypeId]) REFERENCES [dbo].[ExternalType] ([ExternalTypeId])
);
GO

CREATE TABLE [dbo].[GroupTemplate]
(
    [GroupTemplateId] INT IDENTITY (1,1)                                NOT NULL,
    [GroupName]       NVARCHAR(255)                                     NOT NULL,
    [Description]     NVARCHAR(1024)                                    NULL,
    [PartnerUse]      BIT                                               NOT NULL CONSTRAINT [DF_GroupTemplate_PartnerUse] DEFAULT 0,
    [Status]          TINYINT                                           NOT NULL CONSTRAINT [DF_GroupTemplate_Status] DEFAULT (1),
    [CreatedOn]       DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_GroupTemplate_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]      DATETIME2(0)                                      NULL,
    [ModifiedBy]      NVARCHAR(255)                                     NULL,
    [RowGuid]         UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_GroupTemplate_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]    DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]      DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_GroupTemplate] PRIMARY KEY CLUSTERED ([GroupTemplateId] ASC),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[GroupTemplateHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [dbo].[GroupPermissionTemplate]
(
    [GroupPermissionTemplateId] INT IDENTITY (1,1)                                NOT NULL,
    [GroupId]                   INT                                               NULL,
    [PermissionName]            NVARCHAR(255)                                     NOT NULL,
    [PermissionKey]             NVARCHAR(64)                                      NOT NULL,
    [Description]               NVARCHAR(1024)                                    NULL,
    [Status]                    TINYINT                                           NOT NULL CONSTRAINT [DF_GroupPermissionTemplate_Status] DEFAULT (1),
    [CreatedOn]                 DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_GroupPermissionTemplate_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                DATETIME2(0)                                      NULL,
    [ModifiedBy]                NVARCHAR(255)                                     NULL,
    [RowGuid]                   UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PermissionTemplate_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]              DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_GroupPermissionTemplate] PRIMARY KEY CLUSTERED ([GroupPermissionTemplateId] ASC),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[GroupPermissionTemplateHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [dbo].[ResourceScopeLibrary]
(
    [ResourceScopeLibraryId] INT IDENTITY (1,1)                                NOT NULL,
    [Name]                   NVARCHAR(255)                                     NOT NULL,
    [DisplayName]            NVARCHAR(255)                                     NOT NULL,
    [Description]            NVARCHAR(1024)                                    NULL,
    [Required]               BIT                                               NOT NULL,
    [Status]                 TINYINT                                           NOT NULL CONSTRAINT [DF_ResourceScopeLibrary_Status] DEFAULT (1),
    [CreatedOn]              DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ResourceScopeLibrary_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]             DATETIME2(0)                                      NULL,
    [ModifiedBy]             NVARCHAR(255)                                     NULL,
    [RowGuid]                UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ResourceScopeLibrary_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]           DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]             DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScopeLibrary] PRIMARY KEY CLUSTERED ([ResourceScopeLibraryId] ASC),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[ResourceScopeLibraryHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [dbo].[ResourceScopeClaimLibrary]
(
    [ResourceScopeClaimLibraryId] INT IDENTITY (1,1)                                NOT NULL,
    [ResourceScopeLibraryId]      INT                                               NOT NULL,
    [Status]                      TINYINT                                           NOT NULL CONSTRAINT [DF_ResourceScopeClaimLibrary_Status] DEFAULT (1),
    [CreatedOn]                   DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ResourceScopeClaimLibrary_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                  DATETIME2(0)                                      NULL,
    [ModifiedBy]                  NVARCHAR(255)                                     NULL,
    [RowGuid]                     UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ResourceScopeClaimLibrary_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]                DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                  DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScopeClaimLibrary] PRIMARY KEY CLUSTERED ([ResourceScopeClaimLibraryId] ASC),
    CONSTRAINT [FK_ResourceScopeClaimLibrary_ScopeLibraryId] FOREIGN KEY ([ResourceScopeLibraryId]) REFERENCES [dbo].[ResourceScopeLibrary] ([ResourceScopeLibraryId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[ResourceScopeClaimLibraryHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [dbo].[ResourceLibrary]
(
    [ResourceLibraryId] INT IDENTITY (1,1)                                NOT NULL,
    [Name]              NVARCHAR(255)                                     NOT NULL,
    [Description]       NVARCHAR(1024)                                    NULL,
    [Uri]               NVARCHAR(2048)                                    NOT NULL,
    [AllowedMethod]     NVARCHAR(128)                                     NOT NULL,
    [Urn]               NVARCHAR(255)                                     NULL,
    [DisplayEnabled]    BIT                                               NOT NULL CONSTRAINT [DF_ResourceLibrary_DisplayEnabled] DEFAULT 1,
    [Status]            TINYINT                                           NOT NULL CONSTRAINT [DF_ResourceLibrary_Status] DEFAULT 1,
    [CreatedOn]         DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ResourceLibrary_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)                                      NULL,
    [ModifiedBy]        NVARCHAR(255)                                     NULL,
    [RowGuid]           UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ResourceLibrary_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]      DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]        DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceLibrary] PRIMARY KEY CLUSTERED ([ResourceLibraryId] ASC),
    CONSTRAINT [UQ_ResourceLibrary_Yrl_Am_Urn] UNIQUE (Uri, AllowedMethod, Urn),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[ResourceLibraryHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [dbo].[Range]
(
    [RangeId]     INT IDENTITY (1,1) NOT NULL,
    [Name]        NVARCHAR(1024)     NOT NULL,
    [Description] NVARCHAR(1024)     NOT NULL,
    [Min]         INT                NOT NULL,
    [Max]         INT                NOT NULL,
    [Status]      TINYINT            NOT NULL CONSTRAINT [DF_Range_Status] DEFAULT (1),
    [CreatedOn]   DATETIME2(0)       NOT NULL CONSTRAINT [DF_Range_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]  DATETIME2(0)       NULL,
    [ModifiedBy]  NVARCHAR(255)      NULL,
    CONSTRAINT [PK_Range] PRIMARY KEY CLUSTERED ([RangeId] ASC)
);
GO

CREATE TABLE [dbo].[GlobalConfig]
(
    [GlobalConfigId] INT IDENTITY (1,1) NOT NULL,
    [Name]           NVARCHAR(1024)     NOT NULL,
    [Description]    NVARCHAR(1024)     NOT NULL,
    [Value]          INT                NOT NULL,
    [Status]         TINYINT            NOT NULL CONSTRAINT [DF_GlobalConfig_Status] DEFAULT (1),
    [CreatedOn]      DATETIME2(0)       NOT NULL CONSTRAINT [DF_GlobalConfig_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)       NULL,
    [ModifiedBy]     NVARCHAR(255)      NULL,
    CONSTRAINT [PK_GlobalConfig] PRIMARY KEY CLUSTERED ([GlobalConfigId] ASC)
);
GO

-- =============================================================================
-- SECTION 2: PARTNER SCHEMA
-- =============================================================================

CREATE TABLE [Partner].[Organization]
(
    [OrganizationId]              INT IDENTITY (2,1)                                NOT NULL,
    [Name]                        NVARCHAR(255)                                     NOT NULL,
    [Note]                        NVARCHAR(1024)                                    NULL,
    [Status]                      TINYINT                                           NOT NULL CONSTRAINT [DF_PartnerOrganization_Status] DEFAULT (1),
    [PrimaryContactId]            INT                                               NULL,
    [SecondaryContactId]          INT                                               NULL,
    [PrimaryContactName]          NVARCHAR(255)                                     NULL,
    [PrimaryContactEmail]         NVARCHAR(255)                                     NULL,
    [PrimaryContactPhoneNumber]   NVARCHAR(128)                                     NULL,
    [SecondaryContactName]        NVARCHAR(255)                                     NULL,
    [SecondaryContactEmail]       NVARCHAR(255)                                     NULL,
    [SecondaryContactPhoneNumber] NVARCHAR(128)                                     NULL,
    [CreatedOn]                   DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PartnerOrganization_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                  DATETIME2(0)                                      NULL,
    [ModifiedBy]                  NVARCHAR(255)                                     NULL,
    [RowGuid]                     UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PartnerOrganization_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]                DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                  DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerOrganization] PRIMARY KEY CLUSTERED ([OrganizationId] ASC),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerOrganizationHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Partner].[OrganizationGroup]
(
    [OrganizationGroupId] INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]      INT                                               NOT NULL,
    [GroupName]            NVARCHAR(255)                                     NOT NULL,
    [Description]          NVARCHAR(1024)                                    NULL,
    [GroupTemplateId]      INT                                               NULL,
    [Status]               TINYINT                                           NOT NULL CONSTRAINT [DF_PartnerOrganizationGroup_Status] DEFAULT (1),
    [CreatedOn]            DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PartnerOrganizationGroup_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]           DATETIME2(0)                                      NULL,
    [ModifiedBy]           NVARCHAR(255)                                     NULL,
    [RowGuid]              UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PartnerOrganizationGroup_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]         DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]           DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerOrganizationGroup] PRIMARY KEY CLUSTERED ([OrganizationGroupId] ASC),
    CONSTRAINT [Fk_PartnerOrganizationGroup_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_PartnerOrganizationGroupPermission_GroupTemplateId] FOREIGN KEY ([GroupTemplateId]) REFERENCES [dbo].[GroupTemplate] ([GroupTemplateId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerOrganizationGroupHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Partner].[OrganizationGroupPermission]
(
    [OrganizationGroupPermissionId] INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationGroupId]           INT                                               NOT NULL,
    [OrganizationId]                INT                                               NOT NULL,
    [PermissionName]                NVARCHAR(255)                                     NOT NULL,
    [PermissionKey]                 NVARCHAR(64)                                      NOT NULL,
    [Description]                   NVARCHAR(1024)                                    NULL,
    [Status]                        TINYINT                                           NOT NULL CONSTRAINT [DF_PartnerOrganizationGroupPermission_Status] DEFAULT (1),
    [CreatedOn]                     DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PartnerOrganizationGroupPermission_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                    DATETIME2(0)                                      NULL,
    [ModifiedBy]                    NVARCHAR(255)                                     NULL,
    [RowGuid]                       UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PartnerOrganizationGroupPermission_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]                  DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                    DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerOrganizationGroupPermission] PRIMARY KEY CLUSTERED ([OrganizationGroupPermissionId] ASC),
    CONSTRAINT [Fk_PartnerOrganizationGroupPermission_OrganizationGroupId] FOREIGN KEY ([OrganizationGroupId]) REFERENCES [Partner].[OrganizationGroup] ([OrganizationGroupId]),
    CONSTRAINT [Fk_PartnerOrganizationGroupPermission_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerOrganizationGroupPermissionHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Partner].[KeyStorePassword]
(
    [KeyStorePasswordId] INT IDENTITY (1,1)                                NOT NULL,
    [KeyStore]           VARBINARY(MAX)                                    NOT NULL,
    [PasswordKeyId]      UNIQUEIDENTIFIER                                  NOT NULL,
    [CreatedOn]          DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PartnerKeyStorePassword_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]         DATETIME2(0)                                      NULL,
    [ModifiedBy]         NVARCHAR(255)                                     NULL,
    [RowGuid]            UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PartnerKeyStorePassword_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]       DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]         DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerKeyStorePassword] PRIMARY KEY CLUSTERED ([KeyStorePasswordId] ASC),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerKeyStorePasswordHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE UNIQUE NONCLUSTERED INDEX [IX_Partner_KeyStorePasswordId] ON [Partner].[KeyStorePassword]
    ([PasswordKeyId] ASC) WITH (FILLFACTOR = 70);
GO

CREATE TABLE [Partner].[Certificate]
(
    [CertificateId]     INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]    INT                                               NOT NULL,
    [CertificateTypeId] INT                                               NOT NULL,
    [CertificateName]   VARCHAR(255)                                      NULL,
    [IsX509Certificate] BIT                                               NOT NULL CONSTRAINT [DF_PartnerCertificate_IsX509Certificate] DEFAULT 0,
    [KeyStore]          VARBINARY(MAX)                                    NOT NULL,
    [PasswordKeyId]     UNIQUEIDENTIFIER                                  NOT NULL,
    [Status]            TINYINT                                           NOT NULL CONSTRAINT [DF_PartnerCertificate_Status] DEFAULT (1),
    [Subject]           NVARCHAR(1024)                                    NOT NULL,
    [Issuer]            NVARCHAR(1024)                                    NOT NULL,
    [Thumbprint]        NVARCHAR(1024)                                    NOT NULL,
    [FingerPrint]       NVARCHAR(1024)                                    NOT NULL,
    [ValidFrom]         DATETIME2(0)                                      NOT NULL,
    [ValidTo]           DATETIME2(0)                                      NOT NULL,
    [CreatedOn]         DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PartnerCertificate_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)                                      NULL,
    [ModifiedBy]        NVARCHAR(255)                                     NULL,
    [RowGuid]           UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PartnerCertificate_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]      DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]        DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerCertificate] PRIMARY KEY CLUSTERED ([CertificateId] ASC),
    CONSTRAINT [FK_PartnerCertificate_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_PartnerCertificate_CertificateType] FOREIGN KEY ([CertificateTypeId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_PartnerCertificate_PasswordKeyId] FOREIGN KEY ([PasswordKeyId]) REFERENCES [Partner].[KeyStorePassword] ([PasswordKeyId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerCertificateHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

-- =============================================================================
-- SECTION 3: CLIENT SCHEMA
-- =============================================================================

CREATE TABLE [Client].[MFARealm]
(
    [MFARealmId]     INT IDENTITY (2,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [Name]           NVARCHAR(255)                                     NOT NULL,
    [Description]    NVARCHAR(1024)                                    NULL,
    [Uri]            NVARCHAR(1024)                                    NOT NULL,
    [Active]         BIT                                               NOT NULL CONSTRAINT [DF_MFARealm_Active] DEFAULT (1),
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_MFARealm_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NOT NULL CONSTRAINT [DF_MFARealm_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_MFARealm] PRIMARY KEY CLUSTERED ([MFARealmId] ASC),
    CONSTRAINT [FK_MFARealm_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    PERIOD FOR SYSTEM_TIME ([AuditStartOn], [AuditEndOn])
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[MFARealmHistory]));
GO

CREATE TABLE [Client].[Application]
(
    [ApplicationId]          INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]         INT                                               NOT NULL,
    [ClientId]               NCHAR(32)                                         NOT NULL,
    [Name]                   NVARCHAR(255)                                     NOT NULL,
    [Description]            NVARCHAR(1024)                                    NULL,
    [ApplicationTypeId]      INT                                               NOT NULL,
    [AuthFlowId]             INT                                               NOT NULL,
    [Uri]                    NVARCHAR(1024)                                    NOT NULL,
    [ConsentLifetime]        INT                                               NULL,
    [SupportSchemaClaims]    BIT                                               NOT NULL CONSTRAINT [DF_Client_SupportSchemaClaims] DEFAULT 0,
    [AlwaysSendClientClaims] BIT                                               NOT NULL CONSTRAINT [DF_Client_AlwaysSendClientClaims] DEFAULT 0,
    [Active]                 BIT                                               NOT NULL DEFAULT 1,
    [UsernameType]           INT                                               NULL,
    [AllowForgotUsername]    BIT                                               NULL,
    [MFARealmId]             INT                                               NOT NULL CONSTRAINT [DF_Client_MFARealmId] DEFAULT (1),
    [CMSContext]             NVARCHAR(255)                                     NULL,
    [PinTimeToLive]          INT                                               NULL,
    [CreatedOn]              DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_Client_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]             DATETIME2(0)                                      NULL,
    [ModifiedBy]             NVARCHAR(255)                                     NULL,
    [RowGuid]                UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_Client_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]           DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]             DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_Client] PRIMARY KEY CLUSTERED ([ApplicationId] ASC),
    CONSTRAINT [FK_Application_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_Application_ApplicationTypeId] FOREIGN KEY ([ApplicationTypeId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_Application_AuthFlowId] FOREIGN KEY ([AuthFlowId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_Application_MFARealmId] FOREIGN KEY ([MFARealmId]) REFERENCES [Client].[MFARealm] ([MFARealmId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[ClientHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[Claim]
(
    [ClaimId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [Type]           NVARCHAR(255)                                     NOT NULL,
    [Value]          NVARCHAR(255)                                     NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientClaim_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientClaim_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientClaim] PRIMARY KEY CLUSTERED ([ClaimId] ASC),
    CONSTRAINT [FK_ClientClaim_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientClaim_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[ClaimHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[Scope]
(
    [ScopeId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [Scope]          NVARCHAR(255)                                     NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientScope_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientScope_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientScope] PRIMARY KEY CLUSTERED ([ScopeId] ASC),
    CONSTRAINT [FK_ClientScope_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientScope_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[ScopeHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[GrantType]
(
    [GrantTypeId]    INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [GrantType]      NVARCHAR(250)                                     NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientGrantType_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientGrantType_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientGrantType] PRIMARY KEY CLUSTERED ([GrantTypeId] ASC),
    CONSTRAINT [FK_ClientGrantType_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientGrantType_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[GrantTypeHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[Secret]
(
    [SecretId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]  INT                                               NOT NULL,
    [ApplicationId]   INT                                               NOT NULL,
    [Description]     NVARCHAR(1024)                                    NULL,
    [KeyStore]        VARBINARY(MAX)                                    NOT NULL,
    [PasswordKeyId]   UNIQUEIDENTIFIER                                  NOT NULL,
    [SecretHashValue] VARBINARY(MAX)                                    NOT NULL,
    [ExpireOn]        DATETIME2(0)                                      NULL,
    [CreatedOn]       DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientSecret_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]      DATETIME2(0)                                      NULL,
    [ModifiedBy]      NVARCHAR(255)                                     NULL,
    [RowGuid]         UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientSecret_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]    DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]      DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientSecret] PRIMARY KEY CLUSTERED ([SecretId] ASC),
    CONSTRAINT [FK_ClientSecret_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientSecret_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ClientSecret_PasswordKeyId] FOREIGN KEY ([PasswordKeyId]) REFERENCES [Partner].[KeyStorePassword] ([PasswordKeyId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[SecretHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[Credential]
(
    [CredentialId]       INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]     INT                                               NOT NULL,
    [ApplicationId]      INT                                               NOT NULL,
    [Name]               NVARCHAR(1024)                                    NULL,
    [CredentialKey]      NVARCHAR(255)                                     NULL,
    [AuthFlowId]         INT                                               NOT NULL,
    [CredentialStatus]   INT                                               NOT NULL,
    [Fingerprint]        NVARCHAR(1024)                                    NOT NULL,
    [TokenAlgorithmId]   INT                                               NOT NULL,
    [SecretId]           INT                                               NULL,
    [CertificateId]      INT                                               NULL,
    [ExpireOn]           DATETIME2(0)                                      NULL,
    [CreatedOn]          DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientCredential_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]         DATETIME2(0)                                      NULL,
    [ModifiedBy]         NVARCHAR(255)                                     NULL,
    [RowGuid]            UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientCredential_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]       DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]         DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientCredential] PRIMARY KEY CLUSTERED ([CredentialId] ASC),
    CONSTRAINT [FK_ClientCredential_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientCredential_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ClientCredential_AuthFlowId] FOREIGN KEY ([AuthFlowId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_ClientCredential_TokenAlgorithmId] FOREIGN KEY ([TokenAlgorithmId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_CredentialSecretId_SecretId] FOREIGN KEY ([SecretId]) REFERENCES [Client].[Secret] ([SecretId]),
    CONSTRAINT [FK_CredentialCertificateId_CertificateId] FOREIGN KEY ([CertificateId]) REFERENCES [Partner].[Certificate] ([CertificateId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[CredentialHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[Setting]
(
    [SettingId]          INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]     INT                                               NOT NULL,
    [ApplicationId]      INT                                               NOT NULL,
    [JWKSetUrl]          NVARCHAR(255)                                     NULL,
    [JWSAlgorithmId]     INT                                               NULL,
    [RequireConsent]     BIT                                               NOT NULL CONSTRAINT [DF_ClientSetting_RequireConsent] DEFAULT 0,
    [RequirePkce]        BIT                                               NOT NULL CONSTRAINT [DF_ClientSetting_RequirePkce] DEFAULT 0,
    [AllowPlainTextPkce] BIT                                               NOT NULL CONSTRAINT [DF_ClientSetting_AllowPlainTextPkce] DEFAULT 0,
    [CreatedOn]          DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientSetting_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]         DATETIME2(0)                                      NULL,
    [ModifiedBy]         NVARCHAR(255)                                     NULL,
    [RowGuid]            UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientSetting_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]       DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]         DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientSetting] PRIMARY KEY CLUSTERED ([SettingId] ASC),
    CONSTRAINT [FK_ClientSetting_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientSetting_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_SettingJWSAlgorithmId_EnumId] FOREIGN KEY ([JWSAlgorithmId]) REFERENCES [dbo].[Enum] ([EnumId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[SettingHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[TokenSetting]
(
    [TokenSettingId]         INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]         INT                                               NOT NULL,
    [ApplicationId]          INT                                               NOT NULL,
    [AuthCodeTimeToLive]     INT                                               NOT NULL,
    [AccessTokenTimeToLive]  INT                                               NOT NULL,
    [AccessTokenFormatId]    INT                                               NOT NULL,
    [DeviceCodeTimeToLive]   INT                                               NULL,
    [ReuseRefreshTokens]     BIT                                               NOT NULL CONSTRAINT [DF_ClientTokenSetting_ReuseRefreshTokens] DEFAULT 0,
    [RefreshTokenTimeToLive] INT                                               NULL,
    [MaxRequestTransitTime]  INT                                               NOT NULL CONSTRAINT [DF_ClientTokenSetting_MaxRequestTransitTime] DEFAULT 1,
    [CreatedOn]              DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientTokenSetting_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]             DATETIME2(0)                                      NULL,
    [ModifiedBy]             NVARCHAR(255)                                     NULL,
    [RowGuid]                UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientTokenSetting_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]           DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]             DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientTokenSetting] PRIMARY KEY CLUSTERED ([TokenSettingId] ASC),
    CONSTRAINT [FK_ClientTokenSetting_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientTokenSetting_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ClientTokenSetting_AccessTokenFormatId] FOREIGN KEY ([AccessTokenFormatId]) REFERENCES [dbo].[Enum] ([EnumId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[TokenSettingHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[RedirectUri]
(
    [RedirectUriId]  INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [RedirectUri]    NVARCHAR(2000)                                    NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientRedirectUri_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientRedirectUri_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientRedirectUri] PRIMARY KEY CLUSTERED ([RedirectUriId] ASC),
    CONSTRAINT [FK_ClientRedirectUri_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientRedirectUri_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[RedirectUriHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[PostLogoutRedirectUri]
(
    [PostLogoutRedirectUriId] INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]          INT                                               NOT NULL,
    [ApplicationId]           INT                                               NOT NULL,
    [PostLogoutRedirectUri]   NVARCHAR(1024)                                    NOT NULL,
    [CreatedOn]               DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientPostLogoutRedirectUri_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]              DATETIME2(0)                                      NULL,
    [ModifiedBy]              NVARCHAR(255)                                     NULL,
    [RowGuid]                 UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientPostLogoutRedirectUri_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]            DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]              DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientPostLogoutRedirectUri] PRIMARY KEY CLUSTERED ([PostLogoutRedirectUriId] ASC),
    CONSTRAINT [FK_ClientPostLogoutRedirectUri_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientPostLogoutRedirectUri_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[PostLogoutRedirectUriHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[PartnerOrganizationGroup]
(
    [PartnerOrganizationGroupId] INT IDENTITY (1,1)                                NOT NULL,
    [ApplicationId]              INT                                               NOT NULL,
    [OrganizationGroupId]        INT                                               NOT NULL,
    [Status]                     TINYINT                                           NOT NULL CONSTRAINT [DF_ClientPartnerOrganizationGroup_Status] DEFAULT (1),
    [CreatedOn]                  DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientPartnerOrganizationGroup_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                 DATETIME2(0)                                      NULL,
    [ModifiedBy]                 NVARCHAR(255)                                     NULL,
    [RowGuid]                    UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientPartnerOrganizationGroup_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]               DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                 DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientPartnerOrganizationGroup] PRIMARY KEY CLUSTERED ([PartnerOrganizationGroupId] ASC),
    CONSTRAINT [FK_ClientPartnerOrganizationGroup_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ClientPartnerOrganizationGroup_OrganizationGroupId] FOREIGN KEY ([OrganizationGroupId]) REFERENCES [Partner].[OrganizationGroup] ([OrganizationGroupId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[PartnerOrganizationGroupHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE NONCLUSTERED INDEX [IX_ClientPartnerOrganizationGroup_ProfileId_OrganizationId] ON [Client].[PartnerOrganizationGroup]
    ([ApplicationId] ASC, [OrganizationGroupId] ASC);
GO

CREATE TABLE [Client].[Metadata]
(
    [MetadataId]     INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [Key]            NVARCHAR(30)                                      NOT NULL,
    [Value]          NVARCHAR(128)                                     NOT NULL,
    [SystemManaged]  BIT                                               NOT NULL CONSTRAINT [DF_ClientMetadata_SystemManaged] DEFAULT 0,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ClientMetadata_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ClientMetadata_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientMetadata] PRIMARY KEY CLUSTERED ([MetadataId] ASC),
    CONSTRAINT [FK_ClientMetadata_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientMetadata_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[MetadataHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Client].[ForgetUserNameSetting]
(
    [ForgetUserNameSettingId] INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]          INT                                               NOT NULL,
    [ApplicationId]           INT                                               NOT NULL,
    [ParamPriority]           INT                                               NOT NULL,
    [ParamJson]               NVARCHAR(4000)                                    NULL,
    [Active]                  BIT                                               NOT NULL CONSTRAINT [DF_ForgetUserNameSetting_Active] DEFAULT 1,
    [CreatedOn]               DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ForgetUserNameSetting_CreatedOn] DEFAULT GETUTCDATE(),
    [ModifiedOn]              DATETIME2(0)                                      NULL,
    [ModifiedBy]              NVARCHAR(255)                                     NULL,
    [AuditStartOn]            DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]              DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_Client_ForgetUserNameSetting] PRIMARY KEY CLUSTERED ([ForgetUserNameSettingId] ASC),
    CONSTRAINT [FK_ForgetUserNameSetting_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ForgetUserNameSetting_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    PERIOD FOR SYSTEM_TIME ([AuditStartOn], [AuditEndOn])
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[ForgetUserNameSettingHistory]));
GO

-- =============================================================================
-- SECTION 4: PERSON SCHEMA
-- =============================================================================

CREATE TABLE [Person].[LoginProvider]
(
    [LoginProviderId] INT IDENTITY (1,1)                                NOT NULL,
    [ProviderKey]     NVARCHAR(255)                                     NOT NULL,
    [ProviderName]    NVARCHAR(255)                                     NOT NULL,
    [DisplayName]     NVARCHAR(255)                                     NOT NULL,
    [Status]          TINYINT                                           NOT NULL CONSTRAINT [DF_PersonLoginProvider_Status] DEFAULT (1),
    [RowGuid]         UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PersonLoginProvider_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]    DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]      DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonLoginProvider] PRIMARY KEY CLUSTERED ([LoginProviderId] ASC),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[PersonLoginProviderHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Person].[Profile]
(
    [ProfileId]                    INT IDENTITY (1,1)                                NOT NULL,
    [LoginProviderId]              INT                                               NOT NULL,
    [Email]                        NVARCHAR(255)                                     NOT NULL,
    [EmailConfirmed]               BIT                                               NOT NULL,
    [PhoneNumber]                  NVARCHAR(128)                                     NOT NULL,
    [PhoneNumberConfirmed]         BIT                                               NOT NULL,
    [SecondaryPhoneNumber]         NVARCHAR(128)                                     NULL,
    [SecondaryPhoneNumberConfirmed] BIT                                              NULL,
    [TwoFactorEnabled]             BIT                                               NOT NULL,
    [Title]                        NVARCHAR(5)                                       NULL,
    [FirstName]                    NVARCHAR(255)                                     NOT NULL,
    [MiddleInitial]                NVARCHAR(1)                                       NULL,
    [LastName]                     NVARCHAR(255)                                     NULL,
    [Suffix]                       INT                                               NOT NULL,
    [ImageUrl]                     NVARCHAR(1024)                                    NULL,
    [AbsoluteExpirationOn]         DATETIME2(7)                                      NULL,
    [DataOriginId]                 INT                                               NOT NULL,
    [SyncFlag]                     BIT                                               NOT NULL CONSTRAINT [DF_PersonProfile_SyncFlag] DEFAULT 1,
    [Status]                       TINYINT                                           NOT NULL CONSTRAINT [DF_PersonProfile_Status] DEFAULT (1),
    [CreatedOn]                    DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PersonProfile_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                   DATETIME2(0)                                      NULL,
    [ModifiedBy]                   NVARCHAR(255)                                     NULL,
    [RowGuid]                      UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PersonProfile_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]                 DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                   DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonProfile] PRIMARY KEY CLUSTERED ([ProfileId] ASC),
    CONSTRAINT [FK_PersonProfile_LoginProviderId] FOREIGN KEY ([LoginProviderId]) REFERENCES [Person].[LoginProvider] ([LoginProviderId]),
    CONSTRAINT [FK_PersonProfile_OriginId] FOREIGN KEY ([DataOriginId]) REFERENCES [dbo].[DataOrigin] ([DataOriginId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[PersonProfileHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Person].[Credential]
(
    [Id]                     INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]              INT                                               NOT NULL,
    [UserName]               NVARCHAR(255)                                     NOT NULL,
    [Password]               VARBINARY(4000)                                   NOT NULL,
    [SyncPassword]           VARBINARY(4000)                                   NULL,
    [CredentialLocked]       BIT                                               NOT NULL,
    [AccessFailedCount]      INT                                               NOT NULL,
    [DataOriginId]           INT                                               NOT NULL,
    [ExternalId]             UNIQUEIDENTIFIER                                  NOT NULL DEFAULT 0x11111111111111111111111111111111,
    [Version]                INT                                               NULL,
    [PasswordExpirationDate] DATETIME                                          NULL,
    [LastLogin]              DATETIME2(2)                                      NULL,
    [SyncFlag]               BIT                                               NOT NULL CONSTRAINT [DF_PersonCredential_SyncFlag] DEFAULT 0,
    [Status]                 TINYINT                                           NOT NULL CONSTRAINT [DF_PersonCredential_Status] DEFAULT (1),
    [CreatedOn]              DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PersonCredential_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]             DATETIME2(0)                                      NULL,
    [ModifiedBy]             NVARCHAR(255)                                     NULL,
    [RowGuid]                UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PersonCredential_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [PartitionKey]           AS (ProfileId % 11 + 1) PERSISTED NOT NULL,
    [AuditStartOn]           DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]             DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonCredential] PRIMARY KEY CLUSTERED ([Id] ASC),
    CONSTRAINT [FK_PersonCredential_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    CONSTRAINT [FK_PersonCredential_OriginId] FOREIGN KEY ([DataOriginId]) REFERENCES [dbo].[DataOrigin] ([DataOriginId]),
    CONSTRAINT [FK_Credential_ExternalSource] FOREIGN KEY ([ExternalId]) REFERENCES [dbo].[ExternalSource] ([SourceId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[PersonCredentialHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Person].[CredentialAudit]
(
    [CredentialAuditId] INT IDENTITY (1,1) NOT NULL,
    [CredentialId]      INT                NOT NULL,
    [Password]          VARBINARY(4000)    NOT NULL,
    [Version]           INT                NOT NULL,
    [Status]            TINYINT            NOT NULL CONSTRAINT [DF_CredentialAudit_Status] DEFAULT (1),
    [CreatedOn]         DATETIME2(0)       NOT NULL CONSTRAINT [DF_CredentialAudit_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)       NULL,
    [ModifiedBy]        NVARCHAR(255)      NULL,
    CONSTRAINT [PK_CredentialAudit] PRIMARY KEY CLUSTERED ([CredentialAuditId] ASC),
    CONSTRAINT [FK_CredentialAudit_CredentialId] FOREIGN KEY ([CredentialId]) REFERENCES [Person].[Credential] ([Id])
);
GO

CREATE TABLE [Person].[BlockedPassword]
(
    [BlockedPasswordId] INT IDENTITY (1,1)  NOT NULL,
    [Password]          NVARCHAR(255)       NOT NULL,
    [Status]            TINYINT             NOT NULL CONSTRAINT [DF_BlockedPassword_Status] DEFAULT (1),
    [CreatedOn]         DATETIME2(0)        NOT NULL CONSTRAINT [DF_BlockedPassword_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)        NULL,
    [ModifiedBy]        NVARCHAR(255)       NULL,
    CONSTRAINT [PK_BlockedPassword] PRIMARY KEY CLUSTERED ([BlockedPasswordId] ASC),
    CONSTRAINT [Password_Unique_BlockedPassword] UNIQUE ([Password])
);
GO

CREATE TABLE [Person].[ProfileOrganization]
(
    [ProfileOrganizationId] INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]             INT                                               NOT NULL,
    [OrganizationId]        INT                                               NOT NULL,
    [Status]                TINYINT                                           NOT NULL CONSTRAINT [DF_PersonProfileOrganization_Status] DEFAULT (1),
    [CreatedOn]             DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PersonProfileOrganization_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]            DATETIME2(0)                                      NULL,
    [ModifiedBy]            NVARCHAR(255)                                     NULL,
    [RowGuid]               UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PersonProfileOrganization_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]          DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]            DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonProfileOrganization] PRIMARY KEY CLUSTERED ([ProfileOrganizationId] ASC),
    CONSTRAINT [FK_PersonProfileOrganization_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    CONSTRAINT [FK_PersonProfileOrganization_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[ProfileOrganizationHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE NONCLUSTERED INDEX [IX_PersonProfileOrganization_ProfileId_OrganizationId] ON [Person].[ProfileOrganization]
    ([ProfileId] ASC, [OrganizationId] ASC);
GO

CREATE TABLE [Person].[ProfileGroup]
(
    [ProfileGroupId]      INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]           INT                                               NOT NULL,
    [OrganizationGroupId] INT                                               NOT NULL,
    [CreatedOn]           DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PersonProfileOrganizationGroup_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]          DATETIME2(0)                                      NULL,
    [ModifiedBy]          NVARCHAR(255)                                     NULL,
    [RowGuid]             UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PersonProfileOrganizationGroup_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]        DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]          DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonProfileGroup] PRIMARY KEY CLUSTERED ([ProfileGroupId] ASC),
    CONSTRAINT [FK_ProfileGroup_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    CONSTRAINT [FK_ProfileGroup_OrganizationGroupId] FOREIGN KEY ([OrganizationGroupId]) REFERENCES [Partner].[OrganizationGroup] ([OrganizationGroupId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[ProfileGroupHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Person].[Metadata]
(
    [MetadataId]    INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]     INT                                               NOT NULL,
    [Key]           NVARCHAR(30)                                      NOT NULL,
    [Value]         NVARCHAR(128)                                     NOT NULL,
    [SystemManaged] BIT                                               NOT NULL CONSTRAINT [DF_PersonMetadata_SystemManaged] DEFAULT 0,
    [CreatedOn]     DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_PersonMetadata_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]    DATETIME2(0)                                      NULL,
    [ModifiedBy]    NVARCHAR(255)                                     NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_PersonMetadata_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]  DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]    DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonMetadata] PRIMARY KEY CLUSTERED ([MetadataId] ASC),
    CONSTRAINT [FK_PersonMetadata_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[MetadataHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

-- =============================================================================
-- SECTION 5: RESOURCE SCHEMA
-- =============================================================================

CREATE TABLE [Resource].[Resource]
(
    [ResourceId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]    INT                                               NOT NULL,
    [ApplicationId]     INT                                               NOT NULL,
    [ResourceLibraryId] INT                                               NOT NULL,
    [Status]            TINYINT                                           NOT NULL CONSTRAINT [DF_Resource_Status] DEFAULT (1),
    [CreatedOn]         DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_Resource_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)                                      NULL,
    [ModifiedBy]        NVARCHAR(255)                                     NULL,
    [RowGuid]           UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_Resource_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]      DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]        DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_Resource] PRIMARY KEY CLUSTERED ([ResourceId] ASC),
    CONSTRAINT [FK_ApiResource_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ApiResource_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ApiResource_ResourceLibraryId] FOREIGN KEY ([ResourceLibraryId]) REFERENCES [dbo].[ResourceLibrary] ([ResourceLibraryId]),
    CONSTRAINT [UQ_ApiResource_org_app_resource] UNIQUE ([OrganizationId], [ApplicationId], [ResourceLibraryId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[ResourceHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Resource].[Scope]
(
    [ScopeId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [ResourceId]     INT                                               NOT NULL,
    [Scope]          NVARCHAR(255)                                     NOT NULL,
    [Status]         TINYINT                                           NOT NULL CONSTRAINT [DF_ResourceScope_Status] DEFAULT (1),
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ResourceScope_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ResourceScope_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScope] PRIMARY KEY CLUSTERED ([ScopeId] ASC),
    CONSTRAINT [FK_ResourceScope_ScopeId] FOREIGN KEY ([ResourceId]) REFERENCES [Resource].[Resource] ([ResourceId]),
    CONSTRAINT [FK_ResourceScope_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ResourceScope_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[ScopeHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Resource].[ScopeClaim]
(
    [ScopeClaimId]   INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [ScopeId]        INT                                               NOT NULL,
    [Claim]          NVARCHAR(255)                                     NOT NULL,
    [Status]         TINYINT                                           NOT NULL CONSTRAINT [DF_ResourceScopeClaim_Status] DEFAULT (1),
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ResourceScopeClaim_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ResourceScopeClaim_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScopeClaim] PRIMARY KEY CLUSTERED ([ScopeClaimId] ASC),
    CONSTRAINT [FK_ResourceScopeClaim_ScopeId] FOREIGN KEY ([ScopeId]) REFERENCES [Resource].[Scope] ([ScopeId]),
    CONSTRAINT [FK_ResourceScopeClaim_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ResourceScopeClaim_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[ResourceScopeClaimHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE TABLE [Resource].[Metadata]
(
    [MetadataId]     INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [ResourceId]     INT                                               NOT NULL,
    [Key]            NVARCHAR(30)                                      NOT NULL,
    [Value]          NVARCHAR(128)                                     NOT NULL,
    [SystemManaged]  BIT                                               NOT NULL CONSTRAINT [DF_ResourceMetadata_SystemManaged] DEFAULT 0,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_ResourceMetadata_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_ResourceMetadata_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceMetadata] PRIMARY KEY CLUSTERED ([MetadataId] ASC),
    CONSTRAINT [FK_ResourceMetadata_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ResourceMetadata_ResourceId] FOREIGN KEY ([ResourceId]) REFERENCES [Resource].[Resource] ([ResourceId]),
    CONSTRAINT [FK_ResourceMetadata_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[MetadataHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

-- =============================================================================
-- SECTION 6: TOKEN SCHEMA
-- =============================================================================

CREATE TABLE [Token].[AuthSession]
(
    [AuthSessionId]       BIGINT IDENTITY (1,1)                             NOT NULL,
    [ApplicationId]       INT                                               NOT NULL,
    [SessionId]           UNIQUEIDENTIFIER                                  NOT NULL CONSTRAINT [DF_TokenAuthSession_SessionId] DEFAULT (NEWID()),
    [SubjectId]           NVARCHAR(255)                                     NOT NULL,
    [Scope]               NVARCHAR(1024)                                    NOT NULL,
    [AuthSessionStatusId] INT                                               NOT NULL,
    [AuthFlowId]          INT                                               NOT NULL,
    [ClientFingerprint]   VARBINARY(MAX)                                    NULL,
    [Pin]                 VARCHAR(512)                                      NULL,
    [ClientId]            NCHAR(32)                                         NULL,
    [Branding]            NVARCHAR(100)                                     NULL,
    [RedirectUriId]       INT                                               NULL,
    [CreatedOn]           DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_TokenAuthSession_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]          DATETIME2(0)                                      NULL,
    [ModifiedBy]          NVARCHAR(255)                                     NULL,
    [AuditStartOn]        DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]          DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_TokenAuthSession] PRIMARY KEY CLUSTERED ([AuthSessionId] ASC),
    CONSTRAINT [FK_TokenAuthSession_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_TokenAuthSession_AuthSessionStatusId] FOREIGN KEY ([AuthSessionStatusId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_TokenAuthSession_AuthFlowId] FOREIGN KEY ([AuthFlowId]) REFERENCES [dbo].[Enum] ([EnumId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Token].[AuthSessionHistory]));
GO

CREATE UNIQUE NONCLUSTERED INDEX [IX_TokenAuthSession_SessionId] ON [Token].[AuthSession]
    ([SessionId]) WITH (FILLFACTOR = 70);
GO

CREATE TABLE [Token].[Pkce]
(
    [PkceId]        BIGINT IDENTITY (1,1)                             NOT NULL,
    [SessionId]     UNIQUEIDENTIFIER                                  NOT NULL,
    [ApplicationId] INT                                               NOT NULL,
    [Data]          NVARCHAR(2048)                                    NOT NULL,
    [Algorithm]     NVARCHAR(30)                                      NOT NULL,
    [RedirectUri]   NVARCHAR(2000)                                    NULL,
    [CreatedOn]     DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_TokenPkce_CreatedOn] DEFAULT (GETUTCDATE()),
    [Expiration]    DATETIME2(7)                                      NULL,
    [ConsumedOn]    DATETIME2(7)                                      NOT NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_TokenPkce_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]  DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]    DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_TokenPkce] PRIMARY KEY CLUSTERED ([PkceId] ASC),
    CONSTRAINT [FK_TokenPkce_SessionId] FOREIGN KEY ([SessionId]) REFERENCES [Token].[AuthSession] ([SessionId]),
    CONSTRAINT [FK_TokenPkce_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Token].[PkceHistory]));
GO

CREATE TABLE [Token].[AuthCode]
(
    [AuthCodeId]    BIGINT IDENTITY (1,1)                             NOT NULL,
    [ApplicationId] INT                                               NOT NULL,
    [SessionId]     UNIQUEIDENTIFIER                                  NOT NULL,
    [DATA]          NVARCHAR(512)                                     NOT NULL,
    [DataHash]      BINARY(32)                                        NULL,
    [CreatedOn]     DATETIME2(0)                                      NOT NULL CONSTRAINT [DF_TokenAuthCode_CreatedOn] DEFAULT (GETUTCDATE()),
    [Expiration]    DATETIME2(7)                                      NOT NULL,
    [ConsumedOn]    DATETIME2(7)                                      NOT NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL                       NULL CONSTRAINT [DF_TokenAuthCode_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]  DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]    DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_TokenAuthCode] PRIMARY KEY CLUSTERED ([AuthCodeId] ASC),
    CONSTRAINT [FK_TokenAuthCode_SessionId] FOREIGN KEY ([SessionId]) REFERENCES [Token].[AuthSession] ([SessionId]),
    CONSTRAINT [FK_TokenAuthCode_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Token].[AuthCodeHistory], DATA_CONSISTENCY_CHECK = OFF));
GO

CREATE NONCLUSTERED INDEX [IX_AuthCode_DataHash] ON [Token].[AuthCode]
    ([DataHash] ASC) INCLUDE ([SessionId]) WITH (FILLFACTOR = 70);
GO

CREATE TABLE [Token].[Token]
(
    [TokenId]       BIGINT IDENTITY (1,1) NOT NULL,
    [TokenTypeId]   INT                   NOT NULL,
    [SubjectId]     NVARCHAR(200)         NOT NULL,
    [SessionId]     UNIQUEIDENTIFIER      NOT NULL,
    [ApplicationId] INT                   NOT NULL,
    [Data]          NVARCHAR(MAX)         NOT NULL,
    [IsOpaque]      BIT                   NOT NULL,
    [SigningKey]    VARBINARY(MAX)         NULL,
    [DataHash]      BINARY(32)            NULL,
    [CreatedOn]     DATETIME2(0)          NOT NULL CONSTRAINT [DF_TokenToken_CreatedOn] DEFAULT (GETUTCDATE()),
    [Expiration]    DATETIME2(7)          NOT NULL,
    [ConsumedOn]    DATETIME2(7)          NOT NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL NULL CONSTRAINT [DF_Token_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    CONSTRAINT [PK_Token] PRIMARY KEY CLUSTERED ([TokenId] ASC),
    CONSTRAINT [FK_Token_TypeId] FOREIGN KEY ([TokenTypeId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_Token_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId])
);
GO

CREATE NONCLUSTERED INDEX [IX_Token_DataHash] ON [Token].[Token]
    ([DataHash] ASC, [TokenTypeId] ASC) INCLUDE ([TokenId]) WITH (FILLFACTOR = 70);
GO

CREATE TABLE [Token].[SSO]
(
    [ID]                       INT IDENTITY (1,1)  NOT NULL,
    [SessionID]                UNIQUEIDENTIFIER    NOT NULL,
    [EncryptedSessionID]       VARBINARY(4000)     NOT NULL,
    [EncryptionKey]            VARBINARY(4000)     NOT NULL,
    [HashedEncryptedSessionId] BINARY(32)          NOT NULL,
    [CreatedOn]                DATETIME2(0)        NOT NULL DEFAULT (GETUTCDATE()),
    CONSTRAINT [PK_Token_SSO_HashedEncryptedSessionId] PRIMARY KEY CLUSTERED ([HashedEncryptedSessionId])
);
GO

-- =============================================================================
-- SECTION 7: USER-DEFINED TYPES
-- =============================================================================

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

-- =============================================================================
-- SECTION 8: SPRING SESSION TABLES
-- =============================================================================

CREATE TABLE SPRING_SESSION
(
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
GO

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);
GO

CREATE TABLE SPRING_SESSION_ATTRIBUTES
(
    SESSION_PRIMARY_ID CHAR(36)      NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200)  NOT NULL,
    ATTRIBUTE_BYTES    NVARCHAR(MAX) NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
GO

-- =============================================================================
-- SECTION 9: SECURITY - Create login and user
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'acsapp')
    BEGIN
        EXEC ('CREATE LOGIN [acsapp]
          WITH PASSWORD = N''Ac$App@123'',
               CHECK_POLICY = OFF,
               CHECK_EXPIRATION = OFF;');
    END;

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'acsapp')
    BEGIN
        CREATE USER [acsapp] FOR LOGIN [acsapp];
    END;

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'db_spexec')
    CREATE ROLE [db_spexec];

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'db_acsdev')
    CREATE ROLE [db_acsdev];

ALTER ROLE [db_spexec] ADD MEMBER [acsapp];
ALTER ROLE [db_acsdev] ADD MEMBER [acsapp];

GRANT CONTROL ON SCHEMA::[Client] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[dbo] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Partner] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Person] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Resource] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Token] TO [db_acsdev];

GRANT CREATE PROCEDURE TO [acsapp];
GRANT ALTER ON SCHEMA::[dbo] TO [acsapp];
GRANT EXECUTE ON SCHEMA::[dbo] TO [acsapp];
GO

-- =============================================================================
-- SECTION 10: SEED - Enum Types and Values
-- =============================================================================

:r ../src/main/resources/db/v1.0/dev/004_AuthDBSeedEnum.sql

-- Additional enum types from FinalTable

DECLARE @typeId INT;

-- Auth Code Token type
SELECT @typeId = EnumTypeId FROM [dbo].[EnumType] WHERE Name = 'TokenType';
IF NOT EXISTS (SELECT 1 FROM [dbo].[Enum] WHERE EnumTypeId = @typeId AND Code = N'Auth Code Token')
    INSERT INTO [dbo].[Enum] ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'Auth Code Token', N'Auth Code Token');

-- Inactive credential status
SELECT @typeId = EnumTypeId FROM [dbo].[EnumType] WHERE Name = 'CredentialStatus';
IF NOT EXISTS (SELECT 1 FROM [dbo].[Enum] WHERE EnumTypeId = @typeId AND Code = N'Inactive')
    INSERT INTO [dbo].[Enum] ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'Inactive', N'Client credential is inactive.');

-- Auth Session Status
SELECT @typeId = EnumTypeId FROM [dbo].[EnumType] WHERE Name = 'AuthSessionStatus';
IF @typeId IS NULL
BEGIN
    INSERT INTO [dbo].[EnumType] (Name, Description) VALUES (N'AuthSessionStatus', N'Auth session status.');
    SET @typeId = SCOPE_IDENTITY();
END
IF NOT EXISTS (SELECT 1 FROM [dbo].[Enum] WHERE EnumTypeId = @typeId AND Code = N'Session Active')
    INSERT INTO [dbo].[Enum] (EnumTypeId, Code, Description) VALUES (@typeId, N'Session Active', N'Active server auth session status.');
IF NOT EXISTS (SELECT 1 FROM [dbo].[Enum] WHERE EnumTypeId = @typeId AND Code = N'Session Inactive')
    INSERT INTO [dbo].[Enum] (EnumTypeId, Code, Description) VALUES (@typeId, N'Session Inactive', N'Inactive server auth session status.');

-- UsernameLookupField
IF NOT EXISTS (SELECT 1 FROM [dbo].[EnumType] WHERE Name = 'UsernameLookupField')
BEGIN
    INSERT INTO [dbo].[EnumType] ([Name], [Description], [Status]) VALUES ('UsernameLookupField', 'Username lookup field.', 1);
    SET @typeId = SCOPE_IDENTITY();
    INSERT INTO [dbo].[Enum] ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'AccountEmail', N'Email');
    INSERT INTO [dbo].[Enum] ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'AccountNumber', N'Account number');
    INSERT INTO [dbo].[Enum] ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'LastFourSSN', N'Last 4 Digits of SSN');
END
GO

-- =============================================================================
-- SECTION 11: SEED - Reference Data
-- =============================================================================

-- External Types
INSERT INTO [dbo].[ExternalType] ([ExternalTypeName], [ForgetUserSchema], [SyncUserSchema])
VALUES ('IDP', NULL, NULL);
GO

DECLARE @ExtTypeId INT;
SELECT @ExtTypeId = ExternalTypeId FROM [dbo].[ExternalType] WHERE ExternalTypeName = 'IDP';
INSERT INTO [dbo].[ExternalSource] (SourceId, SourceCode, ExternalTypeId)
VALUES (0x11111111111111111111111111111111, 'LOCAL', @ExtTypeId);
GO

-- Group Templates
INSERT INTO [dbo].[GroupTemplate] ([GroupName])
VALUES (N'Administrators'), (N'Developers'), (N'Viewers');
GO

-- Permission Templates
DECLARE @GroupTemplateAdministrators INT, @GroupTemplateDevelopers INT, @GroupTemplateViewers INT;
SELECT TOP 1 @GroupTemplateAdministrators = [GroupTemplateId] FROM [dbo].[GroupTemplate] WHERE [GroupName] = N'Administrators';
SELECT TOP 1 @GroupTemplateDevelopers = [GroupTemplateId] FROM [dbo].[GroupTemplate] WHERE [GroupName] = N'Developers';
SELECT TOP 1 @GroupTemplateViewers = [GroupTemplateId] FROM [dbo].[GroupTemplate] WHERE [GroupName] = N'Viewers';

INSERT INTO [dbo].[GroupPermissionTemplate] ([PermissionName], [PermissionKey], [GroupId])
VALUES
    (N'Organizations: Create', N'idp-admin-org:create', NULL),
    (N'Organizations: Read', N'idp-admin-org:read', @GroupTemplateAdministrators),
    (N'Organizations: Read', N'idp-admin-org:read', @GroupTemplateDevelopers),
    (N'Organizations: Read', N'idp-admin-org:read', @GroupTemplateViewers),
    (N'Organizations: Update', N'idp-admin-org:update', @GroupTemplateAdministrators),
    (N'Groups: Read', N'idp-admin-group:read', @GroupTemplateAdministrators),
    (N'Administrators: Create', N'idp-admin-admin:create', @GroupTemplateAdministrators),
    (N'Administrators: Read', N'idp-admin-admin:read', @GroupTemplateAdministrators),
    (N'Administrators: Update', N'idp-admin-admin:update', @GroupTemplateAdministrators),
    (N'Administrators: Delete', N'idp-admin-admin:delete', @GroupTemplateAdministrators),
    (N'Applications: Create', N'idp-admin-app:create', @GroupTemplateAdministrators),
    (N'Applications: Create', N'idp-admin-app:create', @GroupTemplateDevelopers),
    (N'Applications: Read', N'idp-admin-app:read', @GroupTemplateAdministrators),
    (N'Applications: Read', N'idp-admin-app:read', @GroupTemplateDevelopers),
    (N'Applications: Read', N'idp-admin-app:read', @GroupTemplateViewers),
    (N'Applications: Update', N'idp-admin-app:update', @GroupTemplateAdministrators),
    (N'Applications: Update', N'idp-admin-app:update', @GroupTemplateDevelopers),
    (N'Applications: Delete', N'idp-admin-app:delete', @GroupTemplateAdministrators),
    (N'Applications: Delete', N'idp-admin-app:delete', @GroupTemplateDevelopers),
    (N'Organization Certificates: Read', N'idp-admin-org-cert:read', @GroupTemplateAdministrators),
    (N'Organization Certificates: Read', N'idp-admin-org-cert:read', @GroupTemplateDevelopers),
    (N'Organization Certificates: Read', N'idp-admin-org-cert:read', @GroupTemplateViewers),
    (N'Credential Certificates: Create', N'idp-admin-cred-cert:create', @GroupTemplateAdministrators),
    (N'Credential Certificates: Create', N'idp-admin-cred-cert:create', @GroupTemplateDevelopers),
    (N'Credential Certificates: Read', N'idp-admin-cred-cert:read', @GroupTemplateAdministrators),
    (N'Credential Certificates: Read', N'idp-admin-cred-cert:read', @GroupTemplateDevelopers),
    (N'Credential Certificates: Read', N'idp-admin-cred-cert:read', @GroupTemplateViewers);
GO

-- Range configurations
INSERT INTO [dbo].[Range] ([Name], [Description], [Min], [Max])
VALUES
    (N'AuthCodeTimeToLive', N'Allowed Time To Live Range for Auth Codes', 1, 15),
    (N'AccessTokenTimeToLive', N'Allowed Time To Live Range for Access Tokens', 1, 3600),
    (N'DeviceCodeTimeToLive', N'Allowed Time To Live Range for Device Codes', 1, 5),
    (N'RefreshTokenTimeToLive', N'Allowed Time To Live Range for Refresh Tokens', 1, 900),
    (N'MaxRequestTransitTime', N'Allowed Range for Max Request Transit Time', 1, 30),
    (N'PinTimeToLive', N'Allowed Time To Live Range for MFA Expiry Pin', 300, 1800);
GO

-- Global Config
INSERT INTO [dbo].[GlobalConfig] ([Name], [Description], [Value])
VALUES
    (N'MaxPasswordFailureCount', N'Maximum failed login attempts before account is locked', 7),
    (N'DisallowedRecentPasswordCount', N'Number of recent passwords user cannot use', 12),
    (N'MaxMfaPinFailureCount', N'Maximum failed MFA PIN attempts before restarting MFA process', 3),
    (N'PinTimeToLive', N'Allowed Time To Live Range for MFA Expiry Pin', 300);
GO

-- =============================================================================
-- SECTION 12: SEED - Organization, Users, Admin App
-- =============================================================================

SET NOCOUNT ON;

DECLARE @DataOriginId INT, @LoginProviderId INT, @ProfileId INT, @OrganizationGroupId INT,
    @ApplicationTypeId INT, @AuthFlowId INT, @ExternalSourceId UNIQUEIDENTIFIER;

-- Data Origin
INSERT INTO [dbo].[DataOrigin] (DBName, TableName) VALUES (DB_NAME(), DB_NAME());
SET @DataOriginId = SCOPE_IDENTITY();

-- External Source
SELECT @ExternalSourceId = SourceId FROM [dbo].[ExternalSource] WHERE SourceCode = N'LOCAL';

-- Organization (ID=1 for Ascensus)
SET IDENTITY_INSERT [Partner].[Organization] ON;
INSERT INTO [Partner].[Organization] (OrganizationId, Name, Note)
VALUES (1, N'Ascensus', N'Ascensus Organization');
SET IDENTITY_INSERT [Partner].[Organization] OFF;

-- MFA Realm (ID=1 as default, needed for FK on Application)
SET IDENTITY_INSERT [Client].[MFARealm] ON;
INSERT INTO [Client].[MFARealm] (MFARealmId, OrganizationId, Name, Description, Uri)
VALUES (1, 1, N'Default', N'Default MFA Realm', N'http://localhost:9080');
SET IDENTITY_INSERT [Client].[MFARealm] OFF;

-- Organization Group
INSERT INTO [Partner].[OrganizationGroup] (OrganizationId, GroupName, Description)
VALUES (1, N'System Administrators', N'Ascensus System Administrators Group.');
SET @OrganizationGroupId = SCOPE_IDENTITY();

-- Login Provider
INSERT INTO [Person].[LoginProvider] (ProviderKey, ProviderName, DisplayName)
VALUES (N'ascensus', N'Ascensus', N'Ascensus Identity Provider');
SET @LoginProviderId = SCOPE_IDENTITY();

-- Admin Profile
INSERT INTO [Person].[Profile]
(LoginProviderId, Email, EmailConfirmed, PhoneNumber, PhoneNumberConfirmed, TwoFactorEnabled,
 FirstName, LastName, Suffix, DataOriginId)
VALUES (@LoginProviderId, N'sysadmin@ascensus.com', 1, N'+19999999999', 1, 0,
        N'System', N'Administrator', 0, @DataOriginId);
SET @ProfileId = SCOPE_IDENTITY();

-- Profile Organization
INSERT INTO [Person].[ProfileOrganization] (ProfileId, OrganizationId)
VALUES (@ProfileId, 1);

-- Profile Group
INSERT INTO [Person].[ProfileGroup] (ProfileId, OrganizationGroupId)
VALUES (@ProfileId, @OrganizationGroupId);

-- Admin Credential (password: acsauthadmin - hashed)
INSERT INTO [Person].[Credential]
(ProfileId, UserName, Password, CredentialLocked, AccessFailedCount, DataOriginId, ExternalId, Version)
VALUES (@ProfileId, N'acsauthadmin',
        0x0D001063A74718C004305ACECC027C9FD386D2894F127C5D868ED20819D42C0B6D7A2767C9156FC4145AEA57719133C39945B2F8C8D10C5ED955D22272851409ABE97C93CFEA9F29EC33F6F,
        0, 0, @DataOriginId, @ExternalSourceId, 1);

-- App Type & Auth Flow
SELECT @ApplicationTypeId = e.EnumId
FROM [dbo].[Enum] e JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
WHERE et.Name = N'ApplicationType' AND e.Code = N'web';

SELECT @AuthFlowId = e.EnumId
FROM [dbo].[Enum] e JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
WHERE et.Name = N'AuthFlow' AND e.Code = N'AuthCode';

-- Admin Portal Application
INSERT INTO [Client].[Application]
(OrganizationId, ClientId, Name, Description, ApplicationTypeId, AuthFlowId, Uri)
VALUES (1, N'82C8C1622E945CABB8AF14A40D30064', N'Admin Portal',
        N'Admin Portal Application', @ApplicationTypeId, @AuthFlowId,
        N'http://localhost:3000');
GO

-- =============================================================================
-- SECTION 13: STORED PROCEDURES
-- =============================================================================

:r ../src/main/resources/db/v1.0/dev/200_FinalProc.sql

-- Additional base procedures
:r ../src/main/resources/db/v1.0/dev/006_AuthDBProcs.sql

-- GetEnums procedure
CREATE OR ALTER PROCEDURE [dbo].[GetEnums]
AS
BEGIN
    SELECT MAX(CASE WHEN [Code] = 'Organization' THEN [EnumId] END)     AS [OrganizationEnumId],
           MAX(CASE WHEN [Code] = 'PublicKey' THEN [EnumId] END)        AS [PublicKeyEnumId],
           MAX(CASE WHEN [Code] = 'RS256' THEN [EnumId] END)            AS [RS256EnumId],
           MAX(CASE WHEN [Code] = 'ES256' THEN [EnumId] END)            AS [ES256EnumId],
           MAX(CASE WHEN [Code] = 'HS256' THEN [EnumId] END)            AS [HS256EnumId],
           MAX(CASE WHEN [Code] = 'Mobile' THEN [EnumId] END)           AS [MobileEnumId],
           MAX(CASE WHEN [Code] = 'Web' THEN [EnumId] END)              AS [WebEnumId],
           MAX(CASE WHEN [Code] = 'Server' THEN [EnumId] END)           AS [ServerEnumId],
           MAX(CASE WHEN [Code] = 'AuthCode' THEN [EnumId] END)         AS [AuthCodeEnumId],
           MAX(CASE WHEN [Code] = 'ClientSecretJWT' THEN [EnumId] END)  AS [ClientSecretJWTEnumId],
           MAX(CASE WHEN [Code] = 'PrivateKeyJWT' THEN [EnumId] END)    AS [PrivateKeyJWTEnumId],
           MAX(CASE WHEN [Code] = 'Access Token' THEN [EnumId] END)     AS [AccessTokenEnumId],
           MAX(CASE WHEN [Code] = 'Refresh Token' THEN [EnumId] END)    AS [RefreshTokenEnumId],
           MAX(CASE WHEN [Code] = 'OIDC Token' THEN [EnumId] END)       AS [IDTokenEnumId],
           MAX(CASE WHEN [Code] = 'Session Active' THEN [EnumId] END)   AS [AuthSessionActiveEnumId],
           MAX(CASE WHEN [Code] = 'Session Inactive' THEN [EnumId] END) AS [AuthSessionInactiveEnumId],
           MAX(CASE WHEN [Code] = 'mobile' THEN [EnumId] END)           AS [MobileSuffixEnumId],
           MAX(CASE WHEN [Code] = 'web' THEN [EnumId] END)              AS [WebSuffixEnumId],
           MAX(CASE WHEN [Code] = 'server' THEN [EnumId] END)           AS [ServerSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Auth Code Token' THEN [EnumId] END)  AS [AuthCodeTokenEnumId]
    FROM [dbo].[Enum]
END
GO

GRANT EXECUTE ON [dbo].[GetEnums] TO [db_spexec]
GO

PRINT '=========================================='
PRINT 'AGSAuth database initialized successfully!'
PRINT '=========================================='
GO
