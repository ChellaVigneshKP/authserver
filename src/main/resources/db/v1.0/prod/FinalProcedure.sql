CREATE OR ALTER PROCEDURE [dbo].[GetAdminConfig]
AS
BEGIN
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

CREATE OR ALTER PROCEDURE [Client].[ApplicationExists] @OrganizationId INTEGER,
                                                       @ApplicationGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT ApplicationId
    FROM [Client].[Application] a
    WHERE a.RowGuid = @ApplicationGuid
      AND a.OrganizationId = @OrganizationId
END
GO
CREATE OR ALTER PROCEDURE [Client].[UpdateApplicationUri] @OrganizationId INT,
                                                          @ApplicationId INT,
                                                          @Uri NVARCHAR(1024),
                                                          @ModifiedOn DATETIME2(0) = NULL,
                                                          @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
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

CREATE OR ALTER PROCEDURE [Client].[GetApplicationByName] @Name NVARCHAR(255),
                                                          @orgId INT
AS
BEGIN
    SELECT *
    FROM [Client].[Application] a
    WHERE a.Name = @Name
      AND a.OrganizationId = @orgId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplicationByClientId] @ClientId NVARCHAR(1024)
AS
BEGIN
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
    UPDATE [Client].[Application]
    SET Active = @active
    WHERE [OrganizationId] = @OrganizationId
      AND [ApplicationId] = @ApplicationId
END
GO

GRANT EXECUTE ON [Client].[UpdateApplicationActivation] TO [db_spexec]
GO

CREATE OR ALTER PROCEDURE [Client].[GetApplications] @OrganizationId INT
AS
BEGIN
    SELECT a.*
    FROM [Client].[Application] a
    WHERE a.OrganizationId = @OrganizationId
      AND a.Active > 0
    ORDER BY a.Name
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetSettingsByApplicationId] @ApplicationId INTEGER
AS
BEGIN
    SELECT *
    FROM [Client].[Setting] s
    WHERE s.ApplicationId = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetRedirectUrisByApplicationId] @ApplicationId INTEGER
AS
BEGIN
    SELECT r.RedirectUri
    FROM [Client].[RedirectUri] r
    WHERE r.ApplicationId = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetPostLogoutRedirectUrisByApplicationId] @ApplicationId INTEGER
AS
BEGIN
    SELECT r.PostLogoutRedirectUri
    FROM [Client].[PostLogoutRedirectUri] r
    WHERE r.ApplicationId = @ApplicationId
END
GO

GRANT EXECUTE ON [Client].[GetPostLogoutRedirectUrisByApplicationId] TO [db_spexec]
GO

CREATE OR ALTER PROCEDURE [Resource].[GetResourceById] @resourceId INT
AS
BEGIN
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

CREATE OR ALTER PROCEDURE [Client].[GetMfaExpiryPinTimeV2] @SessionId UNIQUEIDENTIFIER,
                                                           @PinTimeToLive INT
AS
BEGIN

    DECLARE
        @Pin NVARCHAR(512) = NULL,
        @IssueTime DATETIME2,
        @ExpiryTime DATETIME2,
        @MillisToExpiryTime INT,
        @UtcTime DATETIME;

    SELECT @Pin = Pin
    FROM Token.AuthSession
    WHERE SessionId = @SessionId;

    IF @Pin IS NOT NULL
        BEGIN
            SET @IssueTime =
                    CONVERT(
                        DATETIME2,
                            LTRIM(SUBSTRING(@Pin, CHARINDEX(',', @Pin) + 1, LEN(@Pin)))
                    );

            SET @ExpiryTime = DATEADD(s, @PinTimeToLive, @IssueTime);
            SET @UtcTime = GETUTCDATE();

            IF DATEDIFF(y, GETUTCDATE(), @IssueTime) = 0
                AND DATEDIFF(m, GETUTCDATE(), @IssueTime) = 0
                AND DATEDIFF(d, GETUTCDATE(), @IssueTime) = 0
                BEGIN
                    -- Find out time difference in milliseconds from now to expiry time
                    SET @MillisToExpiryTime = DATEDIFF(ms, @UtcTime, @ExpiryTime);

                    SELECT @MillisToExpiryTime AS TimeToExpireMS,
                           @IssueTime          AS IssueTime,
                           @ExpiryTime         AS ExpiryTime;
                END
            ELSE
                BEGIN
                    SELECT -1          AS TimeToExpireMS,
                           @IssueTime  AS IssueTime,
                           @ExpiryTime AS ExpiryTime;
                END
        END
    ELSE
        BEGIN
            SELECT NULL AS TimeToExpireMS,
                   NULL AS IssueTime,
                   NULL AS ExpiryTime;
        END
END
GO

CREATE OR ALTER PROCEDURE [Client].[UpdatePostLogoutRedirectUri] @OrganizationId INT,
                                                                 @ApplicationId INT,
                                                                 @PostLogoutRedirectUri NVARCHAR(1024)
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION
            UPDATE [Client].[PostLogoutRedirectUri]
            SET [PostLogoutRedirectUri] = @PostLogoutRedirectUri
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

        SELECT *
        FROM [Client].[RedirectUri]
        WHERE RedirectUriId = SCOPE_IDENTITY();
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

        SELECT *
        FROM [Client].[RedirectUri]
        WHERE RedirectUriId = SCOPE_IDENTITY();
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
    SELECT *
    FROM [Client].[TokenSetting] a
    WHERE a.OrganizationId = @OrganizationId
      AND a.ApplicationId = @ApplicationId
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetTokenSettingById] @TokenSettingId INT
AS
BEGIN
    SELECT *
    FROM [Client].[TokenSetting] a
    WHERE a.TokenSettingId = @TokenSettingId
END
GO

CREATE OR ALTER PROCEDURE [Client].[TokenSettingExistsForApp] @OrganizationId INTEGER,
                                                              @ApplicationId INTEGER
AS
BEGIN
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

IF EXISTS (SELECT *
           FROM sys.objects
           WHERE object_id = OBJECT_ID(N'[dbo].[SavePasswordKeyStore]')
             AND type IN (N'P', N'PC'))
    BEGIN
        DROP PROCEDURE dbo.SavePasswordKeyStore;
    END
GO

CREATE OR ALTER PROCEDURE [Client].[GetSecretById] @SecretId INT
AS
BEGIN
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
    DELETE
    FROM [Client].[Secret]
    WHERE SecretId = @SecretId;
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetExternalSource] @Branding NVARCHAR(MAX)
AS
BEGIN
    SELECT es.SourceId,
           es.SourceCode,
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
    SELECT es.SourceId,
           es.SourceCode,
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
    SELECT *
    FROM [dbo].[GlobalConfig] r
    ORDER BY r.Name;
END
GO

CREATE OR ALTER PROCEDURE [Partner].[CreateOrganization] @Name NVARCHAR(255),
                                                         @Note NVARCHAR(1024)
AS
BEGIN
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

CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroup] @OrganizationId INT,
                                                           @GroupGuid UNIQUEIDENTIFIER
AS
BEGIN
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
    SELECT *
    FROM [dbo].[ResourceLibrary] rl
    ORDER BY rl.Name
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetResourceLibrary] @resourceLibraryGuid UNIQUEIDENTIFIER
AS
BEGIN
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
                                                @PasswordAuditLimit INT = 12
AS
BEGIN
    BEGIN TRY
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
                            0,
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
                            0,
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
                            0,
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
                            0,
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

        SELECT *
        FROM [Person].[Metadata]
        WHERE MetadataId = SCOPE_IDENTITY();
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

GO
--------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[UpdateUserEmail] @UserGuid UNIQUEIDENTIFIER,
                                                     @Email nvarchar(255),
                                                     @ModifiedOn datetime2(0) = NULL,
                                                     @ModifiedBy nvarchar(255) = NULL
AS
BEGIN
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
--------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[UpdateUserPassword] @UserGuid UNIQUEIDENTIFIER,
                                                        @Password varbinary(4000),
                                                        @Version int,
                                                        @ModifiedOn datetime2(0) = NULL,
                                                        @ModifiedBy nvarchar(255) = NULL,
                                                        @UnlockAccount bit = 0,
                                                        @PasswordAuditLimit int = 12
AS
BEGIN
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

GO
-----------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[UpdateUsername] @UserGuid UNIQUEIDENTIFIER,
                                                    @Username VARCHAR(255),
                                                    @ExternalId UNIQUEIDENTIFIER,
                                                    @ModifiedOn DATETIME2(0) = NULL,
                                                    @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
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

-----------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[UpdateUserSecuritySettings] @UserGuid UNIQUEIDENTIFIER,
                                                                @TwoFactorEnabled BIT,
                                                                @ModifiedOn DATETIME2(0) = NULL,
                                                                @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
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

-----------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[DeleteMetadata] @ProfileId INT
AS
BEGIN
    DELETE
    FROM [Person].[Metadata]
    WHERE ProfileId = @ProfileId;
END
GO

-----------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetMetadata] @ProfileId INT
AS
BEGIN
    SELECT *
    FROM [Person].[Metadata]
    WHERE ProfileId = @ProfileId;
END
GO

-----------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[UpdateUserStatus] @UserGuid UNIQUEIDENTIFIER,
                                                      @Status TINYINT,
                                                      @ModifiedOn DATETIME2(0) = NULL,
                                                      @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
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

GO
--------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserById] @ProfileId INT
AS
BEGIN
    SELECT p.*,
           cred.RowGuid    AS LoginId,
           esrc.SourceCode AS Branding
    FROM [Person].[Profile] p
             INNER JOIN [Person].[Credential] cred
                        ON p.ProfileId = cred.ProfileId
             INNER JOIN [dbo].[ExternalSource] esrc
                        ON cred.ExternalId = esrc.SourceId
    WHERE p.ProfileId = @ProfileId;
END
GO

--------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByUsername] @UserName NVARCHAR(255)
AS
BEGIN
    SELECT c.*
    FROM [Person].[Credential] c
    WHERE c.UserName = @UserName;
END
GO

--------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByUsernameAndBranding] @UserName NVARCHAR(255),
                                                                             @Branding NVARCHAR(255)
AS
BEGIN
    SELECT c.*,
           es.SourceCode AS Branding
    FROM [Person].[Credential] c
             JOIN [dbo].[ExternalSource] es
                  ON es.SourceId = c.ExternalId
                      AND es.SourceCode = @Branding
    WHERE c.UserName = @UserName;
END
GO

GO
--------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByGuid] @LoginId UNIQUEIDENTIFIER
AS
BEGIN
    SELECT c.*
    FROM [Person].[Credential] c
    WHERE c.RowGuid = @LoginId;
END
GO

--------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserByUsername] @Username NVARCHAR(255)
AS
BEGIN
    SELECT p.*,
           c.RowGuid       AS LoginId,
           esrc.SourceCode AS Branding
    FROM [Person].[Profile] p
             INNER JOIN [Person].[Credential] c
                        ON c.ProfileId = p.ProfileId
             INNER JOIN [dbo].[ExternalSource] esrc
                        ON c.ExternalId = esrc.SourceId
    WHERE c.UserName = @Username;
END
GO

--------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserByUsernameAndBranding] @Username NVARCHAR(255),
                                                                  @Branding NVARCHAR(255)
AS
BEGIN
    SELECT p.*,
           c.RowGuid     AS LoginId,
           es.SourceCode AS Branding
    FROM [Person].[Profile] p
             INNER JOIN [Person].[Credential] c
                        ON c.ProfileId = p.ProfileId
             INNER JOIN [dbo].[ExternalSource] es
                        ON es.SourceId = c.ExternalId
    WHERE c.UserName = @Username
      AND es.SourceCode = @Branding
      AND p.Status = 1;
END
GO

GO
------------------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserId] @UserGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT COUNT(p.ProfileId) OVER () AS Results,
           p.*,
           og.RowGuid                 AS GroupGuid,
           og.GroupName,
           cred.UserName,
           cred.RowGuid               AS LoginId,
           CASE
               WHEN o.RowGuid IS NOT NULL THEN o.RowGuid
               ELSE '00000000-0000-0000-0000-000000000000'
               END                    AS OrgId,
           esrc.SourceCode            AS Branding,
           org.RowGuid                AS ProfileOrgGuid
    FROM [Person].[Profile] p
             INNER JOIN [Person].[Credential] cred
                        ON cred.ProfileId = p.ProfileId
             INNER JOIN [dbo].[ExternalSource] esrc
                        ON cred.ExternalId = esrc.SourceId
             LEFT OUTER JOIN [Person].[ProfileGroup] pog
                             ON cred.ProfileId = pog.ProfileId
             LEFT OUTER JOIN [Partner].[OrganizationGroup] og
                             ON og.OrganizationGroupId = pog.OrganizationGroupId
             LEFT OUTER JOIN [Partner].[Organization] o
                             ON o.OrganizationId = og.OrganizationId
             JOIN [Person].[ProfileOrganization] profileOrg
                  ON profileOrg.ProfileId = p.ProfileId
             JOIN [Partner].[Organization] org
                  ON profileOrg.OrganizationId = org.OrganizationId
    WHERE p.RowGuid = @UserGuid
END
GO
------------------------------------------------------------
CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserIdAndBrandingV2] @UserGuid UNIQUEIDENTIFIER,
                                                                         @Branding NVARCHAR(255)
AS
BEGIN
    SELECT COUNT(p.ProfileId) OVER () AS Results,
           p.*,
           og.RowGuid                 AS GroupGuid,
           og.GroupName,
           cred.UserName,
           cred.RowGuid               AS LoginId,
           cred.SyncFlag              AS CredSyncFlag,
           CASE
               WHEN o.RowGuid IS NOT NULL THEN o.RowGuid
               ELSE org.RowGuid
               END                    AS OrgId,
           esrc.SourceCode            AS Branding,
           org.RowGuid                AS ProfileOrgGuid
    FROM [Person].[Profile] p
             INNER JOIN [Person].[Credential] cred
                        ON cred.ProfileId = p.ProfileId
             INNER JOIN [dbo].[ExternalSource] esrc
                        ON cred.ExternalId = esrc.SourceId
                            AND esrc.SourceCode = @Branding
             LEFT OUTER JOIN [Person].[ProfileGroup] pog
                             ON cred.ProfileId = pog.ProfileId
             LEFT OUTER JOIN [Partner].[OrganizationGroup] og
                             ON og.OrganizationGroupId = pog.OrganizationGroupId
             LEFT OUTER JOIN [Partner].[Organization] o
                             ON o.OrganizationId = og.OrganizationId
             JOIN [Person].[ProfileOrganization] profileOrg
                  ON profileOrg.ProfileId = p.ProfileId
             JOIN [Partner].[Organization] org
                  ON profileOrg.OrganizationId = org.OrganizationId
    WHERE p.RowGuid = @UserGuid
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUsers](
    @ResultsPerPage INT,
    @Offset INT,
    @Status TINYINT,
    @Search NVARCHAR(50),
    @Type NVARCHAR(50),
    @OrganizationGuid UNIQUEIDENTIFIER,
    @SourceIds NVARCHAR(MAX) = NULL
)
AS
BEGIN

    /* =========================================================
       FLOW 1–4 : ADMINISTRATOR
       ========================================================= */
    IF (@Type IS NOT NULL AND @Type = 'Administrator')
        BEGIN

            /* -------------------------------
               FLOW 1–2 : Admin + Search
               ------------------------------- */
            IF (@Search IS NOT NULL AND LEN(@Search) > 0)
                BEGIN
                    ;
                    WITH T_ProfileGroup AS
                             (SELECT p.ProfileId,
                                     og.OrganizationGroupId,
                                     og.GroupName,
                                     og.RowGuid AS GroupGuid,
                                     o.RowGuid  AS OrgGuid
                              FROM Person.Profile p
                                       JOIN Person.ProfileGroup pog
                                            ON p.ProfileId = pog.ProfileId
                                       JOIN Partner.OrganizationGroup og
                                            ON og.OrganizationGroupId = pog.OrganizationGroupId
                                       JOIN Partner.Organization o
                                            ON og.OrganizationId = o.OrganizationId
                              WHERE o.RowGuid = ISNULL(@OrganizationGuid, o.RowGuid)),
                         T AS
                             (SELECT p.ProfileId,
                                     esrc.SourceCode   AS Branding,
                                     po.OrganizationId AS ProfileOrgId,
                                     o.RowGuid         AS OrgId,
                                     pg.GroupName,
                                     pg.GroupGuid,
                                     pg.OrgGuid
                              FROM Person.Profile p
                                       INNER JOIN Person.Credential cred
                                                  ON p.ProfileId = cred.ProfileId
                                       INNER JOIN Person.ProfileOrganization po
                                                  ON p.ProfileId = po.ProfileId
                                       INNER JOIN Partner.Organization o
                                                  ON po.OrganizationId = o.OrganizationId
                                       INNER JOIN T_ProfileGroup pg
                                                  ON pg.ProfileId = p.ProfileId
                                       INNER JOIN dbo.ExternalSource esrc
                                                  ON cred.ExternalId = esrc.SourceId
                                       LEFT JOIN STRING_SPLIT(@SourceIds, ',') ext
                                                 ON ext.value = esrc.SourceId
                              WHERE (
                                  CONTAINS (p.Email, @Search)
                                      OR CONTAINS (p.FirstName, @Search)
                                      OR CONTAINS (p.LastName, @Search)
                                      OR CONTAINS (p.PhoneNumber, @Search)
                                      OR CONTAINS (p.SecondaryPhoneNumber, @Search)
                                  )
                                AND p.Status = ISNULL(@Status, p.Status)
                                AND pg.OrganizationGroupId IS NOT NULL
                                AND ISNULL(ext.value, '') =
                                    CASE
                                        WHEN @SourceIds IS NULL THEN ''
                                        ELSE ext.value
                                        END)
                    SELECT (SELECT COUNT(1) FROM T)                                     AS Results,
                           ROW_NUMBER() OVER (ORDER BY p.LastName ASC, p.FirstName ASC) AS Row,
                           p.*,
                           cred.UserName,
                           cred.RowGuid                                                 AS LoginId,
                           T.GroupName,
                           T.GroupGuid,
                           T.ProfileOrgId,
                           T.OrgId,
                           T.OrgGuid,
                           T.Branding,
                           T.OrgId                                                      AS ProfileOrgGuid
                    FROM Person.Profile p
                             INNER JOIN T
                                        ON T.ProfileId = p.ProfileId
                             INNER JOIN Person.Credential cred
                                        ON p.ProfileId = cred.ProfileId
                    ORDER BY p.LastName ASC, p.FirstName ASC
                    OFFSET @Offset ROWS FETCH NEXT @ResultsPerPage ROWS ONLY;
                END

                /* -------------------------------
                   FLOW 3–4 : Admin + NO Search
                   ------------------------------- */
            ELSE
                BEGIN
                    ;
                    WITH T_ProfileGroup AS
                             (SELECT p.ProfileId,
                                     og.OrganizationGroupId,
                                     og.GroupName,
                                     og.RowGuid AS GroupGuid,
                                     o.RowGuid  AS OrgGuid
                              FROM Person.Profile p
                                       JOIN Person.ProfileGroup pog
                                            ON p.ProfileId = pog.ProfileId
                                       JOIN Partner.OrganizationGroup og
                                            ON og.OrganizationGroupId = pog.OrganizationGroupId
                                       JOIN Partner.Organization o
                                            ON og.OrganizationId = o.OrganizationId
                              WHERE o.RowGuid = ISNULL(@OrganizationGuid, o.RowGuid)),
                         T AS
                             (SELECT p.ProfileId,
                                     esrc.SourceCode   AS Branding,
                                     po.OrganizationId AS ProfileOrgId,
                                     o.RowGuid         AS OrgId,
                                     pg.GroupName,
                                     pg.GroupGuid,
                                     pg.OrgGuid
                              FROM Person.Profile p
                                       INNER JOIN Person.Credential cred
                                                  ON p.ProfileId = cred.ProfileId
                                       INNER JOIN Person.ProfileOrganization po
                                                  ON p.ProfileId = po.ProfileId
                                       INNER JOIN Partner.Organization o
                                                  ON po.OrganizationId = o.OrganizationId
                                       INNER JOIN T_ProfileGroup pg
                                                  ON pg.ProfileId = p.ProfileId
                                       INNER JOIN dbo.ExternalSource esrc
                                                  ON cred.ExternalId = esrc.SourceId
                                       LEFT JOIN STRING_SPLIT(@SourceIds, ',') ext
                                                 ON ext.value = esrc.SourceId
                              WHERE p.Status = ISNULL(@Status, p.Status)
                                AND pg.OrganizationGroupId IS NOT NULL
                                AND ISNULL(ext.value, '') =
                                    CASE
                                        WHEN @SourceIds IS NULL THEN ''
                                        ELSE ext.value
                                        END)
                    SELECT (SELECT COUNT(1) FROM T)                                     AS Results,
                           ROW_NUMBER() OVER (ORDER BY p.LastName ASC, p.FirstName ASC) AS Row,
                           p.*,
                           cred.UserName,
                           cred.RowGuid                                                 AS LoginId,
                           T.GroupName,
                           T.GroupGuid,
                           T.ProfileOrgId,
                           T.OrgId,
                           T.OrgGuid,
                           T.Branding,
                           T.OrgId                                                      AS ProfileOrgGuid
                    FROM Person.Profile p
                             INNER JOIN T
                                        ON T.ProfileId = p.ProfileId
                             INNER JOIN Person.Credential cred
                                        ON p.ProfileId = cred.ProfileId
                    ORDER BY p.LastName ASC, p.FirstName ASC
                    OFFSET @Offset ROWS FETCH NEXT @ResultsPerPage ROWS ONLY;
                END
        END

/* =========================================================
   FLOW 5–8 : USER / NULL / OTHER
   ========================================================= */
    ELSE
        BEGIN

            /* -------------------------------
               FLOW 5–6 : User + Search
               ------------------------------- */
            IF (@Search IS NOT NULL AND LEN(@Search) > 0)
                BEGIN
                    ;
                    WITH T_ProfileGroup AS
                             (SELECT p.ProfileId,
                                     og.OrganizationGroupId,
                                     og.GroupName,
                                     og.RowGuid AS GroupGuid,
                                     o.RowGuid  AS OrgGuid
                              FROM Person.Profile p
                                       JOIN Person.ProfileGroup pog
                                            ON p.ProfileId = pog.ProfileId
                                       JOIN Partner.OrganizationGroup og
                                            ON og.OrganizationGroupId = pog.OrganizationGroupId
                                       JOIN Partner.Organization o
                                            ON og.OrganizationId = o.OrganizationId
                              WHERE o.RowGuid = ISNULL(@OrganizationGuid, o.RowGuid)),
                         T AS
                             (SELECT p.ProfileId,
                                     esrc.SourceCode   AS Branding,
                                     po.OrganizationId AS ProfileOrgId,
                                     o.RowGuid         AS OrgId,
                                     pg.GroupName,
                                     pg.GroupGuid,
                                     pg.OrgGuid
                              FROM Person.Profile p
                                       INNER JOIN Person.Credential cred
                                                  ON p.ProfileId = cred.ProfileId
                                       INNER JOIN Person.ProfileOrganization po
                                                  ON p.ProfileId = po.ProfileId
                                       INNER JOIN Partner.Organization o
                                                  ON po.OrganizationId = o.OrganizationId
                                       LEFT JOIN T_ProfileGroup pg
                                                 ON pg.ProfileId = p.ProfileId
                                       INNER JOIN dbo.ExternalSource esrc
                                                  ON cred.ExternalId = esrc.SourceId
                                       LEFT JOIN STRING_SPLIT(@SourceIds, ',') ext
                                                 ON ext.value = esrc.SourceId
                              WHERE (
                                  CONTAINS (p.Email, @Search)
                                      OR CONTAINS (p.FirstName, @Search)
                                      OR CONTAINS (p.LastName, @Search)
                                      OR CONTAINS (p.PhoneNumber, @Search)
                                      OR CONTAINS (p.SecondaryPhoneNumber, @Search)
                                  )
                                AND p.Status = ISNULL(@Status, p.Status)
                                AND 1 =
                                    CASE
                                        WHEN @Type IS NULL THEN 1
                                        WHEN @Type = 'User'
                                            AND pg.OrganizationGroupId IS NULL THEN 1
                                        ELSE 0
                                        END
                                AND ISNULL(ext.value, '') =
                                    CASE
                                        WHEN @SourceIds IS NULL THEN ''
                                        ELSE ext.value
                                        END)
                    SELECT (SELECT COUNT(1) FROM T)                                     AS Results,
                           ROW_NUMBER() OVER (ORDER BY p.LastName ASC, p.FirstName ASC) AS Row,
                           p.*,
                           cred.UserName,
                           cred.RowGuid                                                 AS LoginId,
                           T.GroupName,
                           T.GroupGuid,
                           T.ProfileOrgId,
                           T.OrgId,
                           T.OrgGuid,
                           T.Branding,
                           T.OrgId                                                      AS ProfileOrgGuid
                    FROM Person.Profile p
                             INNER JOIN T
                                        ON T.ProfileId = p.ProfileId
                             INNER JOIN Person.Credential cred
                                        ON p.ProfileId = cred.ProfileId
                    ORDER BY p.LastName ASC, p.FirstName ASC
                    OFFSET @Offset ROWS FETCH NEXT @ResultsPerPage ROWS ONLY;
                END

                /* -------------------------------
                   FLOW 7–8 : User + NO Search
                   ------------------------------- */
            ELSE
                BEGIN
                    ;
                    WITH T_ProfileGroup AS
                             (SELECT p.ProfileId,
                                     og.OrganizationGroupId,
                                     og.GroupName,
                                     og.RowGuid AS GroupGuid,
                                     o.RowGuid  AS OrgGuid
                              FROM Person.Profile p
                                       JOIN Person.ProfileGroup pog
                                            ON p.ProfileId = pog.ProfileId
                                       JOIN Partner.OrganizationGroup og
                                            ON og.OrganizationGroupId = pog.OrganizationGroupId
                                       JOIN Partner.Organization o
                                            ON og.OrganizationId = o.OrganizationId
                              WHERE o.RowGuid = ISNULL(@OrganizationGuid, o.RowGuid)),
                         T AS
                             (SELECT p.ProfileId,
                                     esrc.SourceCode   AS Branding,
                                     po.OrganizationId AS ProfileOrgId,
                                     o.RowGuid         AS OrgId,
                                     pg.GroupName,
                                     pg.GroupGuid,
                                     pg.OrgGuid
                              FROM Person.Profile p
                                       INNER JOIN Person.Credential cred
                                                  ON p.ProfileId = cred.ProfileId
                                       INNER JOIN Person.ProfileOrganization po
                                                  ON p.ProfileId = po.ProfileId
                                       INNER JOIN Partner.Organization o
                                                  ON po.OrganizationId = o.OrganizationId
                                       LEFT JOIN T_ProfileGroup pg
                                                 ON pg.ProfileId = p.ProfileId
                                       INNER JOIN dbo.ExternalSource esrc
                                                  ON cred.ExternalId = esrc.SourceId
                                       LEFT JOIN STRING_SPLIT(@SourceIds, ',') ext
                                                 ON ext.value = esrc.SourceId
                              WHERE p.Status = ISNULL(@Status, p.Status)
                                AND 1 =
                                    CASE
                                        WHEN @Type IS NULL THEN 1
                                        WHEN @Type = 'User'
                                            AND pg.OrganizationGroupId IS NULL THEN 1
                                        ELSE 0
                                        END
                                AND ISNULL(ext.value, '') =
                                    CASE
                                        WHEN @SourceIds IS NULL THEN ''
                                        ELSE ext.value
                                        END)
                    SELECT (SELECT COUNT(1) FROM T)                                     AS Results,
                           ROW_NUMBER() OVER (ORDER BY p.LastName ASC, p.FirstName ASC) AS Row,
                           p.*,
                           cred.UserName,
                           cred.RowGuid                                                 AS LoginId,
                           T.GroupName,
                           T.GroupGuid,
                           T.ProfileOrgId,
                           T.OrgId,
                           T.OrgGuid,
                           T.Branding,
                           T.OrgId                                                      AS ProfileOrgGuid
                    FROM Person.Profile p
                             INNER JOIN T
                                        ON T.ProfileId = p.ProfileId
                             INNER JOIN Person.Credential cred
                                        ON p.ProfileId = cred.ProfileId
                    ORDER BY p.LastName ASC, p.FirstName ASC
                    OFFSET @Offset ROWS FETCH NEXT @ResultsPerPage ROWS ONLY;
                END
        END

END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserAuthDetailsByUserName] @Username NVARCHAR(255)
AS
BEGIN
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
    SELECT ogp.*
    FROM [Partner].[OrganizationGroupPermission] ogp
             INNER JOIN [Person].[ProfileGroup] pg
                        ON pg.OrganizationGroupId = ogp.OrganizationGroupId
    WHERE pg.ProfileId = @UserId
      AND ogp.Status = 1
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateAccessFailedCount]
    @Username NVARCHAR(255),
    @LoginSuccess BIT
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION

            DECLARE @CurrentCount INT

            SELECT
                @CurrentCount = AccessFailedCount
            FROM Person.Credential
            WHERE UserName = @Username

            IF @LoginSuccess = 1
                BEGIN
                    -- If success, reset AccessFailedCount to 0, unlock credential, set status to active
                    UPDATE Person.Credential
                    SET
                        AccessFailedCount = 0,
                        CredentialLocked = 0,
                        Status = 1,
                        LastLogin = GETUTCDATE()
                    WHERE UserName = @Username
                END
            ELSE
                BEGIN
                    -- If failure, increment AccessFailedCount
                    UPDATE Person.Credential
                    SET AccessFailedCount = @CurrentCount + 1
                    WHERE UserName = @Username

                    -- Check if AccessFailedCount reached threshold
                    IF (@CurrentCount + 1) >= 3
                        BEGIN
                            -- Lock account and mark inactive
                            UPDATE Person.Credential
                            SET
                                Status = 0,
                                CredentialLocked = 1
                            WHERE UserName = @Username
                        END
                END

        COMMIT;

        EXEC [Person].[GetUserAuthDetailsByUserName] @Username
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UpdateAccessFailedCountWithExternalSourceCode]
    @Username NVARCHAR(255),
    @SourceCode NVARCHAR(255),
    @LoginSuccess BIT,
    @AccessFailedLimit INT
AS
BEGIN
    BEGIN TRY
        DECLARE @CurrentCount INT

        BEGIN TRANSACTION

            -- Check if login was successful
            IF @LoginSuccess = 1
                BEGIN
                    -- If success, reset AccessFailedCount, unlock credential, set status to active
                    EXEC Person.UnLockAccount @Username, @SourceCode

                    UPDATE cred
                    SET cred.LastLogin = GETUTCDATE()
                    FROM Person.Credential cred
                             INNER JOIN [dbo].[ExternalSource] es
                                        ON cred.ExternalId = es.SourceId
                    WHERE cred.UserName = @Username
                      AND es.SourceCode = @SourceCode
                END
            ELSE
                BEGIN
                    -- Get current AccessFailedCount
                    SELECT
                        @CurrentCount = AccessFailedCount
                    FROM Person.Credential cred
                             INNER JOIN [dbo].[ExternalSource] es
                                        ON cred.ExternalId = es.SourceId
                    WHERE cred.UserName = @Username
                      AND es.SourceCode = @SourceCode

                    -- If failure, increment AccessFailedCount
                    UPDATE cred
                    SET cred.AccessFailedCount = @CurrentCount + 1
                    FROM Person.Credential cred
                             INNER JOIN [dbo].[ExternalSource] es
                                        ON cred.ExternalId = es.SourceId
                    WHERE cred.UserName = @Username
                      AND es.SourceCode = @SourceCode

                    -- Check if AccessFailedCount has reached the limit
                    IF (@CurrentCount + 1) >= @AccessFailedLimit
                        BEGIN
                            -- Lock account and mark inactive
                            EXEC Person.LockAccount
                                 @Username,
                                 @SourceCode,
                                 @AccessFailedLimit
                        END
                END

        COMMIT;

        -- Return updated auth details
        EXEC [Person].[GetUserAuthDetailsByUserNameAndExternalSourceCode]
             @Username,
             @SourceCode
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Person].[UnLockAccount]
    @Username NVARCHAR(255),
    @SourceCode NVARCHAR(255)
AS
BEGIN
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

CREATE OR ALTER PROCEDURE [Person].[ReactivateUserWithPassword]
    @UserGuid UNIQUEIDENTIFIER,
    @FirstName NVARCHAR(255),
    @LastName NVARCHAR(255),
    @PhoneNumber NVARCHAR(128),
    @Email NVARCHAR(255),
    @MemberGuid UNIQUEIDENTIFIER,
    @LoginGuid UNIQUEIDENTIFIER,
    @SecondaryPhoneNumber NVARCHAR(128) = NULL,
    @Username VARCHAR(255),
    @ExternalId UNIQUEIDENTIFIER,
    @Password VARBINARY(4000),
    @Version INT,
    @UnLockAccount BIT = 1,
    @PasswordAuditLimit INT = 12,
    @ModifiedOn DATETIME2(0) = NULL,
    @ModifiedBy NVARCHAR(255) = NULL
AS
BEGIN
    -- The stored procedure [Person].[ReactivateUserWithPassword] should
    -- only be run for an inactive user. If the user does not exist, or the
    -- status of the user is 1 (an active user), the user will not be updated.

    DECLARE @Status TINYINT;
    DECLARE @Title NVARCHAR(5);
    DECLARE @MiddleInitial NCHAR(1);
    DECLARE @Suffix INT;

    SET @Status = 1;

    -- If the user does not exist or is not inactive, do nothing
    IF NOT EXISTS (
        SELECT 1
        FROM [Person].[Profile]
        WHERE RowGuid = @UserGuid
          AND Status = 0
    )
        RETURN;

    -- Preserve existing name-related fields
    SELECT
        @MiddleInitial = MiddleInitial,
        @Title = Title,
        @Suffix = Suffix
    FROM [Person].[Profile]
    WHERE RowGuid = @UserGuid;

    BEGIN TRY
        BEGIN TRANSACTION;

        -- Reactivate user
        EXEC [Person].[UpdateUserStatus]
             @UserGuid,
             @Status,
             @ModifiedOn,
             @ModifiedBy;

        -- Update profile details
        EXEC [Person].[UpdateUserProfile]
             @UserGuid,
             @FirstName,
             @LastName,
             @Title,
             @MiddleInitial,
             @PhoneNumber,
             @Suffix,
             @MemberGuid,
             @LoginGuid,
             @ModifiedOn,
             @ModifiedBy,
             @SecondaryPhoneNumber;

        -- Update username
        EXEC [Person].[UpdateUsername]
             @UserGuid,
             @Username,
             @ExternalId,
             @ModifiedOn,
             @ModifiedBy;

        -- Update email
        EXEC [Person].[UpdateUserEmail]
             @UserGuid,
             @Email,
             @ModifiedOn,
             @ModifiedBy;

        EXEC [Person].[UpdateUserPassword_V2]
             @UserGuid,
             @Password,
             @Version,
             @ModifiedOn,
             @ModifiedBy,
             @ExternalId;

        COMMIT;

        -- Return ProfileId
        SELECT ProfileId
        FROM [Person].[Profile]
        WHERE RowGuid = @UserGuid;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO


CREATE OR ALTER PROCEDURE [Token].[CreateAuthCode]
    @ApplicationId INTEGER,
    @SessionId UNIQUEIDENTIFIER,
    @Data NVARCHAR(512)
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION

            INSERT INTO [Token].[AuthCode]
            (
                [ApplicationId],
                [SessionId],
                [Data],
                [Datahash]
            )
            VALUES
                (
                    @ApplicationId,
                    @SessionId,
                    @Data,
                    HASHBYTES('SHA2_256', @Data)
                );

        COMMIT;

        DECLARE @AuthCodeId INT;
        SET @AuthCodeId = SCOPE_IDENTITY();

        SELECT @AuthCodeId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetAuthCodeById]
@AuthCodeId INTEGER
AS
BEGIN
    -- SELECT * FROM [Token].[AuthCode] c
    -- WHERE c.AuthCodeId = @AuthCodeId

    DECLARE @daynumber INT = DATEPART(DAYOFYEAR, GETUTCDATE());

    IF EXISTS (
        SELECT 1
        FROM [Token].[AuthCode] c
        WHERE c.AuthCodeId = @AuthCodeId
          AND daynumber = @daynumber
    )
        SELECT *
        FROM [Token].[AuthCode] c
        WHERE c.AuthCodeId = @AuthCodeId
          AND daynumber = @daynumber;
    ELSE
        SELECT *
        FROM [Token].[AuthCode] c
        WHERE c.AuthCodeId = @AuthCodeId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetSessionIdByAuthCode]
@Data NVARCHAR(512)
AS
BEGIN
    SELECT c.SessionId
    FROM [Token].[AuthCode] c
    WHERE c.DataHash = HASHBYTES('SHA2_256', @Data)
      AND (c.Expiration IS NULL OR GETUTCDATE() < c.Expiration)
      AND c.ConsumedOn IS NULL;
END
GO

CREATE OR ALTER PROCEDURE [Token].[SetAuthCodeConsumedOn]
@Data NVARCHAR(512)
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION

            UPDATE [Token].[AuthCode]
            SET [ConsumedOn] = GETUTCDATE()
            WHERE DataHash = HASHBYTES('SHA2_256', @Data);

            UPDATE [Token].[Token]
            SET [ConsumedOn] = GETUTCDATE()
            WHERE DataHash = HASHBYTES('SHA2_256', @Data);

        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO


CREATE OR ALTER PROCEDURE [dbo].[GetEnums]
AS
BEGIN
    SELECT
        MAX(CASE WHEN [Code] = 'Organization' THEN [EnumId] END) AS [OrganizationEnumId],
        MAX(CASE WHEN [Code] = 'PublicKey' THEN [EnumId] END) AS [PublicKeyEnumId],
        MAX(CASE WHEN [Code] = 'RS256' THEN [EnumId] END) AS [RS256EnumId],
        MAX(CASE WHEN [Code] = 'ES256' THEN [EnumId] END) AS [ES256EnumId],
        MAX(CASE WHEN [Code] = 'HS256' THEN [EnumId] END) AS [HS256EnumId],
        MAX(CASE WHEN [Code] = 'Mobile' THEN [EnumId] END) AS [MobileEnumId],
        MAX(CASE WHEN [Code] = 'Web' THEN [EnumId] END) AS [WebEnumId],
        MAX(CASE WHEN [Code] = 'Server' THEN [EnumId] END) AS [ServerEnumId],
        MAX(CASE WHEN [Code] = 'AuthCode' THEN [EnumId] END) AS [AuthCodeEnumId],
        MAX(CASE WHEN [Code] = 'ClientSecretJWT' THEN [EnumId] END) AS [ClientSecretJWTEnumId],
        MAX(CASE WHEN [Code] = 'PrivateKeyJWT' THEN [EnumId] END) AS [PrivateKeyJWTEnumId],
        MAX(CASE WHEN [Code] = 'Access Token' THEN [EnumId] END) AS [AccessTokenEnumId],
        MAX(CASE WHEN [Code] = 'Refresh Token' THEN [EnumId] END) AS [RefreshTokenEnumId],
        MAX(CASE WHEN [Code] = 'OIDC Token' THEN [EnumId] END) AS [OIDCTokenEnumId],
        MAX(CASE WHEN [Code] = 'Auth Code Token' THEN [EnumId] END) AS [AuthCodeTokenEnumId],
        MAX(CASE WHEN [Code] = 'Session Active' THEN [EnumId] END) AS [AuthSessionActiveEnumId],
        MAX(CASE WHEN [Code] = 'Session Inactive' THEN [EnumId] END) AS [AuthSessionInactiveEnumId],
        MAX(CASE WHEN [Code] = 'self-contained' THEN [EnumId] END) AS [SelfContainedAccessTokenFormatEnumId],
        MAX(CASE WHEN [Code] = 'reference' THEN [EnumId] END) AS [ReferenceAccessTokenFormatEnumId],
        MAX(CASE WHEN [Code] = 'Mr' THEN [EnumId] END) AS [MrEnumId],
        MAX(CASE WHEN [Code] = 'Mrs' THEN [EnumId] END) AS [MrsEnumId],
        MAX(CASE WHEN [Code] = 'Miss' THEN [EnumId] END) AS [MissEnumId],
        MAX(CASE WHEN [Code] = 'Sr' THEN [EnumId] END) AS [SrEnumId],
        MAX(CASE WHEN [Code] = 'Jr' THEN [EnumId] END) AS [JrEnumId],
        MAX(CASE WHEN [Code] = 'Inactive' THEN [EnumId] END) AS [InactiveEnumId],
        MAX(CASE WHEN [Code] = 'Active' THEN [EnumId] END) AS [ActiveEnumId],
        MAX(CASE WHEN [Code] = 'Disabled' THEN [EnumId] END) AS [DisabledEnumId],
        MAX(CASE WHEN [Code] = 'Username' THEN [EnumId] END) AS [UsernameEnumId],
        MAX(CASE WHEN [Code] = 'Email' THEN [EnumId] END) AS [EmailEnumId]
    FROM [dbo].[Enum];
END
GO

CREATE OR ALTER PROCEDURE [Client].[GetMFARealms]
AS
BEGIN
    SELECT *
    FROM [Client].[MFARealm]
    WHERE Active = 1;
END
GO

CREATE OR ALTER PROCEDURE [Token].[CreateTokenWithHash]
    @TokenTypeId INTEGER,
    @ApplicationId INTEGER,
    @SubjectId NVARCHAR(200),
    @SessionId NVARCHAR(100),
    @IsOpaque BIT,
    @Data NVARCHAR(MAX),
    @SigningKey VARBINARY(MAX),
    @TimeToLive INT,
    @DataHash BINARY(32)
AS
BEGIN
    BEGIN TRY
        DECLARE @Expiration DATETIME2 = '01/01/1900';
        DECLARE @UTCNow DATETIME2 = GETUTCDATE();

        SET @Expiration = DATEADD(SECOND, @TimeToLive, @UTCNow);

        BEGIN TRANSACTION;

        INSERT INTO [Token].[Token]
        (
            [TokenTypeId],
            [ApplicationId],
            [SubjectId],
            [SessionId],
            [IsOpaque],
            [Data],
            [CreatedOn],
            [Expiration],
            [SigningKey],
            [DataHash]
        )
        VALUES
            (
                @TokenTypeId,
                @ApplicationId,
                @SubjectId,
                @SessionId,
                @IsOpaque,
                @Data,
                @UTCNow,
                @Expiration,
                @SigningKey,
                @DataHash -- HASHBYTES('SHA2_256', @Data)
            );

        COMMIT;

        DECLARE @TokenId INT;
        SET @TokenId = SCOPE_IDENTITY();

        SELECT @TokenId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO


CREATE OR ALTER PROCEDURE [Token].[GetTokenById]
@TokenId INTEGER
AS
BEGIN
    -- SELECT * FROM [Token].[Token] t
    -- WHERE t.TokenId = @TokenId

    DECLARE @daynumber INT = DATEPART(DAYOFYEAR, GETUTCDATE());

    IF EXISTS (
        SELECT 1
        FROM [Token].[Token] t
        WHERE t.TokenId = @TokenId
          AND daynumber = @daynumber
    )
        SELECT *
        FROM [Token].[Token] t
        WHERE t.TokenId = @TokenId
          AND daynumber = @daynumber;
    ELSE
        SELECT *
        FROM [Token].[Token] t
        WHERE t.TokenId = @TokenId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetTokenByValueHash]
    @hashValue BINARY(32),
    @TokenTypeId INTEGER
AS
BEGIN
    -- SELECT * FROM [Token].[Token] t
    -- WHERE t.DataHash = HASHBYTES('SHA2_256', @Value)
    --   AND t.TokenTypeId = @TokenTypeId

    DECLARE @daynumber INT = DATEPART(DAYOFYEAR, GETUTCDATE());

    IF EXISTS (
        SELECT *
        FROM [Token].[Token] t
        WHERE t.DataHash = @hashValue
          AND t.TokenTypeId = @TokenTypeId
          AND daynumber = @daynumber
    )
        SELECT *
        FROM [Token].[Token] t
        WHERE t.DataHash = @hashValue
          AND t.TokenTypeId = @TokenTypeId
          AND daynumber = @daynumber;
    ELSE
        SELECT *
        FROM [Token].[Token] t
        WHERE t.DataHash = @hashValue
          AND t.TokenTypeId = @TokenTypeId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetAllActiveTokensBySessionId]
@SessionId UNIQUEIDENTIFIER
AS
BEGIN
    SELECT *
    FROM [Token].[Token] t
    WHERE t.SessionId = @SessionId
      AND (
        t.Expiration IS NULL
            OR t.Expiration >= CAST(GETUTCDATE() AS DATE)
        );
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetTokensByClientIdAndRequestDateTime]
    @ClientId NCHAR(32),
    @requestDatetime DATETIME2(7)
AS
BEGIN
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

CREATE OR ALTER PROCEDURE [Token].[InsertSsoCookie]
    @SessionId UNIQUEIDENTIFIER,
    @EncryptedSessionId VARBINARY(4000),
    @EncryptionKey VARBINARY(4000)
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION;

        INSERT INTO [Token].[Sso]
        (
            SessionID,
            EncryptedSessionID,
            EncryptionKey
        )
        VALUES
            (
                @SessionId,
                @EncryptedSessionId,
                @EncryptionKey
            );

        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Token].[FindSsoCookieByEncryptedSessionId]
@HashedEncryptedSessionId BINARY(32)
AS
BEGIN
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

CREATE OR ALTER PROCEDURE [Token].[GetAuthSessionById]
@AuthSessionId INTEGER
AS
BEGIN
    -- SELECT s.*, r.RedirectUri
    -- FROM [Token].[AuthSession] s
    -- LEFT JOIN [Client].[RedirectUri] r
    --     ON r.RedirectUriId = s.RedirectUriId
    -- WHERE s.AuthSessionId = @AuthSessionId

    DECLARE @daynumber INT = DATEPART(DAYOFYEAR, GETUTCDATE());

    IF EXISTS (
        SELECT 1
        FROM [Token].[AuthSession]
        WHERE AuthSessionId = @AuthSessionId
          AND daynumber = @daynumber
    )
        SELECT s.*, r.RedirectUri
        FROM [Token].[AuthSession] s
                 LEFT JOIN [Client].[RedirectUri] r
                           ON r.RedirectUriId = s.RedirectUriId
        WHERE s.AuthSessionId = @AuthSessionId
          AND daynumber = @daynumber;
    ELSE
        SELECT s.*, r.RedirectUri
        FROM [Token].[AuthSession] s
                 LEFT JOIN [Client].[RedirectUri] r
                           ON r.RedirectUriId = s.RedirectUriId
        WHERE s.AuthSessionId = @AuthSessionId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetAuthSessionBySessionId]
@SessionId UNIQUEIDENTIFIER
AS
BEGIN
    -- SELECT s.*, r.RedirectUri
    -- FROM [Token].[AuthSession] s
    -- LEFT JOIN [Client].[RedirectUri] r
    --     ON r.RedirectUriId = s.RedirectUriId
    -- WHERE s.SessionId = @SessionId

    DECLARE @daynumber INT = DATEPART(DAYOFYEAR, GETUTCDATE());

    IF EXISTS (
        SELECT 1
        FROM [Token].[AuthSession]
        WHERE SessionId = @SessionId
          AND daynumber = @daynumber
    )
        SELECT s.*, r.RedirectUri
        FROM [Token].[AuthSession] s
                 LEFT JOIN [Client].[RedirectUri] r
                           ON r.RedirectUriId = s.RedirectUriId
        WHERE s.SessionId = @SessionId
          AND daynumber = @daynumber;
    ELSE
        SELECT s.*, r.RedirectUri
        FROM [Token].[AuthSession] s
                 LEFT JOIN [Client].[RedirectUri] r
                           ON r.RedirectUriId = s.RedirectUriId
        WHERE s.SessionId = @SessionId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[SetAuthSessionInactive]
@SessionId UNIQUEIDENTIFIER
AS
BEGIN
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

    SELECT TOP 1 *
    FROM [Token].[AuthSession] s
    WHERE @AuthSessionActiveId = s.AuthSessionStatusId
      AND @SubjectId = s.SubjectId
    ORDER BY CreatedOn DESC;
END
GO

CREATE OR ALTER PROCEDURE [Token].[SetBrandingAndRedirectUri]
    @SessionId UNIQUEIDENTIFIER,
    @Branding NVARCHAR(100),
    @RedirectUri NVARCHAR(1024),
    @ApplicationId INT
AS
BEGIN
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


CREATE OR ALTER PROCEDURE [Token].[CreatePkce]
    @ApplicationId INTEGER,
    @SessionId UNIQUEIDENTIFIER,
    @Data NVARCHAR(2048),
    @Algorithm NVARCHAR(30),
    @RedirectUri NVARCHAR(2000)
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION;

        INSERT INTO [Token].[Pkce]
        (
            [ApplicationId],
            [SessionId],
            [Data],
            [Algorithm],
            [RedirectUri]
        )
        VALUES
            (
                @ApplicationId,
                @SessionId,
                @Data,
                @Algorithm,
                @RedirectUri
            );

        COMMIT;

        DECLARE @PkceId INT;
        SET @PkceId = SCOPE_IDENTITY();

        SELECT @PkceId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetPkceById]
@PkceId INTEGER
AS
BEGIN
    --SELECT * FROM [Token].[Pkce] p
    --WHERE p.PkceId = @PkceId

    DECLARE @daynumber INT = DATEPART(DAYOFYEAR, GETUTCDATE());

    IF EXISTS (
        SELECT 1
        FROM [Token].[Pkce] p
        WHERE p.PkceId = @PkceId
          AND daynumber = @daynumber
    )
        SELECT *
        FROM [Token].[Pkce] p
        WHERE p.PkceId = @PkceId
          AND daynumber = @daynumber;
    ELSE
        SELECT *
        FROM [Token].[Pkce] p
        WHERE p.PkceId = @PkceId;
END
GO

CREATE OR ALTER PROCEDURE [Token].[GetPkceBySessionId]
@SessionId UNIQUEIDENTIFIER
AS
BEGIN
    --SELECT * FROM [Token].[Pkce] p
    --WHERE p.SessionId = @SessionId

    DECLARE @daynumber INT = DATEPART(DAYOFYEAR, GETUTCDATE());

    IF EXISTS (
        SELECT 1
        FROM [Token].[Pkce] p
        WHERE p.SessionId = @SessionId
          AND daynumber = @daynumber
    )
        SELECT *
        FROM [Token].[Pkce] p
        WHERE p.SessionId = @SessionId
          AND daynumber = @daynumber;
    ELSE
        SELECT *
        FROM [Token].[Pkce] p
        WHERE p.SessionId = @SessionId;
END
GO

CREATE OR ALTER PROCEDURE [Partner].[SavePasswordKeyStore]
    @PasswordKeyStoreBytes VARBINARY(MAX),
    @PasswordKeyId UNIQUEIDENTIFIER,
    @NewId INT OUT
AS
BEGIN
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
    SELECT @OrgId = OrganizationId
    FROM [Partner].[Organization]
    WHERE RowGuid = @Guid;
END
GO
