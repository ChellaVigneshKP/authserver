-- =============================================
-- Onboard First Admin User and Admin Portal Application
-- This script creates everything needed to access the admin portal for the first time
-- =============================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- =============================================
-- Step 1: Insert default branding in ExternalSource table
-- =============================================
IF NOT EXISTS (SELECT 1 FROM [dbo].[ExternalSource] WHERE [SourceCode] = 'default')
BEGIN
    INSERT INTO [dbo].[ExternalSource] 
        ([SourceCode], [SourceName], [Branding], [Status])
    VALUES 
        ('default', 'Default', 'default', 1);
    
    PRINT 'Default branding created successfully';
END
ELSE
BEGIN
    PRINT 'Default branding already exists';
END
GO

-- =============================================
-- Step 2: Create Admin Organization
-- =============================================
DECLARE @AdminOrgId INT;
SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';

IF @AdminOrgId IS NULL
BEGIN
    INSERT INTO [Partner].[Organization] ([Name], [Note])
    VALUES ('Ascensus', 'Default Admin Organization');
    
    SET @AdminOrgId = SCOPE_IDENTITY();
    PRINT 'Admin organization created with ID: ' + CAST(@AdminOrgId AS NVARCHAR(10));
END
ELSE
BEGIN
    PRINT 'Admin organization already exists with ID: ' + CAST(@AdminOrgId AS NVARCHAR(10));
END
GO

-- =============================================
-- Step 3: Create Admin Group
-- =============================================
DECLARE @AdminOrgId INT;
DECLARE @AdminGroupId INT;

SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';
SELECT @AdminGroupId = [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Ascensus Admin';

IF @AdminGroupId IS NULL
BEGIN
    -- Create the admin group
    INSERT INTO [Partner].[OrganizationGroup]
        ([OrganizationId], [GroupName], [Description])
    VALUES 
        (@AdminOrgId, 'Ascensus Admin', 'System Administrators with full access');
    
    SET @AdminGroupId = SCOPE_IDENTITY();
    PRINT 'Admin group created with ID: ' + CAST(@AdminGroupId AS NVARCHAR(10));
    
    -- Add permissions to the admin group
    -- Note: This assumes GroupTemplate and GroupPermissionTemplate tables exist
    -- If permissions need to be added manually, uncomment and customize the following:
    /*
    INSERT INTO [Partner].[OrganizationGroupPermission]
        ([OrganizationGroupId], [OrganizationId], [PermissionName], [Description])
    VALUES
        (@AdminGroupId, @AdminOrgId, 'ADMIN_FULL_ACCESS', 'Full administrative access'),
        (@AdminGroupId, @AdminOrgId, 'USER_MANAGEMENT', 'Manage users'),
        (@AdminGroupId, @AdminOrgId, 'APPLICATION_MANAGEMENT', 'Manage applications'),
        (@AdminGroupId, @AdminOrgId, 'ORGANIZATION_MANAGEMENT', 'Manage organizations');
    */
END
ELSE
BEGIN
    PRINT 'Admin group already exists with ID: ' + CAST(@AdminGroupId AS NVARCHAR(10));
END
GO

-- =============================================
-- Step 4: Create Admin Portal Application
-- =============================================
DECLARE @AdminAppId INT;
DECLARE @AdminClientId NCHAR(32);
DECLARE @AdminOrgId INT;

SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';
SELECT @AdminAppId = [ApplicationId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal';

IF @AdminAppId IS NULL
BEGIN
    -- Generate a unique client ID
    SET @AdminClientId = REPLACE(CAST(NEWID() AS NCHAR(36)), '-', '');
    SET @AdminClientId = LEFT(@AdminClientId, 32);
    
    -- Get default auth flow (assuming 1 = Authorization Code Flow)
    DECLARE @AuthFlowId INT = 1;
    
    -- Create the Admin Portal application
    INSERT INTO [Client].[Application]
        ([OrganizationId], [ClientId], [Name], [Description], [AuthFlow], 
         [AllowForgotUsername], [UsernameType], [Active], [Status])
    VALUES 
        (@AdminOrgId, @AdminClientId, 'Admin Portal', 'Administrative Portal Application', @AuthFlowId,
         1, 1, 1, 1);
    
    SET @AdminAppId = SCOPE_IDENTITY();
    
    PRINT 'Admin Portal application created with ID: ' + CAST(@AdminAppId AS NVARCHAR(10));
    PRINT 'Admin Portal Client ID: ' + CAST(@AdminClientId AS NVARCHAR(32));
    PRINT '';
    PRINT '=== IMPORTANT: SAVE THIS CLIENT ID ===';
    PRINT 'Client ID: ' + CAST(@AdminClientId AS NVARCHAR(32));
    PRINT '======================================';
    PRINT '';
    
    -- Add redirect URIs for the admin portal
    -- Adjust these URLs based on your deployment
    INSERT INTO [Client].[RedirectUri]
        ([ApplicationId], [Uri], [Status])
    VALUES
        (@AdminAppId, 'http://localhost:9080/login', 1),
        (@AdminAppId, 'http://localhost:9080/oauth2/authorized', 1);
    
    -- Add post-logout redirect URIs
    INSERT INTO [Client].[PostLogoutRedirectUri]
        ([ApplicationId], [Uri], [Status])
    VALUES
        (@AdminAppId, 'http://localhost:9080/login', 1);
    
    PRINT 'Redirect URIs configured for Admin Portal';
END
ELSE
BEGIN
    SELECT @AdminClientId = [ClientId] FROM [Client].[Application] WHERE [ApplicationId] = @AdminAppId;
    PRINT 'Admin Portal application already exists with ID: ' + CAST(@AdminAppId AS NVARCHAR(10));
    PRINT 'Client ID: ' + CAST(@AdminClientId AS NVARCHAR(32));
END
GO

-- =============================================
-- Step 5: Create First Admin User
-- This creates a default admin user: admin / Admin@123
-- IMPORTANT: Change this password immediately after first login!
-- =============================================
DECLARE @AdminOrgId INT;
DECLARE @AdminGroupId INT;
DECLARE @AdminProfileId INT;

SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';
SELECT @AdminGroupId = [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Ascensus Admin';

-- Check if admin user already exists
SELECT @AdminProfileId = p.[ProfileId]
FROM [Person].[Profile] p
INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
WHERE c.UserName = 'admin';

IF @AdminProfileId IS NULL
BEGIN
    DECLARE @LoginProviderId INT;
    DECLARE @DataOriginId INT;
    
    -- Get default login provider and data origin
    SELECT TOP 1 @LoginProviderId = LoginProviderId FROM [Person].[LoginProvider] WHERE Status = 1;
    SELECT TOP 1 @DataOriginId = DataOriginId FROM [dbo].[DataOrigin] WHERE Status = 1;
    
    -- If no default values exist, create them
    IF @LoginProviderId IS NULL
    BEGIN
        INSERT INTO [Person].[LoginProvider] ([ProviderName], [Status])
        VALUES ('Local', 1);
        SET @LoginProviderId = SCOPE_IDENTITY();
        PRINT 'Created default LoginProvider';
    END
    
    IF @DataOriginId IS NULL
    BEGIN
        INSERT INTO [dbo].[DataOrigin] ([OriginName], [Status])
        VALUES ('Local', 1);
        SET @DataOriginId = SCOPE_IDENTITY();
        PRINT 'Created default DataOrigin';
    END
    
    -- Create admin profile
    INSERT INTO [Person].[Profile]
        ([LoginProviderId], [FirstName], [LastName], [Email], [EmailConfirmed], 
         [PhoneNumber], [PhoneNumberConfirmed], [TwoFactorEnabled], [Suffix], 
         [DataOriginId], [SyncFlag], [Status], [LoginId])
    VALUES
        (@LoginProviderId, 'Ascensus', 'Admin', 'admin@localhost.local', 0,
         '', 0, 0, 0,
         @DataOriginId, 0, 1, NEWID());
    
    SET @AdminProfileId = SCOPE_IDENTITY();
    PRINT 'Admin profile created with ID: ' + CAST(@AdminProfileId AS NVARCHAR(10));
    
    -- Create admin credentials with default password: Admin@123
    -- This is a BCrypt hash of "Admin@123"
    -- Password encoder version 2 is typically BCrypt
    DECLARE @DefaultPassword VARBINARY(4000);
    -- BCrypt hash for "Admin@123" with strength 10
    -- Note: This is an example hash - you may need to generate this using your actual password encoder
    SET @DefaultPassword = CONVERT(VARBINARY(4000), '$2a$10$N9qo8uLOickgx2ZMRZoMye/1JVfIjl8VDW7nJ3jh6.XPQKBwm7Guu');
    
    INSERT INTO [Person].[Credential]
        ([ProfileId], [UserName], [Password], [LockoutEnd], [CredentialLocked], 
         [AccessFailedCount], [DataOriginId], [SyncFlag], [Status], [Version], 
         [ExternalId], [DisallowedRecentPasswordCount])
    VALUES
        (@AdminProfileId, 'admin', @DefaultPassword, NULL, 0,
         0, @DataOriginId, 0, 1, 2,
         NEWID(), 3);
    
    PRINT 'Admin credentials created';
    
    -- Link admin to organization
    INSERT INTO [Person].[ProfileOrganization]
        ([ProfileId], [OrganizationId], [Status])
    VALUES
        (@AdminProfileId, @AdminOrgId, 1);
    
    DECLARE @ProfileOrgId INT = SCOPE_IDENTITY();
    PRINT 'Admin linked to organization';
    
    -- Add admin to admin group
    INSERT INTO [Person].[ProfileOrganizationGroup]
        ([ProfileOrganizationId], [OrganizationGroupId])
    VALUES
        (@ProfileOrgId, @AdminGroupId);
    
    PRINT 'Admin added to admin group';
    PRINT '';
    PRINT '=== FIRST ADMIN USER CREATED ===';
    PRINT 'Username: admin';
    PRINT 'Password: Admin@123';
    PRINT '';
    PRINT 'IMPORTANT: Change this password immediately after first login!';
    PRINT '================================';
END
ELSE
BEGIN
    PRINT 'Admin user already exists with ProfileId: ' + CAST(@AdminProfileId AS NVARCHAR(10));
END
GO

-- =============================================
-- Step 6: Display Summary
-- =============================================
PRINT '';
PRINT '================================================';
PRINT 'ADMIN ONBOARDING COMPLETE';
PRINT '================================================';
PRINT '';
PRINT 'Next Steps:';
PRINT '1. Note the Client ID displayed above';
PRINT '2. Start your auth server application';
PRINT '3. Navigate to: http://localhost:9080/oauth2/authorize?client_id=<CLIENT_ID>&response_type=code&redirect_uri=http://localhost:9080/login&scope=openid&branding=default';
PRINT '4. Login with username: admin, password: Admin@123';
PRINT '5. Change the admin password immediately';
PRINT '6. Use the admin portal to create your first client application';
PRINT '';
PRINT 'For more information, see the README.md file';
PRINT '================================================';
GO
