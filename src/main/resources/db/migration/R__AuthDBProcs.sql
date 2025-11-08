SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [dbo].[GetAdminConfig]
AS
BEGIN
    SELECT (SELECT TOP 1 [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus') AS [AdminOrgId],
           (SELECT TOP 1 [ApplicationId]
            FROM [Client].[Application]
            WHERE [Name] = 'Admin Portal')                                                         AS [AdminPortalAppId],
           (SELECT TOP 1 [ClientId]
            FROM [Client].[Application]
            WHERE [Name] = 'Admin Portal')                                                         AS [AdminPortalClientId],
           (SELECT TOP 1 [OrganizationGroupId]
            FROM [Partner].[OrganizationGroup]
            WHERE [GroupName] = 'Ascensus Admin')                                                  AS [AdminGroupId],
           (SELECT TOP 1 [ProfileId]
            FROM [Person].[Profile]
            WHERE [FirstName] = 'Ascensus'
              AND [LastName] = 'Admin')                                                            AS [AdminProfileId]
END
GO


SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [Partner].[CreateAdminGroup] @OrganizationId INT,
                                                       @GroupName NVARCHAR(255)
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Partner].[OrganizationGroup]
            ( [OrganizationId]
            , [GroupName]
            , [Description])
            VALUES ( @OrganizationId
                   , @GroupName
                   , (SELECT TOP 1 t.[Description]
                      FROM [dbo].[GroupTemplate] t
                      WHERE t.[GroupName] = 'Admin'
                        AND t.[Status] = 1));
            DECLARE @ID INT;
            SET @ID = SCOPE_IDENTITY();

            INSERT INTO [Partner].[OrganizationGroupPermission]
            ( [OrganizationGroupId]
            , [OrganizationId]
            , [PermissionName]
            , [Description])
            SELECT @ID, @OrganizationId, p.[PermissionName], p.[Description]
            FROM [dbo].GroupPermissionTemplate p
                     INNER JOIN [dbo].[GroupTemplate] t ON p.GroupId = t.GroupTemplateId
            WHERE t.[GroupName] = 'Admin'
              AND t.[Status] = 1
              AND p.Status = 1;
        COMMIT;
    end try
    begin catch
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    end catch
end

GO

SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [Partner].[CreateOrganization] @Name NVARCHAR(255)
, @Note NVARCHAR(1024)
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Partner].[Organization]
            ( [Name]
            , [Note])
            VALUES ( @Name
                   , @Note);
            DECLARE @OrganizationId INT;
            SET @OrganizationId = SCOPE_IDENTITY();
            INSERT INTO [Partner].[OrganizationGroup]
            ( [OrganizationId]
            , [GroupName]
            , [Description])
            VALUES ( @OrganizationId
                   , @Name + ' Admin'
                   , (SELECT TOP 1 t.[Description]
                      FROM [dbo].[GroupTemplate] t
                      WHERE t.[GroupName] = 'Admin'
                        AND t.[Status] = 1));
            DECLARE @GroupId INT;
            SET @GroupId = SCOPE_IDENTITY()
            INSERT INTO [Partner].[OrganizationGroupPermission]
            ( [OrganizationGroupId]
            , [OrganizationId]
            , [PermissionName]
            , [Description])
            SELECT @GroupId, @OrganizationId, p.[PermissionName], p.[Description]
            FROM [dbo].GroupPermissionTemplate p
                     INNER JOIN [dbo].[GroupTemplate] t ON p.GroupId = t.GroupTemplateId
            WHERE t.[GroupName] = 'Admin'
              AND t.[Status] = 1
              AND p.Status = 1;
        COMMIT;
        SELECT @OrganizationId AS ID;
    end try
    begin catch
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    end catch
end

GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO


CREATE OR ALTER PROCEDURE [Partner].[GetOrganization] @OrganizationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT *
    FROM [Partner].[Organization] o
    WHERE o.RowGuid = @OrganizationGuid
end

GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationById] @OrganizationId INT
AS
BEGIN
    SELECT *
    FROM [Partner].[Organization] o
    WHERE o.OrganizationId = @OrganizationId
end

GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO


CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationByName] @Name NVARCHAR(255)
AS
BEGIN
    SELECT *
    FROM [Partner].[Organization] o
    WHERE o.Name = @Name
end
GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizations]
AS
BEGIN
    SELECT o.*
    FROM [Partner].[Organization] o
    ORDER BY o.Name
end

GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [Person].[AssignUserToGroup] @ProfileOrganizationId INT, @OrganizationGroupId INT
AS
INSERT INTO [Person].[ProfileOrganizationGroup] ([ProfileOrganizationId], [OrganizationGroupId])
VALUES (@ProfileOrganizationId, @OrganizationGroupId)

GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [Person].[CreateProfileOrganization] @OrganizationId INT, @ProfileId INT
AS
INSERT INTO [Person].[ProfileOrganization]
    ([ProfileId], [OrganizationId])
VALUES (@ProfileId, @OrganizationId)

GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

CREATE OR ALTER PROCEDURE [Person].[GetProfile] @ProfileId INT
AS
SELECT *
FROM [Person].[Profile] p
WHERE p.ProfileId = @ProfileId
GO

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO


CREATE OR ALTER PROCEDURE [Person].[GetProfileOrganization] @OrganizationId INT, @ProfileId INT
AS
SELECT *
FROM [Person].[ProfileOrganization] p
WHERE p.OrganizationId = @OrganizationId
  AND p.ProfileId = @ProfileId
GO


CREATE OR ALTER PROCEDURE [Partner].[OrganizationExists] @OrganizationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT OrganizationId
    FROM [Partner].[Organization] o
    WHERE o.RowGuid = @OrganizationGuid
end
GO

CREATE OR ALTER PROCEDURE [Client].[ApplicationExists] @OrganizationId INTEGER, @ApplicationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT ApplicationId
    FROM [Client].[Application] a
    WHERE a.RowGuid = @ApplicationGuid
      AND a.OrganizationId = @OrganizationId
end
GO

CREATE OR ALTER PROCEDURE [Client].[SecretExists] @SecretGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT SecretId
    FROM [Client].[Secret] s
    WHERE s.RowGuid = @SecretGuid
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplication] @ApplicationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT *
    FROM [Client].[Application] a
    WHERE a.RowGuid = @ApplicationGuid
end
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplicationById] @ApplicationId INT
AS
BEGIN
    SELECT *
    FROM [Client].[Application] a
    WHERE a.Applicationid = @ApplicationId
end
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplicationByName] @Name NVARCHAR(255)
AS
BEGIN
    SELECT *
    FROM [Client].[Application] a
    WHERE a.Name = @Name
end
GO


CREATE OR ALTER PROCEDURE [Client].[GetApplications] @OrganizationId INT
AS
BEGIN
    SELECT a.*
    FROM [Client].[Application] a
    WHERE a.OrganizationId = @OrganizationId
    ORDER BY a.Name
end
GO

CREATE OR ALTER PROCEDURE [Client].[CreateApplication] @OrganizationId INT, @Name NVARCHAR(255),
                                                       @Description NVARCHAR(255), @Uri NVARCHAR(1024),
                                                       @ApplicationTypeId INT, @AuthFLowId INT
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Client].[Application]
            ( [OrganizationId]
            , [ClientId]
            , [Name]
            , [Description]
            , [Uri]
            , [ApplicationTypeId]
            , [AuthFlowId])
            VALUES ( @OrganizationId
                   , SUBSTRING(REPLACE(CONVERT(VARCHAR(50), NEWID()), '-', ''), 0, 32)
                   , @Name
                   , @Description
                   , @Uri
                   , @ApplicationTypeId
                   , @AuthFLowId);
        COMMIT;
        DECLARE @ApplicationId INT;
        SET @ApplicationId = SCOPE_IDENTITY();
        SELECT @ApplicationId AS ID;
    end try
    begin catch
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    end catch
end

GO

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
           MAX(CASE WHEN [Code] = 'Auth Code Token' THEN [EnumId] END)  AS [AuthCodeTokenEnumId],
           MAX(CASE WHEN [Code] = 'Session Active' THEN [EnumId] END)   AS [AuthSessionActiveEnumId],
           MAX(CASE WHEN [Code] = 'Session Inactive' THEN [EnumId] END) AS [AuthSessionInactiveEnumId],
           MAX(CASE WHEN [Code] = 'self-contained' THEN [EnumId] END)   AS [SelfContainedAccessTokenFormatEnumId],
           MAX(CASE WHEN [Code] = 'Mr' THEN [EnumId] END)               AS [MrSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Mrs' THEN [EnumId] END)              AS [MrsSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Miss' THEN [EnumId] END)             AS [MissSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Sr' THEN [EnumId] END)               AS [SrSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Jr' THEN [EnumId] END)               AS [JrSuffixEnumId]
    FROM [dbo].[Enum]
END
GO


CREATE OR ALTER PROCEDURE [Client].[GetMfaExpiryPinTimeV2] @SessionId UNIQUEIDENTIFIER,
                                                           @PinTimeToLice INT
AS
BEGIN
    DECLARE @Pin NVARCHAR(512)=NULL,
        @issueTime DATETIME2,
        @ExpiryTime DATETIME2,
        @MillisTpExpiryTime INT,
        @utcTime datetime;
    SELECT @Pin = Pin
    FROM Token.AuthSession
    WHERE SessionId = @SessionId
    IF @Pin IS NOT NULL
        BEGIN
            SET @issueTime = CONVERT(datetime2, LTRIM(SUBSTRING(@Pin, CHARINDEX(',', @Pin) + 1, LEN(@Pin))));
            SET @ExpiryTime = DATEADD(s, @PinTimeToLice, @issueTime);
            SET @utcTime = GETUTCDATE();
            IF DATEDIFF(y, GETUTCDATE(), @issueTime) = 0 AND DATEDIFF(m, GETUTCDATE(), @issueTime) = 0 AND
               DATEDIFF(d, GETUTCDATE(), @issueTime) = 0
                BEGIN
                    SET @MillisTpExpiryTime = DATEDIFF(ms, @utcTime, @ExpiryTime);
                    SELECT @MillisTpExpiryTime TimeToEpireMs, @issueTime IssueTime, @ExpiryTime ExpiryTime
                END
            ELSE
                BEGIN
                    SELECT -1 TimeToEpireMs, @issueTime IssueTime, @ExpiryTime ExpiryTime
                END
        END
    ELSE
        BEGIN
            SELECT NULL TimeToEpireMs, NULL IssueTime, NULL ExpiryTime
        END
END

GO