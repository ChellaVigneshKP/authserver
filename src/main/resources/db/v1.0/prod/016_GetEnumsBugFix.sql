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
           MAX(CASE WHEN [Code] = 'Session Active' THEN [EnumId] END)   AS [AuthSessionActiveEnumId],
           MAX(CASE WHEN [Code] = 'Session Inactive' THEN [EnumId] END) AS [AuthSessionInactiveEnumId]
    FROM [dbo].[Enum]
END
GO

GRANT EXECUTE ON [dbo].[GetEnums] TO [db_spexec]
GO