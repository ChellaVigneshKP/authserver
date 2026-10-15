-- Spring maps authorization token values and JSON attributes as strings. SQL Server
-- VARBINARY metadata makes setObject(String, VARBINARY) interpret JSON as hex.
-- These columns therefore use NVARCHAR(MAX), matching the JDBC mapper contract.
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'attributes';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN attributes NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'authorization_code_value';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN authorization_code_value NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'authorization_code_metadata';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN authorization_code_metadata NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'access_token_value';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN access_token_value NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'access_token_metadata';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN access_token_metadata NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'oidc_id_token_value';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN oidc_id_token_value NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'oidc_id_token_metadata';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN oidc_id_token_metadata NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'refresh_token_value';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN refresh_token_value NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'refresh_token_metadata';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN refresh_token_metadata NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'user_code_value';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN user_code_value NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'user_code_metadata';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN user_code_metadata NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'device_code_value';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN device_code_value NVARCHAR(MAX) NULL;
GO
DECLARE @constraint SYSNAME;
SELECT @constraint=dc.name FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
WHERE dc.parent_object_id=OBJECT_ID(N'dbo.oauth2_authorization') AND c.name=N'device_code_metadata';
IF @constraint IS NOT NULL
BEGIN
    DECLARE @ddl NVARCHAR(MAX)=N'ALTER TABLE dbo.oauth2_authorization DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC sys.sp_executesql @ddl;
END;
ALTER TABLE dbo.oauth2_authorization ALTER COLUMN device_code_metadata NVARCHAR(MAX) NULL;
GO
