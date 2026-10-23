-- Preserve imported history. Exclude ambiguous identities instead of choosing a membership.
-- Only current same-organization groups participate in classification and pagination totals.
CREATE OR ALTER PROCEDURE [Person].[GetUsers] @ResultsPerPage INT, @Offset INT, @Status TINYINT, @Search NVARCHAR(50), @Type NVARCHAR(50),
@OrganizationGuid UNIQUEIDENTIFIER, @SourceIds NVARCHAR(MAX)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
IF @ResultsPerPage<1 OR @ResultsPerPage>1000 OR @Offset<0 THROW 51012, 'Invalid pagination.', 1;
SELECT COUNT(*) OVER() AS Results,p.ProfileId,p.FirstName,p.LastName,c.UserName,p.RowGuid,p.Email,p.Title,p.MiddleInitial,p.Suffix,
p.PhoneNumber,p.TwoFactorEnabled,p.Status,p.SecondaryPhoneNumber,p.SyncFlag,c.SyncFlag AS CredSyncFlag,
c.RowGuid AS LoginId,es.SourceCode AS Branding,po.OrganizationId AS ProfileOrgId,
o.RowGuid AS ProfileOrgGuid,COALESCE(go.RowGuid,o.RowGuid) AS OrgId,og.RowGuid AS GroupGuid,og.GroupName
FROM Person.Profile p JOIN Person.Credential c ON c.ProfileId=p.ProfileId
JOIN dbo.ExternalSource es ON es.SourceId=c.ExternalId
JOIN Person.ProfileOrganization po ON po.ProfileId=p.ProfileId AND po.Status=1
JOIN Partner.Organization o ON o.OrganizationId=po.OrganizationId
OUTER APPLY(SELECT g.* FROM Person.ProfileGroup pg
    JOIN Partner.OrganizationGroup g ON g.OrganizationGroupId=pg.OrganizationGroupId
    WHERE pg.ProfileId=p.ProfileId AND g.Status=1 AND g.OrganizationId=po.OrganizationId) og
LEFT JOIN Partner.Organization go ON go.OrganizationId=og.OrganizationId
WHERE NOT EXISTS(SELECT 1 FROM Person.ManagementMembershipIssues issue WHERE issue.ProfileId=p.ProfileId)
AND (@Status IS NULL OR p.Status=@Status)
AND (@OrganizationGuid IS NULL OR o.RowGuid=@OrganizationGuid)
AND (@Type IS NULL OR @Type<>'Administrator' OR og.OrganizationGroupId IS NOT NULL)
AND (@Type IS NULL OR @Type<>'User' OR og.OrganizationGroupId IS NULL)
AND (@SourceIds IS NULL OR EXISTS(SELECT 1 FROM STRING_SPLIT(@SourceIds,',') v WHERE TRY_CONVERT(UNIQUEIDENTIFIER,v.value)=es.SourceId))
AND (NULLIF(@Search,'') IS NULL OR p.FirstName LIKE '%'+@Search+'%' OR p.LastName LIKE '%'+@Search+'%' OR p.Email LIKE '%'+@Search+'%' OR c.UserName LIKE '%'+@Search+'%' OR p.PhoneNumber LIKE '%'+@Search+'%' OR p.SecondaryPhoneNumber LIKE '%'+@Search+'%')
ORDER BY p.LastName,p.FirstName,p.ProfileId,c.Id,po.ProfileOrganizationId OFFSET @Offset ROWS FETCH NEXT @ResultsPerPage ROWS ONLY;
END
GO
