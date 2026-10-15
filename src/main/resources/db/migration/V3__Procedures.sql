-- Canonical versioned procedure contracts. All timestamps are UTC.
CREATE OR ALTER PROCEDURE [dbo].[GetAdminConfig]
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT (SELECT TOP 1 [OrganizationId]
            FROM [Partner].[Organization]
            WHERE [Name] = 'Ascensus')            AS [AdminOrgId],

           (SELECT TOP 1 [ApplicationId]
            FROM [Client].[Application]
            WHERE [Name] = 'Admin Portal')        AS [AdminPortalAppId],

           (SELECT TOP 1 [ClientId]
            FROM [Client].[Application]
            WHERE [Name] = 'Admin Portal')        AS [AdminPortalClientId],

           (SELECT TOP 1 [OrganizationGroupId]
            FROM [Partner].[OrganizationGroup]
            WHERE [GroupName] = 'Ascensus Admin') AS [AdminGroupId],

           (SELECT TOP 1 [ProfileId]
            FROM [Person].[Profile]
            WHERE [FirstName] = 'Ascensus'
              AND [LastName] = 'Admin')           AS [AdminProfileId]
END
GO

CREATE OR ALTER PROCEDURE [Partner].[CreateOrganization] @Name NVARCHAR(255),
                                                         @Note NVARCHAR(1024)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Partner].[Organization] ([Name],
                                                  [Note])
            VALUES (@Name,
                    @Note);

            DECLARE @OrganizationId INT;
            SET @OrganizationId = SCOPE_IDENTITY();

            -- Insert into OrganizationGroup
            INSERT INTO [Partner].[OrganizationGroup] ([OrganizationId],
                                                       [GroupTemplateId],
                                                       [GroupName],
                                                       [Description])
            SELECT @OrganizationId,
                   [GroupTemplateId],
                   [GroupName],
                   [Description]
            FROM [dbo].[GroupTemplate] t
            WHERE t.Status = 1;

            -- Insert into OrganizationGroupPermission
            INSERT INTO [Partner].[OrganizationGroupPermission] ([OrganizationGroupId],
                                                                 [OrganizationId],
                                                                 [PermissionName],
                                                                 [PermissionKey],
                                                                 [Description])
            SELECT t.[OrganizationGroupId],
                   @OrganizationId,
                   p.[PermissionName],
                   p.[PermissionKey],
                   p.[Description]
            FROM [dbo].[GroupPermissionTemplate] p
                     INNER JOIN [Partner].[OrganizationGroup] t
                                ON p.[GroupId] = t.[GroupTemplateId]
            WHERE p.Status = 1
              AND t.[Status] = 1
              AND t.[OrganizationId] = @OrganizationId
              AND p.[GroupId] IS NOT NULL;
        COMMIT;

        SELECT @OrganizationId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganization] @OrganizationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Partner].[Organization] o
    WHERE o.RowGuid = @OrganizationGuid
end
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationById] @OrganizationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Partner].[Organization] o
    WHERE o.OrganizationId = @OrganizationId
end
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationByName] @Name NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Partner].[Organization] o
    WHERE o.Name = @Name
end
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizations]
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT o.*
    FROM [Partner].[Organization] o
    ORDER BY o.Name
end
GO

CREATE OR ALTER PROCEDURE [Person].[CreateProfileOrganization] @OrganizationId INT, @ProfileId INT
AS
INSERT INTO [Person].[ProfileOrganization]
    ([ProfileId], [OrganizationId])
VALUES (@ProfileId, @OrganizationId)
GO

CREATE OR ALTER PROCEDURE [Person].[GetProfile] @ProfileGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT ProfileId, CONCAT(FirstName, ' ', LastName) AS Name, Email, PhoneNumber FROM Person.Profile WHERE RowGuid=@ProfileGuid;
END
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
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT OrganizationId
    FROM [Partner].[Organization] o
    WHERE o.RowGuid = @OrganizationGuid
end
GO

CREATE OR ALTER PROCEDURE [Client].[ApplicationExists] @OrganizationId INTEGER,
                                                       @ApplicationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT ApplicationId
    FROM [Client].[Application] a
    WHERE a.RowGuid = @ApplicationGuid
      AND a.OrganizationId = @OrganizationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[SecretExists] @OrganizationId INT, @ApplicationId INT, @SecretGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT SecretId FROM Client.Secret WHERE OrganizationId=@OrganizationId AND ApplicationId=@ApplicationId AND RowGuid=@SecretGuid;
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplication] @ApplicationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Application] a
    WHERE a.RowGuid = @ApplicationGuid
end
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplicationById] @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Application] a
    WHERE a.Applicationid = @ApplicationId
end
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplicationByName] @Name NVARCHAR(255),
                                                          @orgId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Application] a
    WHERE a.Name = @Name
      AND a.OrganizationId = @orgId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplications] @OrganizationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT a.*
    FROM [Client].[Application] a
    WHERE a.OrganizationId = @OrganizationId
      AND a.Active > 0
    ORDER BY a.Name
END
GO

CREATE OR ALTER PROCEDURE [Client].[CreateApplication] @OrganizationId INT,
                                                       @Name NVARCHAR(255),
                                                       @Description NVARCHAR(255),
                                                       @Uri NVARCHAR(1024),
                                                       @ApplicationTypeId INT,
                                                       @AuthFlowId INT,
                                                       @JWKSetUrl NVARCHAR(255),
                                                       @RequirePKCE BIT,
                                                       @JWSAlgorithmId INT,
                                                       @AuthCodeTimeToLive INT,
                                                       @AccessTokenTimeToLive INT,
                                                       @RefreshTokenTimeToLive INT,
                                                       @ReuseRefreshTokens BIT,
                                                       @AccessTokenFormatId INT,
                                                       @DeviceCodeTimeToLive INT,
                                                       @MaxRequestTransitTime INT,
                                                       @UsernameType INTEGER,
                                                       @AllowForgotUsername BIT,
                                                       @ForgotUserNameParam ForgetUserNameType READONLY,
                                                       @PinTimeToLive INT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY

        IF @PinTimeToLive IS NULL
            BEGIN
                SELECT @PinTimeToLive = Value
                FROM GlobalConfig gc
                WHERE gc.Name = 'PinTimeToLive';
            END

        BEGIN TRANSACTION
            INSERT INTO [Client].[Application]
            ([OrganizationId],
             [ClientId],
             [Name],
             [Description],
             [Uri],
             [ApplicationTypeId],
             [AuthFlowId],
             [UsernameType],
             [AllowForgotUsername],
             [PinTimeToLive])
            VALUES (@OrganizationId,
                    SUBSTRING(REPLACE(CONVERT(VARCHAR(50), NEWID()), '-', ''), 1, 32),
                    @Name,
                    @Description,
                    @Uri,
                    @ApplicationTypeId,
                    @AuthFlowId,
                    @UsernameType,
                    @AllowForgotUsername,
                    @PinTimeToLive);

            DECLARE @ApplicationId INT;
            SET @ApplicationId = SCOPE_IDENTITY();

            INSERT INTO [Client].[Setting]
            ([OrganizationId],
             [ApplicationId],
             [JWKSetUrl],
             [RequirePKCE],
             [JWSAlgorithmId])
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @JWKSetUrl,
                    @RequirePKCE,
                    @JWSAlgorithmId);

            INSERT INTO [Client].[TokenSetting]
            ([OrganizationId],
             [ApplicationId],
             [AuthCodeTimeToLive],
             [AccessTokenTimeToLive],
             [RefreshTokenTimeToLive],
             [ReuseRefreshTokens],
             [AccessTokenFormatId],
             [DeviceCodeTimeToLive],
             [MaxRequestTransitTime])
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @AuthCodeTimeToLive,
                    @AccessTokenTimeToLive,
                    @RefreshTokenTimeToLive,
                    @ReuseRefreshTokens,
                    @AccessTokenFormatId,
                    @DeviceCodeTimeToLive,
                    @MaxRequestTransitTime);

            INSERT INTO [Client].[ForgetUserNameSetting]
                (OrganizationId, ApplicationId, ParamPriority, ParamJson)
            SELECT OrganizationId,
                   @ApplicationId,
                   ParamPriority,
                   ParamJson
            FROM @ForgotUserNameParam;
        COMMIT;

        SELECT @ApplicationId AS ID;

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdateApplicationUri] @OrganizationId INT,
                                                          @ApplicationId INT,
                                                          @Uri NVARCHAR(1024),
                                                          @ModifiedOn DATETIME2(0) = NULL,
                                                          @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Client].[Application]
            SET [Uri]        = @Uri,
                [ModifiedOn] = @ModifiedOn,
                [ModifiedBy] = @ModifiedBy
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdateApplication] @OrganizationId INT,
                                                       @ApplicationId INT,
                                                       @name NVARCHAR(255),
                                                       @description NVARCHAR(1024) = NULL,
                                                       @ModifiedOn DATETIME2(0) = NULL,
                                                       @ModifiedBy NVARCHAR(255) = NULL,
                                                       @AllowForgotUsername BIT,
                                                       @UsernameType INT,
                                                       @ForgotUserNameParam ForgetUserNameType READONLY,
                                                       @PinTimeToLive INT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Client].[Application]
            SET [Name]                = @name,
                [Description]         = ISNULL(@description, [Description]),
                [ModifiedOn]          = @ModifiedOn,
                [ModifiedBy]          = @ModifiedBy,
                [UsernameType]        = @UsernameType,
                [AllowForgotUsername] = @AllowForgotUsername,
                [PinTimeToLive]       = ISNULL(@PinTimeToLive, [PinTimeToLive])
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId;

            DELETE [Client].[ForgetUserNameSetting]
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId;

            INSERT INTO [Client].[ForgetUserNameSetting]
            (OrganizationId,
             ApplicationId,
             ParamPriority,
             ParamJson)
            SELECT @OrganizationId,
                   @ApplicationId,
                   ParamPriority,
                   ParamJson
            FROM @ForgotUserNameParam;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplicationByClientId] @ClientId NVARCHAR(1024)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Application] a
    WHERE a.ClientId = @ClientId
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdateApplicationActivation] @OrganizationId INT,
                                                                 @ApplicationId INT,
                                                                 @active BIT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    UPDATE [Client].[Application]
    SET Active = @active
    WHERE [OrganizationId] = @OrganizationId
      AND [ApplicationId] = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetSettingsByApplicationId] @ApplicationId INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Setting] s
    WHERE s.ApplicationId = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetRedirectUrisByApplicationId] @ApplicationId INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT r.RedirectUri
    FROM [Client].[RedirectUri] r
    WHERE r.ApplicationId = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetPostLogoutRedirectUrisByApplicationId] @ApplicationId INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT r.PostLogoutRedirectUri
    FROM [Client].[PostLogoutRedirectUri] r
    WHERE r.ApplicationId = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Resource].[GetResourceById] @resourceId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Resource].[Resource] r
    WHERE r.ResourceId = @resourceId
END
GO

CREATE OR ALTER PROCEDURE [Resource].[GetResource] @OrganizationId INT,
                                                   @ApplicationId INT,
                                                   @ResourceLibraryGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Resource].[Resource] r
             JOIN [dbo].[ResourceLibrary] dr
                  ON dr.ResourceLibraryId = r.ResourceLibraryId
    WHERE dr.RowGuid = @ResourceLibraryGuid
      AND r.ApplicationId = @ApplicationId
      AND r.OrganizationId = @OrganizationId
END
GO

CREATE OR ALTER PROCEDURE [Resource].[DeleteResource] @ResourceId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DELETE
    FROM [Resource].[Resource]
    WHERE ResourceId = @ResourceId
END
GO

CREATE OR ALTER PROCEDURE [Resource].[GetResourceByResourceLibraryId] @OrganizationId INT,
                                                                      @ApplicationId INT,
                                                                      @ResourceLibraryId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Resource].[Resource] r
    WHERE r.OrganizationId = @OrganizationId
      AND r.ApplicationId = @ApplicationId
      AND r.ResourceLibraryId = @ResourceLibraryId
END
GO

CREATE OR ALTER PROCEDURE [Resource].[getAllResourcesByClientId] @ClientId NCHAR(32)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT rr.*,
           dr.Name,
           dr.Description,
           dr.Uri,
           dr.AllowedMethod,
           dr.Urn,
           dr.RowGuid AS ResourceLibraryGuid
    FROM [Client].[Application] ap
             JOIN [Resource].[Resource] rr
                  ON rr.ApplicationId = ap.ApplicationId
             JOIN [dbo].[ResourceLibrary] dr
                  ON dr.ResourceLibraryId = rr.ResourceLibraryId
    WHERE ap.ClientId = @ClientId
END
GO

CREATE OR ALTER PROCEDURE [Resource].[getAllResourcesByAppId] @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT rr.*,
           dr.Name,
           dr.Description,
           dr.Uri,
           dr.AllowedMethod,
           dr.Urn,
           dr.RowGuid AS ResourceLibraryGuid
    FROM [Client].[Application] ap
             JOIN [Resource].[Resource] rr
                  ON rr.ApplicationId = ap.ApplicationId
             JOIN [dbo].[ResourceLibrary] dr
                  ON dr.ResourceLibraryId = rr.ResourceLibraryId
    WHERE ap.ApplicationId = @ApplicationId
    ORDER BY dr.Name
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetMfaExpiryPinTimeV2] @SessionId UNIQUEIDENTIFIER,@PinTimeToLive INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
DECLARE @Pin NVARCHAR(512),@Issued DATETIME2(7),@Expires DATETIME2(7);
SELECT @Pin=Pin FROM Token.AuthSession WHERE SessionId=@SessionId;
SET @Issued=TRY_CONVERT(DATETIME2(7),SUBSTRING(@Pin,CHARINDEX(',',@Pin)+1,LEN(@Pin)));
SET @Expires=DATEADD(SECOND,@PinTimeToLive,@Issued);
SELECT DATEDIFF_BIG(MILLISECOND,SYSUTCDATETIME(),@Expires) AS TimeToExpireMS,@Issued AS IssueTime,@Expires AS ExpiryTime;
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdatePostLogoutRedirectUri] @OrganizationId INT,
                                                                 @ApplicationId INT,
                                                                 @PostLogoutRedirectUri NVARCHAR(1024), @ModifiedOn DATETIME2(7)=NULL, @ModifiedBy NVARCHAR(255)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Client].[PostLogoutRedirectUri]
            SET [PostLogoutRedirectUri] = @PostLogoutRedirectUri, ModifiedOn=@ModifiedOn, ModifiedBy=@ModifiedBy
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[DeletePostLogoutRedirectUri] @OrganizationId INT,
                                                                 @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            DELETE
            FROM [Client].[PostLogoutRedirectUri]
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[CreatePostLogoutRedirectUri] @OrganizationId INT,
                                                                 @ApplicationId INT,
                                                                 @PostLogoutRedirectUri NVARCHAR(2000),
                                                                 @ModifiedOn DATETIME2(0) = NULL,
                                                                 @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Client].[PostLogoutRedirectUri]
            (OrganizationId,
             ApplicationId,
             PostLogoutRedirectUri,
             ModifiedOn,
             ModifiedBy)
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @PostLogoutRedirectUri,
                    @ModifiedOn,
                    @ModifiedBy);
        COMMIT;


    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdateRedirectUri] @OrganizationId INT,
                                                       @ApplicationId INT,
                                                       @RedirectUri NVARCHAR(2000)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Client].[RedirectUri]
            SET [RedirectUri] = @RedirectUri
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[DeleteRedirectUri] @OrganizationId INT,
                                                       @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            DELETE
            FROM [Client].[RedirectUri]
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[CreateRedirectUri] @OrganizationId INT,
                                                       @ApplicationId INT,
                                                       @RedirectUri NVARCHAR(2000),
                                                       @ModifiedOn DATETIME2(0) = NULL,
                                                       @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Client].[RedirectUri]
            (OrganizationId,
             ApplicationId,
             RedirectUri,
             ModifiedOn,
             ModifiedBy)
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @RedirectUri,
                    @ModifiedOn,
                    @ModifiedBy);
        COMMIT;


    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[CreateTokenSetting] @OrganizationId INT,
                                                        @ApplicationId INT,
                                                        @AuthCodeTimeToLive INT,
                                                        @AccessTokenTimeToLive INT,
                                                        @RefreshTokenTimeToLive INT,
                                                        @ReuseRefreshTokens BIT,
                                                        @AccessTokenFormatId INT,
                                                        @DeviceCodeTimeToLive INT = 0,
                                                        @MaxRequestTransitTime INT = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Client].[TokenSetting]
            ([OrganizationId],
             [ApplicationId],
             [AuthCodeTimeToLive],
             [AccessTokenTimeToLive],
             [RefreshTokenTimeToLive],
             [ReuseRefreshTokens],
             [AccessTokenFormatId],
             [DeviceCodeTimeToLive],
             [MaxRequestTransitTime])
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @AuthCodeTimeToLive,
                    @AccessTokenTimeToLive,
                    @RefreshTokenTimeToLive,
                    @ReuseRefreshTokens,
                    @AccessTokenFormatId,
                    @DeviceCodeTimeToLive,
                    @MaxRequestTransitTime);
        COMMIT;

        DECLARE @TokenSettingId INT;
        SET @TokenSettingId = SCOPE_IDENTITY();

        SELECT @TokenSettingId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdateTokenSetting] @OrganizationId INT,
                                                        @ApplicationId INT,
                                                        @AuthCodeTimeToLive INT,
                                                        @AccessTokenTimeToLive INT,
                                                        @RefreshTokenTimeToLive INT,
                                                        @ReuseRefreshTokens BIT,
                                                        @DeviceCodeTimeToLive INT = NULL,
                                                        @MaxRequestTransitTime INT = 1,
                                                        @ModifiedOn DATETIME2(0) = NULL,
                                                        @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Client].[TokenSetting]
            SET [AuthCodeTimeToLive]     = @AuthCodeTimeToLive,
                [AccessTokenTimeToLive]  = @AccessTokenTimeToLive,
                [RefreshTokenTimeToLive] = @RefreshTokenTimeToLive,
                [ReuseRefreshTokens]     = @ReuseRefreshTokens,
                [DeviceCodeTimeToLive]   = ISNULL(@DeviceCodeTimeToLive, [DeviceCodeTimeToLive]),
                [MaxRequestTransitTime]  = @MaxRequestTransitTime,
                [ModifiedOn]             = @ModifiedOn,
                [ModifiedBy]             = @ModifiedBy
            WHERE [OrganizationId] = @OrganizationId
              AND [ApplicationId] = @ApplicationId;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetTokenSettingForApp] @OrganizationId INT,
                                                           @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[TokenSetting] a
    WHERE a.OrganizationId = @OrganizationId
      AND a.ApplicationId = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetTokenSettingById] @TokenSettingId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[TokenSetting] a
    WHERE a.TokenSettingId = @TokenSettingId
END
GO

CREATE OR ALTER PROCEDURE [Client].[TokenSettingExistsForApp] @OrganizationId INTEGER,
                                                              @ApplicationId INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT TokenSettingId
    FROM [Client].[TokenSetting] ts
    WHERE ts.ApplicationId = @ApplicationId
      AND ts.OrganizationId = @OrganizationId
END
GO

CREATE OR ALTER PROCEDURE [Partner].[SaveCertificate] @OrgGuid UNIQUEIDENTIFIER,
                                                      @CertificateName VARCHAR(255),
                                                      @CertificateTypeId INT,
                                                      @IsX509Certificate BIT,
                                                      @KeyStoreBytes VARBINARY(MAX),
                                                      @Status TINYINT,
                                                      @Fingerprint NVARCHAR(1024),
                                                      @Thumbprint NVARCHAR(1024),
                                                      @Subject NVARCHAR(1024),
                                                      @Issuer NVARCHAR(1024),
                                                      @ValidFrom DATETIME2(0),
                                                      @ValidTo DATETIME2(0),
                                                      @PasswordKeyStoreBytes VARBINARY(MAX),
                                                      @PasswordKeyId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

        DECLARE @KeyStorePasswordId INT;
        DECLARE @OrgId INT;

        EXEC [Partner].[SavePasswordKeyStore]
             @PasswordKeyStoreBytes,
             @PasswordKeyId,
             @KeyStorePasswordId OUT;

        EXEC [Partner].[OrgGuidToId]
             @OrgGuid,
             @OrgId OUT;

        INSERT INTO [Partner].[Certificate]
        (OrganizationId,
         CertificateName,
         CertificateTypeId,
         IsX509Certificate,
         KeyStore,
         PasswordKeyId,
         Status,
         Fingerprint,
         ValidFrom,
         ValidTo,
         Subject,
         Issuer,
         Thumbprint)
        VALUES (@OrgId,
                @CertificateName,
                @CertificateTypeId,
                @IsX509Certificate,
                @KeyStoreBytes,
                @PasswordKeyId,
                @Status,
                @Fingerprint,
                @ValidFrom,
                @ValidTo,
                @Subject,
                @Issuer,
                @Thumbprint);

        SELECT *
        FROM [Partner].[Certificate]
        WHERE CertificateId = SCOPE_IDENTITY();
        COMMIT;
    END TRY
    BEGIN CATCH
        ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetCertificatesByOrgId] @OrganizationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.*, ks.KeyStore AS PasswordKeyStore
    FROM [Partner].[Certificate] c,
         [Partner].[KeyStorePassword] ks
    WHERE c.PasswordKeyId = ks.PasswordKeyId
      AND c.OrganizationId = @OrganizationId
      AND c.Status > 0
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetCertificate] @OrganizationId INT,
                                                     @CertificationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.*, ks.KeyStore AS PasswordKeyStore
    FROM [Partner].[Certificate] c,
         [Partner].[KeyStorePassword] ks
    WHERE c.PasswordKeyId = ks.PasswordKeyId
      AND c.OrganizationId = @OrganizationId
      AND c.RowGuid = @CertificationGuid
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetCertificatesByClientIdAndCertTypeId] @ClientId NVARCHAR(1024),
                                                                             @CertificateTypeId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.*, ks.KeyStore AS PasswordKeyStore
    FROM [Partner].[Certificate] c,
         [Partner].[KeyStorePassword] ks,
         [Client].[Application] a,
         [Client].[Credential] cr
    WHERE c.PasswordKeyId = ks.PasswordKeyId
      AND a.ApplicationId = cr.ApplicationId
      AND c.CertificateId = cr.CertificateId
      AND c.CertificateTypeId = @CertificateTypeId
      AND a.ClientId = @ClientId
      AND cr.CredentialStatus > 0
    ORDER BY c.CertificateName
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetCertificateById] @OrganizationId INT,
                                                         @CertificateId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.*, ks.KeyStore AS PasswordKeyStore
    FROM [Partner].[Certificate] c
             INNER JOIN [Partner].[KeyStorePassword] ks
                        ON c.PasswordKeyId = ks.PasswordKeyId
    WHERE c.OrganizationId = @OrganizationId
      AND c.CertificateId = @CertificateId
END
GO

CREATE OR ALTER PROCEDURE [Partner].[UpdateCertificateStatus] @OrganizationId INT,
                                                              @CertId UNIQUEIDENTIFIER,
                                                              @Status TINYINT,
                                                              @ModifiedOn DATETIME2(0) = NULL,
                                                              @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Partner].[Certificate]
            SET [Status]     = @Status,
                [ModifiedOn] = @ModifiedOn,
                [ModifiedBy] = @ModifiedBy
            WHERE OrganizationId = @OrganizationId
              AND RowGuid = @CertId;
        COMMIT;

        SELECT [CertificateId] AS ID
        FROM [Partner].[Certificate]
        WHERE RowGuid = @CertId;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[CreateCredential] @OrganizationId INTEGER,
                                                      @ApplicationId INTEGER,
                                                      @Name NVARCHAR(1024),
                                                      @SecretId INTEGER,
                                                      @CertificateId INTEGER,
                                                      @AlgorithmId INTEGER,
                                                      @Expiration DATETIME2(0),
                                                      @AuthFlowId INTEGER,
                                                      @Fingerprint NVARCHAR(1024),
                                                      @CredentialStatus INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        INSERT INTO [Client].[Credential] (OrganizationId,
                                           ApplicationId,
                                           Name,
                                           SecretId,
                                           CertificateId,
                                           TokenAlgorithmId,
                                           AuthFlowId,
                                           Fingerprint,
                                           CredentialStatus,
                                           ExpireOn)
        VALUES (@OrganizationId,
                @ApplicationId,
                @Name,
                @SecretId,
                @CertificateId,
                @AlgorithmId,
                @AuthFlowId,
                @Fingerprint,
                @CredentialStatus,
                @Expiration);

        SELECT *
        FROM [Client].[Credential]
        WHERE CredentialId = SCOPE_IDENTITY();
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetActiveCredentials] @ApplicationId INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Credential]
    WHERE ApplicationId = @ApplicationId
      AND CredentialStatus = (SELECT EnumId
                              FROM [dbo].[Enum]
                              WHERE EnumTypeId = (SELECT EnumTypeId
                                                  FROM [dbo].[EnumType]
                                                  WHERE Name = 'CredentialStatus')
                                AND Code = 'Active');
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetCredentials] @OrganizationId INTEGER,
                                                    @ApplicationId INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Credential]
    WHERE ApplicationId = @ApplicationId
      AND OrganizationId = @OrganizationId;
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetCredentialsByAuthFlow] @OrganizationId INTEGER,
                                                              @ApplicationId INTEGER,
                                                              @AuthFlowId INTEGER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Credential]
    WHERE ApplicationId = @ApplicationId
      AND OrganizationId = @OrganizationId
      AND AuthFlowId = @AuthFlowId
      AND CredentialStatus = (SELECT EnumId
                              FROM [dbo].[Enum]
                              WHERE EnumTypeId = (SELECT EnumTypeId
                                                  FROM [dbo].[EnumType]
                                                  WHERE Name = 'CredentialStatus')
                                AND Code = 'Active');
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetCredentialByGuid] @OrganizationId INTEGER,
                                                         @ApplicationId INTEGER,
                                                         @CredentialGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[Credential]
    WHERE ApplicationId = @ApplicationId
      AND OrganizationId = @OrganizationId
      AND RowGuid = @CredentialGuid;
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdateCredentialStatus] @OrganizationId INT,
                                                            @ApplicationId INT,
                                                            @CredentialGuid UNIQUEIDENTIFIER,
                                                            @Status INT,
                                                            @ModifiedOn DATETIME2(0) = NULL,
                                                            @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Client].[Credential]
            SET [CredentialStatus] = @Status,
                [ModifiedOn]       = @ModifiedOn,
                [ModifiedBy]       = @ModifiedBy
            WHERE [RowGuid] = @CredentialGuid;
        COMMIT;

        SELECT [CredentialId] AS ID
        FROM [Client].[Credential]
        WHERE [RowGuid] = @CredentialGuid;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[CreateSecret] @OrganizationId INTEGER,
                                                  @ApplicationId INTEGER,
                                                  @Description NVARCHAR(1024),
                                                  @SecretHash VARBINARY(MAX),
                                                  @Expiration DATETIME2(0),
                                                  @MainKeyStore VARBINARY(MAX),
                                                  @PasswordKeyStore VARBINARY(MAX),
                                                  @PasswordKeyId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            DECLARE @KeyStorePasswordId INT;

            EXEC [Partner].[SavePasswordKeyStore]
                 @PasswordKeyStore,
                 @PasswordKeyId,
                 @KeyStorePasswordId OUT;

            INSERT INTO [Client].[Secret] (OrganizationId,
                                           ApplicationId,
                                           Description,
                                           SecretHashValue,
                                           KeyStore,
                                           PasswordKeyId,
                                           ExpireOn)
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @Description,
                    @SecretHash,
                    @MainKeyStore,
                    @PasswordKeyId,
                    @Expiration);

            SELECT *
            FROM [Client].[Secret]
            WHERE SecretId = SCOPE_IDENTITY();
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetSecretById] @SecretId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT s.*,
           ks.KeyStore AS PasswordKeyStore
    FROM [Client].[Secret] s
             INNER JOIN [Partner].[KeyStorePassword] ks
                        ON s.PasswordKeyId = ks.PasswordKeyId
    WHERE s.SecretId = @SecretId;
END
GO

CREATE OR ALTER PROCEDURE [Client].[DeleteSecret] @SecretId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DELETE
    FROM [Client].[Secret]
    WHERE SecretId = @SecretId;
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetExternalSource] @Branding NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT es.SourceId,
           es.SourceCode,
           es.SyncFlag,
           et.ExternalTypeId,
           et.ExternalTypeName,
           et.ForgetUserSchema,
           et.SyncUserSchema
    FROM [dbo].[ExternalSource] es
             INNER JOIN [dbo].[ExternalType] et
                        ON es.ExternalTypeId = et.ExternalTypeId
    WHERE es.SourceCode IN (SELECT value
                            FROM string_split(@Branding, ','));
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetExternalSourceById] @SourceIds NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT es.SourceId,
           es.SourceCode,
           es.SyncFlag,
           et.ExternalTypeId,
           et.ExternalTypeName,
           et.ForgetUserSchema,
           et.SyncUserSchema
    FROM [dbo].[ExternalSource] es
             INNER JOIN [dbo].[ExternalType] et
                        ON es.ExternalTypeId = et.ExternalTypeId
    WHERE es.SourceId = @SourceIds;
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetEnumsByType] @enumTypeName NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT enum.EnumId,
           enum.Code,
           enum.Description
    FROM [dbo].[EnumType] eType
             JOIN [dbo].[Enum] enum
                  ON eType.EnumTypeId = enum.EnumTypeId
    WHERE eType.Name = @enumTypeName;
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetGlobalConfig]
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [dbo].[GlobalConfig] r
    ORDER BY r.Name;
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroup] @OrganizationId INT,
                                                           @GroupGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT og.*
    FROM [Partner].[OrganizationGroup] og
    WHERE og.OrganizationId = @OrganizationId
      AND og.RowGuid = @GroupGuid
END
GO

CREATE OR ALTER PROCEDURE [Partner].[UpdateOrganizationPrimaryContact] @OrganizationId INT,
                                                                       @PrimaryContactName NVARCHAR(255),
                                                                       @PrimaryContactEmail NVARCHAR(255),
                                                                       @PrimaryContactPhoneNumber NVARCHAR(128),
                                                                       @ModifiedOn DATETIME2(0) = NULL,
                                                                       @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Partner].[Organization]
            SET PrimaryContactName        = @PrimaryContactName,
                PrimaryContactEmail       = @PrimaryContactEmail,
                PrimaryContactPhoneNumber = @PrimaryContactPhoneNumber,
                ModifiedOn                = @ModifiedOn,
                ModifiedBy                = @ModifiedBy
            WHERE OrganizationId = @OrganizationId
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Partner].[UpdateOrganizationSecondaryContact] @OrganizationId INT,
                                                                         @SecondaryContactName NVARCHAR(255),
                                                                         @SecondaryContactEmail NVARCHAR(255),
                                                                         @SecondaryContactPhoneNumber NVARCHAR(128),
                                                                         @ModifiedOn DATETIME2(0) = NULL,
                                                                         @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Partner].[Organization]
            SET SecondaryContactName        = @SecondaryContactName,
                SecondaryContactEmail       = @SecondaryContactEmail,
                SecondaryContactPhoneNumber = @SecondaryContactPhoneNumber,
                ModifiedOn                  = @ModifiedOn,
                ModifiedBy                  = @ModifiedBy
            WHERE OrganizationId = @OrganizationId
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Partner].[UpdateOrganization] @OrganizationId INT,
                                                         @Name NVARCHAR(255),
                                                         @Desc NVARCHAR(1024),
                                                         @Status TINYINT,
                                                         @ModifiedOn DATETIME2(0) = NULL,
                                                         @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Partner].[Organization]
            SET [Name]       = @Name,
                [Note]       = @Desc,
                [Status]     = @Status,
                [ModifiedOn] = @ModifiedOn,
                [ModifiedBy] = @ModifiedBy
            WHERE [OrganizationId] = @OrganizationId;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroups] @OrganizationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT og.*
    FROM [Partner].[OrganizationGroup] og
    WHERE og.OrganizationId = @OrganizationId
      AND og.Status = 1
    ORDER BY og.GroupName;
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroupPermissions] @OrganizationId INT,
                                                                      @OrganizationGroupId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT ogp.*
    FROM [Partner].[OrganizationGroupPermission] ogp
    WHERE ogp.OrganizationId = @OrganizationId
      AND ogp.OrganizationGroupId = @OrganizationGroupId
      AND ogp.Status = 1
    ORDER BY ogp.PermissionName;
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetRanges]
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [dbo].[Range] r
    ORDER BY r.Name;
END
GO

CREATE OR ALTER PROCEDURE [dbo].[UpdateRanges] @AuthCodeTimeToLive INT,
                                               @AccessTokenTimeToLive INT,
                                               @DeviceCodeTimeToLive INT,
                                               @RefreshTokenTimeToLive INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [dbo].[Range]
            SET [Max] = @AuthCodeTimeToLive
            WHERE [Name] = N'AuthCodeTimeToLive';

            UPDATE [dbo].[Range]
            SET [Max] = @AccessTokenTimeToLive
            WHERE [Name] = N'AccessTokenTimeToLive';

            UPDATE [dbo].[Range]
            SET [Max] = @DeviceCodeTimeToLive
            WHERE [Name] = N'DeviceCodeTimeToLive';

            UPDATE [dbo].[Range]
            SET [Max] = @RefreshTokenTimeToLive
            WHERE [Name] = N'RefreshTokenTimeToLive';
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [dbo].[UpdateResourceLibrary] @ResourceLibraryId INT,
                                                        @Name NVARCHAR(255),
                                                        @Description NVARCHAR(1024),
                                                        @Uri NVARCHAR(2048),
                                                        @AllowedMethod NVARCHAR(128),
                                                        @Urn NVARCHAR(255),
                                                        @ModifiedOn DATETIME2(0) = NULL,
                                                        @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [dbo].[ResourceLibrary]
            SET [Name]          = @Name,
                [Description]   = @Description,
                [Uri]           = @Uri,
                [AllowedMethod] = @AllowedMethod,
                [Urn]           = @Urn,
                [ModifiedOn]    = @ModifiedOn,
                [ModifiedBy]    = @ModifiedBy
            WHERE [ResourceLibraryId] = @ResourceLibraryId
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibraryById] @resourceLibraryId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT rl.*
    FROM [dbo].[ResourceLibrary] rl
    WHERE rl.ResourceLibraryId = @resourceLibraryId
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibraryByUriMethodAndUrn] @Uri NVARCHAR(2048),
                                                                      @AllowedMethod NVARCHAR(128),
                                                                      @Urn NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT ResourceLibraryId
    FROM [dbo].[ResourceLibrary] rl
    WHERE rl.Uri = @Uri
      AND rl.AllowedMethod = @AllowedMethod
      AND ((@Urn IS NULL AND rl.Urn IS NULL) OR rl.Urn = @Urn)
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibraries]
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [dbo].[ResourceLibrary] rl
    ORDER BY rl.Name
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibrary] @resourceLibraryGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [dbo].[ResourceLibrary] rl
    WHERE rl.RowGuid = @resourceLibraryGuid
END
GO

CREATE OR ALTER PROCEDURE [Resource].[CreateResource] @OrganizationId INT,
                                                      @ApplicationId INT,
                                                      @ResourceLibraryId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Resource].[Resource]
            ([OrganizationId],
             [ApplicationId],
             [ResourceLibraryId])
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @ResourceLibraryId)

            DECLARE @ResourceId INT;
            SET @ResourceId = SCOPE_IDENTITY();
        COMMIT;

        SELECT @ResourceId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [dbo].[CreateResourceLibrary] @Name nvarchar(255),
                                                        @Description nvarchar(1024),
                                                        @Uri nvarchar(2048),
                                                        @AllowedMethod nvarchar(128),
                                                        @Urn nvarchar(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [dbo].[ResourceLibrary]
            ([Name],
             [Description],
             [Uri],
             [AllowedMethod],
             [Urn])
            VALUES (@Name,
                    @Description,
                    @Uri,
                    @AllowedMethod,
                    @Urn);

            DECLARE @ResourceLibraryId INT;
            SET @ResourceLibraryId = SCOPE_IDENTITY();
        COMMIT;

        SELECT @ResourceLibraryId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[CreateUser] @OrganizationId INT,
                                                @FirstName NVARCHAR(255),
                                                @LastName NVARCHAR(255),
                                                @UserName NVARCHAR(255),
                                                @Password VARBINARY(4000),
                                                @Version INT,
                                                @GroupId INT,
                                                @Email NVARCHAR(255),
                                                @PhoneNumber NVARCHAR(128),
                                                @MemberGuid UNIQUEIDENTIFIER,
                                                @LoginGuid UNIQUEIDENTIFIER,
                                                @ExternalId UNIQUEIDENTIFIER,
                                                @SecondaryPhoneNumber NVARCHAR(128) = NULL,
                                                @PasswordAuditLimit INT = 12, @SyncFlag BIT = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        IF @GroupId > 0 AND NOT EXISTS(SELECT 1 FROM Partner.OrganizationGroup WHERE OrganizationGroupId=@GroupId AND OrganizationId=@OrganizationId AND Status=1)
            THROW 51010, 'Group does not belong to this organization.', 1;
        BEGIN TRANSACTION
            IF @MemberGuid IS NULL
                BEGIN
                    INSERT INTO [Person].[Profile]
                    ([FirstName],
                     [LastName],
                     [Email],
                     [PhoneNumber],
                     [LoginProviderId],
                     [EmailConfirmed],
                     [PhoneNumberConfirmed],
                     [TwoFactorEnabled],
                     [DataOriginId],
                     [SyncFlag],
                     [SecondaryPhoneNumber],
                     [SecondaryPhoneNumberConfirmed])
                    VALUES (@FirstName,
                            @LastName,
                            @Email,
                            ISNULL(@PhoneNumber, ''),
                            1,
                            0,
                            0,
                            1,
                            0,
                            @SyncFlag,
                            @SecondaryPhoneNumber,
                            0)
                END
            ELSE
                BEGIN
                    INSERT INTO [Person].[Profile]
                    ([FirstName],
                     [LastName],
                     [Email],
                     [PhoneNumber],
                     [LoginProviderId],
                     [EmailConfirmed],
                     [PhoneNumberConfirmed],
                     [TwoFactorEnabled],
                     [DataOriginId],
                     [SyncFlag],
                     [RowGuid],
                     [SecondaryPhoneNumber],
                     [SecondaryPhoneNumberConfirmed])
                    VALUES (@FirstName,
                            @LastName,
                            @Email,
                            ISNULL(@PhoneNumber, ''),
                            1,
                            0,
                            0,
                            1,
                            0,
                            @SyncFlag,
                            @MemberGuid,
                            @SecondaryPhoneNumber,
                            0)
                END

            DECLARE @ProfileId INT;
            SET @ProfileId = SCOPE_IDENTITY();

            IF @LoginGuid IS NULL
                BEGIN
                    INSERT INTO [Person].[Credential]
                    ([UserName],
                     [Password],
                     [Version],
                     [ProfileId],
                     [CredentialLocked],
                     [AccessFailedCount],
                     [DataOriginId],
                     [SyncFlag],
                     [ExternalId])
                    VALUES (@UserName,
                            @Password,
                            @Version,
                            @ProfileId,
                            0,
                            0,
                            1,
                            @SyncFlag,
                            @ExternalId)
                END
            ELSE
                BEGIN
                    INSERT INTO [Person].[Credential]
                    ([UserName],
                     [Password],
                     [Version],
                     [ProfileId],
                     [CredentialLocked],
                     [AccessFailedCount],
                     [DataOriginId],
                     [SyncFlag],
                     [RowGuid],
                     [ExternalId])
                    VALUES (@UserName,
                            @Password,
                            @Version,
                            @ProfileId,
                            0,
                            0,
                            1,
                            @SyncFlag,
                            @LoginGuid,
                            @ExternalId)
                END

            DECLARE @CredentialId INT;
            SET @CredentialId = SCOPE_IDENTITY();

            INSERT INTO [Person].[CredentialAudit]
            ([CredentialId],
             [Password],
             [Version])
            VALUES (@CredentialId,
                    @Password,
                    @Version)

            INSERT INTO [Person].[ProfileOrganization]
            ([OrganizationId],
             [ProfileId])
            VALUES (@OrganizationId,
                    @ProfileId)

            IF @GroupId > 0
                BEGIN
                    INSERT INTO [Person].[ProfileGroup]
                    ([OrganizationGroupId],
                     [ProfileId])
                    VALUES (@GroupId,
                            @ProfileId)
                END
        COMMIT;
        SELECT @ProfileId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[CreateMetadata] @ProfileId INT,
                                                    @Key NVARCHAR(30),
                                                    @Value NVARCHAR(128),
                                                    @ModifiedOn DATETIME2(0) = NULL,
                                                    @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            INSERT INTO [Person].[Metadata]
            ([ProfileId],
             [Key],
             [Value],
             [ModifiedOn],
             [ModifiedBy])
            VALUES (@ProfileId,
                    @Key,
                    @Value,
                    @ModifiedOn,
                    @ModifiedBy);
        COMMIT;


    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUserProfile] @UserGuid UNIQUEIDENTIFIER,
                                                       @FirstName nvarchar(255),
                                                       @LastName nvarchar(255),
                                                       @Title nvarchar(5),
                                                       @MiddleInitial nchar(1),
                                                       @PhoneNumber nvarchar(128),
                                                       @Suffix int,
                                                       @MemberGuid UNIQUEIDENTIFIER,
                                                       @LoginGuid UNIQUEIDENTIFIER,
                                                       @ModifiedOn datetime2(0) = NULL,
                                                       @ModifiedBy nvarchar(255) = NULL,
                                                       @SecondaryPhoneNumber nvarchar(128) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            IF @LoginGuid IS NOT NULL
                BEGIN
                    UPDATE [Person].[Credential]
                    SET [RowGuid]    = @LoginGuid,
                        [ModifiedOn] = @ModifiedOn,
                        [ModifiedBy] = @ModifiedBy
                    WHERE [ProfileId] = (SELECT p.[ProfileId]
                                         FROM [Person].[Profile] p
                                         WHERE p.[RowGuid] = @UserGuid)
                END

            IF @MemberGuid IS NULL
                BEGIN
                    UPDATE [Person].[Profile]
                    SET [FirstName]            = @FirstName,
                        [LastName]             = @LastName,
                        [PhoneNumber]          = ISNULL(@PhoneNumber, ''),
                        [Title]                = @Title,
                        [MiddleInitial]        = @MiddleInitial,
                        [Suffix]               = @Suffix,
                        [ModifiedOn]           = @ModifiedOn,
                        [ModifiedBy]           = @ModifiedBy,
                        [SecondaryPhoneNumber] = @SecondaryPhoneNumber
                    WHERE [RowGuid] = @UserGuid
                END
            ELSE
                BEGIN
                    UPDATE [Person].[Profile]
                    SET [FirstName]            = @FirstName,
                        [LastName]             = @LastName,
                        [PhoneNumber]          = ISNULL(@PhoneNumber, ''),
                        [Title]                = @Title,
                        [MiddleInitial]        = @MiddleInitial,
                        [Suffix]               = @Suffix,
                        [RowGuid]              = @MemberGuid,
                        [ModifiedOn]           = @ModifiedOn,
                        [ModifiedBy]           = @ModifiedBy,
                        [SecondaryPhoneNumber] = @SecondaryPhoneNumber
                    WHERE [RowGuid] = @UserGuid
                END
        COMMIT;

        SELECT [ProfileId] AS ID
        FROM [Person].[Profile]
        WHERE (
                  (@MemberGuid IS NULL AND [RowGuid] = @UserGuid)
                      OR (@MemberGuid IS NOT NULL AND [RowGuid] = @MemberGuid)
                  );

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUserProfileForChangeProfilePage] @UserGuid UNIQUEIDENTIFIER,
                                                                           @Email nvarchar(255),
                                                                           @PhoneNumber nvarchar(128),
                                                                           @SecondaryPhoneNumber nvarchar(128) = NULL,
                                                                           @ModifiedBy nvarchar(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Person].[Profile]
            SET [Email]                = @Email,
                [PhoneNumber]          = @PhoneNumber,
                [SecondaryPhoneNumber] = @SecondaryPhoneNumber,
                [ModifiedOn]           = GETUTCDATE(),
                [ModifiedBy]           = @ModifiedBy
            WHERE [RowGuid] = @UserGuid
        COMMIT;

        SELECT [ProfileId] AS ID
        FROM [Person].[Profile]
        WHERE [RowGuid] = @UserGuid;

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUserEmail] @UserGuid UNIQUEIDENTIFIER,
                                                     @Email nvarchar(255),
                                                     @ModifiedOn datetime2(0) = NULL,
                                                     @ModifiedBy nvarchar(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Person].[Profile]
            SET [Email]      = @Email,
                [ModifiedOn] = @ModifiedOn,
                [ModifiedBy] = @ModifiedBy
            WHERE [RowGuid] = @UserGuid;
        COMMIT;

        SELECT [ProfileId] ID
        FROM [Person].[Profile]
        WHERE [RowGuid] = @UserGuid;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUserPassword] @UserGuid UNIQUEIDENTIFIER,
                                                        @Password varbinary(4000),
                                                        @Version int,
                                                        @ModifiedOn datetime2(0) = NULL,
                                                        @ModifiedBy nvarchar(255) = NULL,
                                                        @UnlockAccount bit = 0,
                                                        @PasswordAuditLimit int = 12
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Person].[Credential]
            SET [Password]   = @Password,
                [Version]    = @Version,
                [ModifiedOn] = @ModifiedOn,
                [ModifiedBy] = @ModifiedBy
            WHERE [ProfileId] = (SELECT p.[ProfileId]
                                 FROM [Person].[Profile] p
                                 WHERE p.[RowGuid] = @UserGuid);

            DECLARE @CredentialId INT;
            DECLARE @Username nvarchar(255);

            SELECT @CredentialId = pc.Id,
                   @Username = pc.UserName
            FROM [Person].[Credential] pc
                     JOIN [Person].[Profile] pp
                          ON pp.ProfileId = pc.ProfileId
                              AND pp.[RowGuid] = @UserGuid;

            INSERT INTO [Person].[CredentialAudit]
            ([CredentialId],
             [Password],
             [Version])
            VALUES (@CredentialId,
                    @Password,
                    @Version);

            DELETE pca
            FROM [Person].[CredentialAudit] pca
                     JOIN (SELECT ROW_NUMBER() OVER (ORDER BY ca.CreatedOn DESC) AS rowNumber,
                                  ca.CredentialAuditId
                           FROM [Person].[CredentialAudit] ca) caToDelete
                          ON pca.CredentialAuditId = caToDelete.CredentialAuditId
                              AND caToDelete.rowNumber > @PasswordAuditLimit;

            IF (@UnlockAccount = 1)
                BEGIN
                    UPDATE [Person].[Credential]
                    SET AccessFailedCount = 0,
                        CredentialLocked  = 0,
                        Status            = 1,
                        LastLogin         = GETUTCDATE()
                    WHERE UserName = @Username;
                END
        COMMIT;

        SELECT [ProfileId] ID
        FROM [Person].[Profile]
        WHERE [RowGuid] = @UserGuid;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUserPassword_V2] @UserGuid UNIQUEIDENTIFIER,
                                                           @Password VARBINARY(4000),
                                                           @Version INT,
                                                           @ModifiedOn DATETIME2(0) = NULL,
                                                           @ModifiedBy NVARCHAR(255) = NULL,
                                                           @ExternalId UNIQUEIDENTIFIER,
                                                           @UnlockAccount BIT = 0,
                                                           @PasswordAuditLimit INT = 12
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Person].[Credential]
            SET [Password]   = @Password,
                [Version]    = @Version,
                [ModifiedOn] = @ModifiedOn,
                [ModifiedBy] = @ModifiedBy
            -- PasswordExpirationDate = DATEADD(day, 60, GETUTCDATE()) -- feature not released yet
            WHERE [ProfileId] = (SELECT p.[ProfileId]
                                 FROM [Person].[Profile] p
                                 WHERE p.[RowGuid] = @UserGuid)
              AND ExternalId = @ExternalId;

            -- Password history / audit
            DECLARE @CredentialId INT;
            DECLARE @Username NVARCHAR(255);
            DECLARE @SourceCode NVARCHAR(255);

            SELECT @CredentialId = pc.Id,
                   @Username = pc.UserName
            FROM [Person].[Credential] pc
                     JOIN [Person].[Profile] pp
                          ON pp.ProfileId = pc.ProfileId
                              AND pp.RowGuid = @UserGuid;

            SELECT @SourceCode = es.SourceCode
            FROM [dbo].[ExternalSource] es
            WHERE es.SourceId = @ExternalId;

            INSERT INTO [Person].[CredentialAudit]
            ([CredentialId],
             [Password],
             [Version])
            VALUES (@CredentialId,
                    @Password,
                    @Version);

            -- Remove older passwords beyond audit limit
            DELETE pca
            FROM Person.CredentialAudit pca
                     JOIN (SELECT ROW_NUMBER() OVER (ORDER BY ca.CreatedOn DESC) AS rowNumber,
                                  ca.CredentialAuditId
                           FROM Person.CredentialAudit ca
                           WHERE ca.CredentialId = @CredentialId) caToDelete
                          ON pca.CredentialAuditId = caToDelete.CredentialAuditId
                              AND caToDelete.rowNumber > @PasswordAuditLimit;

            -- Unlock account if requested
            IF (@UnlockAccount = 1)
                BEGIN
                    EXEC Person.UnlockAccount @Username, @SourceCode;
                END
        COMMIT;

        SELECT [ProfileId] AS ID
        FROM [Person].[Profile]
        WHERE [RowGuid] = @UserGuid;

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUsername] @UserGuid UNIQUEIDENTIFIER,
                                                    @Username VARCHAR(255),
                                                    @ExternalId UNIQUEIDENTIFIER,
                                                    @ModifiedOn DATETIME2(0) = NULL,
                                                    @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Person].[Credential]
            SET [Username]   = @Username,
                [ModifiedOn] = GETUTCDATE(),
                [ModifiedBy] = ISNULL(@ModifiedBy, SUSER_NAME())
            WHERE [ProfileId] = (SELECT p.[ProfileId]
                                 FROM [Person].[Profile] p
                                 WHERE p.[RowGuid] = @UserGuid)
              AND ExternalId = @ExternalId;
        COMMIT;

        SELECT [ProfileId] ID
        FROM [Person].[Profile]
        WHERE [RowGuid] = @UserGuid;

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUserSecuritySettings] @UserGuid UNIQUEIDENTIFIER,
                                                                @TwoFactorEnabled BIT,
                                                                @ModifiedOn DATETIME2(0) = NULL,
                                                                @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Person].[Profile]
            SET [TwoFactorEnabled] = @TwoFactorEnabled,
                [ModifiedOn]       = @ModifiedOn,
                [ModifiedBy]       = @ModifiedBy
            WHERE [RowGuid] = @UserGuid;
        COMMIT;

        SELECT [ProfileId] ID
        FROM [Person].[Profile]
        WHERE [RowGuid] = @UserGuid;

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[DeleteMetadata] @ProfileId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DELETE
    FROM [Person].[Metadata]
    WHERE ProfileId = @ProfileId;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetMetadata] @ProfileId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Person].[Metadata]
    WHERE ProfileId = @ProfileId;
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateUserStatus] @UserGuid UNIQUEIDENTIFIER,
                                                      @Status TINYINT,
                                                      @ModifiedOn DATETIME2(0) = NULL,
                                                      @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Person].[Profile]
            SET [Status]     = @Status,
                [ModifiedOn] = @ModifiedOn,
                [ModifiedBy] = @ModifiedBy
            WHERE [RowGuid] = @UserGuid;
        COMMIT;

        SELECT [ProfileId] ID
        FROM [Person].[Profile]
        WHERE [RowGuid] = @UserGuid;

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserById] @ProfileId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
LEFT JOIN Person.ProfileGroup pg ON pg.ProfileId=p.ProfileId
LEFT JOIN Partner.OrganizationGroup og ON og.OrganizationGroupId=pg.OrganizationGroupId AND og.Status=1 AND og.OrganizationId=po.OrganizationId
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE p.ProfileId=@ProfileId;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByUsername] @UserName NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF (SELECT COUNT(*) FROM Person.Credential WHERE UserName=@Username)>1 THROW 51013, 'Username requires branding.', 1;
    SELECT c.*
    FROM [Person].[Credential] c
    WHERE c.UserName = @UserName;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByUsernameAndBranding] @UserName NVARCHAR(255),
                                                                             @Branding NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.*,
           es.SourceCode AS Branding
    FROM [Person].[Credential] c
             JOIN [dbo].[ExternalSource] es
                  ON es.SourceId = c.ExternalId
                      AND es.SourceCode = @Branding
    WHERE c.UserName = @UserName;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByGuid] @LoginId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.*
    FROM [Person].[Credential] c
    WHERE c.RowGuid = @LoginId;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserByUsername] @Username NVARCHAR(255)
AS
BEGIN
    IF (SELECT COUNT(*) FROM Person.Credential WHERE UserName=@Username)>1 THROW 51013, 'Username requires branding.', 1;
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
LEFT JOIN Person.ProfileGroup pg ON pg.ProfileId=p.ProfileId
LEFT JOIN Partner.OrganizationGroup og ON og.OrganizationGroupId=pg.OrganizationGroupId AND og.Status=1 AND og.OrganizationId=po.OrganizationId
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE c.UserName=@Username AND p.Status=1;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserByUsernameAndBranding] @Username NVARCHAR(255), @Branding NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
LEFT JOIN Person.ProfileGroup pg ON pg.ProfileId=p.ProfileId
LEFT JOIN Partner.OrganizationGroup og ON og.OrganizationGroupId=pg.OrganizationGroupId AND og.Status=1 AND og.OrganizationId=po.OrganizationId
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE c.UserName=@Username AND es.SourceCode=@Branding AND p.Status=1;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserId] @UserGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
LEFT JOIN Person.ProfileGroup pg ON pg.ProfileId=p.ProfileId
LEFT JOIN Partner.OrganizationGroup og ON og.OrganizationGroupId=pg.OrganizationGroupId AND og.Status=1 AND og.OrganizationId=po.OrganizationId
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE p.RowGuid=@UserGuid;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserIdAndBrandingV2] @UserGuid UNIQUEIDENTIFIER,@Branding NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
LEFT JOIN Person.ProfileGroup pg ON pg.ProfileId=p.ProfileId
LEFT JOIN Partner.OrganizationGroup og ON og.OrganizationGroupId=pg.OrganizationGroupId AND og.Status=1 AND og.OrganizationId=po.OrganizationId
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE p.RowGuid=@UserGuid AND es.SourceCode=@Branding;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUsers] @ResultsPerPage INT, @Offset INT, @Status TINYINT, @Search NVARCHAR(50), @Type NVARCHAR(50),
@OrganizationGuid UNIQUEIDENTIFIER, @SourceIds NVARCHAR(MAX)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
IF @ResultsPerPage<1 OR @ResultsPerPage>1000 OR @Offset<0 THROW 51012, 'Invalid pagination.', 1;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
LEFT JOIN Person.ProfileGroup pg ON pg.ProfileId=p.ProfileId
LEFT JOIN Partner.OrganizationGroup og ON og.OrganizationGroupId=pg.OrganizationGroupId AND og.Status=1 AND og.OrganizationId=po.OrganizationId
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE (@Status IS NULL OR p.Status=@Status)
AND (@OrganizationGuid IS NULL OR o.RowGuid=@OrganizationGuid)
AND (@Type IS NULL OR @Type<>'Administrator' OR og.OrganizationGroupId IS NOT NULL)
AND (@Type IS NULL OR @Type<>'User' OR og.OrganizationGroupId IS NULL)
AND (@SourceIds IS NULL OR EXISTS(SELECT 1 FROM STRING_SPLIT(@SourceIds,',') v WHERE TRY_CONVERT(UNIQUEIDENTIFIER,v.value)=es.SourceId))
AND (NULLIF(@Search,'') IS NULL OR p.FirstName LIKE '%'+@Search+'%' OR p.LastName LIKE '%'+@Search+'%' OR p.Email LIKE '%'+@Search+'%' OR c.UserName LIKE '%'+@Search+'%' OR p.PhoneNumber LIKE '%'+@Search+'%' OR p.SecondaryPhoneNumber LIKE '%'+@Search+'%')
ORDER BY p.LastName,p.FirstName,p.ProfileId,c.Id,po.ProfileOrganizationId OFFSET @Offset ROWS FETCH NEXT @ResultsPerPage ROWS ONLY;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserAuthDetailsByUserName] @Username NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF (SELECT COUNT(*) FROM Person.Credential WHERE UserName=@Username)>1 THROW 51013, 'Username requires branding.', 1;
    SELECT p.*,
           cred.UserName,
           cred.Password,
           cred.Version,
           cred.AccessFailedCount,
           cred.CredentialLocked,
           cred.LastLogin,
           cred.Status AS CredentialStatus
    FROM [Person].[Credential] cred
             INNER JOIN [Person].[Profile] p
                        ON cred.ProfileId = p.ProfileId
    WHERE cred.UserName = @Username
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserAuthDetailsByUserNameAndExternalSourceCode] @Username NVARCHAR(255),
                                                                                       @SourceCode NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT p.*,
           cred.UserName,
           cred.Password,
           cred.Version,
           cred.AccessFailedCount,
           cred.CredentialLocked,
           cred.LastLogin,
           cred.Status AS CredentialStatus
    FROM [Person].[Credential] cred
             INNER JOIN [Person].[Profile] p
                        ON cred.ProfileId = p.ProfileId
             INNER JOIN [dbo].[ExternalSource] es
                        ON cred.ExternalId = es.SourceId
    WHERE cred.UserName = @Username
      AND es.SourceCode = @SourceCode
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetProfileOrganizationByProfileId]
@ProfileId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT
        p.*,
        pa.RowGuid AS OrganizationRowGuid
    FROM [Person].[ProfileOrganization] p
             INNER JOIN [Partner].[Organization] pa
                        ON p.OrganizationId = pa.OrganizationId
    WHERE p.ProfileId = @ProfileId
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserPermissions]
@UserId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT ogp.*
    FROM [Partner].[OrganizationGroupPermission] ogp
             INNER JOIN [Person].[ProfileGroup] pg
                        ON pg.OrganizationGroupId = ogp.OrganizationGroupId
    WHERE pg.ProfileId = @UserId
      AND ogp.Status = 1
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateAccessFailedCount] @Username NVARCHAR(255),@LoginSuccess BIT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
IF (SELECT COUNT(*) FROM Person.Credential WHERE UserName=@Username)>1 THROW 51013, 'Username requires branding.',1;
DECLARE @Source NVARCHAR(255),@Limit INT;
SELECT @Source=es.SourceCode FROM Person.Credential c JOIN dbo.ExternalSource es ON c.ExternalId=es.SourceId WHERE c.UserName=@Username;
SELECT @Limit=Value FROM dbo.GlobalConfig WHERE Name='MaxPasswordFailureCount';
EXEC Person.UpdateAccessFailedCountWithExternalSourceCode @Username,@Source,@LoginSuccess,@Limit;
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateAccessFailedCountWithExternalSourceCode] @Username NVARCHAR(255), @SourceCode NVARCHAR(255), @LoginSuccess BIT, @AccessFailedLimit INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
IF @AccessFailedLimit<1 THROW 51014, 'Invalid account failure limit.',1;
UPDATE c SET AccessFailedCount=CASE WHEN @LoginSuccess=1 THEN 0 ELSE AccessFailedCount+1 END,
CredentialLocked=CASE WHEN @LoginSuccess=0 AND AccessFailedCount+1>=@AccessFailedLimit THEN 1 ELSE CredentialLocked END,
LastLogin=CASE WHEN @LoginSuccess=1 THEN SYSUTCDATETIME() ELSE LastLogin END
FROM Person.Credential c JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
WHERE c.UserName=@Username AND es.SourceCode=@SourceCode;
EXEC Person.GetUserAuthDetailsByUserNameAndExternalSourceCode @Username,@SourceCode;
END
GO

CREATE OR ALTER PROCEDURE [Person].[UnLockAccount]
    @Username NVARCHAR(255),
    @SourceCode NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

        UPDATE cred
        SET
            cred.Status = 1,
            cred.CredentialLocked = 0,
            cred.AccessFailedCount = 0
        FROM Person.Credential cred
                 INNER JOIN [dbo].[ExternalSource] es
                            ON es.SourceId = cred.ExternalId
        WHERE cred.UserName = @Username
          AND es.SourceCode = @SourceCode
          AND (cred.CredentialLocked <> 0 OR cred.AccessFailedCount > 0);

        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[LockAccount]
    @Username NVARCHAR(255),
    @SourceCode NVARCHAR(255),
    @AccessFailedLimit INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;

        UPDATE cred
        SET
            cred.Status = 0,
            cred.CredentialLocked = 1,
            cred.AccessFailedCount = @AccessFailedLimit
        FROM Person.Credential cred
                 INNER JOIN [dbo].[ExternalSource] es
                            ON es.SourceId = cred.ExternalId
        WHERE cred.UserName = @Username
          AND es.SourceCode = @SourceCode;

        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[ReactivateUserWithPassword] @UserGuid UNIQUEIDENTIFIER, @FirstName NVARCHAR(255), @LastName NVARCHAR(255),
@PhoneNumber NVARCHAR(128), @Email NVARCHAR(255), @MemberGuid UNIQUEIDENTIFIER, @LoginGuid UNIQUEIDENTIFIER,
@SecondaryPhoneNumber NVARCHAR(128), @Username NVARCHAR(255), @ExternalId UNIQUEIDENTIFIER,
@Password VARBINARY(4000), @Version INT, @UnlockAccount BIT, @PasswordAuditLimit INT,
@ModifiedOn DATETIME2(7), @ModifiedBy NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @ProfileId INT;
SELECT @ProfileId=p.ProfileId FROM Person.Profile p WITH(UPDLOCK) JOIN Person.Credential c ON c.ProfileId=p.ProfileId
WHERE p.RowGuid=@UserGuid AND p.Status=0 AND c.ExternalId=@ExternalId;
IF @ProfileId IS NULL THROW 51011, 'Inactive branded user not found.', 1;
UPDATE Person.Profile SET FirstName=@FirstName, LastName=@LastName, Email=@Email, PhoneNumber=ISNULL(@PhoneNumber,''),
SecondaryPhoneNumber=@SecondaryPhoneNumber, Status=1, RowGuid=ISNULL(@MemberGuid,RowGuid), ModifiedOn=@ModifiedOn, ModifiedBy=@ModifiedBy WHERE ProfileId=@ProfileId;
UPDATE Person.Credential SET UserName=@Username, Password=@Password, Version=@Version, Status=1,
CredentialLocked=CASE WHEN @UnlockAccount=1 THEN 0 ELSE CredentialLocked END,
AccessFailedCount=CASE WHEN @UnlockAccount=1 THEN 0 ELSE AccessFailedCount END,
RowGuid=ISNULL(@LoginGuid,RowGuid),ModifiedOn=@ModifiedOn,ModifiedBy=@ModifiedBy WHERE ProfileId=@ProfileId AND ExternalId=@ExternalId;
INSERT Person.CredentialAudit(CredentialId,Password,Version) SELECT Id,@Password,@Version FROM Person.Credential WHERE ProfileId=@ProfileId AND ExternalId=@ExternalId;
DELETE ca FROM Person.CredentialAudit ca JOIN Person.Credential c ON c.Id=ca.CredentialId WHERE c.ProfileId=@ProfileId AND c.ExternalId=@ExternalId AND ca.CredentialAuditId NOT IN
(SELECT TOP(CASE WHEN @PasswordAuditLimit<1 THEN 1 ELSE @PasswordAuditLimit END) a.CredentialAuditId FROM Person.CredentialAudit a WHERE a.CredentialId=c.Id ORDER BY a.CredentialAuditId DESC);
COMMIT;
SELECT @ProfileId AS ID;
END
GO

CREATE OR ALTER PROCEDURE [Token].[CreateAuthCode] @ApplicationId INT,@SessionId UNIQUEIDENTIFIER,@Data NVARCHAR(512),@ExpiresAt DATETIME2(7)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
DECLARE @Now DATETIME2(7)=SYSUTCDATETIME(),@TTL INT;
SELECT @TTL=AuthCodeTimeToLive FROM Client.TokenSetting WHERE ApplicationId=@ApplicationId;
IF @TTL IS NULL OR @TTL<=0 THROW 51015,'Authorization code lifetime is not configured.',1;
IF NOT EXISTS(SELECT 1 FROM Token.AuthSession WHERE SessionId=@SessionId AND ApplicationId=@ApplicationId) THROW 51015,'Authorization session mismatch.',1;
INSERT Token.AuthCode(ApplicationId,SessionId,Data,DataHash,CreatedOn,Expiration)
VALUES(@ApplicationId,@SessionId,@Data,HASHBYTES('SHA2_256',@Data),@Now,COALESCE(@ExpiresAt,DATEADD(SECOND,@TTL,@Now)));
SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetAuthCodeById] @AuthCodeId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT * FROM Token.[AuthCode] WHERE [AuthCodeId]=@AuthCodeId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetSessionIdByAuthCode]
@Data NVARCHAR(512)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.SessionId
    FROM [Token].[AuthCode] c
    WHERE c.DataHash = HASHBYTES('SHA2_256', @Data)
      AND (c.Expiration IS NULL OR GETUTCDATE() < c.Expiration)
      AND c.ConsumedOn IS NULL;
END
GO

CREATE OR ALTER PROCEDURE [Token].[SetAuthCodeConsumedOn] @Data NVARCHAR(512)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @SessionId UNIQUEIDENTIFIER;
SELECT @SessionId=SessionId FROM Token.AuthCode WITH(UPDLOCK,HOLDLOCK) WHERE DataHash=HASHBYTES('SHA2_256',@Data) AND Data=@Data AND ConsumedOn IS NULL AND Expiration>SYSUTCDATETIME();
IF @SessionId IS NULL THROW 51016,'Authorization code is expired, unknown or already consumed.',1;
UPDATE Token.AuthCode SET ConsumedOn=SYSUTCDATETIME() WHERE DataHash=HASHBYTES('SHA2_256',@Data);
UPDATE Token.Pkce SET ConsumedOn=SYSUTCDATETIME() WHERE SessionId=@SessionId AND ConsumedOn IS NULL;
UPDATE Token.Token SET ConsumedOn=SYSUTCDATETIME() WHERE DataHash=HASHBYTES('SHA2_256',@Data) AND ConsumedOn IS NULL;
COMMIT;
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetMFARealms]
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Client].[MFARealm]
    WHERE Active = 1;
END
GO

CREATE OR ALTER PROCEDURE [Token].[CreateTokenWithHash] @TokenTypeId INT,@ApplicationId INT,@SubjectId NVARCHAR(200),@SessionId UNIQUEIDENTIFIER,
@IsOpaque BIT,@Data NVARCHAR(MAX),@SigningKey VARBINARY(MAX),@TimeToLive INT,@DataHash BINARY(32),
@IssuedAt DATETIME2(7)=NULL,@ExpiresAt DATETIME2(7)=NULL,@Scope NVARCHAR(MAX)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
DECLARE @Now DATETIME2(7)=SYSUTCDATETIME();
SET @IssuedAt=COALESCE(@IssuedAt,@Now);
SET @ExpiresAt=COALESCE(@ExpiresAt,DATEADD(SECOND,@TimeToLive,@IssuedAt));
IF @ExpiresAt<=@IssuedAt THROW 51018,'Invalid token lifetime.',1;
IF NOT EXISTS(SELECT 1 FROM Token.AuthSession WHERE SessionId=@SessionId AND ApplicationId=@ApplicationId) THROW 51018,'Token session mismatch.',1;
INSERT Token.Token(TokenTypeId,ApplicationId,SubjectId,SessionId,IsOpaque,Data,SigningKey,CreatedOn,Expiration,DataHash,Scope)
VALUES(@TokenTypeId,@ApplicationId,@SubjectId,@SessionId,@IsOpaque,@Data,@SigningKey,@IssuedAt,@ExpiresAt,@DataHash,@Scope);
SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetTokenById] @TokenId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT * FROM Token.[Token] WHERE [TokenId]=@TokenId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetTokenByValueHash] @HashValue BINARY(32),@TokenTypeId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT * FROM Token.Token WHERE DataHash=@HashValue AND TokenTypeId=@TokenTypeId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetAllActiveTokensBySessionId] @SessionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT * FROM Token.Token WHERE SessionId=@SessionId AND ConsumedOn IS NULL AND Expiration>SYSUTCDATETIME();
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetTokensByClientIdAndRequestDateTime]
    @ClientId NCHAR(32),
    @requestDatetime DATETIME2(7)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT t.*
    FROM Token.Token t
             JOIN Client.Application a
                  ON a.ApplicationId = t.ApplicationId
    WHERE a.ClientId = @ClientId
      AND t.SigningKey IS NOT NULL
      AND t.CreatedOn <= @requestDatetime   -- Before requested datetime
      AND t.Expiration >= @requestDatetime -- After requested datetime
    ORDER BY t.CreatedOn DESC;
END
GO

CREATE OR ALTER PROCEDURE [Token].[InsertSsoCookie] @HashedEncryptedSessionId BINARY(32),@SessionId UNIQUEIDENTIFIER,@EncryptedSessionId VARBINARY(4000),@EncryptionKey VARBINARY(4000)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
INSERT Token.SSO(HashedEncryptedSessionId,SessionId,EncryptedSessionId,EncryptionKey) VALUES(@HashedEncryptedSessionId,@SessionId,@EncryptedSessionId,@EncryptionKey);
END
GO

CREATE OR ALTER PROCEDURE [Token].[FindSsoCookieByEncryptedSessionId]
@HashedEncryptedSessionId BINARY(32)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT *
    FROM [Token].[Sso]
    WHERE HashedEncryptedSessionId = @HashedEncryptedSessionId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[CreateAuthSession]
    @ApplicationId INTEGER,
    @SubjectId NVARCHAR(100),
    @Scope NVARCHAR(1024),
    @AuthFlowId INTEGER,
    @ClientFingerprint VARBINARY(MAX),
    @ClientId NCHAR(32),
    @Branding NVARCHAR(100)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @AuthSessionActiveId INT;

    SET @AuthSessionActiveId = (
        SELECT EnumId
        FROM [dbo].[Enum] e
        WHERE e.Code = 'Session Active'
          AND e.EnumTypeId = (
            SELECT EnumTypeId
            FROM [dbo].[EnumType] et
            WHERE et.Name = 'AuthSessionStatus'
        )
    );

    BEGIN TRY
        BEGIN TRANSACTION;

        INSERT INTO [Token].[AuthSession]
        (
            [ApplicationId],
            [SubjectId],
            [Scope],
            [AuthFlowId],
            [AuthSessionStatusId],
            [ClientFingerprint],
            [ClientId],
            [Branding]
        )
        VALUES
            (
                @ApplicationId,
                @SubjectId,
                @Scope,
                @AuthFlowId,
                @AuthSessionActiveId,
                @ClientFingerprint,
                @ClientId,
                @Branding
            );

        COMMIT;

        DECLARE @AuthSessionId INT;
        SET @AuthSessionId = SCOPE_IDENTITY();

        SELECT @AuthSessionId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetAuthSessionById] @AuthSessionId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT s.*,r.RedirectUri FROM Token.AuthSession s LEFT JOIN Client.RedirectUri r ON r.RedirectUriId=s.RedirectUriId WHERE s.AuthSessionId=@AuthSessionId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetAuthSessionBySessionId] @SessionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT s.*,r.RedirectUri FROM Token.AuthSession s LEFT JOIN Client.RedirectUri r ON r.RedirectUriId=s.RedirectUriId WHERE s.SessionId=@SessionId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[SetAuthSessionInactive]
@SessionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @AuthSessionInactiveId INT;

    SET @AuthSessionInactiveId = (
        SELECT EnumId
        FROM [dbo].[Enum] e
        WHERE e.Code = 'Session Inactive'
          AND e.EnumTypeId = (
            SELECT EnumTypeId
            FROM [dbo].[EnumType] et
            WHERE et.Name = 'AuthSessionStatus'
        )
    );

    UPDATE [Token].[AuthSession]
    SET AuthSessionStatusId = @AuthSessionInactiveId
    WHERE SessionId = @SessionId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetMostRecentActiveSession]
@SubjectId NVARCHAR(100)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @AuthSessionActiveId INT;

    SET @AuthSessionActiveId = (
        SELECT EnumId
        FROM [dbo].[Enum] e
        WHERE e.Code = 'Session Active'
          AND e.EnumTypeId = (
            SELECT EnumTypeId
            FROM [dbo].[EnumType] et
            WHERE et.Name = 'AuthSessionStatus'
        )
    );

    SELECT TOP 1 s.*,r.RedirectUri
    FROM [Token].[AuthSession] s LEFT JOIN Client.RedirectUri r ON r.RedirectUriId=s.RedirectUriId
    WHERE @AuthSessionActiveId = s.AuthSessionStatusId
      AND @SubjectId = s.SubjectId
    ORDER BY s.CreatedOn DESC,s.AuthSessionId DESC;
END
GO

CREATE OR ALTER PROCEDURE [Token].[SetBrandingAndRedirectUri]
    @SessionId UNIQUEIDENTIFIER,
    @Branding NVARCHAR(100),
    @RedirectUri NVARCHAR(1024),
    @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @RedirectUriId INT;

    SET @RedirectUriId = (
        SELECT RedirectUriId
        FROM [Client].[RedirectUri]
        WHERE RedirectUri = @RedirectUri
          AND ApplicationId = @ApplicationId
    );

    UPDATE [Token].[AuthSession]
    SET Branding = @Branding,
        RedirectUriId = @RedirectUriId
    WHERE SessionId = @SessionId;
END
GO

CREATE OR ALTER PROCEDURE [Person].[getHistoricPasswords]
@ProfileId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT ca.Password,
           ca.Version
    FROM [Person].[Credential] pc
             INNER JOIN [Person].[CredentialAudit] ca
                        ON pc.Id = ca.CredentialId
    WHERE pc.ProfileId = @ProfileId;
END
GO

CREATE OR ALTER PROCEDURE [Person].[validatePasswordBlackListed]
@Password NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @PasswordValid TINYINT;

    IF (SELECT COUNT(0)
        FROM [Person].[BlockedPassword]
        WHERE [Password] = @Password) > 0
        SET @PasswordValid = 0;
    ELSE
        SET @PasswordValid = 1;

    SELECT @PasswordValid;
END
GO

CREATE OR ALTER PROCEDURE [Token].[CreatePkce] @ApplicationId INT,@SessionId UNIQUEIDENTIFIER,@Data NVARCHAR(2048),@Algorithm NVARCHAR(30),@RedirectUri NVARCHAR(2000)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
DECLARE @Now DATETIME2(7)=SYSUTCDATETIME(),@TTL INT;
SELECT @TTL=AuthCodeTimeToLive FROM Client.TokenSetting WHERE ApplicationId=@ApplicationId;
IF @TTL IS NULL OR @TTL<=0 THROW 51015,'PKCE lifetime is not configured.',1;
IF NOT EXISTS(SELECT 1 FROM Token.AuthSession WHERE SessionId=@SessionId AND ApplicationId=@ApplicationId) THROW 51015,'PKCE session mismatch.',1;
INSERT Token.Pkce(ApplicationId,SessionId,Data,Algorithm,RedirectUri,CreatedOn,Expiration)
VALUES(@ApplicationId,@SessionId,@Data,@Algorithm,@RedirectUri,@Now,DATEADD(SECOND,@TTL,@Now));
SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetPkceById] @PkceId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT * FROM Token.[Pkce] WHERE [PkceId]=@PkceId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetPkceBySessionId] @SessionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT * FROM Token.Pkce WHERE SessionId=@SessionId AND ConsumedOn IS NULL AND Expiration>SYSUTCDATETIME();
END
GO

CREATE OR ALTER PROCEDURE [Partner].[SavePasswordKeyStore]
    @PasswordKeyStoreBytes VARBINARY(MAX),
    @PasswordKeyId UNIQUEIDENTIFIER,
    @NewId INT OUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    INSERT INTO [Partner].[KeyStorePassword]
    (KeyStore, PasswordKeyId)
    VALUES
        (@PasswordKeyStoreBytes, @PasswordKeyId);

    SET @NewId = SCOPE_IDENTITY();
END
GO

CREATE OR ALTER PROCEDURE [Partner].[OrgGuidToId]
    @Guid NVARCHAR(255),
    @OrgId INT OUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT @OrgId = OrganizationId
    FROM [Partner].[Organization]
    WHERE RowGuid = @Guid;
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetSecrets] @OrganizationId INT,@ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT s.SecretId,s.RowGuid,s.OrganizationId,s.ApplicationId,s.Description,s.SecretHashValue,s.ExpireOn,
s.KeyStore,s.PasswordKeyId,k.KeyStore AS PasswordKeyStore
FROM Client.Secret s JOIN Partner.KeyStorePassword k ON k.PasswordKeyId=s.PasswordKeyId
WHERE s.OrganizationId=@OrganizationId AND s.ApplicationId=@ApplicationId;
END
GO

CREATE OR ALTER PROCEDURE [Partner].[GetCertificatesByCertTypeId] @CertificateTypeId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT c.*, k.KeyStore AS PasswordKeyStore FROM Partner.Certificate c JOIN Partner.KeyStorePassword k ON k.PasswordKeyId=c.PasswordKeyId WHERE c.CertificateTypeId=@CertificateTypeId AND c.Status=1 AND c.ValidFrom<=SYSUTCDATETIME() AND c.ValidTo>SYSUTCDATETIME();
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetTokenByClientId] @ClientId NCHAR(32)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT t.* FROM Token.Token t JOIN Client.Application a ON a.ApplicationId=t.ApplicationId WHERE a.ClientId=@ClientId AND a.Active=1 AND t.ConsumedOn IS NULL AND t.Expiration>SYSUTCDATETIME() ORDER BY t.CreatedOn DESC,t.TokenId DESC;
END
GO

CREATE OR ALTER PROCEDURE [Token].[ConsumeToken] @TokenId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
UPDATE Token.Token SET ConsumedOn=SYSUTCDATETIME() WHERE TokenId=@TokenId AND ConsumedOn IS NULL AND Expiration>SYSUTCDATETIME();
IF @@ROWCOUNT<>1 THROW 51017,'Token is expired, unknown or already consumed.',1;
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetEnums] 
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT MAX(CASE WHEN et.Name='CertificateType' AND e.Code='Organization' THEN e.EnumId END) AS [OrganizationEnumId],
MAX(CASE WHEN et.Name='CertificateType' AND e.Code='PublicKey' THEN e.EnumId END) AS [PublicKeyEnumId],
MAX(CASE WHEN et.Name='TokenSignatureAlgorithm' AND e.Code='RS256' THEN e.EnumId END) AS [RS256EnumId],
MAX(CASE WHEN et.Name='TokenSignatureAlgorithm' AND e.Code='ES256' THEN e.EnumId END) AS [ES256EnumId],
MAX(CASE WHEN et.Name='TokenSignatureAlgorithm' AND e.Code='HS256' THEN e.EnumId END) AS [HS256EnumId],
MAX(CASE WHEN et.Name='ApplicationType' AND e.Code='mobile' THEN e.EnumId END) AS [MobileEnumId],
MAX(CASE WHEN et.Name='ApplicationType' AND e.Code='web' THEN e.EnumId END) AS [WebEnumId],
MAX(CASE WHEN et.Name='ApplicationType' AND e.Code='server' THEN e.EnumId END) AS [ServerEnumId],
MAX(CASE WHEN et.Name='AuthFlow' AND e.Code='AuthCode' THEN e.EnumId END) AS [AuthCodeEnumId],
MAX(CASE WHEN et.Name='AuthFlow' AND e.Code='ClientSecretJWT' THEN e.EnumId END) AS [ClientSecretJwtEnumId],
MAX(CASE WHEN et.Name='AuthFlow' AND e.Code='PrivateKeyJWT' THEN e.EnumId END) AS [PrivateKeyJwtEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='Access Token' THEN e.EnumId END) AS [AccessTokenEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='Refresh Token' THEN e.EnumId END) AS [RefreshTokenEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='OIDC Token' THEN e.EnumId END) AS [IdTokenEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='Auth Code Token' THEN e.EnumId END) AS [AuthCodeTokenEnumId],
MAX(CASE WHEN et.Name='AuthSessionStatus' AND e.Code='Session Active' THEN e.EnumId END) AS [AuthSessionActiveEnumId],
MAX(CASE WHEN et.Name='AuthSessionStatus' AND e.Code='Session Inactive' THEN e.EnumId END) AS [AuthSessionInactiveEnumId],
MAX(CASE WHEN et.Name='TokenFormat' AND e.Code='self-contained' THEN e.EnumId END) AS [SelfContainedAccessTokenFormatEnumId],
MAX(CASE WHEN et.Name='TokenFormat' AND e.Code='reference' THEN e.EnumId END) AS [ReferenceAccessTokenFormatEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Mr' THEN e.EnumId END) AS [MrEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Mrs' THEN e.EnumId END) AS [MrsEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Miss' THEN e.EnumId END) AS [MissEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Sr' THEN e.EnumId END) AS [SrEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Jr' THEN e.EnumId END) AS [JrEnumId],
MAX(CASE WHEN et.Name='UsernameType' AND e.Code='Username' THEN e.EnumId END) AS [UsernameEnumId],
MAX(CASE WHEN et.Name='UsernameType' AND e.Code='Email' THEN e.EnumId END) AS [EmailEnumId] FROM dbo.Enum e JOIN dbo.EnumType et ON et.EnumTypeId=e.EnumTypeId;
END
GO

CREATE OR ALTER PROCEDURE Client.GetApplicationDetailById @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT a.*,t.AuthCodeTimeToLive,t.AccessTokenTimeToLive,t.DeviceCodeTimeToLive,
           t.RefreshTokenTimeToLive,t.MaxRequestTransitTime
    FROM Client.Application a JOIN Client.TokenSetting t ON t.ApplicationId=a.ApplicationId
    WHERE a.ApplicationId=@ApplicationId;
    SELECT OrganizationId,ApplicationId,ParamPriority,ParamJson
    FROM Client.ForgetUserNameSetting WHERE ApplicationId=@ApplicationId ORDER BY ParamPriority;
END
GO
