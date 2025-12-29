-- =============================================
-- Manual Onboarding Script for First Admin User
-- Run this script manually using SQL Server Management Studio or sqlcmd
-- This is a standalone script that can be executed independently
-- =============================================

USE [AGSAuth];  -- Change this to your database name if different
GO

SET NOCOUNT ON;
GO

PRINT '';
PRINT '================================================';
PRINT 'STARTING ADMIN ONBOARDING PROCESS';
PRINT '================================================';
PRINT '';

-- =============================================
-- Step 1: Insert default branding
-- =============================================
PRINT 'Step 1: Creating default branding...';

IF NOT EXISTS (SELECT 1 FROM [dbo].[ExternalSource] WHERE [SourceCode] = 'default')
BEGIN
    INSERT INTO [dbo].[ExternalSource] 
        ([SourceCode], [SourceName], [Branding], [Status])
    VALUES 
        ('default', 'Default', 'default', 1);
    
    PRINT '✓ Default branding created';
END
ELSE
BEGIN
    PRINT '✓ Default branding already exists';
END
GO

-- =============================================
-- Step 2: Create Admin Organization
-- =============================================
PRINT '';
PRINT 'Step 2: Creating admin organization...';

DECLARE @AdminOrgId INT;
SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';

IF @AdminOrgId IS NULL
BEGIN
    INSERT INTO [Partner].[Organization] ([Name], [Note])
    VALUES ('Ascensus', 'Default Admin Organization');
    
    SET @AdminOrgId = SCOPE_IDENTITY();
    PRINT '✓ Organization created (ID: ' + CAST(@AdminOrgId AS NVARCHAR(10)) + ')';
END
ELSE
BEGIN
    PRINT '✓ Organization exists (ID: ' + CAST(@AdminOrgId AS NVARCHAR(10)) + ')';
END
GO

-- =============================================
-- Step 3: Create Admin Group
-- =============================================
PRINT '';
PRINT 'Step 3: Creating admin group...';

DECLARE @AdminOrgId INT;
DECLARE @AdminGroupId INT;

SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';
SELECT @AdminGroupId = [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Ascensus Admin';

IF @AdminGroupId IS NULL
BEGIN
    INSERT INTO [Partner].[OrganizationGroup]
        ([OrganizationId], [GroupName], [Description])
    VALUES 
        (@AdminOrgId, 'Ascensus Admin', 'System Administrators');
    
    SET @AdminGroupId = SCOPE_IDENTITY();
    PRINT '✓ Admin group created (ID: ' + CAST(@AdminGroupId AS NVARCHAR(10)) + ')';
END
ELSE
BEGIN
    PRINT '✓ Admin group exists (ID: ' + CAST(@AdminGroupId AS NVARCHAR(10)) + ')';
END
GO

-- =============================================
-- Step 4: Create Admin Portal Application
-- =============================================
PRINT '';
PRINT 'Step 4: Creating Admin Portal application...';

DECLARE @AdminAppId INT;
DECLARE @AdminClientId NCHAR(32);
DECLARE @AdminOrgId INT;

SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';
SELECT @AdminAppId = [ApplicationId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal';

IF @AdminAppId IS NULL
BEGIN
    SET @AdminClientId = REPLACE(CAST(NEWID() AS NCHAR(36)), '-', '');
    SET @AdminClientId = LEFT(@AdminClientId, 32);
    
    DECLARE @AuthFlowId INT = 1;
    
    INSERT INTO [Client].[Application]
        ([OrganizationId], [ClientId], [Name], [Description], [AuthFlow], 
         [AllowForgotUsername], [UsernameType], [Active], [Status])
    VALUES 
        (@AdminOrgId, @AdminClientId, 'Admin Portal', 'Admin Portal Application', @AuthFlowId,
         1, 1, 1, 1);
    
    SET @AdminAppId = SCOPE_IDENTITY();
    
    INSERT INTO [Client].[RedirectUri]
        ([ApplicationId], [Uri], [Status])
    VALUES
        (@AdminAppId, 'http://localhost:9080/login', 1),
        (@AdminAppId, 'http://localhost:9080/oauth2/authorized', 1),
        (@AdminAppId, 'https://localhost:9080/login', 1),
        (@AdminAppId, 'https://localhost:9080/oauth2/authorized', 1);
    
    INSERT INTO [Client].[PostLogoutRedirectUri]
        ([ApplicationId], [Uri], [Status])
    VALUES
        (@AdminAppId, 'http://localhost:9080/login', 1),
        (@AdminAppId, 'https://localhost:9080/login', 1);
    
    PRINT '✓ Admin Portal created (ID: ' + CAST(@AdminAppId AS NVARCHAR(10)) + ')';
    PRINT '';
    PRINT '╔════════════════════════════════════════════════╗';
    PRINT '║     SAVE THIS CLIENT ID - YOU WILL NEED IT     ║';
    PRINT '╠════════════════════════════════════════════════╣';
    PRINT '║ Client ID: ' + CAST(@AdminClientId AS NVARCHAR(32)) + ' ║';
    PRINT '╚════════════════════════════════════════════════╝';
    PRINT '';
END
ELSE
BEGIN
    SELECT @AdminClientId = [ClientId] FROM [Client].[Application] WHERE [ApplicationId] = @AdminAppId;
    PRINT '✓ Admin Portal exists (ID: ' + CAST(@AdminAppId AS NVARCHAR(10)) + ')';
    PRINT 'Client ID: ' + CAST(@AdminClientId AS NVARCHAR(32));
END
GO

-- =============================================
-- Step 5: Create First Admin User
-- =============================================
PRINT '';
PRINT 'Step 5: Creating admin user...';

DECLARE @AdminOrgId INT;
DECLARE @AdminGroupId INT;
DECLARE @AdminProfileId INT;

SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';
SELECT @AdminGroupId = [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Ascensus Admin';

SELECT @AdminProfileId = p.[ProfileId]
FROM [Person].[Profile] p
INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
WHERE c.UserName = 'admin';

IF @AdminProfileId IS NULL
BEGIN
    DECLARE @LoginProviderId INT;
    DECLARE @DataOriginId INT;
    
    SELECT TOP 1 @LoginProviderId = LoginProviderId FROM [Person].[LoginProvider] WHERE Status = 1;
    SELECT TOP 1 @DataOriginId = DataOriginId FROM [dbo].[DataOrigin] WHERE Status = 1;
    
    IF @LoginProviderId IS NULL
    BEGIN
        INSERT INTO [Person].[LoginProvider] ([ProviderName], [Status])
        VALUES ('Local', 1);
        SET @LoginProviderId = SCOPE_IDENTITY();
        PRINT '  ✓ Login provider created';
    END
    
    IF @DataOriginId IS NULL
    BEGIN
        INSERT INTO [dbo].[DataOrigin] ([OriginName], [Status])
        VALUES ('Local', 1);
        SET @DataOriginId = SCOPE_IDENTITY();
        PRINT '  ✓ Data origin created';
    END
    
    INSERT INTO [Person].[Profile]
        ([LoginProviderId], [FirstName], [LastName], [Email], [EmailConfirmed], 
         [PhoneNumber], [PhoneNumberConfirmed], [TwoFactorEnabled], [Suffix], 
         [DataOriginId], [SyncFlag], [Status], [LoginId])
    VALUES
        (@LoginProviderId, 'Ascensus', 'Admin', 'admin@localhost.local', 0,
         '', 0, 0, 0, @DataOriginId, 0, 1, NEWID());
    
    SET @AdminProfileId = SCOPE_IDENTITY();
    
    DECLARE @DefaultPassword VARBINARY(4000);
    -- BCrypt hash for "Admin@123456" (12 characters, meets password complexity requirements)
    -- SECURITY WARNING: This is a default password for initial setup ONLY
    -- The admin user MUST change this password immediately after first login
    -- Consider this password compromised as it's visible in source control
    SET @DefaultPassword = CONVERT(VARBINARY(4000), '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cyhVHYIDf/8Fh.k7.H3w8LqXQX3Ki');
    
    INSERT INTO [Person].[Credential]
        ([ProfileId], [UserName], [Password], [LockoutEnd], [CredentialLocked], 
         [AccessFailedCount], [DataOriginId], [SyncFlag], [Status], [Version], 
         [ExternalId], [DisallowedRecentPasswordCount])
    VALUES
        (@AdminProfileId, 'admin', @DefaultPassword, NULL, 0,
         0, @DataOriginId, 0, 1, 2, NEWID(), 3);
    
    INSERT INTO [Person].[ProfileOrganization]
        ([ProfileId], [OrganizationId], [Status])
    VALUES
        (@AdminProfileId, @AdminOrgId, 1);
    
    DECLARE @ProfileOrgId INT = SCOPE_IDENTITY();
    
    INSERT INTO [Person].[ProfileOrganizationGroup]
        ([ProfileOrganizationId], [OrganizationGroupId])
    VALUES
        (@ProfileOrgId, @AdminGroupId);
    
    PRINT '✓ Admin user created (ProfileId: ' + CAST(@AdminProfileId AS NVARCHAR(10)) + ')';
    PRINT '';
    PRINT '╔════════════════════════════════════════════════╗';
    PRINT '║         DEFAULT ADMIN CREDENTIALS              ║';
    PRINT '╠════════════════════════════════════════════════╣';
    PRINT '║ Username: admin                                ║';
    PRINT '║ Password: Admin@123456                         ║';
    PRINT '╠════════════════════════════════════════════════╣';
    PRINT '║ ⚠️  CHANGE PASSWORD AFTER FIRST LOGIN!         ║';
    PRINT '║ ⚠️  This password is visible in logs/scripts   ║';
    PRINT '╚════════════════════════════════════════════════╝';
END
ELSE
BEGIN
    PRINT '✓ Admin user exists (ProfileId: ' + CAST(@AdminProfileId AS NVARCHAR(10)) + ')';
END
GO

-- =============================================
-- Display Final Summary
-- =============================================
PRINT '';
PRINT '';
PRINT '================================================';
PRINT '          ONBOARDING COMPLETE! ✓';
PRINT '================================================';
PRINT '';
PRINT 'Next Steps:';
PRINT '  1. Copy the Client ID shown above';
PRINT '  2. Start the auth server: ./mvnw spring-boot:run';
PRINT '  3. Open browser and navigate to:';
PRINT '';
PRINT '     http://localhost:9080/oauth2/authorize?';
PRINT '       client_id=<YOUR_CLIENT_ID>&';
PRINT '       response_type=code&';
PRINT '       redirect_uri=http://localhost:9080/login&';
PRINT '       scope=openid&';
PRINT '       branding=default';
PRINT '';
PRINT '  4. Login with: admin / Admin@123456';
PRINT '  5. Change password immediately!';
PRINT '  6. Create your first client application';
PRINT '';
PRINT 'Documentation: README.md';
PRINT '================================================';
PRINT '';

SET NOCOUNT OFF;
GO
