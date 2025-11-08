DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE NAME = 'CertificateType'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'CertificateType',
                N'Identify certificate as Organization used for signing vs client signature validation');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE [EnumTypeId] = @typeId
                AND Code = N'Organization')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Organization',
                N'Organization certificate to generate signature for the payloads sent to client');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE [EnumTypeId] = @typeId
                AND Code = N'PublicKey')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'PublicKey',
                N'Public key certificate to validate the communication signed by the client private key');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE NAME = 'CertificateFileFormat'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'CertificateFileFormat',
                N'Certificate file format for uploading certificates');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'pem')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'pem',
                N'A .pem file format suppots multiple digital certificates, including a certificate chain.');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'pfx')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'pfx',
                N'A .pfx file format (PKCS #12) supports storing a private key with the associated public key certificate.');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'ApplicationType'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'ApplicationType',
                N'Type of application for authentication purposes');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'mobile')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'mobile', N'Mobile application');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'web')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'web', N'Web application');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'server')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'server', N'Server application');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'AuthFlow'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'AuthFlow',
                N'Type of authentication flow');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'AuthCode')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'AuthCode', N'Authorization code flow with PKCE');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'ClientSecret')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'ClientSecret', N'Client secret flow');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'ClientSecretJWT')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'ClientSecretJWT', N'Client secret flow with JWT');
    end
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'PrivateKeyJWT')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'PrivateKeyJWT', N'Private key flow with JWT');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].EnumType
WHERE Name = 'CredentialStatus'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'CredentialStatus',
                N'Status of the credential');
        SET @typeId = SCOPE_IDENTITY()
    end
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Active')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Active', N'Active credential');
    end
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Disabled')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Disabled', N'Disabled credential');
    END
IF NOT EXISTS(SELECT 1
              FROM Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Disabled')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Disabled', N'Disabled credential');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Expired')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Expired', N'Expired credential');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Suspended')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Suspended', N'Suspended credential');
    END

GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'TokenType'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'TokenType',
                N'Type of token enum.');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Access Token')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Access Token', N'Access token');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'OIDC Token')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'OIDC Token', N'OIDC Token');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'Refresh Token')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'Refresh Token', N'Refresh token');
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = 'TokenFormat'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'TokenFormat',
                N'Format of the token');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'self-contained')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'self-contained',
                N'Self-contained token use a protected, time-limited data structure that contains token metadata and claims. JSON Web Tokens (JWTs) are self-contained tokens.');;
    END
IF NOT EXISTS(SELECT 1
              FROM dbo.Enum
              WHERE EnumTypeId = @typeId
                AND Code = N'reference')
    BEGIN
        INSERT INTO [dbo].Enum (EnumTypeId, Code, Description)
        VALUES (@typeId, N'reference',
                N'Reference tokens are opaque strings that act as pointers to the token metadata and claims stored on the server.');;
    END
GO

DECLARE @typeId INT
SELECT @typeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE NAME = 'TokenSignatureAlgorithm'
IF @typeId IS NULL
    BEGIN
        INSERT INTO [dbo].EnumType
            (NAME, Description)
        VALUES (N'TokenSignatureAlgorithm',
                N'Signature algorithm used to sign the token');
        SET @typeId = SCOPE_IDENTITY()
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'HS256')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'HS256',
                N'HMAC using SHA-256 hash algorithm');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE EnumTypeId = @typeId
                AND Code = N'RS256')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'RS256',
                N'RSA using SHA-256 hash algorithm');
    END
IF NOT EXISTS(SELECT 1
              FROM [dbo].[Enum]
              WHERE [EnumTypeId] = @typeId
                AND [Code] = N'ES256')
    BEGIN
        INSERT INTO [dbo].Enum
            (EnumTypeId, Code, Description)
        VALUES (@typeId, N'ES256',
                N'ECDSA using P-256 curve and SHA-256 hash algorithm');
    END
GO
