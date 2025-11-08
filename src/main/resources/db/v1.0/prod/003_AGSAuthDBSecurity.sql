USE [AGSAuth]
GO

CREATE USER [acsapp] FOR LOGIN [acsapp]
GO

ALTER ROLE [db_denydatareader] ADD MEMBER [acsapp]
GO

ALTER ROLE [db_denydatawriter] ADD MEMBER [acsapp]
GO

CREATE ROLE [db_spexec]
GO

ALTER ROLE [db_spexec] ADD MEMBER [acsapp]
GO

USE [AGSAuth]
GO

CREATE ROLE [db_acsdev]
GO

GRANT CONTROL ON SCHEMA ::[Client] TO [db_acsdev]
GO

GRANT CONTROL ON SCHEMA::[dbo] TO [db_acsdev]
GO

GRANT CONTROL ON SCHEMA::[Partner] TO [db_acsdev]
GO

GRANT CONTROL ON SCHEMA::[Person] TO [db_acsdev]
GO

GRANT CONTROL ON SCHEMA::[Resource] TO [db_acsdev]
GO

GRANT CONTROL ON SCHEMA::[Token] TO [db_acsdev]
GO