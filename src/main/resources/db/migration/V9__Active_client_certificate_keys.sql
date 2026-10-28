-- Client assertions may use only a live credential and a currently valid certificate.
CREATE OR ALTER PROCEDURE [Partner].[GetCertificatesByClientIdAndCertTypeId]
    @ClientId NVARCHAR(1024), @CertificateTypeId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SELECT c.*, ks.KeyStore AS PasswordKeyStore
    FROM Partner.Certificate c
    JOIN Partner.KeyStorePassword ks ON ks.PasswordKeyId=c.PasswordKeyId
    JOIN Client.Credential cr ON cr.CertificateId=c.CertificateId AND cr.OrganizationId=c.OrganizationId
    JOIN Client.Application a ON a.ApplicationId=cr.ApplicationId AND a.OrganizationId=c.OrganizationId
    JOIN Partner.Organization o ON o.OrganizationId=a.OrganizationId
    JOIN dbo.Enum credentialStatus ON credentialStatus.EnumId=cr.CredentialStatus
    JOIN dbo.EnumType credentialStatusType ON credentialStatusType.EnumTypeId=credentialStatus.EnumTypeId
    WHERE a.ClientId=@ClientId AND c.CertificateTypeId=@CertificateTypeId
      AND credentialStatusType.Name='CredentialStatus' AND credentialStatus.Code='Active'
      AND a.Active=1 AND o.Status=1 AND c.Status=1
      AND cr.ExpireOn>SYSUTCDATETIME()
      AND c.ValidFrom<=SYSUTCDATETIME() AND c.ValidTo>SYSUTCDATETIME()
    ORDER BY c.CertificateName;
END
GO
