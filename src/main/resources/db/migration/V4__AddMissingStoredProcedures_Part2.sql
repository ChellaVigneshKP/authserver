-- =============================================
-- Add All Missing Stored Procedures - Part 2
-- Client Schema Procedures
-- =============================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- =============================================
-- Client Schema Stored Procedures
-- =============================================

-- Client.GetApplicationByClientId
CREATE OR ALTER PROCEDURE [Client].[GetApplicationByClientId]
    @clientId NCHAR(32)
AS
BEGIN
    SELECT * FROM [Client].[Application] WHERE [ClientId] = @clientId;
END
GO

-- Client.UpdateApplication
CREATE OR ALTER PROCEDURE [Client].[UpdateApplication]
    @orgId INT,
    @appId INT,
    @name NVARCHAR(255),
    @description NVARCHAR(1024),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255),
    @allowForgotUsername BIT,
    @usernameType INT,
    @forgotUserNameSetting [dbo].[ForgetUserNameType] READONLY,
    @pinTimeToLive INT
AS
BEGIN
    UPDATE [Client].[Application]
    SET [Name] = @name,
        [Description] = @description,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy,
        [AllowForgotUsername] = @allowForgotUsername,
        [UsernameType] = @usernameType,
        [PinTimeToLive] = @pinTimeToLive
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId;
END
GO

-- Client.CreateCredential
CREATE OR ALTER PROCEDURE [Client].[CreateCredential]
    @orgId INT,
    @appId INT,
    @name NVARCHAR(1024),
    @secretId INT,
    @certificateId INT,
    @algorithmId INT,
    @expiration DATETIME2(0),
    @authFlowId INT,
    @fingerprint NVARCHAR(1024),
    @credentialStatus INT
AS
BEGIN
    INSERT INTO [Client].[Credential]
        ([OrganizationId], [ApplicationId], [Name], [SecretId], [CertificateId], [TokenAlgorithmId], [ExpireOn], [AuthFlowId], [Fingerprint], [CredentialStatusId], [Status])
    VALUES
        (@orgId, @appId, @name, @secretId, @certificateId, @algorithmId, @expiration, @authFlowId, @fingerprint, @credentialStatus, 1);
    
    SELECT * FROM [Client].[Credential] WHERE [CredentialId] = SCOPE_IDENTITY();
END
GO

-- Client.GetCredentials
CREATE OR ALTER PROCEDURE [Client].[GetCredentials]
    @orgId INT,
    @appId INT
AS
BEGIN
    SELECT * FROM [Client].[Credential]
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId
    ORDER BY [CreatedOn] DESC;
END
GO

-- Client.GetActiveCredentials
CREATE OR ALTER PROCEDURE [Client].[GetActiveCredentials]
    @appId INT
AS
BEGIN
    SELECT * FROM [Client].[Credential]
    WHERE [ApplicationId] = @appId
      AND [Status] = 1
    ORDER BY [CreatedOn] DESC;
END
GO

-- Client.GetCredentialByGuid
CREATE OR ALTER PROCEDURE [Client].[GetCredentialByGuid]
    @orgId INT,
    @appId INT,
    @credentialGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT * FROM [Client].[Credential]
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId
      AND [RowGuid] = @credentialGuid;
END
GO

-- Client.GetCredentialsByAuthFLow
CREATE OR ALTER PROCEDURE [Client].[GetCredentialsByAuthFLow]
    @orgId INT,
    @appId INT,
    @authFlow INT
AS
BEGIN
    SELECT * FROM [Client].[Credential]
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId
      AND [AuthFlowId] = @authFlow
      AND [Status] = 1
    ORDER BY [CreatedOn] DESC;
END
GO

-- Client.UpdateCredentialStatus
CREATE OR ALTER PROCEDURE [Client].[UpdateCredentialStatus]
    @orgId INT,
    @appId INT,
    @credentialGuid UNIQUEIDENTIFIER,
    @status TINYINT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Client].[Credential]
    SET [Status] = @status,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId
      AND [RowGuid] = @credentialGuid;
END
GO

-- Client.CreateSecret
CREATE OR ALTER PROCEDURE [Client].[CreateSecret]
    @orgId INT,
    @appId INT,
    @description NVARCHAR(1024),
    @secretHash VARBINARY(MAX),
    @expiration DATETIME2(0),
    @mainKeyStoreBytes VARBINARY(MAX),
    @passwordKeyStoreBytes VARBINARY(MAX),
    @passwordKeyId UNIQUEIDENTIFIER
AS
BEGIN
    INSERT INTO [Client].[Secret]
        ([OrganizationId], [ApplicationId], [Description], [SecretHashValue], [ExpireOn], [KeyStore], [PasswordKeyId])
    VALUES
        (@orgId, @appId, @description, @secretHash, @expiration, @mainKeyStoreBytes, @passwordKeyId);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Client.GetSecrets
CREATE OR ALTER PROCEDURE [Client].[GetSecrets]
    @orgId INT,
    @appId INT
AS
BEGIN
    SELECT * FROM [Client].[Secret]
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId
    ORDER BY [CreatedOn] DESC;
END
GO

-- Client.GetSecretById
CREATE OR ALTER PROCEDURE [Client].[GetSecretById]
    @secretId INT
AS
BEGIN
    SELECT * FROM [Client].[Secret] WHERE [SecretId] = @secretId;
END
GO

-- Client.DeleteSecret
CREATE OR ALTER PROCEDURE [Client].[DeleteSecret]
    @secretId INT
AS
BEGIN
    DELETE FROM [Client].[Secret] WHERE [SecretId] = @secretId;
END
GO

-- Client.CreateTokenSetting
CREATE OR ALTER PROCEDURE [Client].[CreateTokenSetting]
    @orgId INT,
    @appId INT,
    @authCodeTimeToLive INT,
    @accessTokenTimeToLive INT,
    @refreshTokenTimeToLive INT,
    @reuseRefreshTokens BIT,
    @accessTokenFormatId INT,
    @maxRequestTransitTime INT
AS
BEGIN
    INSERT INTO [Client].[TokenSetting]
        ([OrganizationId], [ApplicationId], [AuthCodeTimeToLive], [AccessTokenTimeToLive], [RefreshTokenTimeToLive], [ReuseRefreshTokens], [AccessTokenFormatId], [MaxRequestTransitTime])
    VALUES
        (@orgId, @appId, @authCodeTimeToLive, @accessTokenTimeToLive, @refreshTokenTimeToLive, @reuseRefreshTokens, @accessTokenFormatId, @maxRequestTransitTime);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Client.UpdateTokenSetting
CREATE OR ALTER PROCEDURE [Client].[UpdateTokenSetting]
    @orgId INT,
    @appId INT,
    @authCodeTimeToLive INT,
    @accessTokenTimeToLive INT,
    @refreshTokenTimeToLive INT,
    @reuseRefreshTokens BIT,
    @deviceCodeTimeToLive INT,
    @maxRequestTransitTime INT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Client].[TokenSetting]
    SET [AuthCodeTimeToLive] = @authCodeTimeToLive,
        [AccessTokenTimeToLive] = @accessTokenTimeToLive,
        [RefreshTokenTimeToLive] = @refreshTokenTimeToLive,
        [ReuseRefreshTokens] = @reuseRefreshTokens,
        [DeviceCodeTimeToLive] = @deviceCodeTimeToLive,
        [MaxRequestTransitTime] = @maxRequestTransitTime,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId;
END
GO

-- Client.GetTokenSettingForApp
CREATE OR ALTER PROCEDURE [Client].[GetTokenSettingForApp]
    @orgId INT,
    @appId INT
AS
BEGIN
    SELECT * FROM [Client].[TokenSetting]
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId;
END
GO

-- Client.GetTokenSettingById
CREATE OR ALTER PROCEDURE [Client].[GetTokenSettingById]
    @tokenSettingId INT
AS
BEGIN
    SELECT * FROM [Client].[TokenSetting] WHERE [TokenSettingId] = @tokenSettingId;
END
GO

-- Client.GetSettingsByApplicationId
CREATE OR ALTER PROCEDURE [Client].[GetSettingsByApplicationId]
    @appId INT
AS
BEGIN
    SELECT * FROM [Client].[TokenSetting] WHERE [ApplicationId] = @appId;
END
GO

-- Client.SaveCertificate
CREATE OR ALTER PROCEDURE [Client].[SaveCertificate]
    @OrgGuid UNIQUEIDENTIFIER,
    @CertificateName NVARCHAR(255),
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
    DECLARE @orgId INT;
    SELECT @orgId = OrganizationId FROM [Partner].[Organization] WHERE [RowGuid] = @OrgGuid;
    
    INSERT INTO [Partner].[Certificate]
        ([OrganizationId], [CertificateName], [CertificateTypeId], [IsX509Certificate], [KeyStore], [Status], [Fingerprint], [Thumbprint], [Subject], [Issuer], [ValidFrom], [ValidTo], [PasswordKeyStore], [PasswordKeyId])
    VALUES
        (@orgId, @CertificateName, @CertificateTypeId, @IsX509Certificate, @KeyStoreBytes, @Status, @Fingerprint, @Thumbprint, @Subject, @Issuer, @ValidFrom, @ValidTo, @PasswordKeyStoreBytes, @PasswordKeyId);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Client.CreateRedirectUri
CREATE OR ALTER PROCEDURE [Client].[CreateRedirectUri]
    @orgId INT,
    @appId INT,
    @uri NVARCHAR(2000),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    INSERT INTO [Client].[RedirectUri]
        ([OrganizationId], [ApplicationId], [RedirectUri], [CreatedOn], [ModifiedOn], [ModifiedBy])
    VALUES
        (@orgId, @appId, @uri, GETUTCDATE(), @modifiedOn, @modifiedBy);
END
GO

-- Client.UpdateRedirectUri
CREATE OR ALTER PROCEDURE [Client].[UpdateRedirectUri]
    @orgId INT,
    @appId INT,
    @uri NVARCHAR(2000)
AS
BEGIN
    -- This might be a batch update, handled by deleting and recreating
    -- Or it could be a direct update if there's a specific ID
    UPDATE [Client].[RedirectUri]
    SET [RedirectUri] = @uri,
        [ModifiedOn] = GETUTCDATE()
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId;
END
GO

-- Client.DeleteRedirectUri
CREATE OR ALTER PROCEDURE [Client].[DeleteRedirectUri]
    @orgId INT,
    @appId INT
AS
BEGIN
    DELETE FROM [Client].[RedirectUri]
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId;
END
GO

-- Client.GetRedirectUrisByApplicationId
CREATE OR ALTER PROCEDURE [Client].[GetRedirectUrisByApplicationId]
    @appId INT
AS
BEGIN
    SELECT * FROM [Client].[RedirectUri]
    WHERE [ApplicationId] = @appId
    ORDER BY [CreatedOn];
END
GO

-- Client.GetPostLogoutRedirectUrisByApplicationId
CREATE OR ALTER PROCEDURE [Client].[GetPostLogoutRedirectUrisByApplicationId]
    @appId INT
AS
BEGIN
    SELECT * FROM [Client].[PostLogoutRedirectUri]
    WHERE [ApplicationId] = @appId
    ORDER BY [CreatedOn];
END
GO

-- CLient.DeletePostLogoutRedirectUris (note the typo from Java code)
CREATE OR ALTER PROCEDURE [CLient].[DeletePostLogoutRedirectUris]
    @orgId INT,
    @appId INT
AS
BEGIN
    DELETE FROM [Client].[PostLogoutRedirectUri]
    WHERE [OrganizationId] = @orgId
      AND [ApplicationId] = @appId;
END
GO

-- Client.GetMFARealms
CREATE OR ALTER PROCEDURE [Client].[GetMFARealms]
AS
BEGIN
    SELECT * FROM [Client].[MFARealm]
    WHERE [Status] = 1
    ORDER BY [RealmName];
END
GO

-- Client.getAllResourcesByAppId
CREATE OR ALTER PROCEDURE [Client].[getAllResourcesByAppId]
    @appId INT
AS
BEGIN
    SELECT r.*, rl.* 
    FROM [Resource].[Resource] r
    INNER JOIN [dbo].[ResourceLibrary] rl ON r.[ResourceLibraryId] = rl.[ResourceLibraryId]
    WHERE r.[ApplicationId] = @appId
      AND r.[Status] = 1;
END
GO

-- Client.getAllResourcesByClientId
CREATE OR ALTER PROCEDURE [Client].[getAllResourcesByClientId]
    @clientId NCHAR(32)
AS
BEGIN
    SELECT r.*, rl.* 
    FROM [Resource].[Resource] r
    INNER JOIN [dbo].[ResourceLibrary] rl ON r.[ResourceLibraryId] = rl.[ResourceLibraryId]
    INNER JOIN [Client].[Application] a ON r.[ApplicationId] = a.[ApplicationId]
    WHERE a.[ClientId] = @clientId
      AND r.[Status] = 1;
END
GO

