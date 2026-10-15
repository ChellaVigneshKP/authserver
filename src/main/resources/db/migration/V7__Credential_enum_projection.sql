CREATE OR ALTER PROCEDURE [dbo].[GetEnums] 
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
SELECT MAX(CASE WHEN et.Name='CredentialStatus' AND e.Code='Active' THEN e.EnumId END) AS [ActiveEnumId],
MAX(CASE WHEN et.Name='CredentialStatus' AND e.Code='Inactive' THEN e.EnumId END) AS [InactiveEnumId],
MAX(CASE WHEN et.Name='CredentialStatus' AND e.Code='Disabled' THEN e.EnumId END) AS [DisabledEnumId],
MAX(CASE WHEN et.Name='CertificateType' AND e.Code='Organization' THEN e.EnumId END) AS [OrganizationEnumId],
MAX(CASE WHEN et.Name='CertificateType' AND e.Code='PublicKey' THEN e.EnumId END) AS [PublicKeyEnumId],
MAX(CASE WHEN et.Name='TokenSignatureAlgorithm' AND e.Code='RS256' THEN e.EnumId END) AS [RS256EnumId],
MAX(CASE WHEN et.Name='TokenSignatureAlgorithm' AND e.Code='ES256' THEN e.EnumId END) AS [ES256EnumId],
MAX(CASE WHEN et.Name='TokenSignatureAlgorithm' AND e.Code='HS256' THEN e.EnumId END) AS [HS256EnumId],
MAX(CASE WHEN et.Name='ApplicationType' AND e.Code='mobile' THEN e.EnumId END) AS [MobileEnumId],
MAX(CASE WHEN et.Name='ApplicationType' AND e.Code='web' THEN e.EnumId END) AS [WebEnumId],
MAX(CASE WHEN et.Name='ApplicationType' AND e.Code='server' THEN e.EnumId END) AS [ServerEnumId],
MAX(CASE WHEN et.Name='AuthFlow' AND e.Code='AuthCode' THEN e.EnumId END) AS [AuthCodeEnumId],
MAX(CASE WHEN et.Name='AuthFlow' AND e.Code='ClientSecretJWT' THEN e.EnumId END) AS [ClientSecretJwtEnumId],
MAX(CASE WHEN et.Name='AuthFlow' AND e.Code='PrivateKeyJWT' THEN e.EnumId END) AS [PrivateKeyJwtEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='Access Token' THEN e.EnumId END) AS [AccessTokenEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='Refresh Token' THEN e.EnumId END) AS [RefreshTokenEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='OIDC Token' THEN e.EnumId END) AS [IdTokenEnumId],
MAX(CASE WHEN et.Name='TokenType' AND e.Code='Auth Code Token' THEN e.EnumId END) AS [AuthCodeTokenEnumId],
MAX(CASE WHEN et.Name='AuthSessionStatus' AND e.Code='Session Active' THEN e.EnumId END) AS [AuthSessionActiveEnumId],
MAX(CASE WHEN et.Name='AuthSessionStatus' AND e.Code='Session Inactive' THEN e.EnumId END) AS [AuthSessionInactiveEnumId],
MAX(CASE WHEN et.Name='TokenFormat' AND e.Code='self-contained' THEN e.EnumId END) AS [SelfContainedAccessTokenFormatEnumId],
MAX(CASE WHEN et.Name='TokenFormat' AND e.Code='reference' THEN e.EnumId END) AS [ReferenceAccessTokenFormatEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Mr' THEN e.EnumId END) AS [MrEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Mrs' THEN e.EnumId END) AS [MrsEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Miss' THEN e.EnumId END) AS [MissEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Sr' THEN e.EnumId END) AS [SrEnumId],
MAX(CASE WHEN et.Name='SuffixType' AND e.Code='Jr' THEN e.EnumId END) AS [JrEnumId],
MAX(CASE WHEN et.Name='UsernameType' AND e.Code='Username' THEN e.EnumId END) AS [UsernameEnumId],
MAX(CASE WHEN et.Name='UsernameType' AND e.Code='Email' THEN e.EnumId END) AS [EmailEnumId] FROM dbo.Enum e JOIN dbo.EnumType et ON et.EnumTypeId=e.EnumTypeId;
END
GO
