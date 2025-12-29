-- ============================================================================
-- First User Onboarding Script for AGSAuth
-- ============================================================================
-- This script creates all necessary records to onboard the first user
-- into the AGSAuth database for successful authentication.
--
-- Prerequisites:
-- 1. AGSAuth database exists
-- 2. All Flyway migrations have been applied
-- 3. Execute this script as a user with INSERT permissions
--
-- After running this script:
-- - Username: admin
-- - Temporary Password: TempPassword123!
-- - IMPORTANT: Change the password through the application immediately!
-- ============================================================================

USE [AGSAuth];
GO

SET NOCOUNT ON;
GO

PRINT '============================================================================';
PRINT 'Starting First User Onboarding Process';
PRINT '============================================================================';
PRINT '';

BEGIN TRANSACTION;

BEGIN TRY
    -- Variables to store IDs
    DECLARE @DataOriginId INT;
    DECLARE @OrganizationId INT;
    DECLARE @OrganizationGroupId INT;
    DECLARE @LoginProviderId INT;
    DECLARE @ProfileId INT;
    DECLARE @ProfileOrganizationId INT;
    DECLARE @ProfileGroupId INT;

    -- ========================================================================
    -- Step 1: Create or Get DataOrigin
    -- ========================================================================
    PRINT 'Step 1: Setting up DataOrigin...';
    
    IF NOT EXISTS (SELECT 1 FROM [dbo].[DataOrigin] WHERE [DBName] = 'AGSAuth' AND [TableName] = 'Person.Profile')
    BEGIN
        INSERT INTO [dbo].[DataOrigin] ([DBName], [TableName], [SyncEnabled], [Status])
        VALUES ('AGSAuth', 'Person.Profile', 0, 1);
        PRINT '  - Created new DataOrigin';
    END
    ELSE
    BEGIN
        PRINT '  - DataOrigin already exists';
    END

    SELECT @DataOriginId = [DataOriginId] 
    FROM [dbo].[DataOrigin] 
    WHERE [DBName] = 'AGSAuth' AND [TableName] = 'Person.Profile';

    PRINT '  - DataOriginId: ' + CAST(@DataOriginId AS NVARCHAR(10));
    PRINT '';

    -- ========================================================================
    -- Step 2: Create Organization
    -- ========================================================================
    PRINT 'Step 2: Creating Organization...';
    
    INSERT INTO [Partner].[Organization] ([Name], [Note], [Status])
    VALUES ('MyCompany', 'Default organization for first user', 1);
    
    SET @OrganizationId = SCOPE_IDENTITY();
    PRINT '  - Created Organization: MyCompany';
    PRINT '  - OrganizationId: ' + CAST(@OrganizationId AS NVARCHAR(10));
    PRINT '';

    -- ========================================================================
    -- Step 3: Create Admin GroupTemplate (if not exists)
    -- ========================================================================
    PRINT 'Step 3: Setting up Admin Group Template...';
    
    IF NOT EXISTS (SELECT 1 FROM [dbo].[GroupTemplate] WHERE [GroupName] = 'Admin')
    BEGIN
        INSERT INTO [dbo].[GroupTemplate] 
            ([GroupName], [Description], [PartnerUse], [Status])
        VALUES 
            ('Admin', 'Administrator group with full permissions', 1, 1);
        PRINT '  - Created Admin GroupTemplate';
    END
    ELSE
    BEGIN
        PRINT '  - Admin GroupTemplate already exists';
    END
    PRINT '';

    -- ========================================================================
    -- Step 4: Create Organization Group
    -- ========================================================================
    PRINT 'Step 4: Creating Organization Group...';
    
    INSERT INTO [Partner].[OrganizationGroup] 
        ([OrganizationId], [GroupName], [Description], [Status])
    VALUES 
        (@OrganizationId, 'MyCompany Admin', 'Administrator group with full permissions', 1);
    
    SET @OrganizationGroupId = SCOPE_IDENTITY();
    PRINT '  - Created OrganizationGroup: MyCompany Admin';
    PRINT '  - OrganizationGroupId: ' + CAST(@OrganizationGroupId AS NVARCHAR(10));
    PRINT '';

    -- ========================================================================
    -- Step 5: Create or Get Login Provider
    -- ========================================================================
    PRINT 'Step 5: Setting up Login Provider...';
    
    IF NOT EXISTS (SELECT 1 FROM [Person].[LoginProvider] WHERE [ProviderName] = 'Local')
    BEGIN
        INSERT INTO [Person].[LoginProvider] 
            ([ProviderKey], [ProviderName], [DisplayName], [Status])
        VALUES 
            ('local-auth', 'Local', 'Local Authentication', 1);
        PRINT '  - Created Local LoginProvider';
    END
    ELSE
    BEGIN
        PRINT '  - Local LoginProvider already exists';
    END

    SELECT @LoginProviderId = [LoginProviderId] 
    FROM [Person].[LoginProvider] 
    WHERE [ProviderName] = 'Local';

    PRINT '  - LoginProviderId: ' + CAST(@LoginProviderId AS NVARCHAR(10));
    PRINT '';

    -- ========================================================================
    -- Step 6: Create User Profile
    -- ========================================================================
    PRINT 'Step 6: Creating User Profile...';
    
    INSERT INTO [Person].[Profile] 
        ([LoginProviderId], [Email], [EmailConfirmed], [PhoneNumber], 
         [PhoneNumberConfirmed], [TwoFactorEnabled], [FirstName], 
         [LastName], [Suffix], [DataOriginId], [SyncFlag], [Status])
    VALUES 
        (@LoginProviderId, 'admin@mycompany.com', 1, '+1234567890', 
         1, 0, 'Admin', 
         'User', 0, @DataOriginId, 0, 1);
    
    SET @ProfileId = SCOPE_IDENTITY();
    PRINT '  - Created User Profile: Admin User';
    PRINT '  - ProfileId: ' + CAST(@ProfileId AS NVARCHAR(10));
    PRINT '  - Email: admin@mycompany.com';
    PRINT '';

    -- ========================================================================
    -- Step 7: Create User Credentials
    -- ========================================================================
    PRINT 'Step 7: Creating User Credentials...';
    PRINT '  - Username: admin';
    PRINT '  - Password: TempPassword123! (TEMPORARY - Change immediately!)';
    
    DECLARE @Username NVARCHAR(255) = 'admin';
    DECLARE @PlaceholderPassword NVARCHAR(255) = 'TempPassword123!';
    DECLARE @PasswordHash VARBINARY(4000) = HASHBYTES('SHA2_256', CAST(@PlaceholderPassword AS VARBINARY(MAX)));

    INSERT INTO [Person].[Credential] 
        ([ProfileId], [UserName], [Password], [LockoutEnd], 
         [CredentialLocked], [AccessFailedCount], [DataOriginId], 
         [SyncFlag], [Status])
    VALUES 
        (@ProfileId, @Username, @PasswordHash, NULL, 
         0, 0, @DataOriginId, 
         0, 1);

    PRINT '  - Credential created successfully';
    PRINT '  - WARNING: Using SHA-256 hash (fallback method)';
    PRINT '  - For production, ensure crypto service is running for proper password hashing';
    PRINT '';

    -- ========================================================================
    -- Step 8: Link User to Organization
    -- ========================================================================
    PRINT 'Step 8: Linking User to Organization...';
    
    INSERT INTO [Person].[ProfileOrganization] 
        ([ProfileId], [OrganizationId], [Status])
    VALUES 
        (@ProfileId, @OrganizationId, 1);
    
    SET @ProfileOrganizationId = SCOPE_IDENTITY();
    PRINT '  - User linked to Organization';
    PRINT '  - ProfileOrganizationId: ' + CAST(@ProfileOrganizationId AS NVARCHAR(10));
    PRINT '';

    -- ========================================================================
    -- Step 9: Assign User to Group
    -- ========================================================================
    PRINT 'Step 9: Assigning User to Admin Group...';
    
    INSERT INTO [Person].[ProfileGroup] 
        ([ProfileId], [OrganizationGroupId])
    VALUES 
        (@ProfileId, @OrganizationGroupId);
    
    SET @ProfileGroupId = SCOPE_IDENTITY();
    PRINT '  - User assigned to Admin Group';
    PRINT '  - ProfileGroupId: ' + CAST(@ProfileGroupId AS NVARCHAR(10));
    PRINT '';

    -- ========================================================================
    -- Commit Transaction
    -- ========================================================================
    COMMIT TRANSACTION;
    PRINT 'Transaction committed successfully!';
    PRINT '';

    -- ========================================================================
    -- Display Summary
    -- ========================================================================
    PRINT '============================================================================';
    PRINT 'USER ONBOARDING COMPLETED SUCCESSFULLY!';
    PRINT '============================================================================';
    PRINT '';
    PRINT 'Created User Summary:';
    PRINT '--------------------';

    SELECT 
        p.[ProfileId],
        p.[RowGuid] AS ProfileGuid,
        p.[Email],
        p.[FirstName],
        p.[LastName],
        c.[UserName],
        CASE WHEN c.[CredentialLocked] = 0 THEN 'Unlocked' ELSE 'Locked' END AS LockStatus,
        c.[AccessFailedCount],
        o.[Name] AS OrganizationName,
        og.[GroupName]
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.[ProfileId] = c.[ProfileId]
    INNER JOIN [Person].[ProfileOrganization] po ON p.[ProfileId] = po.[ProfileId]
    INNER JOIN [Partner].[Organization] o ON po.[OrganizationId] = o.[OrganizationId]
    INNER JOIN [Person].[ProfileGroup] pg ON p.[ProfileId] = pg.[ProfileId]
    INNER JOIN [Partner].[OrganizationGroup] og ON pg.[OrganizationGroupId] = og.[OrganizationGroupId]
    WHERE p.[ProfileId] = @ProfileId;

    PRINT '';
    PRINT 'Login Credentials:';
    PRINT '-----------------';
    PRINT 'Username: admin';
    PRINT 'Password: TempPassword123!';
    PRINT '';
    PRINT 'Next Steps:';
    PRINT '----------';
    PRINT '1. Start the authserver application';
    PRINT '2. Navigate to: http://localhost:9080/login';
    PRINT '3. Login with the credentials above';
    PRINT '4. IMMEDIATELY change the password through the application';
    PRINT '5. Consider enabling two-factor authentication';
    PRINT '';
    PRINT 'IMPORTANT SECURITY NOTES:';
    PRINT '------------------------';
    PRINT '- The password hash used is SHA-256 (local fallback)';
    PRINT '- For production use, ensure the crypto service is running';
    PRINT '- Change the temporary password immediately after first login';
    PRINT '- Review and update user permissions as needed';
    PRINT '';
    PRINT '============================================================================';

END TRY
BEGIN CATCH
    -- ========================================================================
    -- Error Handling
    -- ========================================================================
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    PRINT '';
    PRINT '============================================================================';
    PRINT 'ERROR: User onboarding failed!';
    PRINT '============================================================================';
    PRINT '';
    PRINT 'Error Details:';
    PRINT 'Error Number: ' + CAST(ERROR_NUMBER() AS NVARCHAR(10));
    PRINT 'Error Message: ' + ERROR_MESSAGE();
    PRINT 'Error Line: ' + CAST(ERROR_LINE() AS NVARCHAR(10));
    PRINT '';
    PRINT 'Common Issues:';
    PRINT '1. Foreign key constraints - Ensure parent records exist';
    PRINT '2. Duplicate data - Check if records already exist';
    PRINT '3. Permissions - Ensure you have INSERT permissions';
    PRINT '';
    PRINT 'Please review the error and try again.';
    PRINT '============================================================================';
    
    -- Re-throw the error
    THROW;
END CATCH;

GO

SET NOCOUNT OFF;
GO
