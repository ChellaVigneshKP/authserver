-- =============================================
-- Add Missing Schema Changes
-- These columns are required by stored procedures
-- =============================================

-- Add missing columns to Token.AuthSession
IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'AuthSession' AND COLUMN_NAME = 'ClientFingerprint')
BEGIN
    ALTER TABLE [Token].[AuthSession]
        ADD [ClientFingerprint] NVARCHAR(1024) NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'AuthSession' AND COLUMN_NAME = 'ClientId')
BEGIN
    ALTER TABLE [Token].[AuthSession]
        ADD [ClientId] NCHAR(32) NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'AuthSession' AND COLUMN_NAME = 'Branding')
BEGIN
    ALTER TABLE [Token].[AuthSession]
        ADD [Branding] NVARCHAR(255) NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'AuthSession' AND COLUMN_NAME = 'RedirectUri')
BEGIN
    ALTER TABLE [Token].[AuthSession]
        ADD [RedirectUri] NVARCHAR(2000) NULL
END
GO

-- Add missing columns to Token.Token
IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'Token' AND COLUMN_NAME = 'SigningKey')
BEGIN
    ALTER TABLE [Token].[Token]
        ADD [SigningKey] VARBINARY(MAX) NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'Token' AND COLUMN_NAME = 'DataHash')
BEGIN
    ALTER TABLE [Token].[Token]
        ADD [DataHash] VARBINARY(MAX) NULL
END
GO

-- Add missing columns to Token.Pkce
IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'Pkce' AND COLUMN_NAME = 'RedirectUri')
BEGIN
    ALTER TABLE [Token].[Pkce]
        ADD [RedirectUri] NVARCHAR(2000) NULL
END
GO

-- Create Token.SsoCookie table if it doesn't exist
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'Token' AND TABLE_NAME = 'SsoCookie')
BEGIN
    CREATE TABLE [Token].[SsoCookie]
    (
        [SsoCookieId] BIGINT IDENTITY(1,1) NOT NULL,
        [HashedEncryptedSessionId] VARBINARY(MAX) NOT NULL,
        [SessionId] UNIQUEIDENTIFIER NOT NULL,
        [EncryptedSessionId] NVARCHAR(MAX) NOT NULL,
        [EncryptionKey] VARBINARY(MAX) NOT NULL,
        [CreatedOn] DATETIME2(0) NOT NULL CONSTRAINT [DF_TokenSsoCookie_CreatedOn] DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        CONSTRAINT [PK_TokenSsoCookie] PRIMARY KEY CLUSTERED ([SsoCookieId] ASC)
    )
END
GO

-- Add missing columns to Client.Application for forgot username and PIN features
IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Application' AND COLUMN_NAME = 'AllowForgotUsername')
BEGIN
    ALTER TABLE [Client].[Application]
        ADD [AllowForgotUsername] BIT NULL DEFAULT 0
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Application' AND COLUMN_NAME = 'UsernameType')
BEGIN
    ALTER TABLE [Client].[Application]
        ADD [UsernameType] INT NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Application' AND COLUMN_NAME = 'PinTimeToLive')
BEGIN
    ALTER TABLE [Client].[Application]
        ADD [PinTimeToLive] INT NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Application' AND COLUMN_NAME = 'JwkSetUrl')
BEGIN
    ALTER TABLE [Client].[Application]
        ADD [JwkSetUrl] NVARCHAR(1024) NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Application' AND COLUMN_NAME = 'RequirePKCE')
BEGIN
    ALTER TABLE [Client].[Application]
        ADD [RequirePKCE] BIT NULL DEFAULT 0
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Application' AND COLUMN_NAME = 'JwsAlgorithmId')
BEGIN
    ALTER TABLE [Client].[Application]
        ADD [JwsAlgorithmId] INT NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Application' AND COLUMN_NAME = 'Active')
BEGIN
    ALTER TABLE [Client].[Application]
        ADD [Active] BIT NULL DEFAULT 1
END
GO

-- Add Status column to Client.Credential if missing
IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Credential' AND COLUMN_NAME = 'Status')
BEGIN
    ALTER TABLE [Client].[Credential]
        ADD [Status] TINYINT NULL DEFAULT 1
END
GO

-- Add certificate relation columns if missing
IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Credential' AND COLUMN_NAME = 'SecretId')
BEGIN
    ALTER TABLE [Client].[Credential]
        ADD [SecretId] INT NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'Credential' AND COLUMN_NAME = 'CertificateId')
BEGIN
    ALTER TABLE [Client].[Credential]
        ADD [CertificateId] INT NULL
END
GO

-- Add Person.Profile columns if missing
IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Person' AND TABLE_NAME = 'Profile' AND COLUMN_NAME = 'SecondaryPhoneNumber')
BEGIN
    ALTER TABLE [Person].[Profile]
        ADD [SecondaryPhoneNumber] NVARCHAR(128) NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Person' AND TABLE_NAME = 'Profile' AND COLUMN_NAME = 'MemberId')
BEGIN
    ALTER TABLE [Person].[Profile]
        ADD [MemberId] NVARCHAR(255) NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Person' AND TABLE_NAME = 'Profile' AND COLUMN_NAME = 'LoginId')
BEGIN
    ALTER TABLE [Person].[Profile]
        ADD [LoginId] UNIQUEIDENTIFIER NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Person' AND TABLE_NAME = 'Credential' AND COLUMN_NAME = 'ExternalId')
BEGIN
    ALTER TABLE [Person].[Credential]
        ADD [ExternalId] UNIQUEIDENTIFIER NULL
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Person' AND TABLE_NAME = 'Credential' AND COLUMN_NAME = 'DisallowedRecentPasswordCount')
BEGIN
    ALTER TABLE [Person].[Credential]
        ADD [DisallowedRecentPasswordCount] INT NULL DEFAULT 0
END
GO

IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'Person' AND TABLE_NAME = 'Credential' AND COLUMN_NAME = 'Version')
BEGIN
    ALTER TABLE [Person].[Credential]
        ADD [Version] INT NULL DEFAULT 1
END
GO

-- Create dbo.Range table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'Range')
BEGIN
    CREATE TABLE [dbo].[Range]
    (
        [RangeId] INT IDENTITY(1,1) NOT NULL,
        [RangeName] NVARCHAR(255) NOT NULL,
        [MinValue] INT NOT NULL,
        [MaxValue] INT NOT NULL,
        [CurrentValue] INT NOT NULL,
        [Status] TINYINT NOT NULL DEFAULT 1,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        CONSTRAINT [PK_Range] PRIMARY KEY CLUSTERED ([RangeId] ASC)
    )
END
GO

-- Create dbo.GlobalConfig table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'GlobalConfig')
BEGIN
    CREATE TABLE [dbo].[GlobalConfig]
    (
        [GlobalConfigId] INT IDENTITY(1,1) NOT NULL,
        [ConfigKey] NVARCHAR(255) NOT NULL,
        [ConfigValue] NVARCHAR(MAX) NOT NULL,
        [Description] NVARCHAR(1024) NULL,
        [Status] TINYINT NOT NULL DEFAULT 1,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        CONSTRAINT [PK_GlobalConfig] PRIMARY KEY CLUSTERED ([GlobalConfigId] ASC)
    )
END
GO

-- Create dbo.ResourceLibrary table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'ResourceLibrary')
BEGIN
    CREATE TABLE [dbo].[ResourceLibrary]
    (
        [ResourceLibraryId] INT IDENTITY(1,1) NOT NULL,
        [Name] NVARCHAR(255) NOT NULL,
        [Description] NVARCHAR(1024) NULL,
        [Uri] NVARCHAR(1024) NOT NULL,
        [AllowedMethod] NVARCHAR(10) NOT NULL,
        [Urn] NVARCHAR(255) NOT NULL,
        [Status] TINYINT NOT NULL DEFAULT 1,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        [RowGuid] UNIQUEIDENTIFIER ROWGUIDCOL NULL DEFAULT (NEWSEQUENTIALID()),
        CONSTRAINT [PK_ResourceLibrary] PRIMARY KEY CLUSTERED ([ResourceLibraryId] ASC)
    )
END
GO

-- Create Partner.Certificate table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'Partner' AND TABLE_NAME = 'Certificate')
BEGIN
    CREATE TABLE [Partner].[Certificate]
    (
        [CertificateId] INT IDENTITY(1,1) NOT NULL,
        [OrganizationId] INT NOT NULL,
        [CertificateName] NVARCHAR(255) NOT NULL,
        [CertificateTypeId] INT NOT NULL,
        [IsX509Certificate] BIT NOT NULL DEFAULT 1,
        [KeyStore] VARBINARY(MAX) NOT NULL,
        [PasswordKeyStore] VARBINARY(MAX) NULL,
        [PasswordKeyId] UNIQUEIDENTIFIER NULL,
        [Fingerprint] NVARCHAR(1024) NULL,
        [Thumbprint] NVARCHAR(1024) NULL,
        [Subject] NVARCHAR(1024) NULL,
        [Issuer] NVARCHAR(1024) NULL,
        [ValidFrom] DATETIME2(0) NULL,
        [ValidTo] DATETIME2(0) NULL,
        [Status] TINYINT NOT NULL DEFAULT 1,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        [RowGuid] UNIQUEIDENTIFIER ROWGUIDCOL NULL DEFAULT (NEWSEQUENTIALID()),
        CONSTRAINT [PK_PartnerCertificate] PRIMARY KEY CLUSTERED ([CertificateId] ASC),
        CONSTRAINT [FK_PartnerCertificate_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId])
    )
END
GO

-- Create Resource.Resource table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'Resource' AND TABLE_NAME = 'Resource')
BEGIN
    EXEC('CREATE SCHEMA Resource')
    
    CREATE TABLE [Resource].[Resource]
    (
        [ResourceId] INT IDENTITY(1,1) NOT NULL,
        [OrganizationId] INT NOT NULL,
        [ApplicationId] INT NOT NULL,
        [ResourceLibraryId] INT NOT NULL,
        [Status] TINYINT NOT NULL DEFAULT 1,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        [RowGuid] UNIQUEIDENTIFIER ROWGUIDCOL NULL DEFAULT (NEWSEQUENTIALID()),
        CONSTRAINT [PK_Resource] PRIMARY KEY CLUSTERED ([ResourceId] ASC),
        CONSTRAINT [FK_Resource_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
        CONSTRAINT [FK_Resource_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId]),
        CONSTRAINT [FK_Resource_ResourceLibraryId] FOREIGN KEY ([ResourceLibraryId]) REFERENCES [dbo].[ResourceLibrary] ([ResourceLibraryId])
    )
END
GO

-- Create Client.TokenSetting table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'TokenSetting')
BEGIN
    CREATE TABLE [Client].[TokenSetting]
    (
        [TokenSettingId] INT IDENTITY(1,1) NOT NULL,
        [OrganizationId] INT NOT NULL,
        [ApplicationId] INT NOT NULL,
        [AuthCodeTimeToLive] INT NOT NULL,
        [AccessTokenTimeToLive] INT NOT NULL,
        [RefreshTokenTimeToLive] INT NOT NULL,
        [DeviceCodeTimeToLive] INT NULL,
        [ReuseRefreshTokens] BIT NOT NULL DEFAULT 0,
        [AccessTokenFormatId] INT NOT NULL,
        [MaxRequestTransitTime] INT NULL,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        [RowGuid] UNIQUEIDENTIFIER ROWGUIDCOL NULL DEFAULT (NEWSEQUENTIALID()),
        CONSTRAINT [PK_ClientTokenSetting] PRIMARY KEY CLUSTERED ([TokenSettingId] ASC),
        CONSTRAINT [FK_ClientTokenSetting_OrganizationId] FOREIGN KEY ([OrganizationId]) REFERENCES [Partner].[Organization] ([OrganizationId]),
        CONSTRAINT [FK_ClientTokenSetting_ApplicationId] FOREIGN KEY ([ApplicationId]) REFERENCES [Client].[Application] ([ApplicationId])
    )
END
GO

-- Ensure MaxRequestTransitTime exists for older databases
IF COL_LENGTH('Client.TokenSetting', 'MaxRequestTransitTime') IS NULL
    BEGIN
        ALTER TABLE [Client].[TokenSetting]
            ADD [MaxRequestTransitTime] INT NULL;
    END
GO

-- Create Client.MFARealm table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'Client' AND TABLE_NAME = 'MFARealm')
BEGIN
    CREATE TABLE [Client].[MFARealm]
    (
        [MFARealmId] INT IDENTITY(1,1) NOT NULL,
        [RealmName] NVARCHAR(255) NOT NULL,
        [RealmUrl] NVARCHAR(1024) NOT NULL,
        [Status] TINYINT NOT NULL DEFAULT 1,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        CONSTRAINT [PK_ClientMFARealm] PRIMARY KEY CLUSTERED ([MFARealmId] ASC)
    )
END
GO

-- Create dbo.ExternalSource table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'ExternalSource')
BEGIN
    CREATE TABLE [dbo].[ExternalSource]
    (
        [ExternalSourceId] INT IDENTITY(1,1) NOT NULL,
        [SourceCode] NVARCHAR(50) NOT NULL,
        [SourceName] NVARCHAR(255) NOT NULL,
        [Branding] NVARCHAR(255) NULL,
        [Status] TINYINT NOT NULL DEFAULT 1,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        [ModifiedOn] DATETIME2(0) NULL,
        [ModifiedBy] NVARCHAR(255) NULL,
        CONSTRAINT [PK_ExternalSource] PRIMARY KEY CLUSTERED ([ExternalSourceId] ASC)
    )
END
GO

-- Create Person.PasswordHistory table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'Person' AND TABLE_NAME = 'PasswordHistory')
BEGIN
    CREATE TABLE [Person].[PasswordHistory]
    (
        [PasswordHistoryId] INT IDENTITY(1,1) NOT NULL,
        [ProfileId] INT NOT NULL,
        [Password] VARBINARY(4000) NOT NULL,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        CONSTRAINT [PK_PersonPasswordHistory] PRIMARY KEY CLUSTERED ([PasswordHistoryId] ASC),
        CONSTRAINT [FK_PersonPasswordHistory_ProfileId] FOREIGN KEY ([ProfileId]) REFERENCES [Person].[Profile] ([ProfileId])
    )
END
GO

-- Create dbo.PasswordBlacklist table if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'PasswordBlacklist')
BEGIN
    CREATE TABLE [dbo].[PasswordBlacklist]
    (
        [PasswordBlacklistId] INT IDENTITY(1,1) NOT NULL,
        [PasswordHash] VARBINARY(MAX) NOT NULL,
        [CreatedOn] DATETIME2(0) NOT NULL DEFAULT (GETUTCDATE()),
        CONSTRAINT [PK_PasswordBlacklist] PRIMARY KEY CLUSTERED ([PasswordBlacklistId] ASC)
    )
END
GO

-- Create dbo.ForgetUserNameType table type if missing
IF NOT EXISTS (SELECT * FROM sys.table_types WHERE name = 'ForgetUserNameType')
BEGIN
    CREATE TYPE [dbo].[ForgetUserNameType] AS TABLE
    (
        [OrganizationId] INT,
        [ApplicationId] INT,
        [ParamPriority] INT,
        [ParamJson] NVARCHAR(MAX)
    )
END
GO
