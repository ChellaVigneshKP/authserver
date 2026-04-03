-- ============================================================
-- Setup Admin Portal as OAuth2 PKCE Client
-- Run this after init-db.sql to configure the Admin Portal
-- application for the React admin-portal frontend
-- ============================================================

SET QUOTED_IDENTIFIER ON;
GO

-- ============================================================
-- 1. Find the Admin Portal Application
-- ============================================================
DECLARE @AppId INT;
DECLARE @OrgId INT;
DECLARE @AuthFlowId INT;
DECLARE @AppTypeId INT;
DECLARE @AccessTokenFormatId INT;

-- Get enum IDs
SELECT @AuthFlowId = EnumId FROM dbo.[Enum] WHERE Code = 'AuthCode';
SELECT @AppTypeId = EnumId FROM dbo.[Enum] WHERE Code = 'web';
SELECT @AccessTokenFormatId = EnumId FROM dbo.[Enum] WHERE Code = 'reference';

-- Get Org ID
SELECT @OrgId = OrganizationId FROM [Partner].[Organization] WHERE Name = 'Ascensus';

-- Find or create Admin Portal application
SELECT @AppId = ApplicationId
FROM [Client].[Application]
WHERE [Name] = 'Admin Portal' AND OrganizationId = @OrgId;

IF @AppId IS NULL
BEGIN
    INSERT INTO [Client].[Application] (OrganizationId, ClientId, [Name], [Description], Uri, ApplicationTypeId, AuthFlowId)
    VALUES (@OrgId,
            REPLACE(CONVERT(NCHAR(36), NEWID()), '-', ''),
            'Admin Portal',
            'Admin Portal Application',
            'http://localhost:5173',
            @AppTypeId,
            @AuthFlowId);
    SET @AppId = SCOPE_IDENTITY();
    PRINT 'Created Admin Portal application with ID: ' + CAST(@AppId AS VARCHAR);
END
ELSE
BEGIN
    -- Update URI to match Vite dev server
    UPDATE [Client].[Application]
    SET Uri = 'http://localhost:5173',
        AuthFlowId = @AuthFlowId,
        ApplicationTypeId = @AppTypeId
    WHERE ApplicationId = @AppId;
    PRINT 'Updated Admin Portal application ID: ' + CAST(@AppId AS VARCHAR);
END

-- ============================================================
-- 2. Create Client.Setting (PKCE config)
-- ============================================================
IF NOT EXISTS (SELECT 1 FROM [Client].[Setting] WHERE ApplicationId = @AppId)
BEGIN
    INSERT INTO [Client].[Setting] (ApplicationId, JWKSetUrl, JWSAlgorithm, RequirePkce, RequireConsent, AllowPlainTextPkce)
    VALUES (@AppId, '', NULL, 1, 0, 0);
    PRINT 'Created Client.Setting for Admin Portal';
END
ELSE
BEGIN
    UPDATE [Client].[Setting]
    SET RequirePkce = 1, JWSAlgorithm = NULL, RequireConsent = 0
    WHERE ApplicationId = @AppId;
    PRINT 'Updated Client.Setting for Admin Portal';
END

-- ============================================================
-- 3. Create Client.TokenSetting
-- ============================================================
IF NOT EXISTS (SELECT 1 FROM [Client].[TokenSetting] WHERE ApplicationId = @AppId)
BEGIN
    INSERT INTO [Client].[TokenSetting]
        (ApplicationId, OrganizationId, AuthCodeTimeToLive, AccessTokenTimeToLive,
         RefreshTokenTimeToLive, AccessTokenFormatId, DeviceCodeTimeToLive,
         ReuseRefreshTokens, MaxRequestTransitTime)
    VALUES (@AppId, @OrgId, 300, 3600, 86400, @AccessTokenFormatId, 300, 0, 300);
    PRINT 'Created Client.TokenSetting for Admin Portal';
END
ELSE
BEGIN
    PRINT 'Client.TokenSetting already exists for Admin Portal';
END

-- ============================================================
-- 4. Add Redirect URIs
-- ============================================================
IF NOT EXISTS (SELECT 1 FROM [Client].[RedirectUri]
               WHERE ApplicationId = @AppId AND RedirectUri LIKE '%localhost:5173%')
BEGIN
    INSERT INTO [Client].[RedirectUri] (OrganizationId, ApplicationId, RedirectUri)
    VALUES (@OrgId, @AppId, 'http://localhost:5173/callback');
    PRINT 'Added redirect URI: http://localhost:5173/callback';
END

-- Also keep localhost:3000 callback in case needed
IF NOT EXISTS (SELECT 1 FROM [Client].[RedirectUri]
               WHERE ApplicationId = @AppId AND RedirectUri LIKE '%localhost:3000%')
BEGIN
    INSERT INTO [Client].[RedirectUri] (OrganizationId, ApplicationId, RedirectUri)
    VALUES (@OrgId, @AppId, 'http://localhost:3000/callback');
    PRINT 'Added redirect URI: http://localhost:3000/callback';
END

-- ============================================================
-- 5. Add Post-Logout Redirect URIs
-- ============================================================
IF NOT EXISTS (SELECT 1 FROM [Client].[PostLogoutRedirectUri]
               WHERE ApplicationId = @AppId AND PostLogoutRedirectUri LIKE '%localhost:5173%')
BEGIN
    INSERT INTO [Client].[PostLogoutRedirectUri] (OrganizationId, ApplicationId, PostLogoutRedirectUri)
    VALUES (@OrgId, @AppId, 'http://localhost:5173');
    PRINT 'Added post-logout redirect URI: http://localhost:5173';
END

-- ============================================================
-- 6. Verify ExternalSource 'LOCAL' exists
-- ============================================================
IF NOT EXISTS (SELECT 1 FROM [dbo].[ExternalSource] WHERE SourceCode = 'LOCAL')
BEGIN
    DECLARE @ExternalTypeId INT;
    SELECT @ExternalTypeId = ExternalTypeId FROM [dbo].[ExternalType] WHERE [Name] = 'IDP';

    INSERT INTO [dbo].[ExternalSource] (SourceId, SourceCode, ExternalTypeId, SyncFlag, CreatedOn, ModifiedOn)
    VALUES (CONVERT(UNIQUEIDENTIFIER, '11111111-1111-1111-1111-111111111111'),
            'LOCAL', @ExternalTypeId, 1, GETUTCDATE(), GETUTCDATE());
    PRINT 'Created ExternalSource LOCAL';
END
ELSE
BEGIN
    PRINT 'ExternalSource LOCAL already exists';
END

-- ============================================================
-- 7. Print the ClientId for frontend configuration
-- ============================================================
DECLARE @ClientId NCHAR(32);
SELECT @ClientId = ClientId FROM [Client].[Application] WHERE ApplicationId = @AppId;
PRINT '';
PRINT '========================================';
PRINT 'Admin Portal OAuth2 Client Configuration';
PRINT '========================================';
PRINT 'Client ID: ' + RTRIM(@ClientId);
PRINT 'Redirect URI: http://localhost:5173/callback';
PRINT 'Auth Flow: PKCE (Authorization Code + Proof Key)';
PRINT 'Branding: LOCAL';
PRINT 'Access Token TTL: 3600s (1 hour)';
PRINT 'Refresh Token TTL: 86400s (24 hours)';
PRINT 'Max Request Transit Time: 300s (5 minutes)';
PRINT '========================================';
GO
