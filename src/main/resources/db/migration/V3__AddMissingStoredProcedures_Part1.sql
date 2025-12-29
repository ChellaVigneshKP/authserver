-- =============================================
-- Add All Missing Stored Procedures - Part 1
-- Token Schema Procedures
-- =============================================

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- =============================================
-- Token Schema Stored Procedures
-- =============================================

-- Token.CreateAuthSession
CREATE OR ALTER PROCEDURE [Token].[CreateAuthSession]
    @applicationId INT,
    @subjectId NVARCHAR(255),
    @scope NVARCHAR(1024),
    @authFlowId INT,
    @clientFingerprint NVARCHAR(1024),
    @clientId NCHAR(32),
    @branding NVARCHAR(255)
AS
BEGIN
    DECLARE @authSessionActiveEnumId INT;
    SELECT @authSessionActiveEnumId = EnumId FROM [dbo].[Enum] WHERE [Code] = 'Session Active';
    
    INSERT INTO [Token].[AuthSession]
        ([ApplicationId], [SubjectId], [Scope], [AuthFlowId], [AuthSessionStatusId], [ClientFingerprint], [ClientId], [Branding])
    VALUES
        (@applicationId, @subjectId, @scope, @authFlowId, @authSessionActiveEnumId, @clientFingerprint, @clientId, @branding);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Token.GetAuthSessionById
CREATE OR ALTER PROCEDURE [Token].[GetAuthSessionById]
    @id BIGINT
AS
BEGIN
    SELECT * FROM [Token].[AuthSession] WHERE [AuthSessionId] = @id;
END
GO

-- Token.GetAuthSessionBySessionId
CREATE OR ALTER PROCEDURE [Token].[GetAuthSessionBySessionId]
    @sessionId UNIQUEIDENTIFIER
AS
BEGIN
    SELECT * FROM [Token].[AuthSession] WHERE [SessionId] = @sessionId;
END
GO

-- Token.SetAuthSessionInactive
CREATE OR ALTER PROCEDURE [Token].[SetAuthSessionInactive]
    @sessionId UNIQUEIDENTIFIER
AS
BEGIN
    DECLARE @authSessionInactiveEnumId INT;
    SELECT @authSessionInactiveEnumId = EnumId FROM [dbo].[Enum] WHERE [Code] = 'Session Inactive';
    
    UPDATE [Token].[AuthSession]
    SET [AuthSessionStatusId] = @authSessionInactiveEnumId,
        [ModifiedOn] = GETUTCDATE()
    WHERE [SessionId] = @sessionId;
END
GO

-- Token.GetMostRecentActiveSession
CREATE OR ALTER PROCEDURE [Token].[GetMostRecentActiveSession]
    @principal NVARCHAR(255)
AS
BEGIN
    DECLARE @authSessionActiveEnumId INT;
    SELECT @authSessionActiveEnumId = EnumId FROM [dbo].[Enum] WHERE [Code] = 'Session Active';
    
    SELECT TOP 1 * FROM [Token].[AuthSession]
    WHERE [SubjectId] = @principal
      AND [AuthSessionStatusId] = @authSessionActiveEnumId
    ORDER BY [CreatedOn] DESC;
END
GO

-- Token.SetBrandingAndRedirectUri
CREATE OR ALTER PROCEDURE [Token].[SetBrandingAndRedirectUri]
    @sessionId UNIQUEIDENTIFIER,
    @branding NVARCHAR(255),
    @redirectUri NVARCHAR(2000),
    @applicationId INT
AS
BEGIN
    UPDATE [Token].[AuthSession]
    SET [Branding] = @branding,
        [RedirectUri] = @redirectUri,
        [ApplicationId] = @applicationId,
        [ModifiedOn] = GETUTCDATE()
    WHERE [SessionId] = @sessionId;
END
GO

-- Token.CreateAuthCode
CREATE OR ALTER PROCEDURE [Token].[CreateAuthCode]
    @applicationId INT,
    @sessionId UNIQUEIDENTIFIER,
    @data NVARCHAR(512)
AS
BEGIN
    DECLARE @expiration DATETIME2(7);
    -- Auth codes typically expire in 5 minutes
    SET @expiration = DATEADD(MINUTE, 5, GETUTCDATE());
    
    INSERT INTO [Token].[AuthCode]
        ([ApplicationId], [SessionId], [Data], [Expiration], [ConsumedOn])
    VALUES
        (@applicationId, @sessionId, @data, @expiration, '9999-12-31 23:59:59.9999999');
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Token.GetAuthCodeById
CREATE OR ALTER PROCEDURE [Token].[GetAuthCodeById]
    @id BIGINT
AS
BEGIN
    SELECT * FROM [Token].[AuthCode] WHERE [AuthCodeId] = @id;
END
GO

-- Token.GetSessionIdByAuthCode
CREATE OR ALTER PROCEDURE [Token].[GetSessionIdByAuthCode]
    @data NVARCHAR(512)
AS
BEGIN
    SELECT [SessionId] FROM [Token].[AuthCode]
    WHERE [Data] = @data
      AND [Expiration] > GETUTCDATE()
      AND [ConsumedOn] = '9999-12-31 23:59:59.9999999';
END
GO

-- Token.SetAuthCodeConsumedOn
CREATE OR ALTER PROCEDURE [Token].[SetAuthCodeConsumedOn]
    @data NVARCHAR(512)
AS
BEGIN
    UPDATE [Token].[AuthCode]
    SET [ConsumedOn] = GETUTCDATE()
    WHERE [Data] = @data;
END
GO

-- Token.CreatePkce
CREATE OR ALTER PROCEDURE [Token].[CreatePkce]
    @applicationId INT,
    @sessionId UNIQUEIDENTIFIER,
    @data NVARCHAR(2048),
    @algorithm NVARCHAR(30),
    @redirectUri NVARCHAR(2000)
AS
BEGIN
    DECLARE @expiration DATETIME2(7);
    -- PKCE challenges typically expire in 10 minutes
    SET @expiration = DATEADD(MINUTE, 10, GETUTCDATE());
    
    INSERT INTO [Token].[Pkce]
        ([ApplicationId], [SessionId], [Data], [Algorithm], [Expiration], [ConsumedOn], [RedirectUri])
    VALUES
        (@applicationId, @sessionId, @data, @algorithm, @expiration, '9999-12-31 23:59:59.9999999', @redirectUri);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Token.GetPkceById
CREATE OR ALTER PROCEDURE [Token].[GetPkceById]
    @id BIGINT
AS
BEGIN
    SELECT * FROM [Token].[Pkce] WHERE [PkceId] = @id;
END
GO

-- Token.GetPkceBySessionId
CREATE OR ALTER PROCEDURE [Token].[GetPkceBySessionId]
    @sessionId UNIQUEIDENTIFIER
AS
BEGIN
    SELECT * FROM [Token].[Pkce]
    WHERE [SessionId] = @sessionId
      AND [Expiration] > GETUTCDATE()
      AND [ConsumedOn] = '9999-12-31 23:59:59.9999999'
    ORDER BY [CreatedOn] DESC;
END
GO

-- Token.CreateTokenWithHash
CREATE OR ALTER PROCEDURE [Token].[CreateTokenWithHash]
    @tokenTypeId INT,
    @applicationId INT,
    @subjectId NVARCHAR(200),
    @sessionId UNIQUEIDENTIFIER,
    @isOpaque BIT,
    @data NVARCHAR(MAX),
    @signingKey VARBINARY(MAX),
    @timeToLive INT,
    @dataHash VARBINARY(MAX)
AS
BEGIN
    DECLARE @expiration DATETIME2(7);
    SET @expiration = DATEADD(SECOND, @timeToLive, GETUTCDATE());
    
    INSERT INTO [Token].[Token]
        ([TokenTypeId], [ApplicationId], [SubjectId], [SessionId], [Data], [IsOpaque], [Expiration], [ConsumedOn], [SigningKey], [DataHash])
    VALUES
        (@tokenTypeId, @applicationId, @subjectId, @sessionId, @data, @isOpaque, @expiration, '9999-12-31 23:59:59.9999999', @signingKey, @dataHash);
    
    SELECT SCOPE_IDENTITY() AS ID;
END
GO

-- Token.GetTokenById
CREATE OR ALTER PROCEDURE [Token].[GetTokenById]
    @id BIGINT
AS
BEGIN
    SELECT * FROM [Token].[Token] WHERE [TokenId] = @id;
END
GO

-- Token.GetTokenByClientId
CREATE OR ALTER PROCEDURE [Token].[GetTokenByClientId]
    @clientId NCHAR(32)
AS
BEGIN
    SELECT t.* FROM [Token].[Token] t
    INNER JOIN [Token].[AuthSession] a ON t.[SessionId] = a.[SessionId]
    WHERE a.[ClientId] = @clientId
      AND t.[Expiration] > GETUTCDATE()
    ORDER BY t.[CreatedOn] DESC;
END
GO

-- Token.GetTokenByValueHash
CREATE OR ALTER PROCEDURE [Token].[GetTokenByValueHash]
    @hashValue VARBINARY(MAX),
    @typeId INT
AS
BEGIN
    SELECT * FROM [Token].[Token]
    WHERE [DataHash] = @hashValue
      AND [TokenTypeId] = @typeId
      AND [Expiration] > GETUTCDATE()
      AND [ConsumedOn] = '9999-12-31 23:59:59.9999999';
END
GO

-- Token.GetAllActiveTokensBySessionId
CREATE OR ALTER PROCEDURE [Token].[GetAllActiveTokensBySessionId]
    @sessionId UNIQUEIDENTIFIER
AS
BEGIN
    SELECT * FROM [Token].[Token]
    WHERE [SessionId] = @sessionId
      AND [Expiration] > GETUTCDATE()
      AND [ConsumedOn] = '9999-12-31 23:59:59.9999999';
END
GO

-- Token.GetTokensByClientIdAndRequestDateTime
CREATE OR ALTER PROCEDURE [Token].[GetTokensByClientIdAndRequestDateTime]
    @clientId NCHAR(32),
    @requestDateTime DATETIME2
AS
BEGIN
    SELECT t.* FROM [Token].[Token] t
    INNER JOIN [Token].[AuthSession] a ON t.[SessionId] = a.[SessionId]
    WHERE a.[ClientId] = @clientId
      AND t.[CreatedOn] >= @requestDateTime
      AND t.[Expiration] > GETUTCDATE();
END
GO

-- Token.InsertSsoCookie
CREATE OR ALTER PROCEDURE [Token].[InsertSsoCookie]
    @hashedEncryptedSessionId VARBINARY(MAX),
    @sessionId UNIQUEIDENTIFIER,
    @encryptedSessionId NVARCHAR(MAX),
    @encryptionKey VARBINARY(MAX)
AS
BEGIN
    INSERT INTO [Token].[SsoCookie]
        ([HashedEncryptedSessionId], [SessionId], [EncryptedSessionId], [EncryptionKey], [CreatedOn])
    VALUES
        (@hashedEncryptedSessionId, @sessionId, @encryptedSessionId, @encryptionKey, GETUTCDATE());
END
GO

-- Token.FindSsoCookieByEncryptedSessionId
CREATE OR ALTER PROCEDURE [Token].[FindSsoCookieByEncryptedSessionId]
    @hashedEncryptedSessionId VARBINARY(MAX)
AS
BEGIN
    SELECT * FROM [Token].[SsoCookie]
    WHERE [HashedEncryptedSessionId] = @hashedEncryptedSessionId;
END
GO

