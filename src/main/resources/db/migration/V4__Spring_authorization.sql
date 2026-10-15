-- Spring Authorization Server 1.5.3 schema; BLOB and timestamp adapted for SQL Server.
/*
IMPORTANT:
    If using PostgreSQL:
        - update ALL columns defined with 'VARBINARY(MAX)' to 'text', as PostgreSQL does not support the 'VARBINARY(MAX)' data type.
        - update ALL columns defined with 'DATETIME2(7)' to 'timestamptz', to ensure that time instants are stored accurately.
    If using MySQL:
        - add 'preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' to JDBC connection URL
          to ensure that time instants are stored accurately. See https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html
*/
CREATE TABLE dbo.oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) DEFAULT NULL,
    attributes VARBINARY(MAX) DEFAULT NULL,
    state varchar(500) DEFAULT NULL,
    authorization_code_value VARBINARY(MAX) DEFAULT NULL,
    authorization_code_issued_at DATETIME2(7) DEFAULT NULL,
    authorization_code_expires_at DATETIME2(7) DEFAULT NULL,
    authorization_code_metadata VARBINARY(MAX) DEFAULT NULL,
    access_token_value VARBINARY(MAX) DEFAULT NULL,
    access_token_issued_at DATETIME2(7) DEFAULT NULL,
    access_token_expires_at DATETIME2(7) DEFAULT NULL,
    access_token_metadata VARBINARY(MAX) DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes varchar(1000) DEFAULT NULL,
    oidc_id_token_value VARBINARY(MAX) DEFAULT NULL,
    oidc_id_token_issued_at DATETIME2(7) DEFAULT NULL,
    oidc_id_token_expires_at DATETIME2(7) DEFAULT NULL,
    oidc_id_token_metadata VARBINARY(MAX) DEFAULT NULL,
    refresh_token_value VARBINARY(MAX) DEFAULT NULL,
    refresh_token_issued_at DATETIME2(7) DEFAULT NULL,
    refresh_token_expires_at DATETIME2(7) DEFAULT NULL,
    refresh_token_metadata VARBINARY(MAX) DEFAULT NULL,
    user_code_value VARBINARY(MAX) DEFAULT NULL,
    user_code_issued_at DATETIME2(7) DEFAULT NULL,
    user_code_expires_at DATETIME2(7) DEFAULT NULL,
    user_code_metadata VARBINARY(MAX) DEFAULT NULL,
    device_code_value VARBINARY(MAX) DEFAULT NULL,
    device_code_issued_at DATETIME2(7) DEFAULT NULL,
    device_code_expires_at DATETIME2(7) DEFAULT NULL,
    device_code_metadata VARBINARY(MAX) DEFAULT NULL,
    PRIMARY KEY (id)
);
