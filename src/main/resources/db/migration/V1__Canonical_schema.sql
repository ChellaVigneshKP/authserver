SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
-- Canonical fresh database only. No database creation, logins or legacy filegroups.
-- Dates are UTC. ConsumedOn NULL represents unused; Expiration is always explicit.
CREATE SCHEMA Partner;
GO
CREATE SCHEMA Client;
GO
CREATE SCHEMA Person;
GO
CREATE SCHEMA Resource;
GO
CREATE SCHEMA Token;
GO
CREATE TABLE [dbo].[DataOrigin]
(
    [DataOriginId] INT IDENTITY (1,1) NOT NULL,
    [DBName]       NVARCHAR(128)       NOT NULL,
    [TableName]    NVARCHAR(50)       NOT NULL,
    [SyncEnabled]  BIT                NOT NULL
        CONSTRAINT [DF_DateOrigin_SyncEnabled] DEFAULT 0,
    [Status]       TINYINT            NOT NULL
        CONSTRAINT [DF_DataOrigin_Status] DEFAULT (1),
    [CreatedOn]    DATETIME2(0)       NOT NULL
        CONSTRAINT [DF_DataOrigin_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]   DATETIME2(0)       NULL,
    [ModifiedBy]   NVARCHAR(100)      NULL,
    CONSTRAINT [PK_DataOrigin] PRIMARY KEY CLUSTERED ([DataOriginId] ASC)
        ON [PRIMARY]
)
    ON [PRIMARY]
GO

CREATE TABLE [dbo].[EnumType]
(
    [EnumTypeId]  INT IDENTITY (1,1) NOT NULL,
    [Name]        NVARCHAR(255)      NOT NULL,
    [Description] NVARCHAR(1024)     NULL,
    [Status]      TINYINT            NOT NULL
        CONSTRAINT [DF_EnumType_Status] DEFAULT (1),
    [CreatedOn]   DATETIME2(0)       NOT NULL
        CONSTRAINT [DF_EnumType_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]  DATETIME2(0)       NULL,
    [ModifiedBy]  NVARCHAR(100)      NULL,
    CONSTRAINT [PK_EnumType] PRIMARY KEY CLUSTERED ([EnumTypeId] ASC)
        ON [PRIMARY]
)
    ON [PRIMARY]
GO

CREATE TABLE [dbo].[Enum]
(
    [EnumId]      INT IDENTITY (1,1) NOT NULL,
    [EnumTypeId]  INT                NOT NULL,
    [Code]        NVARCHAR(255)      NOT NULL,
    [Description] NVARCHAR(1024)     NOT NULL,
    [Status]      TINYINT            NOT NULL
        CONSTRAINT [DF_Enum_Status] DEFAULT (1),
    [CreatedOn]   DATETIME2(0)       NOT NULL
        CONSTRAINT [DF_Enum_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]  DATETIME2(0)       NULL,
    [ModifiedBy]  NVARCHAR(255)      NULL,
    CONSTRAINT [Pk_Enum] PRIMARY KEY CLUSTERED ([EnumId] ASC)
        ON [PRIMARY],
    CONSTRAINT [Fk_Enum_TypeId] FOREIGN KEY ([EnumTypeId]) REFERENCES [dbo].[EnumType] ([EnumTypeId])
)
    ON [PRIMARY]
GO

CREATE TABLE [Partner].[Organization]
(
    [OrganizationId]              INT IDENTITY (2,1)                                NOT NULL,
    [Name]                        NVARCHAR(255)                                     NOT NULL,
    [Note]                        NVARCHAR(1024)                                    NULL,
    [Status]                      TINYINT                                           NOT NULL
        CONSTRAINT [DF_PartnerOrganization_Status] DEFAULT (1),
    [PrimaryContactId]            [int]                                             NULL,
    [SecondaryContactId]          [int]                                             NULL,
    [PrimaryContactName]          [NVARCHAR](255)                                   NULL,
    [PrimaryContactEmail]         [NVARCHAR](255)                                   NULL,
    [PrimaryContactPhoneNumber]   [NVARCHAR](128)                                   NULL,
    [SecondaryContactName]        [NVARCHAR](255)                                   NULL,
    [SecondaryContactEmail]       [NVARCHAR](255)                                   NULL,
    [SecondaryContactPhoneNumber] [NVARCHAR](128)                                   NULL,
    [CreatedOn]                   DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PartnerOrganization_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                  DATETIME2(0)                                      NULL,
    [ModifiedBy]                  NVARCHAR(255)                                     NULL,
    [RowGuid]                     uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PartnerOrganization_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]                datetime2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                  datetime2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerOrganization] PRIMARY KEY CLUSTERED ([OrganizationId] ASC)
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerOrganizationHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Partner].[OrganizationGroup]
(
[GroupTemplateId] INT NULL,
    [OrganizationGroupId] INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]      INT                                               NOT NULL,
    [GroupName]           NVARCHAR(255)                                     NOT NULL,
    [Description]         NVARCHAR(1024)                                    NULL,
    [Status]              TINYINT                                           NOT NULL
        CONSTRAINT [DF_PartnerOrganizationGroup_Status] DEFAULT (1),
    [CreatedOn]           DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PartnerOrganizationGroup_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]          DATETIME2(0)                                      NULL,
    [ModifiedBy]          NVARCHAR(255)                                     NULL,
    [RowGuid]             uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PartnerOrganizationGroup_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]        datetime2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]          datetime2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerOrganizationGroup] PRIMARY KEY CLUSTERED ([OrganizationGroupId] ASC)
        ON [PRIMARY],
    CONSTRAINT [Fk_PartnerOrganizationGroup_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerOrganizationGroupHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Partner].[OrganizationGroupPermission]
(
[PermissionKey] NVARCHAR(64) NOT NULL,
    [OrganizationGroupPermissionId] INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationGroupId]           INT                                               NOT NULL,
    [OrganizationId]                INT                                               NOT NULL,
    [PermissionName]                NVARCHAR(255)                                     NOT NULL,
    [Description]                   NVARCHAR(1024)                                    NULL,
    [Status]                        TINYINT                                           NOT NULL
        CONSTRAINT [DF_PartnerOrganizationGroupPermission_Status] DEFAULT (1),
    [CreatedOn]                     DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PartnerOrganizationGroupPermission_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                    DATETIME2(0)                                      NULL,
    [ModifiedBy]                    NVARCHAR(255)                                     NULL,
    [RowGuid]                       uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PartnerOrganizationGroupPermission_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]                  datetime2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                    datetime2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerOrganizationGroupPermission] PRIMARY KEY CLUSTERED ([OrganizationGroupPermissionId] ASC)
        ON [PRIMARY],
    CONSTRAINT [Fk_PartnerOrganizationGroupPermission_OrganizationGroupId] FOREIGN KEY ([OrganizationGroupId]) REFERENCES [Partner].[OrganizationGroup] ([OrganizationGroupId]),
    CONSTRAINT [Fk_PartnerOrganizationGroupPermission_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerOrganizationGroupPermissionHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[Application]
(
    [Active] BIT NOT NULL CONSTRAINT DF_Application_Active DEFAULT 1,
    [UsernameType] INT NULL,
    [AllowForgotUsername] BIT NOT NULL CONSTRAINT DF_Application_AllowForgotUsername DEFAULT 0,
    [MFARealmId] INT NOT NULL CONSTRAINT DF_Application_MFARealmId DEFAULT 1,
    [CMSContext] NVARCHAR(255) NULL,
    [PinTimeToLive] INT NULL,
    [ApplicationId]          INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]         INT                                               NOT NULL,
    [ClientId]               NCHAR(32)                                         NOT NULL,
    [Name]                   NVARCHAR(255)                                     NOT NULL,
    [Description]            NVARCHAR(1024)                                    NULL,
    [ApplicationTypeId]      INT                                               NOT NULL,
    [AuthFlowId]             INT                                               NOT NULL,
    [Uri]                    NVARCHAR(1024)                                    NOT NULL,
    [ConsentLifetime]        INT                                               NULL,
    [SupportSchemaClaims]    BIT                                               NOT NULL
        CONSTRAINT [DF_Client_SupportSchemaClaims] DEFAULT 0,
    [AlwaysSendClientClaims] BIT                                               NOT NULL
        CONSTRAINT [DF_Client_AlwaysSendClientClaims] DEFAULT 0,
    [CreatedOn]              DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_Client_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]             DATETIME2(0)                                      NULL,
    [ModifiedBy]             NVARCHAR(255)                                     NULL,
    [RowGuid]                uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_Client_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]           DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]             DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_Client] PRIMARY KEY CLUSTERED ([ApplicationId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_Application_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_Application_ApplicationTypeId] FOREIGN KEY ([ApplicationTypeId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_Application_AuthFlowId] FOREIGN KEY ([AuthFlowId]) REFERENCES [dbo].[Enum] ([EnumId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[ClientHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE UNIQUE NONCLUSTERED INDEX [IX_Enum_TypeId_Code] ON [dbo].[Enum]
    (
     [EnumTypeId] ASC,
     [Code] ASC
        )
    WITH
        (FILLFACTOR = 70)
    ON [PRIMARY]
GO

CREATE TABLE [Partner].[KeyStorePassword]
(
    [KeyStorePasswordId] INT IDENTITY (1,1)                                NOT NULL,
    [KeyStore]           VARBINARY(MAX)                                    NOT NULL,
    [PasswordKeyId]      UNIQUEIDENTIFIER                                  NOT NULL,
    [CreatedOn]          DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PartnerKeyStorePassword_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]         DATETIME2(0)                                      NULL,
    [ModifiedBy]         NVARCHAR(255)                                     NULL,
    [RowGuid]            uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PartnerKeyStorePassword_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]       DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]         DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerKeyStorePassword] PRIMARY KEY CLUSTERED ([KeyStorePasswordId] ASC)
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerKeyStorePasswordHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE UNIQUE NONCLUSTERED INDEX [IX_Partner_KeyStorePasswordId] ON [Partner].[KeyStorePassword]
    (
     [PasswordKeyId] ASC
        ) WITH (FILLFACTOR = 70) ON [PRIMARY]
GO

CREATE TABLE [Partner].[Certificate]
(
[CertificateName] NVARCHAR(255) NULL,
    [CertificateId]     INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]    INT                                               NOT NULL,
    [CertificateTypeId] INT                                               NOT NULL,
    [IsX509Certificate] BIT                                               NOT NULL
        CONSTRAINT [DF_PartnerCertificate_IsX509Certificate] DEFAULT 0,
    [KeyStore]          VARBINARY(MAX)                                    NOT NULL,
    [PasswordKeyId]     UNIQUEIDENTIFIER                                  NOT NULL,
    [Status]            TINYINT                                           NOT NULL
        CONSTRAINT [DF_PartnerCertificate_Status] DEFAULT (1),
    [Subject]           NVARCHAR(1024)                                    NOT NULL,
    [Issuer]            NVARCHAR(1024)                                    NOT NULL,
    [Thumbprint]        NVARCHAR(1024)                                    NOT NULL,
    [FingerPrint]       NVARCHAR(1024)                                    NOT NULL,
    [ValidFrom]         DATETIME2(0)                                      NOT NULL,
    [ValidTo]           DATETIME2(0)                                      NOT NULL,
    [CreatedOn]         DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PartnerCertificate_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)                                      NULL,
    [ModifiedBy]        NVARCHAR(255)                                     NULL,
    [RowGuid]           uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PartnerCertificate_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]      DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]        DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PartnerCertificate] PRIMARY KEY CLUSTERED ([CertificateId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_PartnerCertificate_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_PartnerCertificate_CertificateType] FOREIGN KEY ([CertificateTypeId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_PartnerCertificate_PasswordKeyId] FOREIGN KEY ([PasswordKeyId]) REFERENCES [Partner].[KeyStorePassword] ([PasswordKeyId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Partner].[PartnerCertificateHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[Claim]
(
    [ClaimId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [Type]           NVARCHAR(255)                                     NOT NULL,
    [Value]          NVARCHAR(255)                                     NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientClaim_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientClaim_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientClaim] PRIMARY KEY CLUSTERED ([ClaimId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientClaim_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientClaim_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[ClaimHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Person].[LoginProvider]
(
    [LoginProviderId] INT IDENTITY (1,1)                                NOT NULL,
    [ProviderKey]     NVARCHAR(255)                                     NOT NULL,
    [ProviderName]    NVARCHAR(255)                                     NOT NULL,
    [DisplayName]     NVARCHAR(255)                                     NOT NULL,
    [Status]          TINYINT                                           NOT NULL
        CONSTRAINT [DF_PersonLoginProvider_Status] DEFAULT (1),
    [RowGuid]         uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PersonLoginProvider_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]    DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]      DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonLoginProvider] PRIMARY KEY CLUSTERED ([LoginProviderId] ASC)
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[PersonLoginProviderHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Person].[Profile]
(
[SecondaryPhoneNumber] NVARCHAR(128) NULL, [SecondaryPhoneNumberConfirmed] BIT NULL,
    [ProfileId]            INT IDENTITY (1,1)                                NOT NULL,
    [LoginProviderId]INT                                                     NOT NULL,
    [Email]                NVARCHAR(255)                                     NOT NULL,
    [EmailConfirmed]       BIT                                               NOT NULL,
    [PhoneNumber]          NVARCHAR(128)                                     NOT NULL,
    [PhoneNumberConfirmed] BIT                                               NOT NULL,
    [TwoFactorEnabled]     BIT                                               NOT NULL,
    [Title]                NVARCHAR(5)                                       NULL,
    [FirstName]            NVARCHAR(255)                                     NOT NULL,
    [MiddleInitial]        NVARCHAR(1)                                       NULL,
    [LastName]             NVARCHAR(255)                                     NULL,
    [Suffix]               INT                                               NULL,
    [ImageUrl]             NVARCHAR(1024)                                    NULL,
    [AbsoluteExpirationOn] DATETIME2(7)                                      NULL,
    [DataOriginId]         INT                                               NOT NULL,
    [SyncFlag]             BIT                                               NOT NULL
        CONSTRAINT [DF_PersonProfile_SyncFlag] DEFAULT 0,
    [Status]               TINYINT                                           NOT NULL
        CONSTRAINT [DF_PersonProfile_Status] DEFAULT (1),
    [CreatedOn]            DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PersonProfile_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]           DATETIME2(0)                                      NULL,
    [ModifiedBy]           NVARCHAR(255)                                     NULL,
    [RowGuid]              uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PersonProfile_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]         DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]           DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonProfile] PRIMARY KEY CLUSTERED ([ProfileId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_PersonProfile_LoginProviderId] FOREIGN KEY ([LoginProviderId]) REFERENCES [Person].[LoginProvider] ([LoginProviderId]),
    CONSTRAINT [FK_PersonProfile_OriginId] FOREIGN KEY ([DataOriginId]) REFERENCES [dbo].[DataOrigin] ([DataOriginId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[PersonProfileHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Person].[Credential]
(
[Version] INT NOT NULL CONSTRAINT DF_PersonCredential_Version DEFAULT 1,
    [ExternalId] UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_PersonCredential_ExternalId DEFAULT '11111111-1111-1111-1111-111111111111',
    [PasswordExpirationDate] DATETIME2(7) NULL, [LastLogin] DATETIME2(7) NULL,
    [Id]                INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]         INT                                               NOT NULL,
    [UserName]          NVARCHAR(255)                                     NOT NULL,
    [Password]          VARBINARY(4000)                                   NOT NULL,
    [SyncPassword]      VARBINARY(4000)                                   NULL,
    [CredentialLocked]  BIT                                               NOT NULL,
    [AccessFailedCount] INT                                               NOT NULL,
    [DataOriginId]      INT                                               NOT NULL,
    [SyncFlag]          BIT                                               NOT NULL
        CONSTRAINT [DF_PersonCredential_SyncFlag] DEFAULT 0,
    [Status]            TINYINT                                           NOT NULL
        CONSTRAINT [DF_PersonCredential_Status] DEFAULT (1),
    [CreatedOn]         DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PersonCredential_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)                                      NULL,
    [ModifiedBy]        NVARCHAR(255)                                     NULL,
    [RowGuid]           uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PersonCredential_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [PartitionKey]      AS (ProfileId % 11 + 1) PERSISTED NOT NULL,
    [AuditStartOn]      DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]        DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonCredential] PRIMARY KEY CLUSTERED ([Id] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_PersonCredential_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    CONSTRAINT [FK_PersonCredential_OriginId] FOREIGN KEY ([DataOriginId]) REFERENCES [dbo].[DataOrigin] ([DataOriginId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[PersonCredentialHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [dbo].[ResourceScopeLibrary]
(
    [ResourceScopeLibraryId] INT IDENTITY (1,1)                                NOT NULL,
    [Name]                   NVARCHAR(255)                                     NOT NULL,
    [DisplayName]            NVARCHAR(255)                                     NOT NULL,
    [Description]            NVARCHAR(1024)                                    NULL,
    [Required]               BIT                                               NOT NULL,
    [Status]                 TINYINT                                           NOT NULL
        CONSTRAINT [DF_ResourceScopeLibrary_Status] DEFAULT (1),
    [CreatedOn]              DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ResourceScopeLibrary_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]             DATETIME2(0)                                      NULL,
    [ModifiedBy]             NVARCHAR(255)                                     NULL,
    [RowGuid]                uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ResourceScopeLibrary_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]           DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]             DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScopeLibrary] PRIMARY KEY CLUSTERED ([ResourceScopeLibraryId] ASC)
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[ResourceScopeLibraryHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [dbo].[ResourceScopeClaimLibrary]
(
    [ResourceScopeClaimLibraryId] INT IDENTITY (1,1)                                NOT NULL,
    [ResourceScopeLibraryId]      INT                                               NOT NULL,
    [Status]                      TINYINT                                           NOT NULL
        CONSTRAINT [DF_ResourceScopeClaimLibrary_Status] DEFAULT (1),
    [CreatedOn]                   DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ResourceScopeClaimLibrary_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                  DATETIME2(0)                                      NULL,
    [ModifiedBy]                  NVARCHAR(255)                                     NULL,
    [RowGuid]                     uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ResourceScopeClaimLibrary_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]                DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                  DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScopeClaimLibrary] PRIMARY KEY CLUSTERED ([ResourceScopeClaimLibraryId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ResourceScopeClaimLibrary_ScopeLibraryId] FOREIGN KEY ([ResourceScopeLibraryId]) REFERENCES [dbo].[ResourceScopeLibrary] ([ResourceScopeLibraryId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[ResourceScopeClaimLibraryHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[Scope]
(
    [ScopeId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [Scope]          NVARCHAR(255)                                     NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientScope_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientScope_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientScope] PRIMARY KEY CLUSTERED ([ScopeId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientScope_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientScope_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[ScopeHistory], DATA_CONSISTENCY_CHECK = OFF)
    )
GO

CREATE TABLE [dbo].[ResourceLibrary]
(
[AllowedMethod] NVARCHAR(128) NOT NULL, [Urn] NVARCHAR(255) NULL,
    [ResourceLibraryId] INT IDENTITY (1,1)                                NOT NULL,
    [Name]       NVARCHAR(255)                                     NOT NULL,
    [Description]       NVARCHAR(1024)                                    NULL,
    [Uri]               NVARCHAR(2048)                                    NOT NULL,
    [DisplayEnabled]    BIT                                               NOT NULL
        CONSTRAINT [DF_ResourceLibrary_DisplayEnabled] DEFAULT 1,
    [Status]            TINYINT                                           NOT NULL
        CONSTRAINT [DF_ResourceLibrary_Status] DEFAULT 1,
    [CreatedOn]         DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ResourceLibrary_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]        DATETIME2(0)                                      NULL,
    [ModifiedBy]        NVARCHAR(255)                                     NULL,
    [RowGuid]           UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ResourceLibrary_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]      DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]        DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceLibrary] PRIMARY KEY CLUSTERED ([ResourceLibraryId] ASC)
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[ResourceLibraryHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Resource].[Resource]
(
[ResourceLibraryId] INT NOT NULL,
    [ResourceId]     INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [Status]         TINYINT                                           NOT NULL
        CONSTRAINT [DF_Resource_Status] DEFAULT (1),
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_Resource_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_Resource_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_Resource] PRIMARY KEY CLUSTERED ([ResourceId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ApiResource_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ApiResource_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[ResourceHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Resource].[Scope]
(
    [ScopeId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [ResourceId]     INT                                               NOT NULL,
    [Scope]          NVARCHAR(255)                                     NOT NULL,
    [Status]         TINYINT                                           NOT NULL
        CONSTRAINT [DF_ResourceScope_Status] DEFAULT (1),
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ResourceScope_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ResourceScope_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScope] PRIMARY KEY CLUSTERED ([ScopeId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ResourceScope_ScopeId] FOREIGN KEY ([ResourceId]) REFERENCES [Resource].[Resource] ([ResourceId]),
    CONSTRAINT [FK_ResourceScope_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ResourceScope_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[ScopeHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Resource].[ScopeClaim]
(
    [ScopeClaimId]   INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [ScopeId]        INT                                               NOT NULL,
    [Claim]          NVARCHAR(255)                                     NOT NULL,
    [Status]         TINYINT                                           NOT NULL
        CONSTRAINT [DF_ResourceScopeClaim_Status] DEFAULT (1),
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ResourceScopeClaim_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ResourceScopeClaim_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceScopeClaim] PRIMARY KEY CLUSTERED ([ScopeClaimId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ResourceScopeClaim_ScopeId] FOREIGN KEY ([ScopeId]) REFERENCES [Resource].[Scope] ([ScopeId]),
    CONSTRAINT [FK_ResourceScopeClaim_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ResourceScopeClaim_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME ( AuditStartOn, AuditEndOn )
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[ResourceScopeClaimHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[GrantType]
(
    [GrantTypeId]    INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [GrantType]      NVARCHAR(250)                                     NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientGrantType_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientGrantType_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientGrantType] PRIMARY KEY CLUSTERED ([GrantTypeId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientGrantType_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientGrantType_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[GrantTypeHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [dbo].[GroupPermissionTemplate]
(
[PermissionKey] NVARCHAR(64) NOT NULL,
    [GroupPermissionTemplateId] INT IDENTITY (1,1)                                NOT NULL,
    [GroupId]                   INT                                               NULL,
    [PermissionName]            NVARCHAR(255)                                     NOT NULL,
    [Description]               NVARCHAR(1024)                                    NULL,
    [Status]                    TINYINT                                           NOT NULL
        CONSTRAINT [DF_GroupPermissionTemplate_Status] DEFAULT (1),
    [CreatedOn]                 DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_GroupPermissionTemplate_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                DATETIME2(0)                                      NULL,
    [ModifiedBy]                NVARCHAR(255)                                     NULL,
    [RowGuid]                   uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PermissionTemplate_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]              DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_GroupPermissionTemplate] PRIMARY KEY CLUSTERED ([GroupPermissionTemplateId] ASC)
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[GroupPermissionTemplateHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [dbo].[GroupTemplate]
(
    [GroupTemplateId] INT IDENTITY (1,1)                                NOT NULL,
    [GroupName]       NVARCHAR(255)                                     NOT NULL,
    [Description]     NVARCHAR(1024)                                    NULL,
    [PartnerUse]      BIT                                               NOT NULL
        CONSTRAINT [DF_GroupTemplate_PartnerUse] DEFAULT 0,
    [Status]          TINYINT                                           NOT NULL
        CONSTRAINT [DF_GroupTemplate_Status] DEFAULT (1),
    [CreatedOn]       DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_GroupTemplate_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]      DATETIME2(0)                                      NULL,
    [ModifiedBy]      NVARCHAR(255)                                     NULL,
    [RowGuid]         uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_GroupTemplate_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]    DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]      DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_GroupTemplate] PRIMARY KEY CLUSTERED ([GroupTemplateId] ASC)
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [dbo].[GroupTemplateHistory], DATA_CONSISTENCY_CHECK = OFF)
    )
GO

CREATE TABLE [Client].[Secret]
(
    [SecretId]        INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]  INT                                               NOT NULL,
    [ApplicationId]   INT                                               NOT NULL,
    [Description]     NVARCHAR(1024)                                    NULL,
    [KeyStore]        varbinary(max)                                    NOT NULL,
    [PasswordKeyId]   UNIQUEIDENTIFIER                                  NOT NULL,
    [SecretHashValue] VARBINARY(MAX)                                    NOT NULL,
    [ExpireOn]        DATETIME2(0)                                      NULL,
    [CreatedOn]       DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientSecret_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]      DATETIME2(0)                                      NULL,
    [ModifiedBy]      NVARCHAR(255)                                     NULL,
    [RowGuid]         uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientSecret_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]    DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]      DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientSecret] PRIMARY KEY CLUSTERED ([SecretId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientSecret_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientSecret_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_PartnerCertificate_PasswordKeyId] FOREIGN KEY ([PasswordKeyId]) REFERENCES [Partner].[KeyStorePassword] ([PasswordKeyId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[SecretHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[Credential]
(
[SecretId] INT NULL, [CertificateId] INT NULL,
    [CredentialId]       INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]     INT                                               NOT NULL,
    [ApplicationId]      INT                                               NOT NULL,
    [Name]               NVARCHAR(1024)                                    NULL,
    [CredentialKey]      NVARCHAR(255)                                     NULL,
    [AuthFlowId]         INT                                               NOT NULL,
    [CredentialStatus] INT                                               NOT NULL,
    [Fingerprint]        NVARCHAR(1024)                                    NOT NULL,
    [TokenAlgorithmId]   INT                                               NOT NULL,
    [ExpireOn]           DATETIME2(0)                                      NULL,
    [CreatedOn]          DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientCredential_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]         DATETIME2(0)                                      NULL,
    [ModifiedBy]         NVARCHAR(255)                                     NULL,
    [RowGuid]            uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientCredential_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]       DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]         DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientCredential] PRIMARY KEY CLUSTERED ([CredentialId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientCredential_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientCredential_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ClientCredential_AuthFlowId] FOREIGN KEY ([AuthFlowId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_ClientCredential_TokenAlgorithmId] FOREIGN KEY ([TokenAlgorithmId]) REFERENCES [dbo].[Enum] ([EnumId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[CredentialHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Resource].[Metadata]
(
    [MetadataId]     INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [ResourceId]     INT                                               NOT NULL,
    [Key]            NVARCHAR(30)                                      NOT NULL,
    [Value]          NVARCHAR(128)                                     NOT NULL,
    [SystemManaged]  BIT                                               NOT NULL
        CONSTRAINT [DF_ResourceMetadata_SystemManaged] DEFAULT 0,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ResourceMetadata_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        uniqueidentifier ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ResourceMetadata_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ResourceMetadata] PRIMARY KEY CLUSTERED ([MetadataId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ResourceMetadata_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ResourceMetadata_ResourceId] FOREIGN KEY ([ResourceId]) REFERENCES [Resource].[Resource] ([ResourceId]),
    CONSTRAINT [FK_ResourceMetadata_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Resource].[MetadataHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[Metadata]
(
    [MetadataId]     INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [Key]            NVARCHAR(30)                                      NOT NULL,
    [Value]          NVARCHAR(128)                                     NOT NULL,
    [SystemManaged]  BIT                                               NOT NULL
        CONSTRAINT [DF_ClientMetadata_SystemManaged] DEFAULT 0,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientMetadata_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientMetadata_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientMetadata] PRIMARY KEY CLUSTERED ([MetadataId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientMetadata_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientMetadata_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[MetadataHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Person].[Metadata]
(
    [MetadataId]    INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]     INT                                               NOT NULL,
    [Key]           NVARCHAR(30)                                      NOT NULL,
    [Value]         NVARCHAR(128)                                     NOT NULL,
    [SystemManaged] BIT                                               NOT NULL
        CONSTRAINT [DF_PersonMetadata_SystemManaged] DEFAULT 0,
    [CreatedOn]     DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PersonMetadata_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]    DATETIME2(0)                                      NULL,
    [ModifiedBy]    NVARCHAR(255)                                     NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PersonMetadata_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]  DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]    DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonMetadata] PRIMARY KEY CLUSTERED ([MetadataId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_PersonMetadata_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    PERIOD FOR SYSTEM_TIME ( AuditStartOn, AuditEndOn )
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[MetadataHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[Setting]
(
    [SettingId]          INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]     INT                                               NOT NULL,
    [ApplicationId]      INT                                               NOT NULL,
    [JWKSetUrl]          NVARCHAR(255)                                     NULL,
    [JWSAlgorithmId] INT                                     NULL,
    [RequireConsent]     BIT                                               NOT NULL
        CONSTRAINT [DF_ClientSetting_RequireConsent] DEFAULT 0,
    [RequirePkce]        BIT                                               NOT NULL
        CONSTRAINT [DF_ClientSetting_RequirePkce] DEFAULT 0,
    [AllowPlainTextPkce] BIT                                               NOT NULL
        CONSTRAINT [DF_ClientSetting_AllowPlainTextPkce] DEFAULT 0,
    [CreatedOn]          DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientSetting_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]         DATETIME2(0)                                      NULL,
    [ModifiedBy]         NVARCHAR(255)                                     NULL,
    [RowGuid]            UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientSetting_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]       DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]         DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientSetting] PRIMARY KEY CLUSTERED ([SettingId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientSetting_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientSetting_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[SettingHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Token].[AuthSession]
(
[ClientFingerprint] VARBINARY(MAX) NULL, [Pin] VARCHAR(512) NULL, [ClientId] NCHAR(32) NULL, [Branding] NVARCHAR(100) NULL, [RedirectUriId] INT NULL,
    [AuthSessionId]       BIGINT IDENTITY (1,1)                             NOT NULL,
    [ApplicationId]       INT                                               NOT NULL,
    [SessionId]           UNIQUEIDENTIFIER                                  NOT NULL
        CONSTRAINT [DF_TokenAuthSession_SessionId] DEFAULT (NEWID()),
    [SubjectId]           NVARCHAR(255)                                     NOT NULL,
    [Scope]               NVARCHAR(1024)                                    NOT NULL,
    [AuthSessionStatusId] INT                                               NOT NULL,
    [AuthFlowId]          INT                                               NOT NULL,
    [CreatedOn]           DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_TokenAuthSession_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]          DATETIME2(0)                                      NULL,
    [ModifiedBy]          NVARCHAR(255)                                     NULL,
    [AuditStartOn]        DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]          DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_TokenAuthSession] PRIMARY KEY CLUSTERED ([AuthSessionId] ASC) ON [PRIMARY],
    CONSTRAINT [FK_TokenAuthSession_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].Application ([ApplicationId]),
    CONSTRAINT [FK_TokenAuthSession_AuthSessionStatusId] FOREIGN KEY ([AuthSessionStatusId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_TokenAuthSession_AuthFlowId] FOREIGN KEY ([AuthFlowId]) REFERENCES [dbo].[Enum] ([EnumId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Token].[AuthSessionHistory]))
GO

CREATE UNIQUE NONCLUSTERED INDEX [IX_TokenAuthSession_SessionId] ON [Token].[AuthSession]
    (
     [SessionId]
        ) WITH (
    FILLFACTOR = 70
    )
    ON [PRIMARY]
GO

CREATE TABLE [Token].[Pkce]
(
[RedirectUri] NVARCHAR(2000) NULL,
    [PkceId]        BIGINT IDENTITY (1,1)                             NOT NULL,
    [SessionId]     UNIQUEIDENTIFIER                                  NOT NULL,
    [ApplicationId] INT                                               NOT NULL,
    [Data]          NVARCHAR(2048)                                    NOT NULL,
    [Algorithm]     NVARCHAR(30)                                      NOT NULL,
    [CreatedOn]     DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_TokenPkce_CreatedOn] DEFAULT (GETUTCDATE()),
    [Expiration]    DATETIME2(0)                                      NOT NULL,
    [ConsumedOn]    DATETIME2(7)                                      NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL            NULL
        CONSTRAINT [DF_TokenPkce_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    CONSTRAINT [PK_TokenPkce] PRIMARY KEY CLUSTERED ([PkceId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_TokenPkce_SessionId] FOREIGN KEY ([SessionId]) REFERENCES [Token].[AuthSession] ([SessionId]),
    CONSTRAINT [FK_TokenPkce_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId])
)
    ON [PRIMARY]
GO

CREATE TABLE [Token].[AuthCode]
(
[DataHash] BINARY(32) NOT NULL,
    [AuthCodeId]    BIGINT IDENTITY (1,1)                             NOT NULL,
    [ApplicationId] INT                                               NOT NULL,
    [SessionId]     UNIQUEIDENTIFIER                                  NOT NULL,
    [DATA]          NVARCHAR(512)                                     NOT NULL,
    [CreatedOn]     DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_TokenAuthCode_CreatedOn] DEFAULT (GETUTCDATE()),
    [Expiration]    DATETIME2(7)                                      NOT NULL,
    [ConsumedOn]    DATETIME2(7)                                      NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_TokenAuthCode_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    CONSTRAINT [PK_TokenAuthCode] PRIMARY KEY CLUSTERED ([AuthCodeId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_TokenAuthCode_SessionId] FOREIGN KEY ([SessionId]) REFERENCES [Token].[AuthSession] ([SessionId]),
    CONSTRAINT [FK_TokenAuthCode_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId])
)
    ON [PRIMARY]
GO

CREATE TABLE [Token].[Token]
(
[Scope] NVARCHAR(MAX) NULL, [SigningKey] VARBINARY(MAX) NULL, [DataHash] BINARY(32) NOT NULL,
    [TokenId]       BIGINT IDENTITY (1,1)                             NOT NULL,
    [TokenTypeId]   INT                                               NOT NULL,
    [SubjectId]     NVARCHAR(200)                                     NOT NULL,
    [SessionId]     UNIQUEIDENTIFIER                                  NOT NULL,
    [ApplicationId] INT                                               NOT NULL,
    [Data]          NVARCHAR(MAX)                                     NOT NULL,
    [IsOpaque]      BIT                                               NOT NULL,
    [CreatedOn]     DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_TokenToken_CreatedOn] DEFAULT (GETUTCDATE()),
    [Expiration]    DATETIME2(7)                                      NOT NULL,
    [ConsumedOn]    DATETIME2(7)                                      NULL,
    [RowGuid]       UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_Token_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    CONSTRAINT [PK_Token] PRIMARY KEY CLUSTERED ([TokenId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_Token_TypeId] FOREIGN KEY ([TokenTypeId]) REFERENCES [dbo].[Enum] ([EnumId]),
    CONSTRAINT [FK_Token_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId])
)
    ON [PRIMARY]
GO

CREATE TABLE [Client].[TokenSetting]
(
[MaxRequestTransitTime] INT NOT NULL CONSTRAINT DF_TokenSetting_MaxRequestTransitTime DEFAULT 30,
    [TokenSettingId]         INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]         INT                                               NOT NULL,
    [ApplicationId]          INT                                               NOT NULL,
    [AuthCodeTimeToLive]     INT                                               NOT NULL,
    [AccessTokenTimeToLive]  INT                                               NOT NULL,
    [AccessTokenFormatId]    INT                                               NOT NULL,
    [DeviceCodeTimeToLive]   INT                                               NULL,
    [ReuseRefreshTokens]     BIT                                               NOT NULL
        CONSTRAINT [DF_ClientTokenSetting_ReuseRefreshTokens] DEFAULT 0,
    [RefreshTokenTimeToLive] INT                                               NULL,
    [CreatedOn]              DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientTokenSetting_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]             DATETIME2(0)                                      NULL,
    [ModifiedBy]             NVARCHAR(255)                                     NULL,
    [RowGuid]                UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientTokenSetting_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]           DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]             DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientTokenSetting] PRIMARY KEY CLUSTERED ([TokenSettingId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientTokenSetting_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientTokenSetting_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[TokenSettingHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[PartnerOrganizationGroup]
(
    [PartnerOrganizationGroupId] INT IDENTITY (1,1)                                NOT NULL,
    [ApplicationId]              INT                                               NOT NULL,
    [OrganizationGroupId]        INT                                               NOT NULL,
    [Status]                     TINYINT                                           NOT NULL
        CONSTRAINT [DF_ClientPartnerOrganizationGroup_Status] DEFAULT (1),
    [CreatedOn]                  DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientPartnerOrganizationGroup_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]                 DATETIME2(0)                                      NULL,
    [ModifiedBy]                 NVARCHAR(255)                                     NULL,
    [RowGuid]                    UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientPartnerOrganizationGroup_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]               DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]                 DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientPartnerOrganizationGroup] PRIMARY KEY CLUSTERED ([PartnerOrganizationGroupId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientPartnerOrganizationGroup_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    CONSTRAINT [FK_ClientPartnerOrganizationGroup_OrganizationGroupId] FOREIGN KEY ([OrganizationGroupId]) REFERENCES [Partner].[OrganizationGroup] ([OrganizationGroupId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[PartnerOrganizationGroupHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE NONCLUSTERED INDEX [IX_ClientPartnerOrganizationGroup_ProfileId_OrganizationId] ON [Client].[PartnerOrganizationGroup]
    (
     [ApplicationId] ASC,
     [OrganizationGroupId] ASC
        )
    ON [PRIMARY]
GO

CREATE TABLE [Client].[PostLogoutRedirectUri]
(
    [PostLogoutRedirectUriId] INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId]          INT                                               NOT NULL,
    [ApplicationId]           INT                                               NOT NULL,
    [PostLogoutRedirectUri]   NVARCHAR(1024)                                    NOT NULL,
    [CreatedOn]               DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientPostLogoutRedirectUri_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]              DATETIME2(0)                                      NULL,
    [ModifiedBy]              NVARCHAR(255)                                     NULL,
    [RowGuid]                 UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientPostLogoutRedirectUri_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]            DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]              DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientPostLogoutRedirectUri] PRIMARY KEY CLUSTERED ([PostLogoutRedirectUriId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientPostLogoutRedirectUri_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientPostLogoutRedirectUri_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[PostLogoutRedirectUriHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Person].[ProfileOrganization]
(
    [ProfileOrganizationId] INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]             INT                                               NOT NULL,
    [OrganizationId]        INT                                               NOT NULL,
    [Status]                TINYINT                                           NOT NULL
        CONSTRAINT [DF_PersonProfileOrganization_Status] DEFAULT (1),
    [CreatedOn]             DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PersonProfileOrganization_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]            DATETIME2(0)                                      NULL,
    [ModifiedBy]            NVARCHAR(255)                                     NULL,
    [RowGuid]               UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PersonProfileOrganization_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]          DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]            DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonProfileOrganization] PRIMARY KEY CLUSTERED ([ProfileOrganizationId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_PersonProfileOrganization_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    CONSTRAINT [FK_PersonProfileOrganization_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[ProfileOrganizationHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE NONCLUSTERED INDEX [IX_PersonProfileOrganization_ProfileId_OrganizationId] ON [Person].[ProfileOrganization]
    (
     [ProfileId] ASC,
     [OrganizationId] ASC
        )
    ON [PRIMARY]
GO

CREATE TABLE [Person].[ProfileGroup]
(
    [ProfileGroupId]      INT IDENTITY (1,1)                                NOT NULL,
    [ProfileId]           INT                                               NOT NULL,
    [OrganizationGroupId] INT                                               NOT NULL,
    [CreatedOn]           DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_PersonProfileOrganizationGroup_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]          DATETIME2(0)                                      NULL,
    [ModifiedBy]          NVARCHAR(255)                                     NULL,
    [RowGuid]             UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_PersonProfileOrganizationGroup_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]        DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]          DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_PersonProfileGroup] PRIMARY KEY CLUSTERED ([ProfileGroupId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ProfileGroup_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId]),
    CONSTRAINT [FK_ProfileGroup_OrganizationGroupId] FOREIGN KEY ([OrganizationGroupId]) REFERENCES [Partner].[OrganizationGroup] ([OrganizationGroupId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Person].[ProfileGroupHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

CREATE TABLE [Client].[RedirectUri]
(
    [RedirectUriId]  INT IDENTITY (1,1)                                NOT NULL,
    [OrganizationId] INT                                               NOT NULL,
    [ApplicationId]  INT                                               NOT NULL,
    [RedirectUri]    NVARCHAR(2000)                                    NOT NULL,
    [CreatedOn]      DATETIME2(0)                                      NOT NULL
        CONSTRAINT [DF_ClientRedirectUri_CreatedOn] DEFAULT (GETUTCDATE()),
    [ModifiedOn]     DATETIME2(0)                                      NULL,
    [ModifiedBy]     NVARCHAR(255)                                     NULL,
    [RowGuid]        UNIQUEIDENTIFIER ROWGUIDCOL                       NULL
        CONSTRAINT [DF_ClientRedirectUri_RowGuid] DEFAULT (NEWSEQUENTIALID()),
    [AuditStartOn]   DATETIME2(2) GENERATED ALWAYS AS ROW START HIDDEN NOT NULL,
    [AuditEndOn]     DATETIME2(2) GENERATED ALWAYS AS ROW END HIDDEN   NOT NULL,
    CONSTRAINT [PK_ClientRedirectUri] PRIMARY KEY CLUSTERED ([RedirectUriId] ASC)
        ON [PRIMARY],
    CONSTRAINT [FK_ClientRedirectUri_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
    CONSTRAINT [FK_ClientRedirectUri_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
    PERIOD FOR SYSTEM_TIME (AuditStartOn, AuditEndOn)
)
    ON [PRIMARY]
    WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[RedirectUriHistory], DATA_CONSISTENCY_CHECK = OFF))
GO

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
                    ON [PRIMARY]
        )
            ON [PRIMARY]
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
            ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME ([AuditStartOn], [AuditEndOn])
)
    ON [PRIMARY]
    WITH (
        SYSTEM_VERSIONING = ON
        (HISTORY_TABLE = [Client].[ForgetUserNameSettingHistory])
    );
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
    CONSTRAINT [PK_ExternalType] PRIMARY KEY CLUSTERED ([ExternalTypeId] ASC) ON [PRIMARY]
) ON [PRIMARY]
GO

CREATE TABLE [dbo].[ExternalSource]
(
    [SyncFlag] TINYINT NOT NULL CONSTRAINT DF_ExternalSource_SyncFlag DEFAULT 1,
    [SourceId]       [uniqueidentifier]                                 NOT NULL,
    [SourceCode]     [nvarchar](255)                                    NOT NULL,
    [ExternalTypeId] [int]                                              NOT NULL,
    [CreatedOn]      [datetime2](6)
        CONSTRAINT [DF_ExternalSource_CreatedOn] DEFAULT (getutcdate()) NOT NULL,
    [ModifiedOn]     [datetime2](6)                                     NULL,
    [ModifiedBy]     [nvarchar](255)                                    NULL,
    CONSTRAINT [PK_ExternalSource] PRIMARY KEY CLUSTERED ([SourceId] ASC) ON [PRIMARY]
) ON [PRIMARY]
GO

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
                    ON [PRIMARY],
            CONSTRAINT [FK_CredentialAudit_CredentialId]
                FOREIGN KEY ([CredentialId])
                    REFERENCES [Person].[Credential] ([Id])
        )
            ON [PRIMARY]
GO

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
                    ON [PRIMARY]
        )
            ON [PRIMARY]
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
        ON [PRIMARY],
    PERIOD FOR SYSTEM_TIME ([AuditStartOn], [AuditEndOn])
)
    ON [PRIMARY]
    WITH
        (
        SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Client].[MFARealmHistory])
    );
GO

CREATE TABLE [Person].[BlockedPassword] (
    BlockedPasswordId INT IDENTITY PRIMARY KEY, [Password] NVARCHAR(255) NOT NULL UNIQUE,
    [Status] TINYINT NOT NULL DEFAULT 1, CreatedOn DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    ModifiedOn DATETIME2(7) NULL, ModifiedBy NVARCHAR(255) NULL
)
GO

CREATE TABLE [Token].[SSO] (
    Id BIGINT IDENTITY NOT NULL, SessionId UNIQUEIDENTIFIER NOT NULL,
    EncryptedSessionId VARBINARY(4000) NOT NULL, EncryptionKey VARBINARY(4000) NOT NULL,
    HashedEncryptedSessionId BINARY(32) NOT NULL PRIMARY KEY,
    CreatedOn DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME()
)
GO

ALTER TABLE [Client].[Setting] ADD CONSTRAINT FK_Setting_JWSAlgorithm FOREIGN KEY (JWSAlgorithmId) REFERENCES dbo.Enum(EnumId);
ALTER TABLE [Client].[Credential] ADD CONSTRAINT FK_Credential_Secret FOREIGN KEY (SecretId) REFERENCES Client.Secret(SecretId), CONSTRAINT FK_Credential_Certificate FOREIGN KEY(CertificateId) REFERENCES Partner.Certificate(CertificateId);
ALTER TABLE [Person].[Credential] ADD CONSTRAINT FK_Credential_ExternalSource FOREIGN KEY(ExternalId) REFERENCES dbo.ExternalSource(SourceId), CONSTRAINT UQ_Credential_Username_Source UNIQUE(UserName, ExternalId);
ALTER TABLE [dbo].[ExternalSource] ADD CONSTRAINT FK_ExternalSource_Type FOREIGN KEY(ExternalTypeId) REFERENCES dbo.ExternalType(ExternalTypeId), CONSTRAINT UQ_ExternalSource_Code UNIQUE(SourceCode);
ALTER TABLE [Client].[Application] ADD CONSTRAINT UQ_Application_ClientId UNIQUE(ClientId), CONSTRAINT FK_Application_MFARealm FOREIGN KEY(MFARealmId) REFERENCES Client.MFARealm(MFARealmId);
ALTER TABLE [Client].[MFARealm] ADD CONSTRAINT FK_MFARealm_Organization FOREIGN KEY(OrganizationId) REFERENCES Partner.Organization(OrganizationId);
ALTER TABLE [Resource].[Resource] ADD CONSTRAINT FK_Resource_Library FOREIGN KEY(ResourceLibraryId) REFERENCES dbo.ResourceLibrary(ResourceLibraryId), CONSTRAINT UQ_Resource_Assignment UNIQUE(OrganizationId, ApplicationId, ResourceLibraryId);
ALTER TABLE [Partner].[OrganizationGroup] ADD CONSTRAINT FK_OrgGroup_Template FOREIGN KEY(GroupTemplateId) REFERENCES dbo.GroupTemplate(GroupTemplateId);
ALTER TABLE [Token].[AuthSession] ADD CONSTRAINT FK_AuthSession_RedirectUri FOREIGN KEY(RedirectUriId) REFERENCES Client.RedirectUri(RedirectUriId);
CREATE UNIQUE INDEX UX_Token_Hash_Type ON Token.Token(DataHash, TokenTypeId);
CREATE UNIQUE INDEX UX_AuthCode_Hash ON Token.AuthCode(DataHash);
CREATE UNIQUE INDEX UX_Pkce_Session ON Token.Pkce(SessionId);
CREATE UNIQUE INDEX UX_TokenSetting_App ON Client.TokenSetting(ApplicationId);
CREATE UNIQUE INDEX UX_ClientSetting_App ON Client.Setting(ApplicationId);
CREATE UNIQUE INDEX UX_EnumType_Name ON dbo.EnumType(Name);
CREATE INDEX IX_Token_Session ON Token.Token(SessionId);
GO
CREATE TYPE dbo.ForgetUserNameType AS TABLE(OrganizationId INT, ApplicationId INT, ParamPriority INT, ParamJson NVARCHAR(4000));
GO
