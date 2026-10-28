-- Additive policy projections. Incompatible imported relationships are reported, never repaired.
CREATE OR ALTER VIEW Person.ManagementMembershipIssues AS
SELECT ProfileId,'MULTIPLE_ACTIVE_ORGANIZATIONS' AS Code
FROM Person.ProfileOrganization WHERE Status=1 GROUP BY ProfileId HAVING COUNT(*)>1
UNION
SELECT pg.ProfileId,'MULTIPLE_ORGANIZATION_GROUPS'
FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
GROUP BY pg.ProfileId,g.OrganizationId HAVING COUNT(*)>1
UNION
SELECT pg.ProfileId,'FOREIGN_ACTIVE_GROUP'
FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId AND g.Status=1
WHERE NOT EXISTS(SELECT 1 FROM Person.ProfileOrganization po WHERE po.ProfileId=pg.ProfileId AND po.OrganizationId=g.OrganizationId AND po.Status=1);
GO
CREATE OR ALTER VIEW Person.ActiveManagementIdentity AS
SELECT p.ProfileId,p.RowGuid AS UserGuid,o.OrganizationId,o.RowGuid AS OrganizationGuid
FROM Person.Profile p
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId AND o.Status=1
WHERE p.Status=1 AND (p.AbsoluteExpirationOn IS NULL OR p.AbsoluteExpirationOn>SYSUTCDATETIME())
AND NOT EXISTS(SELECT 1 FROM Person.ManagementMembershipIssues issue WHERE issue.ProfileId=p.ProfileId)
AND EXISTS(SELECT 1 FROM Person.Credential c WHERE c.ProfileId=p.ProfileId AND c.Status=1 AND c.CredentialLocked=0);
GO
CREATE OR ALTER VIEW Person.EffectivePlatformAdministrator AS
SELECT DISTINCT actor.ProfileId,actor.UserGuid,actor.OrganizationId,actor.OrganizationGuid,g.OrganizationGroupId
FROM Person.ActiveManagementIdentity actor
JOIN Person.ProfileGroup pg ON pg.ProfileId=actor.ProfileId
JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId AND g.OrganizationId=actor.OrganizationId AND g.Status=1
JOIN Partner.OrganizationGroupPermission permission ON permission.OrganizationGroupId=g.OrganizationGroupId
    AND permission.OrganizationId=actor.OrganizationId AND permission.Status=1
WHERE actor.OrganizationId=1 AND permission.PermissionKey COLLATE Latin1_General_100_BIN2=N'idp-admin-org:switch'
AND DATALENGTH(permission.PermissionKey)=DATALENGTH(N'idp-admin-org:switch');
GO
CREATE OR ALTER PROCEDURE Person.GetUserPermissions @UserId INT AS
BEGIN
    SET NOCOUNT ON;
    SELECT permission.*
    FROM Person.ActiveManagementIdentity actor
    JOIN Person.ProfileGroup pg ON pg.ProfileId=actor.ProfileId
    JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId AND g.OrganizationId=actor.OrganizationId AND g.Status=1
    JOIN Partner.OrganizationGroupPermission permission ON permission.OrganizationGroupId=g.OrganizationGroupId
        AND permission.OrganizationId=actor.OrganizationId AND permission.Status=1
    WHERE actor.ProfileId=@UserId AND (actor.OrganizationId=1 OR NOT EXISTS(
        SELECT 1 FROM dbo.PermissionCatalog c WHERE c.PermissionKey=permission.PermissionKey COLLATE Latin1_General_100_BIN2 AND c.PlatformOnly=1));
END
GO
-- Nonunique supporting indexes are safe even when imported ambiguity prevents new writes.
CREATE INDEX IX_ProfileGroup_Management_Profile ON Person.ProfileGroup(ProfileId,OrganizationGroupId);
CREATE INDEX IX_ProfileOrganization_Management_Active ON Person.ProfileOrganization(ProfileId,OrganizationId) WHERE Status=1;
GO
CREATE OR ALTER PROCEDURE Person.AssignOrganizationGroup @OrganizationId INT,@ProfileId INT,@GroupId INT=NULL AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @@TRANCOUNT=0 OR ISNULL(APPLOCK_MODE('public','authserver:identity-management:v1','Transaction'),'NoLock')<>'Exclusive'
        THROW 51014,'Assignment requires the management transaction lock.',1;
    IF EXISTS(SELECT 1 FROM Person.ManagementMembershipIssues WHERE ProfileId=@ProfileId)
        THROW 51014,'Membership requires review.',1;
    IF NOT EXISTS(SELECT 1 FROM Person.ProfileOrganization WHERE ProfileId=@ProfileId AND OrganizationId=@OrganizationId AND Status=1)
        THROW 51014,'Active organization membership required.',1;
    IF @GroupId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM Partner.OrganizationGroup WHERE OrganizationGroupId=@GroupId AND OrganizationId=@OrganizationId AND Status=1)
        THROW 51014,'Active same-organization group required.',1;
    DELETE pg FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
        WHERE pg.ProfileId=@ProfileId AND g.OrganizationId=@OrganizationId;
    IF @GroupId IS NOT NULL INSERT Person.ProfileGroup(ProfileId,OrganizationGroupId) VALUES(@ProfileId,@GroupId);
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserById] @ProfileId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
OUTER APPLY(SELECT g.* FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
    WHERE pg.ProfileId=p.ProfileId AND g.Status=1 AND g.OrganizationId=po.OrganizationId) og
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE p.ProfileId=@ProfileId;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserByUsernameAndBranding] @Username NVARCHAR(255), @Branding NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
OUTER APPLY(SELECT g.* FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
    WHERE pg.ProfileId=p.ProfileId AND g.Status=1 AND g.OrganizationId=po.OrganizationId) og
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE c.UserName=@Username AND es.SourceCode=@Branding AND p.Status=1;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserId] @UserGuid UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
OUTER APPLY(SELECT g.* FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
    WHERE pg.ProfileId=p.ProfileId AND g.Status=1 AND g.OrganizationId=po.OrganizationId) og
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE p.RowGuid=@UserGuid;
END
GO

CREATE OR ALTER PROCEDURE [Person].[GetUserDetailsByUserIdAndBrandingV2] @UserGuid UNIQUEIDENTIFIER,@Branding NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
OUTER APPLY(SELECT g.* FROM Person.ProfileGroup pg JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
    WHERE pg.ProfileId=p.ProfileId AND g.Status=1 AND g.OrganizationId=po.OrganizationId) og
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE p.RowGuid=@UserGuid AND es.SourceCode=@Branding;
END
GO
