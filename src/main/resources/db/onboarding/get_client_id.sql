-- =============================================
-- Get Admin Portal Client ID
-- Run this if you need to retrieve the Client ID
-- =============================================

USE [AGSAuth];  -- Change this to your database name if different
GO

PRINT '';
PRINT '================================================';
PRINT 'ADMIN PORTAL CLIENT ID LOOKUP';
PRINT '================================================';
PRINT '';

DECLARE @AdminClientId NCHAR(32);
DECLARE @AdminAppId INT;

SELECT 
    @AdminAppId = [ApplicationId],
    @AdminClientId = [ClientId]
FROM [Client].[Application] 
WHERE [Name] = 'Admin Portal';

IF @AdminClientId IS NOT NULL
BEGIN
    PRINT 'Admin Portal Application Details:';
    PRINT '-----------------------------------';
    PRINT 'Application ID: ' + CAST(@AdminAppId AS NVARCHAR(10));
    PRINT 'Client ID: ' + CAST(@AdminClientId AS NVARCHAR(32));
    PRINT '';
    PRINT 'Use this URL to login (replace YOUR_CLIENT_ID with the value above):';
    PRINT '';
    PRINT 'http://localhost:9080/oauth2/authorize?';
    PRINT '  client_id=YOUR_CLIENT_ID&';
    PRINT '  response_type=code&';
    PRINT '  redirect_uri=http://localhost:9080/login&';
    PRINT '  scope=openid&';
    PRINT '  branding=default';
    PRINT '';
    PRINT 'Login credentials:';
    PRINT '  Username: admin';
    PRINT '  Password: Admin@123456';
    PRINT '';
END
ELSE
BEGIN
    PRINT 'ERROR: Admin Portal application not found!';
    PRINT '';
    PRINT 'Run the onboarding script first:';
    PRINT 'sqlcmd -S localhost -d AGSAuth -U sa -P your_password \';
    PRINT '  -i src/main/resources/db/onboarding/manual_onboarding.sql';
END

PRINT '================================================';
PRINT '';

GO
