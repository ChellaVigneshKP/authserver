CREATE OR ALTER PROCEDURE [Token].[SetAuthCodeConsumedOn] @Data NVARCHAR(512)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @SessionId UNIQUEIDENTIFIER;
SELECT @SessionId=SessionId FROM Token.AuthCode WITH(UPDLOCK,HOLDLOCK) WHERE DataHash=HASHBYTES('SHA2_256',@Data) AND Data=@Data AND ConsumedOn IS NULL AND Expiration>SYSUTCDATETIME();
IF @SessionId IS NULL THROW 51016,'Authorization code is expired, unknown or already consumed.',1;
UPDATE Token.AuthCode SET ConsumedOn=SYSUTCDATETIME() WHERE DataHash=HASHBYTES('SHA2_256',@Data);
UPDATE Token.Pkce SET ConsumedOn=SYSUTCDATETIME() WHERE SessionId=@SessionId AND ConsumedOn IS NULL;
UPDATE Token.Token SET ConsumedOn=SYSUTCDATETIME() WHERE SessionId=@SessionId AND Data=@Data AND ConsumedOn IS NULL;
COMMIT;
END
GO
INSERT dbo.GroupPermissionTemplate(PermissionName,PermissionKey,GroupId)
SELECT 'Organizations: List','idp-admin-org:list',NULL
WHERE NOT EXISTS(SELECT 1 FROM dbo.GroupPermissionTemplate WHERE PermissionKey='idp-admin-org:list');
INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey)
SELECT g.OrganizationGroupId,g.OrganizationId,'Organizations: List','idp-admin-org:list'
FROM Partner.OrganizationGroup g WHERE g.OrganizationId=1 AND g.GroupTemplateId=1
AND NOT EXISTS(SELECT 1 FROM Partner.OrganizationGroupPermission p WHERE p.OrganizationGroupId=g.OrganizationGroupId AND p.PermissionKey='idp-admin-org:list');
GO
