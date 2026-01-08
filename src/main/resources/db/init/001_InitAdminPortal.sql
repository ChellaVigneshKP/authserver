-- =============================================
-- Admin Portal Initialization Script
-- =============================================
-- This script initializes the admin portal by creating:
-- 1. Admin Organization (Ascensus)
-- 2. Admin Application (Admin Portal) 
-- 3. Admin Group (Ascensus Admin)
-- 4. Admin Profile (Ascensus Admin user)
-- 5. Admin User credentials
-- =============================================
-- PREREQUISITES:
-- This script must be run AFTER all migration scripts have been executed,
-- specifically FinalTable.sql and FinalProcedure.sql which define:
-- - ForgetUserNameType table type
-- - Client.CreateApplication stored procedure
-- - Required table schemas and constraints
-- =============================================

-- Check if admin organization already exists
IF NOT EXISTS (SELECT 1 FROM [Partner].[Organization] WHERE [Name] = 'Ascensus')
BEGIN
    PRINT 'Creating Admin Organization: Ascensus'
    EXEC [Partner].[CreateOrganization] @Name = 'Ascensus', @Note = 'Administrative Organization for Auth Server Management'
END
ELSE
BEGIN
    PRINT 'Admin Organization already exists'
END
GO

-- Get the Organization ID
DECLARE @AdminOrgId INT
SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus'
PRINT 'Admin Organization ID: ' + CAST(@AdminOrgId AS VARCHAR)
GO

-- Check if Admin Portal application already exists
DECLARE @AdminOrgId INT
SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus'

IF NOT EXISTS (SELECT 1 FROM [Client].[Application] WHERE [Name] = 'Admin Portal' AND [OrganizationId] = @AdminOrgId)
BEGIN
    PRINT 'Creating Admin Portal Application'
    
    -- Get required enum IDs
    DECLARE @AppTypeId INT
    DECLARE @AuthFlowId INT
    DECLARE @AccessTokenFormatId INT
    
    -- Get Web Application Type ID
    SELECT @AppTypeId = [EnumId] 
    FROM [dbo].[Enum] e
    INNER JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
    WHERE et.[Name] = 'ApplicationType' AND e.[Code] = 'Web'
    
    -- Get Authorization Code Flow ID
    SELECT @AuthFlowId = [EnumId]
    FROM [dbo].[Enum] e
    INNER JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
    WHERE et.[Name] = 'AuthFlow' AND e.[Code] = 'AuthorizationCode'
    
    -- Get self-contained Access Token Format ID (JWT)
    SELECT @AccessTokenFormatId = [EnumId]
    FROM [dbo].[Enum] e
    INNER JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
    WHERE et.[Name] = 'AccessTokenFormat' AND e.[Code] = 'self-contained'
    
    PRINT 'AppTypeId: ' + CAST(@AppTypeId AS VARCHAR)
    PRINT 'AuthFlowId: ' + CAST(@AuthFlowId AS VARCHAR)
    PRINT 'AccessTokenFormatId: ' + CAST(@AccessTokenFormatId AS VARCHAR)
    
    -- Create Admin Portal Application
    -- Note: ForgetUserNameType must be created first (defined in FinalTable.sql)
    -- This assumes all migration scripts have been run before this init script
    DECLARE @ForgotUserNameParam ForgetUserNameType;
    
    EXEC [Client].[CreateApplication] 
        @OrganizationId = @AdminOrgId,
        @Name = 'Admin Portal',
        @Description = 'Administrative Portal for Auth Server Management',
        @ApplicationTypeId = @AppTypeId,
        @AuthFlowId = @AuthFlowId,
        @Uri = 'http://localhost:3000',
        @JWKSetUrl = NULL,
        @RequirePKCE = 0,
        @JWSAlgorithmId = NULL,
        @AuthCodeTimeToLive = 300,
        @AccessTokenTimeToLive = 3600,
        @RefreshTokenTimeToLive = 86400,
        @ReuseRefreshTokens = 0,
        @AccessTokenFormatId = @AccessTokenFormatId,
        @DeviceCodeTimeToLive = 300,
        @MaxRequestTransitTime = 1,
        @UsernameType = NULL,
        @AllowForgotUsername = 0,
        @ForgotUserNameParam = @ForgotUserNameParam,
        @PinTimeToLive = NULL
END
ELSE
BEGIN
    PRINT 'Admin Portal Application already exists'
END
GO

-- Update Admin Portal Application settings for proper OAuth2 flow
DECLARE @AdminAppId INT
DECLARE @AdminOrgId INT

SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus'
SELECT @AdminAppId = [ApplicationId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal' AND [OrganizationId] = @AdminOrgId

PRINT 'Configuring Admin Portal Application settings'

-- Add redirect URIs if not already present
IF NOT EXISTS (SELECT 1 FROM [Client].[RedirectUri] WHERE [ApplicationId] = @AdminAppId AND [Uri] = 'http://localhost:3000/api/auth/callback/authserver')
BEGIN
    INSERT INTO [Client].[RedirectUri] ([ApplicationId], [Uri], [Type])
    VALUES (@AdminAppId, 'http://localhost:3000/api/auth/callback/authserver', 1)
    PRINT 'Added redirect URI: http://localhost:3000/api/auth/callback/authserver'
END

IF NOT EXISTS (SELECT 1 FROM [Client].[RedirectUri] WHERE [ApplicationId] = @AdminAppId AND [Uri] = 'http://localhost:3000')
BEGIN
    INSERT INTO [Client].[RedirectUri] ([ApplicationId], [Uri], [Type])
    VALUES (@AdminAppId, 'http://localhost:3000', 2)
    PRINT 'Added post logout redirect URI: http://localhost:3000'
END

-- Add required scopes
IF NOT EXISTS (SELECT 1 FROM [Client].[Scope] WHERE [ApplicationId] = @AdminAppId AND [Name] = 'openid')
BEGIN
    INSERT INTO [Client].[Scope] ([ApplicationId], [Name])
    VALUES (@AdminAppId, 'openid')
    PRINT 'Added scope: openid'
END

IF NOT EXISTS (SELECT 1 FROM [Client].[Scope] WHERE [ApplicationId] = @AdminAppId AND [Name] = 'profile')
BEGIN
    INSERT INTO [Client].[Scope] ([ApplicationId], [Name])
    VALUES (@AdminAppId, 'profile')
    PRINT 'Added scope: profile'
END

IF NOT EXISTS (SELECT 1 FROM [Client].[Scope] WHERE [ApplicationId] = @AdminAppId AND [Name] = 'email')
BEGIN
    INSERT INTO [Client].[Scope] ([ApplicationId], [Name])
    VALUES (@AdminAppId, 'email')
    PRINT 'Added scope: email'
END

-- Add authentication methods
IF NOT EXISTS (SELECT 1 FROM [Client].[AuthenticationMethod] WHERE [ApplicationId] = @AdminAppId AND [Name] = 'client_secret_basic')
BEGIN
    INSERT INTO [Client].[AuthenticationMethod] ([ApplicationId], [Name])
    VALUES (@AdminAppId, 'client_secret_basic')
    PRINT 'Added authentication method: client_secret_basic'
END

IF NOT EXISTS (SELECT 1 FROM [Client].[AuthenticationMethod] WHERE [ApplicationId] = @AdminAppId AND [Name] = 'client_secret_post')
BEGIN
    INSERT INTO [Client].[AuthenticationMethod] ([ApplicationId], [Name])
    VALUES (@AdminAppId, 'client_secret_post')
    PRINT 'Added authentication method: client_secret_post'
END

-- Add grant type
IF NOT EXISTS (SELECT 1 FROM [Client].[GrantType] WHERE [ApplicationId] = @AdminAppId AND [Name] = 'authorization_code')
BEGIN
    INSERT INTO [Client].[GrantType] ([ApplicationId], [Name])
    VALUES (@AdminAppId, 'authorization_code')
    PRINT 'Added grant type: authorization_code'
END

IF NOT EXISTS (SELECT 1 FROM [Client].[GrantType] WHERE [ApplicationId] = @AdminAppId AND [Name] = 'refresh_token')
BEGIN
    INSERT INTO [Client].[GrantType] ([ApplicationId], [Name])
    VALUES (@AdminAppId, 'refresh_token')
    PRINT 'Added grant type: refresh_token'
END

PRINT 'Admin Portal Application configured successfully'
GO

-- Check if admin group exists, if not rename the Administrators group for Ascensus org to 'Ascensus Admin'
DECLARE @AdminOrgId INT
SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus'

DECLARE @AdminGroupId INT
SELECT @AdminGroupId = [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Ascensus Admin'

-- If 'Ascensus Admin' doesn't exist but 'Administrators' does for this org, rename it
IF @AdminGroupId IS NULL
BEGIN
    DECLARE @AdministratorsGroupId INT
    SELECT @AdministratorsGroupId = [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
    WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Administrators'
    
    IF @AdministratorsGroupId IS NOT NULL
    BEGIN
        UPDATE [Partner].[OrganizationGroup]
        SET [GroupName] = 'Ascensus Admin'
        WHERE [OrganizationGroupId] = @AdministratorsGroupId
        
        PRINT 'Renamed Administrators group to Ascensus Admin'
        SET @AdminGroupId = @AdministratorsGroupId
    END
    ELSE
    BEGIN
        PRINT 'ERROR: Neither Ascensus Admin nor Administrators group exists. Check GroupTemplate setup.'
    END
END

IF @AdminGroupId IS NOT NULL
BEGIN
    PRINT 'Admin Group ID: ' + CAST(@AdminGroupId AS VARCHAR)
END
GO

-- Create admin profile if it doesn't exist
DECLARE @AdminOrgId INT
SELECT @AdminOrgId = [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus'

DECLARE @AdminGroupId INT
SELECT @AdminGroupId = [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Ascensus Admin'

IF NOT EXISTS (SELECT 1 FROM [Person].[Profile] WHERE [FirstName] = 'Ascensus' AND [LastName] = 'Admin')
BEGIN
    PRINT 'Creating Admin Profile'
    
    -- Note: This creates the profile structure. 
    -- The actual admin user with credentials should be created via the API or manual SQL
    -- after the system is running, using proper password hashing.
    
    -- Create a placeholder that will be updated with proper credentials
    INSERT INTO [Person].[Profile] ([FirstName], [LastName], [LoginProviderId], [EmailConfirmed], [PhoneNumberConfirmed], [TwoFactorEnabled], [DataOriginId], [SyncFlag], [Email], [PhoneNumber], [Suffix])
    VALUES ('Ascensus', 'Admin', 1, 0, 0, 1, 1, 0, 'admin@authserver.local', '', 0)
    
    DECLARE @ProfileId INT = SCOPE_IDENTITY()
    PRINT 'Created Admin Profile with ID: ' + CAST(@ProfileId AS VARCHAR)
    
    -- Link profile to organization (direct INSERT instead of non-existent stored procedure)
    INSERT INTO [Person].[ProfileOrganization] ([ProfileId], [OrganizationId])
    VALUES (@ProfileId, @AdminOrgId)
    
    PRINT 'Linked profile to organization'
    
    -- Assign to admin group (direct INSERT instead of non-existent stored procedure)
    INSERT INTO [Person].[ProfileGroup] ([ProfileId], [OrganizationGroupId])
    VALUES (@ProfileId, @AdminGroupId)
    
    PRINT 'Admin Profile linked to organization and admin group'
    PRINT ''
    PRINT '============================================='
    PRINT 'IMPORTANT: Admin User Setup Required'
    PRINT '============================================='
    PRINT 'The admin profile structure has been created.'
    PRINT 'You must create an admin user via the API to set proper credentials:'
    PRINT ''
    PRINT 'Use the following endpoint to create an admin user:'
    PRINT 'POST /api/v1/users'
    PRINT ''
    PRINT 'Example payload:'
    PRINT '{'
    PRINT '  "orgId": ' + CAST(@AdminOrgId AS VARCHAR) + ','
    PRINT '  "firstName": "Admin",'
    PRINT '  "lastName": "User",'
    PRINT '  "username": "admin@authserver.local",'
    PRINT '  "email": "admin@authserver.local",'
    PRINT '  "password": "YourSecurePassword123!",'
    PRINT '  "branding": "default",'
    PRINT '  "phoneNumber": "1234567890"'
    PRINT '}'
    PRINT '============================================='
END
ELSE
BEGIN
    PRINT 'Admin Profile already exists'
END
GO

PRINT ''
PRINT '============================================='
PRINT 'Admin Portal Initialization Complete'
PRINT '============================================='
PRINT ''
PRINT 'Summary of created resources:'
SELECT 
    'Organization' AS [Resource Type],
    [Name] AS [Name],
    [OrganizationId] AS [ID],
    [RowGuid] AS [GUID]
FROM [Partner].[Organization] 
WHERE [Name] = 'Ascensus'

UNION ALL

SELECT 
    'Application' AS [Resource Type],
    [Name] AS [Name],
    [ApplicationId] AS [ID],
    [RowGuid] AS [GUID]
FROM [Client].[Application]
WHERE [Name] = 'Admin Portal'

UNION ALL

SELECT 
    'Admin Group' AS [Resource Type],
    [GroupName] AS [Name],
    [OrganizationGroupId] AS [ID],
    [RowGuid] AS [GUID]
FROM [Partner].[OrganizationGroup]
WHERE [GroupName] = 'Ascensus Admin'

UNION ALL

SELECT 
    'Profile' AS [Resource Type],
    [FirstName] + ' ' + [LastName] AS [Name],
    [ProfileId] AS [ID],
    [RowGuid] AS [GUID]
FROM [Person].[Profile]
WHERE [FirstName] = 'Ascensus' AND [LastName] = 'Admin'

GO

-- Display Admin Portal Client ID and instructions
DECLARE @AdminClientId NCHAR(32)
SELECT @AdminClientId = [ClientId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal'

PRINT ''
PRINT '============================================='
PRINT 'Admin Portal Configuration'
PRINT '============================================='
PRINT 'Client ID: ' + CAST(@AdminClientId AS VARCHAR(50))
PRINT ''
PRINT 'Configure your Next.js frontend with:'
PRINT 'NEXTAUTH_URL=http://localhost:3000'
PRINT 'AUTHSERVER_ISSUER=http://localhost:9080'
PRINT 'AUTHSERVER_CLIENT_ID=' + CAST(@AdminClientId AS VARCHAR(50))
PRINT 'AUTHSERVER_CLIENT_SECRET=<generate-via-api>'
PRINT ''
PRINT 'To generate a client secret, use:'
PRINT 'POST /api/v1/applications/' + CAST(@AdminClientId AS VARCHAR(50)) + '/secrets'
PRINT '============================================='
GO
