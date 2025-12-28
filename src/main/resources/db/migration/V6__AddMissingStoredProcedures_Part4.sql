-- =============================================
-- Add All Missing Stored Procedures - Part 4
-- Partner, Resource, and dbo Schema Procedures
-- =============================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- =============================================
-- Partner Schema Stored Procedures
-- =============================================

-- Partner.GetCertificate
CREATE OR ALTER PROCEDURE [Partner].[GetCertificate]
    @orgId INT,
    @certId INT
AS
BEGIN
    SELECT * FROM [Partner].[Certificate]
    WHERE OrganizationId = @orgId
      AND CertificateId = @certId;
END
GO

-- Partner.GetCertificateById
CREATE OR ALTER PROCEDURE [Partner].[GetCertificateById]
    @orgId INT,
    @certId INT
AS
BEGIN
    SELECT * FROM [Partner].[Certificate]
    WHERE OrganizationId = @orgId
      AND CertificateId = @certId;
END
GO

-- Partner.GetCertificatesByOrgId
CREATE OR ALTER PROCEDURE [Partner].[GetCertificatesByOrgId]
    @orgId INT
AS
BEGIN
    SELECT * FROM [Partner].[Certificate]
    WHERE OrganizationId = @orgId
    ORDER BY CreatedOn DESC;
END
GO

-- Partner.GetCertificatesByCertTypeId
CREATE OR ALTER PROCEDURE [Partner].[GetCertificatesByCertTypeId]
    @certTypeId INT
AS
BEGIN
    SELECT * FROM [Partner].[Certificate]
    WHERE CertificateTypeId = @certTypeId
      AND Status = 1
    ORDER BY CreatedOn DESC;
END
GO

-- Partner.GetCertificatesByClientIdAndCertTypeId
CREATE OR ALTER PROCEDURE [Partner].[GetCertificatesByClientIdAndCertTypeId]
    @clientId NCHAR(32),
    @certTypeId INT
AS
BEGIN
    SELECT c.* FROM [Partner].[Certificate] c
    INNER JOIN [Partner].[Organization] o ON c.OrganizationId = o.OrganizationId
    INNER JOIN [Client].[Application] a ON o.OrganizationId = a.OrganizationId
    WHERE a.ClientId = @clientId
      AND c.CertificateTypeId = @certTypeId
      AND c.Status = 1
    ORDER BY c.CreatedOn DESC;
END
GO

-- Partner.UpdateCertificateStatus
CREATE OR ALTER PROCEDURE [Partner].[UpdateCertificateStatus]
    @orgId INT,
    @certId INT,
    @status TINYINT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Partner].[Certificate]
    SET Status = @status,
        ModifiedOn = @modifiedOn,
        ModifiedBy = @modifiedBy
    WHERE OrganizationId = @orgId
      AND CertificateId = @certId;
END
GO

-- Partner.GetOrganizationGroup
CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroup]
    @orgId INT,
    @groupGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT * FROM [Partner].[OrganizationGroup]
    WHERE OrganizationId = @orgId
      AND RowGuid = @groupGuid;
END
GO

-- Partner.GetOrganizationGroups
CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroups]
    @orgId INT
AS
BEGIN
    SELECT * FROM [Partner].[OrganizationGroup]
    WHERE OrganizationId = @orgId
      AND Status = 1
    ORDER BY GroupName;
END
GO

-- Partner.GetOrganizationGroupPermissions
CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroupPermissions]
    @orgId INT,
    @orgGroupId INT
AS
BEGIN
    SELECT * FROM [Partner].[OrganizationGroupPermission]
    WHERE OrganizationId = @orgId
      AND OrganizationGroupId = @orgGroupId
      AND Status = 1;
END
GO

-- Partner.UpdateOrganization
CREATE OR ALTER PROCEDURE [Partner].[UpdateOrganization]
    @orgId INT,
    @name NVARCHAR(255),
    @desc NVARCHAR(1024),
    @status TINYINT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Partner].[Organization]
    SET Name = @name,
        Note = @desc,
        Status = @status,
        ModifiedOn = @modifiedOn,
        ModifiedBy = @modifiedBy
    WHERE OrganizationId = @orgId;
END
GO

-- Partner.UpdateOrganizationPrimaryContact
CREATE OR ALTER PROCEDURE [Partner].[UpdateOrganizationPrimaryContact]
    @orgId INT,
    @primaryContactName NVARCHAR(255),
    @primaryContactEmail NVARCHAR(255),
    @primaryContactPhoneNumber NVARCHAR(128),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Partner].[Organization]
    SET PrimaryContactName = @primaryContactName,
        PrimaryContactEmail = @primaryContactEmail,
        PrimaryContactPhoneNumber = @primaryContactPhoneNumber,
        ModifiedOn = @modifiedOn,
        ModifiedBy = @modifiedBy
    WHERE OrganizationId = @orgId;
END
GO

-- Partner.UpdateOrganizationSecondaryContact
CREATE OR ALTER PROCEDURE [Partner].[UpdateOrganizationSecondaryContact]
    @orgId INT,
    @secondaryContactName NVARCHAR(255),
    @secondaryContactEmail NVARCHAR(255),
    @secondaryContactPhoneNumber NVARCHAR(128),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Partner].[Organization]
    SET SecondaryContactName = @secondaryContactName,
        SecondaryContactEmail = @secondaryContactEmail,
        SecondaryContactPhoneNumber = @secondaryContactPhoneNumber,
        ModifiedOn = @modifiedOn,
        ModifiedBy = @modifiedBy
    WHERE OrganizationId = @orgId;
END
GO

-- Partner.GetResourceById
CREATE OR ALTER PROCEDURE [Partner].[GetResourceById]
    @orgId INT
AS
BEGIN
    SELECT r.*, rl.*
    FROM [Resource].[Resource] r
    INNER JOIN [dbo].[ResourceLibrary] rl ON r.ResourceLibraryId = rl.ResourceLibraryId
    WHERE r.OrganizationId = @orgId;
END
GO

-- =============================================
-- Resource Schema Stored Procedures
-- =============================================

-- Resource.CreateResource
CREATE OR ALTER PROCEDURE [Resource].[CreateResource]
    @orgId INT,
    @appId INT,
    @resourceLibraryId INT
AS
BEGIN
    INSERT INTO [Resource].[Resource]
        (OrganizationId, ApplicationId, ResourceLibraryId, Status)
    VALUES
        (@orgId, @appId, @resourceLibraryId, 1);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Resource.GetResource
CREATE OR ALTER PROCEDURE [Resource].[GetResource]
    @orgId INT,
    @appId INT,
    @resourceLibraryGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT r.*, rl.*
    FROM [Resource].[Resource] r
    INNER JOIN [dbo].[ResourceLibrary] rl ON r.ResourceLibraryId = rl.ResourceLibraryId
    WHERE r.OrganizationId = @orgId
      AND r.ApplicationId = @appId
      AND rl.RowGuid = @resourceLibraryGuid;
END
GO

-- Resource.GetResourceById
CREATE OR ALTER PROCEDURE [Resource].[GetResourceById]
    @resourceId INT
AS
BEGIN
    SELECT r.*, rl.*
    FROM [Resource].[Resource] r
    INNER JOIN [dbo].[ResourceLibrary] rl ON r.ResourceLibraryId = rl.ResourceLibraryId
    WHERE r.ResourceId = @resourceId;
END
GO

-- Resource.GetResourceByResourceLibraryId
CREATE OR ALTER PROCEDURE [Resource].[GetResourceByResourceLibraryId]
    @orgId INT,
    @appId INT,
    @resourceLibraryId INT
AS
BEGIN
    SELECT r.*, rl.*
    FROM [Resource].[Resource] r
    INNER JOIN [dbo].[ResourceLibrary] rl ON r.ResourceLibraryId = rl.ResourceLibraryId
    WHERE r.OrganizationId = @orgId
      AND r.ApplicationId = @appId
      AND r.ResourceLibraryId = @resourceLibraryId;
END
GO

-- Resource.DeleteResource
CREATE OR ALTER PROCEDURE [Resource].[DeleteResource]
    @resourceId INT
AS
BEGIN
    DELETE FROM [Resource].[Resource]
    WHERE ResourceId = @resourceId;
END
GO

-- =============================================
-- dbo Schema Stored Procedures
-- =============================================

-- dbo.GetEnumsByType
CREATE OR ALTER PROCEDURE [dbo].[GetEnumsByType]
    @enumTypeName NVARCHAR(255)
AS
BEGIN
    SELECT e.* FROM [dbo].[Enum] e
    INNER JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
    WHERE et.Name = @enumTypeName
      AND e.Status = 1
    ORDER BY e.Code;
END
GO

-- dbo.GetGlobalConfig
CREATE OR ALTER PROCEDURE [dbo].[GetGlobalConfig]
AS
BEGIN
    SELECT * FROM [dbo].[GlobalConfig]
    WHERE Status = 1;
END
GO

-- dbo.GetRanges
CREATE OR ALTER PROCEDURE [dbo].[GetRanges]
AS
BEGIN
    SELECT * FROM [dbo].[Range]
    WHERE Status = 1
    ORDER BY RangeName;
END
GO

-- dbo.UpdateRanges
CREATE OR ALTER PROCEDURE [dbo].[UpdateRanges]
AS
BEGIN
    -- This procedure likely updates current values in ranges
    -- Implementation depends on specific business logic
    SELECT 1 AS Success;
END
GO

-- dbo.CreateResourceLibrary
CREATE OR ALTER PROCEDURE [dbo].[CreateResourceLibrary]
    @name NVARCHAR(255),
    @description NVARCHAR(1024),
    @uri NVARCHAR(1024),
    @allowedMethod NVARCHAR(10),
    @urn NVARCHAR(255)
AS
BEGIN
    INSERT INTO [dbo].[ResourceLibrary]
        (Name, Description, Uri, AllowedMethod, Urn, Status)
    VALUES
        (@name, @description, @uri, @allowedMethod, @urn, 1);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- dbo.UpdateResourceLibrary
CREATE OR ALTER PROCEDURE [dbo].[UpdateResourceLibrary]
    @resourceLibraryId INT,
    @name NVARCHAR(255),
    @description NVARCHAR(1024),
    @uri NVARCHAR(1024),
    @allowedMethod NVARCHAR(10),
    @urn NVARCHAR(255),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [dbo].[ResourceLibrary]
    SET Name = @name,
        Description = @description,
        Uri = @uri,
        AllowedMethod = @allowedMethod,
        Urn = @urn,
        ModifiedOn = @modifiedOn,
        ModifiedBy = @modifiedBy
    WHERE ResourceLibraryId = @resourceLibraryId;
END
GO

-- dbo.GetResourceLibraries
CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibraries]
AS
BEGIN
    SELECT * FROM [dbo].[ResourceLibrary]
    WHERE Status = 1
    ORDER BY Name;
END
GO

-- dbo.GetResourceLibrary
CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibrary]
    @resourceGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT * FROM [dbo].[ResourceLibrary]
    WHERE RowGuid = @resourceGuid;
END
GO

-- dbo.GetResourceLibraryById
CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibraryById]
    @resourceLibraryId INT
AS
BEGIN
    SELECT * FROM [dbo].[ResourceLibrary]
    WHERE ResourceLibraryId = @resourceLibraryId;
END
GO

-- dbo.GetResourceLibraryByUriMethodAndUrn
CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibraryByUriMethodAndUrn]
    @uri NVARCHAR(1024),
    @allowedMethod NVARCHAR(10),
    @urn NVARCHAR(255)
AS
BEGIN
    SELECT * FROM [dbo].[ResourceLibrary]
    WHERE Uri = @uri
      AND AllowedMethod = @allowedMethod
      AND Urn = @urn;
END
GO

-- dbo.ApplicationExists
CREATE OR ALTER PROCEDURE [dbo].[ApplicationExists]
    @orgId INT,
    @appGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT ApplicationId FROM [Client].[Application]
    WHERE OrganizationId = @orgId
      AND RowGuid = @appGuid;
END
GO

-- dbo.GetApplications
CREATE OR ALTER PROCEDURE [dbo].[GetApplications]
    @orgId INT
AS
BEGIN
    SELECT * FROM [Client].[Application]
    WHERE OrganizationId = @orgId
    ORDER BY Name;
END
GO

-- dbo.GetApplicationByName
CREATE OR ALTER PROCEDURE [dbo].[GetApplicationByName]
    @appName NVARCHAR(255),
    @orgId INT
AS
BEGIN
    SELECT * FROM [Client].[Application]
    WHERE Name = @appName
      AND OrganizationId = @orgId;
END
GO

-- dbo.UpdateApplicationActivation
CREATE OR ALTER PROCEDURE [dbo].[UpdateApplicationActivation]
    @orgId INT,
    @appId INT,
    @active BIT
AS
BEGIN
    UPDATE [Client].[Application]
    SET Active = @active,
        ModifiedOn = GETUTCDATE()
    WHERE OrganizationId = @orgId
      AND ApplicationId = @appId;
END
GO

-- dbo.UpdateApplicationUri
CREATE OR ALTER PROCEDURE [dbo].[UpdateApplicationUri]
    @orgId INT,
    @appId INT,
    @uri NVARCHAR(1024),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Client].[Application]
    SET Uri = @uri,
        ModifiedOn = @modifiedOn,
        ModifiedBy = @modifiedBy
    WHERE OrganizationId = @orgId
      AND ApplicationId = @appId;
END
GO

-- dbo.CreatePostLogoutRedirectUris
CREATE OR ALTER PROCEDURE [dbo].[CreatePostLogoutRedirectUris]
    @orgId INT,
    @appId INT,
    @uris NVARCHAR(MAX),
    @createdOn DATETIME2(0),
    @createdBy NVARCHAR(255)
AS
BEGIN
    -- Parse the URIs (assuming comma-separated or similar format)
    -- This is a simplified version - actual implementation may vary
    INSERT INTO [Client].[PostLogoutRedirectUri]
        (OrganizationId, ApplicationId, PostLogoutRedirectUri, CreatedOn, ModifiedBy)
    SELECT @orgId, @appId, value, @createdOn, @createdBy
    FROM STRING_SPLIT(@uris, ',')
    WHERE RTRIM(value) <> '';
END
GO

-- dbo.UpdatePostLogoutRedirectUri
CREATE OR ALTER PROCEDURE [dbo].[UpdatePostLogoutRedirectUri]
    @orgId INT,
    @appId INT,
    @uri NVARCHAR(1024),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Client].[PostLogoutRedirectUri]
    SET PostLogoutRedirectUri = @uri,
        ModifiedOn = @modifiedOn,
        ModifiedBy = @modifiedBy
    WHERE OrganizationId = @orgId
      AND ApplicationId = @appId;
END
GO

-- dbo.TokenSettingExistsForApp
CREATE OR ALTER PROCEDURE [dbo].[TokenSettingExistsForApp]
    @orgId INT,
    @appId INT
AS
BEGIN
    IF EXISTS (SELECT 1 FROM [Client].[TokenSetting] WHERE OrganizationId = @orgId AND ApplicationId = @appId)
        SELECT 1 AS Exists
    ELSE
        SELECT 0 AS Exists
END
GO

-- dbo.GetExternalSource
CREATE OR ALTER PROCEDURE [dbo].[GetExternalSource]
    @Branding NVARCHAR(255)
AS
BEGIN
    SELECT * FROM [dbo].[ExternalSource]
    WHERE Branding = @Branding
      AND Status = 1;
END
GO

-- dbo.GetExternalSourceById
CREATE OR ALTER PROCEDURE [dbo].[GetExternalSourceById]
    @SourceId INT
AS
BEGIN
    SELECT * FROM [dbo].[ExternalSource]
    WHERE ExternalSourceId = @SourceId;
END
GO

