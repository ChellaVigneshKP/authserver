-- =============================================
-- Sample SQL Queries for Admin Portal Testing
-- =============================================
-- These queries help verify the admin portal setup
-- and retrieve important configuration information

-- 1. Check if admin organization exists
SELECT * FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';
GO

-- 2. Check if Admin Portal application exists
SELECT 
    a.[ApplicationId],
    a.[ClientId],
    a.[Name],
    a.[Description],
    a.[Uri],
    a.[RowGuid],
    a.[CreatedOn]
FROM [Client].[Application] a
WHERE a.[Name] = 'Admin Portal';
GO

-- 3. Check admin group
SELECT * FROM [Partner].[OrganizationGroup] 
WHERE [GroupName] = 'Ascensus Admin';
GO

-- 4. Check admin profile
SELECT * FROM [Person].[Profile] 
WHERE [FirstName] = 'Ascensus' AND [LastName] = 'Admin';
GO

-- 5. Get Admin Portal configuration
EXEC [dbo].[GetAdminConfig];
GO

-- 6. Check redirect URIs for Admin Portal
SELECT 
    ru.[Uri],
    ru.[Type],
    a.[Name] as ApplicationName
FROM [Client].[RedirectUri] ru
INNER JOIN [Client].[Application] a ON ru.[ApplicationId] = a.[ApplicationId]
WHERE a.[Name] = 'Admin Portal';
GO

-- 7. Check scopes for Admin Portal
SELECT 
    s.[Name] as ScopeName,
    a.[Name] as ApplicationName
FROM [Client].[Scope] s
INNER JOIN [Client].[Application] a ON s.[ApplicationId] = a.[ApplicationId]
WHERE a.[Name] = 'Admin Portal';
GO

-- 8. Check grant types for Admin Portal
SELECT 
    gt.[Name] as GrantType,
    a.[Name] as ApplicationName
FROM [Client].[GrantType] gt
INNER JOIN [Client].[Application] a ON gt.[ApplicationId] = a.[ApplicationId]
WHERE a.[Name] = 'Admin Portal';
GO

-- 9. Check authentication methods for Admin Portal
SELECT 
    am.[Name] as AuthMethod,
    a.[Name] as ApplicationName
FROM [Client].[AuthenticationMethod] am
INNER JOIN [Client].[Application] a ON am.[ApplicationId] = a.[ApplicationId]
WHERE a.[Name] = 'Admin Portal';
GO

-- 10. List all organizations
SELECT 
    [OrganizationId],
    [Name],
    [Note],
    [Status],
    [CreatedOn]
FROM [Partner].[Organization]
WHERE [Status] = 1
ORDER BY [CreatedOn] DESC;
GO

-- 11. List all applications
SELECT 
    a.[ApplicationId],
    a.[ClientId],
    a.[Name],
    a.[Uri],
    o.[Name] as OrganizationName,
    a.[CreatedOn]
FROM [Client].[Application] a
INNER JOIN [Partner].[Organization] o ON a.[OrganizationId] = o.[OrganizationId]
ORDER BY a.[CreatedOn] DESC;
GO

-- 12. List all users (profiles)
SELECT 
    p.[ProfileId],
    p.[FirstName],
    p.[LastName],
    p.[Email],
    p.[PhoneNumber],
    po.[OrganizationId],
    o.[Name] as OrganizationName,
    p.[CreatedOn]
FROM [Person].[Profile] p
LEFT JOIN [Person].[ProfileOrganization] po ON p.[ProfileId] = po.[ProfileId]
LEFT JOIN [Partner].[Organization] o ON po.[OrganizationId] = o.[OrganizationId]
WHERE p.[Status] = 1
ORDER BY p.[CreatedOn] DESC;
GO

-- 13. Check external sources (branding)
SELECT 
    [SourceId],
    [SourceCode],
    [Name],
    [Status]
FROM [Partner].[ExternalSource]
WHERE [Status] = 1;
GO

-- 14. Get admin group permissions
SELECT 
    ogp.[PermissionName],
    ogp.[Description],
    og.[GroupName]
FROM [Partner].[OrganizationGroupPermission] ogp
INNER JOIN [Partner].[OrganizationGroup] og ON ogp.[OrganizationGroupId] = og.[OrganizationGroupId]
WHERE og.[GroupName] = 'Ascensus Admin';
GO

-- =============================================
-- Cleanup Queries (Use with caution!)
-- =============================================

-- UNCOMMENT ONLY IF YOU NEED TO RESET THE ADMIN PORTAL SETUP
-- WARNING: This will delete all admin portal data

/*
-- Delete Admin Portal application
DELETE FROM [Client].[RedirectUri] 
WHERE [ApplicationId] IN (SELECT [ApplicationId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal');

DELETE FROM [Client].[Scope] 
WHERE [ApplicationId] IN (SELECT [ApplicationId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal');

DELETE FROM [Client].[GrantType] 
WHERE [ApplicationId] IN (SELECT [ApplicationId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal');

DELETE FROM [Client].[AuthenticationMethod] 
WHERE [ApplicationId] IN (SELECT [ApplicationId] FROM [Client].[Application] WHERE [Name] = 'Admin Portal');

DELETE FROM [Client].[Application] WHERE [Name] = 'Admin Portal';

-- Delete admin organization
DELETE FROM [Partner].[OrganizationGroupPermission] 
WHERE [OrganizationGroupId] IN (
    SELECT [OrganizationGroupId] FROM [Partner].[OrganizationGroup] 
    WHERE [OrganizationId] IN (SELECT [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus')
);

DELETE FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] IN (SELECT [OrganizationId] FROM [Partner].[Organization] WHERE [Name] = 'Ascensus');

DELETE FROM [Partner].[Organization] WHERE [Name] = 'Ascensus';

-- Delete admin profile
DELETE FROM [Person].[ProfileOrganizationGroup] 
WHERE [ProfileOrganizationId] IN (
    SELECT [ProfileOrganizationId] FROM [Person].[ProfileOrganization]
    WHERE [ProfileId] IN (SELECT [ProfileId] FROM [Person].[Profile] WHERE [FirstName] = 'Ascensus' AND [LastName] = 'Admin')
);

DELETE FROM [Person].[ProfileOrganization] 
WHERE [ProfileId] IN (SELECT [ProfileId] FROM [Person].[Profile] WHERE [FirstName] = 'Ascensus' AND [LastName] = 'Admin');

DELETE FROM [Person].[Profile] WHERE [FirstName] = 'Ascensus' AND [LastName] = 'Admin';
*/
GO
