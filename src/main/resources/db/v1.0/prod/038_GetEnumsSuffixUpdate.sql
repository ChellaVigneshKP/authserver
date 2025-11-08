BEGIN
    INSERT INTO [dbo].[EnumType] ([Name], [Description], [Status])
    VALUES ('SuffixType', 'Types of suffixes for names', 1);
end
GO

DECLARE @typeId INT;
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'SuffixType';
BEGIN
    INSERT INTO [dbo].Enum ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'Mr', N'Mr');
    INSERT INTO [dbo].Enum ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'Mrs', N'Mrs');
    INSERT INTO [dbo].Enum ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'Miss', N'Miss');
    INSERT INTO [dbo].Enum ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'Sr', N'Sr');
    INSERT INTO [dbo].Enum ([EnumTypeId], [Code], [Description]) VALUES (@typeId, N'Jr', N'Jr');
END
GO

CREATE OR ALTER PROCEDURE [dbo].[GetEnums]
AS
BEGIN
    SELECT MAX(CASE WHEN [Code] = 'Organization' THEN [EnumId] END)     AS [OrganizationEnumId],
           MAX(CASE WHEN [Code] = 'PublicKey' THEN [EnumId] END)        AS [PublicKeyEnumId],
           MAX(CASE WHEN [Code] = 'RS256' THEN [EnumId] END)            AS [RS256EnumId],
           MAX(CASE WHEN [Code] = 'ES256' THEN [EnumId] END)            AS [ES256EnumId],
           MAX(CASE WHEN [Code] = 'HS256' THEN [EnumId] END)            AS [HS256EnumId],
           MAX(CASE WHEN [Code] = 'Mobile' THEN [EnumId] END)           AS [MobileEnumId],
           MAX(CASE WHEN [Code] = 'Web' THEN [EnumId] END)              AS [WebEnumId],
           MAX(CASE WHEN [Code] = 'Server' THEN [EnumId] END)           AS [ServerEnumId],
           MAX(CASE WHEN [Code] = 'AuthCode' THEN [EnumId] END)         AS [AuthCodeEnumId],
           MAX(CASE WHEN [Code] = 'ClientSecretJWT' THEN [EnumId] END)  AS [ClientSecretJWTEnumId],
           MAX(CASE WHEN [Code] = 'PrivateKeyJWT' THEN [EnumId] END)    AS [PrivateKeyJWTEnumId],
           MAX(CASE WHEN [Code] = 'Access Token' THEN [EnumId] END)     AS [AccessTokenEnumId],
           MAX(CASE WHEN [Code] = 'Refresh Token' THEN [EnumId] END)    AS [RefreshTokenEnumId],
           MAX(CASE WHEN [Code] = 'OIDC Token' THEN [EnumId] END)       AS [IDTokenEnumId],
           MAX(CASE WHEN [Code] = 'Auth Code Token' THEN [EnumId] END)  AS [AuthCodeTokenEnumId],
           MAX(CASE WHEN [Code] = 'Session Active' THEN [EnumId] END)   AS [AuthSessionActiveEnumId],
           MAX(CASE WHEN [Code] = 'Session Inactive' THEN [EnumId] END) AS [AuthSessionInactiveEnumId],
           MAX(CASE WHEN [Code] = 'self-contained' THEN [EnumId] END)   AS [SelfContainedAccessTokenFormatEnumId],
           MAX(CASE WHEN [Code] = 'Mr' THEN [EnumId] END)               AS [MrSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Mrs' THEN [EnumId] END)              AS [MrsSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Miss' THEN [EnumId] END)             AS [MissSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Sr' THEN [EnumId] END)               AS [SrSuffixEnumId],
           MAX(CASE WHEN [Code] = 'Jr' THEN [EnumId] END)               AS [JrSuffixEnumId]
    FROM [dbo].[Enum]
END
GO

GRANT EXECUTE ON [dbo].[GetEnums] TO [db_spexec]
GO
