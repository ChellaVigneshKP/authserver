IF COL_LENGTH('Partner.Certificate', 'PasswordKeyStore') IS NULL
    BEGIN
        ALTER TABLE [Partner].[Certificate]
            ADD [PasswordKeyStore] VARBINARY(MAX) NULL;
    END
GO

-- Add missing CertificateName column (if missing)
IF COL_LENGTH('Partner.Certificate', 'CertificateName') IS NULL
    BEGIN
        ALTER TABLE [Partner].[Certificate]
            ADD [CertificateName] NVARCHAR(255) NOT NULL DEFAULT ('');
    END
GO

-- Add missing ResourceLibraryId column
IF COL_LENGTH('Resource.Resource', 'ResourceLibraryId') IS NULL
    BEGIN
        ALTER TABLE [Resource].[Resource]
            ADD [ResourceLibraryId] INT NOT NULL DEFAULT 0;
    END
GO


IF COL_LENGTH('Client.Application', 'Name') IS NULL
    BEGIN
        ALTER TABLE [Client].[Application]
            ADD [Name] NVARCHAR(255) NULL;
    END
GO

IF COL_LENGTH('dbo.ResourceLibrary', 'Urn') IS NULL
    BEGIN
        ALTER TABLE [dbo].[ResourceLibrary]
            ADD [Urn] NVARCHAR(255) NULL;
    END
GO
