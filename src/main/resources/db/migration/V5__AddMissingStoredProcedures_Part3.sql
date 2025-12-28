-- =============================================
-- Add All Missing Stored Procedures - Part 3
-- Person Schema Procedures
-- =============================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- =============================================
-- Person Schema Stored Procedures
-- =============================================

-- Person.CreateUser
CREATE OR ALTER PROCEDURE [Person].[CreateUser]
    @orgId INT,
    @firstName NVARCHAR(255),
    @lastName NVARCHAR(255),
    @username NVARCHAR(255),
    @password VARBINARY(4000),
    @version INT,
    @groupId INT,
    @email NVARCHAR(255),
    @phoneNumber NVARCHAR(128),
    @memberId NVARCHAR(255),
    @loginId UNIQUEIDENTIFIER,
    @externalId UNIQUEIDENTIFIER,
    @secondaryPhoneNumber NVARCHAR(128),
    @disallowedRecentPasswordCount INT,
    @syncFlag BIT
AS
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION
            -- Get default login provider
            DECLARE @loginProviderId INT;
            SELECT TOP 1 @loginProviderId = LoginProviderId FROM [Person].[LoginProvider] WHERE Status = 1;
            
            -- Get default data origin
            DECLARE @dataOriginId INT;
            SELECT TOP 1 @dataOriginId = DataOriginId FROM [dbo].[DataOrigin] WHERE Status = 1;
            
            -- Get default suffix enum
            DECLARE @suffixId INT = 0;
            
            -- Create Profile
            INSERT INTO [Person].[Profile]
                ([LoginProviderId], [FirstName], [LastName], [Email], [EmailConfirmed], [PhoneNumber], [PhoneNumberConfirmed], 
                 [TwoFactorEnabled], [Suffix], [DataOriginId], [SyncFlag], [Status], [SecondaryPhoneNumber], [MemberId], [LoginId])
            VALUES
                (@loginProviderId, @firstName, @lastName, @email, 0, @phoneNumber, 0, 
                 0, @suffixId, @dataOriginId, @syncFlag, 1, @secondaryPhoneNumber, @memberId, @loginId);
            
            DECLARE @profileId INT = SCOPE_IDENTITY();
            
            -- Create Credential
            INSERT INTO [Person].[Credential]
                ([ProfileId], [UserName], [Password], [LockoutEnd], [CredentialLocked], [AccessFailedCount], 
                 [DataOriginId], [SyncFlag], [Status], [Version], [ExternalId], [DisallowedRecentPasswordCount])
            VALUES
                (@profileId, @username, @password, NULL, 0, 0, 
                 @dataOriginId, @syncFlag, 1, @version, @externalId, @disallowedRecentPasswordCount);
            
            -- Create ProfileOrganization
            INSERT INTO [Person].[ProfileOrganization]
                ([ProfileId], [OrganizationId], [Status])
            VALUES
                (@profileId, @orgId, 1);
            
            DECLARE @profileOrgId INT = SCOPE_IDENTITY();
            
            -- Assign to group
            IF @groupId IS NOT NULL AND @groupId > 0
            BEGIN
                INSERT INTO [Person].[ProfileOrganizationGroup]
                    ([ProfileOrganizationId], [OrganizationGroupId])
                VALUES
                    (@profileOrgId, @groupId);
            END
            
        COMMIT;
        SELECT @profileId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;
        THROW;
    END CATCH
END
GO

-- Person.GetUserById
CREATE OR ALTER PROCEDURE [Person].[GetUserById]
    @id INT
AS
BEGIN
    SELECT p.*, c.*, po.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    INNER JOIN [Person].[ProfileOrganization] po ON p.ProfileId = po.ProfileId
    WHERE p.ProfileId = @id;
END
GO

-- Person.GetUserByUsername
CREATE OR ALTER PROCEDURE [Person].[GetUserByUsername]
    @username NVARCHAR(255)
AS
BEGIN
    SELECT p.*, c.*, po.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    INNER JOIN [Person].[ProfileOrganization] po ON p.ProfileId = po.ProfileId
    WHERE c.UserName = @username;
END
GO

-- Person.GetUserByUsernameAndBranding
CREATE OR ALTER PROCEDURE [Person].[GetUserByUsernameAndBranding]
    @username NVARCHAR(255),
    @branding NVARCHAR(255)
AS
BEGIN
    -- TODO: Implement branding filter logic
    SELECT p.*, c.*, po.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    INNER JOIN [Person].[ProfileOrganization] po ON p.ProfileId = po.ProfileId
    WHERE c.UserName = @username;
END
GO

-- Person.GetUserCredentialsByUsername
CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByUsername]
    @username NVARCHAR(255)
AS
BEGIN
    SELECT p.*, c.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    WHERE c.UserName = @username;
END
GO

-- Person.GetUserCredentialsByUsernameAndBranding
CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByUsernameAndBranding]
    @username NVARCHAR(255),
    @branding NVARCHAR(255)
AS
BEGIN
    -- TODO: Implement branding filter logic
    SELECT p.*, c.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    WHERE c.UserName = @username;
END
GO

-- Person.GetUserCredentialsByGuid
CREATE OR ALTER PROCEDURE [Person].[GetUserCredentialsByGuid]
    @loginId UNIQUEIDENTIFIER
AS
BEGIN
    SELECT p.*, c.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    WHERE p.LoginId = @loginId;
END
GO

-- Person.UpdateUserProfile
CREATE OR ALTER PROCEDURE [Person].[UpdateUserProfile]
    @userGuid UNIQUEIDENTIFIER,
    @firstName NVARCHAR(255),
    @lastName NVARCHAR(255),
    @title NVARCHAR(5),
    @middleInitial NVARCHAR(1),
    @phoneNumber NVARCHAR(128),
    @suffix INT,
    @memberId NVARCHAR(255),
    @loginId UNIQUEIDENTIFIER,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255),
    @secondaryPhoneNumber NVARCHAR(128)
AS
BEGIN
    UPDATE [Person].[Profile]
    SET [FirstName] = @firstName,
        [LastName] = @lastName,
        [Title] = @title,
        [MiddleInitial] = @middleInitial,
        [PhoneNumber] = @phoneNumber,
        [Suffix] = @suffix,
        [MemberId] = @memberId,
        [LoginId] = @loginId,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy,
        [SecondaryPhoneNumber] = @secondaryPhoneNumber
    WHERE [RowGuid] = @userGuid;
    
    SELECT ProfileId AS ID FROM [Person].[Profile] WHERE [RowGuid] = @userGuid;
END
GO

-- Person.UpdateUserEmail
CREATE OR ALTER PROCEDURE [Person].[UpdateUserEmail]
    @userGuid UNIQUEIDENTIFIER,
    @email NVARCHAR(255),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Person].[Profile]
    SET [Email] = @email,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy
    WHERE [RowGuid] = @userGuid;
    
    SELECT ProfileId AS ID FROM [Person].[Profile] WHERE [RowGuid] = @userGuid;
END
GO

-- Person.UpdateUserPassword
CREATE OR ALTER PROCEDURE [Person].[UpdateUserPassword]
    @userGuid UNIQUEIDENTIFIER,
    @password VARBINARY(4000),
    @version INT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255),
    @unlockAccount BIT,
    @disallowedRecentPasswordCount INT
AS
BEGIN
    UPDATE c
    SET c.[Password] = @password,
        c.[Version] = @version,
        c.[ModifiedOn] = @modifiedOn,
        c.[ModifiedBy] = @modifiedBy,
        c.[CredentialLocked] = CASE WHEN @unlockAccount = 1 THEN 0 ELSE c.[CredentialLocked] END,
        c.[AccessFailedCount] = CASE WHEN @unlockAccount = 1 THEN 0 ELSE c.[AccessFailedCount] END,
        c.[LockoutEnd] = CASE WHEN @unlockAccount = 1 THEN NULL ELSE c.[LockoutEnd] END,
        c.[DisallowedRecentPasswordCount] = @disallowedRecentPasswordCount
    FROM [Person].[Credential] c
    INNER JOIN [Person].[Profile] p ON c.ProfileId = p.ProfileId
    WHERE p.[RowGuid] = @userGuid;
    
    SELECT c.Id AS ID 
    FROM [Person].[Credential] c
    INNER JOIN [Person].[Profile] p ON c.ProfileId = p.ProfileId
    WHERE p.[RowGuid] = @userGuid;
END
GO

-- Person.UpdateUserPassword_V2
CREATE OR ALTER PROCEDURE [Person].[UpdateUserPassword_V2]
    @userGuid UNIQUEIDENTIFIER,
    @password VARBINARY(4000),
    @version INT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255),
    @externalId UNIQUEIDENTIFIER,
    @unlockAccount BIT,
    @disallowedRecentPasswordCount INT
AS
BEGIN
    UPDATE c
    SET c.[Password] = @password,
        c.[Version] = @version,
        c.[ModifiedOn] = @modifiedOn,
        c.[ModifiedBy] = @modifiedBy,
        c.[ExternalId] = @externalId,
        c.[CredentialLocked] = CASE WHEN @unlockAccount = 1 THEN 0 ELSE c.[CredentialLocked] END,
        c.[AccessFailedCount] = CASE WHEN @unlockAccount = 1 THEN 0 ELSE c.[AccessFailedCount] END,
        c.[LockoutEnd] = CASE WHEN @unlockAccount = 1 THEN NULL ELSE c.[LockoutEnd] END,
        c.[DisallowedRecentPasswordCount] = @disallowedRecentPasswordCount
    FROM [Person].[Credential] c
    INNER JOIN [Person].[Profile] p ON c.ProfileId = p.ProfileId
    WHERE p.[RowGuid] = @userGuid;
    
    SELECT c.Id AS ID 
    FROM [Person].[Credential] c
    INNER JOIN [Person].[Profile] p ON c.ProfileId = p.ProfileId
    WHERE p.[RowGuid] = @userGuid;
END
GO

-- Person.UpdateUsername
CREATE OR ALTER PROCEDURE [Person].[UpdateUsername]
    @userGuid UNIQUEIDENTIFIER,
    @username NVARCHAR(255),
    @externalId UNIQUEIDENTIFIER,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE c
    SET c.[UserName] = @username,
        c.[ExternalId] = @externalId,
        c.[ModifiedOn] = @modifiedOn,
        c.[ModifiedBy] = @modifiedBy
    FROM [Person].[Credential] c
    INNER JOIN [Person].[Profile] p ON c.ProfileId = p.ProfileId
    WHERE p.[RowGuid] = @userGuid;
    
    SELECT c.Id AS ID 
    FROM [Person].[Credential] c
    INNER JOIN [Person].[Profile] p ON c.ProfileId = p.ProfileId
    WHERE p.[RowGuid] = @userGuid;
END
GO

-- Person.UpdateUserSecuritySettings
CREATE OR ALTER PROCEDURE [Person].[UpdateUserSecuritySettings]
    @userGuid UNIQUEIDENTIFIER,
    @twoFactorEnabled BIT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Person].[Profile]
    SET [TwoFactorEnabled] = @twoFactorEnabled,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy
    WHERE [RowGuid] = @userGuid;
    
    SELECT ProfileId AS ID FROM [Person].[Profile] WHERE [RowGuid] = @userGuid;
END
GO

-- Person.UpdateUserStatus
CREATE OR ALTER PROCEDURE [Person].[UpdateUserStatus]
    @userGuid UNIQUEIDENTIFIER,
    @status TINYINT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Person].[Profile]
    SET [Status] = @status,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy
    WHERE [RowGuid] = @userGuid;
    
    SELECT ProfileId AS ID FROM [Person].[Profile] WHERE [RowGuid] = @userGuid;
END
GO

-- Person.UpdateUserForChangeProfilePage
CREATE OR ALTER PROCEDURE [Person].[UpdateUserForChangeProfilePage]
    @userGuid UNIQUEIDENTIFIER,
    @email NVARCHAR(255),
    @phoneNumber NVARCHAR(128),
    @secondaryPhoneNumber NVARCHAR(128),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    UPDATE [Person].[Profile]
    SET [Email] = @email,
        [PhoneNumber] = @phoneNumber,
        [SecondaryPhoneNumber] = @secondaryPhoneNumber,
        [ModifiedOn] = GETUTCDATE(),
        [ModifiedBy] = @modifiedBy
    WHERE [RowGuid] = @userGuid;
    
    SELECT ProfileId AS ID FROM [Person].[Profile] WHERE [RowGuid] = @userGuid;
END
GO

-- Person.GetUserDetailsByUserId
CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserId]
    @userGuid UNIQUEIDENTIFIER
AS
BEGIN
    SELECT p.*, c.*, po.*, o.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    INNER JOIN [Person].[ProfileOrganization] po ON p.ProfileId = po.ProfileId
    INNER JOIN [Partner].[Organization] o ON po.OrganizationId = o.OrganizationId
    WHERE p.[RowGuid] = @userGuid;
END
GO

-- Person.GetUserDetailsByUserIdAndBrandingV2
CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserIdAndBrandingV2]
    @userGuid UNIQUEIDENTIFIER,
    @branding NVARCHAR(255)
AS
BEGIN
    -- TODO: Implement branding filter logic
    SELECT p.*, c.*, po.*, o.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    INNER JOIN [Person].[ProfileOrganization] po ON p.ProfileId = po.ProfileId
    INNER JOIN [Partner].[Organization] o ON po.OrganizationId = o.OrganizationId
    WHERE p.[RowGuid] = @userGuid;
END
GO

-- Person.GetUserAuthDetailsByUserName
CREATE OR ALTER PROCEDURE [Person].[GetUserAuthDetailsByUserName]
    @username NVARCHAR(255)
AS
BEGIN
    SELECT p.*, c.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    WHERE c.UserName = @username;
END
GO

-- Person.GetUserAuthDetailsByUserNameAndExternalSourceCode
CREATE OR ALTER PROCEDURE [Person].[GetUserAuthDetailsByUserNameAndExternalSourceCode]
    @username NVARCHAR(255),
    @sourceCode NVARCHAR(50)
AS
BEGIN
    -- TODO: Implement external source code filter logic
    SELECT p.*, c.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    WHERE c.UserName = @username;
END
GO

-- Person.GetUserPermissions
CREATE OR ALTER PROCEDURE [Person].[GetUserPermissions]
    @userId UNIQUEIDENTIFIER
AS
BEGIN
    SELECT DISTINCT ogp.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[ProfileOrganization] po ON p.ProfileId = po.ProfileId
    INNER JOIN [Person].[ProfileOrganizationGroup] pog ON po.ProfileOrganizationId = pog.ProfileOrganizationId
    INNER JOIN [Partner].[OrganizationGroupPermission] ogp ON pog.OrganizationGroupId = ogp.OrganizationGroupId
    WHERE p.[RowGuid] = @userId
      AND ogp.Status = 1;
END
GO

-- Person.GetUsers
CREATE OR ALTER PROCEDURE [Person].[GetUsers]
    @resultsPerPage INT,
    @offset INT,
    @status TINYINT,
    @search NVARCHAR(255),
    @type NVARCHAR(50),
    @organizationId INT,
    @sourceIds NVARCHAR(MAX)
AS
BEGIN
    SELECT p.*, c.*, po.*
    FROM [Person].[Profile] p
    INNER JOIN [Person].[Credential] c ON p.ProfileId = c.ProfileId
    INNER JOIN [Person].[ProfileOrganization] po ON p.ProfileId = po.ProfileId
    WHERE (@status IS NULL OR p.Status = @status)
      AND (@organizationId IS NULL OR po.OrganizationId = @organizationId)
      AND (@search IS NULL OR c.UserName LIKE '%' + @search + '%' OR p.Email LIKE '%' + @search + '%')
    ORDER BY p.CreatedOn DESC
    OFFSET @offset ROWS
    FETCH NEXT @resultsPerPage ROWS ONLY;
END
GO

-- Person.GetProfileOrganizationByProfileId
CREATE OR ALTER PROCEDURE [Person].[GetProfileOrganizationByProfileId]
    @profileId INT
AS
BEGIN
    SELECT * FROM [Person].[ProfileOrganization]
    WHERE ProfileId = @profileId;
END
GO

-- Person.UpdateAccessFailedCount
CREATE OR ALTER PROCEDURE [Person].[UpdateAccessFailedCount]
    @username NVARCHAR(255),
    @loginSuccess BIT
AS
BEGIN
    IF @loginSuccess = 1
    BEGIN
        UPDATE c
        SET c.[AccessFailedCount] = 0,
            c.[CredentialLocked] = 0,
            c.[LockoutEnd] = NULL
        FROM [Person].[Credential] c
        WHERE c.UserName = @username;
    END
    ELSE
    BEGIN
        UPDATE c
        SET c.[AccessFailedCount] = c.[AccessFailedCount] + 1
        FROM [Person].[Credential] c
        WHERE c.UserName = @username;
    END
END
GO

-- Person.UpdateAccessFailedCountWithExternalSourceCode
CREATE OR ALTER PROCEDURE [Person].[UpdateAccessFailedCountWithExternalSourceCode]
    @username NVARCHAR(255),
    @sourceCode NVARCHAR(50),
    @loginSuccess BIT,
    @accessFailedLimit INT
AS
BEGIN
    IF @loginSuccess = 1
    BEGIN
        UPDATE c
        SET c.[AccessFailedCount] = 0,
            c.[CredentialLocked] = 0,
            c.[LockoutEnd] = NULL
        FROM [Person].[Credential] c
        WHERE c.UserName = @username;
    END
    ELSE
    BEGIN
        UPDATE c
        SET c.[AccessFailedCount] = c.[AccessFailedCount] + 1,
            c.[CredentialLocked] = CASE WHEN c.[AccessFailedCount] + 1 >= @accessFailedLimit THEN 1 ELSE 0 END,
            c.[LockoutEnd] = CASE WHEN c.[AccessFailedCount] + 1 >= @accessFailedLimit THEN DATEADD(MINUTE, 30, GETUTCDATE()) ELSE NULL END
        FROM [Person].[Credential] c
        WHERE c.UserName = @username;
    END
END
GO

-- Person.LockAccount
CREATE OR ALTER PROCEDURE [Person].[LockAccount]
    @username NVARCHAR(255),
    @sourceCode NVARCHAR(50),
    @accessFailedLimit INT
AS
BEGIN
    UPDATE c
    SET c.[CredentialLocked] = 1,
        c.[LockoutEnd] = DATEADD(MINUTE, 30, GETUTCDATE()),
        c.[AccessFailedCount] = @accessFailedLimit
    FROM [Person].[Credential] c
    WHERE c.UserName = @username;
END
GO

-- Person.UnlockAccount
CREATE OR ALTER PROCEDURE [Person].[UnlockAccount]
    @username NVARCHAR(255),
    @sourceCode NVARCHAR(50)
AS
BEGIN
    UPDATE c
    SET c.[CredentialLocked] = 0,
        c.[LockoutEnd] = NULL,
        c.[AccessFailedCount] = 0
    FROM [Person].[Credential] c
    WHERE c.UserName = @username;
END
GO

-- Person.ReactivateUserWithPassword
CREATE OR ALTER PROCEDURE [Person].[ReactivateUserWithPassword]
    @userGuid UNIQUEIDENTIFIER,
    @password VARBINARY(4000),
    @version INT,
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255),
    @externalId UNIQUEIDENTIFIER,
    @unlockAccount BIT,
    @disallowedRecentPasswordCount INT
AS
BEGIN
    -- Reactivate the profile
    UPDATE [Person].[Profile]
    SET [Status] = 1,
        [ModifiedOn] = @modifiedOn,
        [ModifiedBy] = @modifiedBy
    WHERE [RowGuid] = @userGuid;
    
    -- Update the password
    UPDATE c
    SET c.[Password] = @password,
        c.[Version] = @version,
        c.[ModifiedOn] = @modifiedOn,
        c.[ModifiedBy] = @modifiedBy,
        c.[ExternalId] = @externalId,
        c.[Status] = 1,
        c.[CredentialLocked] = 0,
        c.[AccessFailedCount] = 0,
        c.[LockoutEnd] = NULL,
        c.[DisallowedRecentPasswordCount] = @disallowedRecentPasswordCount
    FROM [Person].[Credential] c
    INNER JOIN [Person].[Profile] p ON c.ProfileId = p.ProfileId
    WHERE p.[RowGuid] = @userGuid;
    
    SELECT p.ProfileId AS ID 
    FROM [Person].[Profile] p
    WHERE p.[RowGuid] = @userGuid;
END
GO

-- Person.CreateMetadata
CREATE OR ALTER PROCEDURE [Person].[CreateMetadata]
    @profileId INT,
    @key NVARCHAR(30),
    @value NVARCHAR(128),
    @modifiedOn DATETIME2(0),
    @modifiedBy NVARCHAR(255)
AS
BEGIN
    INSERT INTO [Person].[Metadata]
        ([ProfileId], [Key], [Value], [CreatedOn], [ModifiedOn], [ModifiedBy])
    VALUES
        (@profileId, @key, @value, GETUTCDATE(), @modifiedOn, @modifiedBy);
END
GO

-- Person.GetMetadata
CREATE OR ALTER PROCEDURE [Person].[GetMetadata]
    @profileId INT
AS
BEGIN
    SELECT * FROM [Person].[Metadata]
    WHERE ProfileId = @profileId;
END
GO

-- Person.DeleteMetadata
CREATE OR ALTER PROCEDURE [Person].[DeleteMetadata]
    @profileId INT
AS
BEGIN
    DELETE FROM [Person].[Metadata]
    WHERE ProfileId = @profileId;
END
GO

-- Person.getHistoricPasswords
CREATE OR ALTER PROCEDURE [Person].[getHistoricPasswords]
    @profileId INT
AS
BEGIN
    SELECT * FROM [Person].[PasswordHistory]
    WHERE ProfileId = @profileId
    ORDER BY CreatedOn DESC;
END
GO

-- Person.validatePasswordBlacklisted
CREATE OR ALTER PROCEDURE [Person].[validatePasswordBlacklisted]
    @password VARBINARY(MAX)
AS
BEGIN
    IF EXISTS (SELECT 1 FROM [dbo].[PasswordBlacklist] WHERE PasswordHash = @password)
        SELECT 1 AS IsBlacklisted
    ELSE
        SELECT 0 AS IsBlacklisted
END
GO

