---------------------------------------------------------------------------
-- Create server login [acsapp] if it doesn’t exist
---------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1
               FROM sys.server_principals
               WHERE name = N'acsapp')
    BEGIN
        EXEC ('CREATE LOGIN [acsapp]
          WITH PASSWORD = N''Ac$App@123'',
               CHECK_POLICY = OFF,
               CHECK_EXPIRATION = OFF;');
    END;
---------------------------------------------------------------------------
-- Create database user for that login
---------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1
               FROM sys.database_principals
               WHERE name = N'acsapp')
    BEGIN
        CREATE USER [acsapp] FOR LOGIN [acsapp];
    END;
---------------------------------------------------------------------------
-- Create custom roles if not exist
---------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1
               FROM sys.database_principals
               WHERE name = N'db_spexec')
    BEGIN
        CREATE ROLE [db_spexec];
    END;

IF NOT EXISTS (SELECT 1
               FROM sys.database_principals
               WHERE name = N'db_acsdev')
    BEGIN
        CREATE ROLE [db_acsdev];
    END;
---------------------------------------------------------------------------
-- Add [acsapp] to safe roles only (NO DENY ROLES!)
---------------------------------------------------------------------------
-- Do NOT add to db_denydatareader or db_denydatawriter
-- They override GRANT permissions and block Flyway access.
ALTER ROLE [db_spexec] ADD MEMBER [acsapp];
ALTER ROLE [db_acsdev] ADD MEMBER [acsapp];
---------------------------------------------------------------------------
-- Grant schema-level control permissions to app dev role
---------------------------------------------------------------------------
GRANT CONTROL ON SCHEMA::[Client] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[dbo] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Partner] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Person] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Resource] TO [db_acsdev];
GRANT CONTROL ON SCHEMA::[Token] TO [db_acsdev];
---------------------------------------------------------------------------
-- Grant access for Flyway tracking table
---------------------------------------------------------------------------
IF OBJECT_ID(N'[dbo].[flyway_schema_history]', N'U') IS NOT NULL
    BEGIN
        GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::[dbo].[flyway_schema_history] TO [acsapp];
    END;
---------------------------------------------------------------------------
-- (Optional) Allow procedure execution within dbo schema
---------------------------------------------------------------------------
GRANT CREATE PROCEDURE TO [acsapp];
GRANT ALTER ON SCHEMA::[dbo] TO [acsapp];
GO
GRANT EXECUTE ON SCHEMA::[dbo] TO [acsapp];
---------------------------------------------------------------------------

-- End of Script