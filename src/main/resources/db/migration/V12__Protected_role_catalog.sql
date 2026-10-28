-- Additive role contracts. Legacy IDs, permission rows and ambiguous data are preserved.
ALTER TABLE Partner.OrganizationGroup ADD Revision INT NOT NULL CONSTRAINT DF_OrganizationGroup_Revision DEFAULT 1;
ALTER TABLE Partner.OrganizationGroup ADD IsBuiltin BIT NOT NULL CONSTRAINT DF_OrganizationGroup_IsBuiltin DEFAULT 0;
GO
UPDATE Partner.OrganizationGroup SET IsBuiltin=1 WHERE GroupTemplateId IS NOT NULL;
GO
CREATE TABLE dbo.PermissionCatalog (
    PermissionKey NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    Description NVARCHAR(255) NOT NULL,
    PlatformOnly BIT NOT NULL
);
INSERT dbo.PermissionCatalog(PermissionKey,Description,PlatformOnly) VALUES
(N'idp-admin-admin:create',N'Administrators: Create',0),
(N'idp-admin-admin:delete',N'Administrators: Delete',0),
(N'idp-admin-admin:read',N'Administrators: Read',0),
(N'idp-admin-admin:update',N'Administrators: Update',0),
(N'idp-admin-app:assign-resource',N'Applications: Assign Resource',1),
(N'idp-admin-app:create',N'Applications: Create',0),
(N'idp-admin-app:delete',N'Applications: Delete',0),
(N'idp-admin-app:read',N'Applications: Read',0),
(N'idp-admin-app:update',N'Applications: Update',0),
(N'idp-admin-cred-cert:create',N'Credential Certificates: Create',0),
(N'idp-admin-cred-cert:delete',N'Credential Certificates: Delete',0),
(N'idp-admin-cred-cert:read',N'Credential Certificates: Read',0),
(N'idp-admin-cred-cert:update',N'Credential Certificates: Update',0),
(N'idp-admin-group:assign',N'Group: Assign',0),
(N'idp-admin-group:create',N'Group: Create',1),
(N'idp-admin-group:delete',N'Group: Delete',1),
(N'idp-admin-group:read',N'Groups: Read',0),
(N'idp-admin-group:update',N'Group: Update',1),
(N'idp-admin-org-cert:create',N'Organization Certificates: Create',1),
(N'idp-admin-org-cert:delete',N'Organization Certificates: Delete',1),
(N'idp-admin-org-cert:read',N'Organization Certificates: Read',0),
(N'idp-admin-org-cert:update',N'Organization Certificates: Update',1),
(N'idp-admin-org:create',N'Organizations: Create',1),
(N'idp-admin-org:deactivate',N'Organizations: Deactivate',1),
(N'idp-admin-org:list',N'Organizations: List',1),
(N'idp-admin-org:read',N'Organizations: Read',0),
(N'idp-admin-org:switch',N'Organizations: Switch',1),
(N'idp-admin-org:update',N'Organizations: Update',0),
(N'idp-admin-permission:read',N'Permission: Read',1),
(N'idp-admin-resource:create',N'Resource: Create',1),
(N'idp-admin-resource:delete',N'Resource: Delete',1),
(N'idp-admin-resource:read',N'Resource: Read',1),
(N'idp-admin-resource:update',N'Resource: Update',1),
(N'idp-admin-user:create',N'User: Create',0),
(N'idp-admin-user:delete',N'User: Delete',0),
(N'idp-admin-user:read',N'User: Read',0),
(N'idp-admin-user:update',N'User: Update',0);
GO
-- Deliberate grants only. Tenant Administrators receive user CRUD/read and role assignment.
-- New role-definition and catalog grants are platform-only (NULL template group).
INSERT dbo.GroupPermissionTemplate(PermissionName,PermissionKey,GroupId)
SELECT c.Description,c.PermissionKey,1 FROM dbo.PermissionCatalog c
WHERE c.PermissionKey IN ('idp-admin-user:create','idp-admin-user:read','idp-admin-user:update','idp-admin-user:delete','idp-admin-group:assign')
AND NOT EXISTS(SELECT 1 FROM dbo.GroupPermissionTemplate p WHERE p.GroupId=1 AND p.PermissionKey COLLATE Latin1_General_100_BIN2=c.PermissionKey);
INSERT dbo.GroupPermissionTemplate(PermissionName,PermissionKey,GroupId)
SELECT c.Description,c.PermissionKey,NULL FROM dbo.PermissionCatalog c
WHERE c.PermissionKey IN ('idp-admin-group:create','idp-admin-group:update','idp-admin-group:delete','idp-admin-permission:read')
AND NOT EXISTS(SELECT 1 FROM dbo.GroupPermissionTemplate p WHERE p.GroupId IS NULL AND p.PermissionKey COLLATE Latin1_General_100_BIN2=c.PermissionKey);
INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey)
SELECT g.OrganizationGroupId,g.OrganizationId,c.Description,c.PermissionKey
FROM Partner.OrganizationGroup g CROSS JOIN dbo.PermissionCatalog c
WHERE g.GroupTemplateId=1 AND (
 c.PermissionKey IN ('idp-admin-user:create','idp-admin-user:read','idp-admin-user:update','idp-admin-user:delete','idp-admin-group:assign')
 OR (g.OrganizationId=1 AND c.PermissionKey IN ('idp-admin-group:create','idp-admin-group:update','idp-admin-group:delete','idp-admin-permission:read')))
AND NOT EXISTS(SELECT 1 FROM Partner.OrganizationGroupPermission p WHERE p.OrganizationGroupId=g.OrganizationGroupId AND p.PermissionKey COLLATE Latin1_General_100_BIN2=c.PermissionKey);
GO
-- Read-only preflight report; never rename roles, collapse memberships or rewrite user data.
CREATE OR ALTER VIEW Partner.RolePreflightIssues AS
WITH AmbiguousProfiles AS (
 SELECT ProfileId FROM Person.ProfileGroup GROUP BY ProfileId HAVING COUNT(*)>1
 UNION SELECT ProfileId FROM Person.ProfileOrganization WHERE Status=1 GROUP BY ProfileId HAVING COUNT(*)>1
 UNION SELECT pg.ProfileId FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
 WHERE NOT EXISTS(SELECT 1 FROM Person.ProfileOrganization po WHERE po.ProfileId=pg.ProfileId AND po.OrganizationId=g.OrganizationId AND po.Status=1)
), AffectedOrganizations AS (
 SELECT po.OrganizationId,a.ProfileId FROM AmbiguousProfiles a JOIN Person.ProfileOrganization po ON po.ProfileId=a.ProfileId
 UNION SELECT g.OrganizationId,a.ProfileId FROM AmbiguousProfiles a JOIN Person.ProfileGroup pg ON pg.ProfileId=a.ProfileId JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
)
SELECT OrganizationId,CAST('DUPLICATE_ROLE_NAME' AS VARCHAR(40)) Code,
 CAST(LOWER(LTRIM(RTRIM(GroupName))) COLLATE Latin1_General_100_CI_AS AS NVARCHAR(255)) Detail,CAST(NULL AS INT) ProfileId
FROM Partner.OrganizationGroup
GROUP BY OrganizationId,LOWER(LTRIM(RTRIM(GroupName))) COLLATE Latin1_General_100_CI_AS HAVING COUNT(*)>1
UNION ALL
SELECT OrganizationId,'AMBIGUOUS_MEMBERSHIP',CAST('Review existing memberships before mutation' AS NVARCHAR(255)),ProfileId FROM AffectedOrganizations;
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
                                                       [Description], [IsBuiltin])
            SELECT @OrganizationId,
                   [GroupTemplateId],
                   [GroupName],
                   [Description], 1
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
CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroups] @OrganizationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT og.*, (SELECT COUNT(DISTINCT pg.ProfileId) FROM Person.ProfileGroup pg WHERE pg.OrganizationGroupId=og.OrganizationGroupId) MemberCount
    FROM [Partner].[OrganizationGroup] og
    WHERE og.OrganizationId = @OrganizationId
      AND og.Status = 1
    ORDER BY og.GroupName;
END
GO
CREATE OR ALTER PROCEDURE [Partner].[GetOrganizationGroup] @OrganizationId INT,
                                                           @GroupGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT og.*, (SELECT COUNT(DISTINCT pg.ProfileId) FROM Person.ProfileGroup pg WHERE pg.OrganizationGroupId=og.OrganizationGroupId) MemberCount
    FROM [Partner].[OrganizationGroup] og
    WHERE og.OrganizationId = @OrganizationId
      AND og.RowGuid = @GroupGuid
END
GO
