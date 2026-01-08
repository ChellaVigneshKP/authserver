SET NOCOUNT ON
GO

DECLARE @DataOriginId INT
DECLARE @LoginProviderId INT
DECLARE @ProfileId INT
DECLARE @OrganizationGroupId INT
DECLARE @ApplicationTypeId INT
DECLARE @AuthFlowId INT
DECLARE @EnumTypeId INT
DECLARE @ExternalSourceId UNIQUEIDENTIFIER

/* ======================================================
   DATA ORIGIN
====================================================== */
SELECT @DataOriginId = DataOriginId
FROM [dbo].[DataOrigin]
WHERE DBName = DB_NAME()
  AND TableName = DB_NAME()

IF @DataOriginId IS NULL
    BEGIN
        INSERT INTO [dbo].[DataOrigin] (DBName, TableName)
        VALUES (DB_NAME(), DB_NAME())

        SET @DataOriginId = SCOPE_IDENTITY()
    END

/* ======================================================
   EXTERNAL SOURCE
====================================================== */
SELECT @ExternalSourceId = SourceId
FROM [dbo].[ExternalSource]
WHERE SourceCode = N'LOCAL'

IF @ExternalSourceId IS NULL
    BEGIN
        SET @ExternalSourceId = NEWID()

        INSERT INTO [dbo].[ExternalSource]
        (
            SourceId,
            SourceCode,
            ExternalTypeId,
            CreatedOn
        )
        VALUES
            (
                @ExternalSourceId,
                N'LOCAL',
                1,
                SYSUTCDATETIME()
            )
    END

/* ======================================================
   ORGANIZATION
====================================================== */
IF NOT EXISTS (SELECT 1 FROM [Partner].[Organization] WHERE OrganizationId = 1)
    BEGIN
        SET IDENTITY_INSERT [Partner].[Organization] ON

        INSERT INTO [Partner].[Organization]
        (OrganizationId, Name, Note)
        VALUES
            (1, N'Ascensus', N'Ascensus Organization')

        SET IDENTITY_INSERT [Partner].[Organization] OFF
    END

/* ======================================================
   ORGANIZATION GROUP
====================================================== */
SELECT @OrganizationGroupId = OrganizationGroupId
FROM [Partner].[OrganizationGroup]
WHERE GroupName = N'System Administrators'
  AND OrganizationId = 1

IF @OrganizationGroupId IS NULL
    BEGIN
        INSERT INTO [Partner].[OrganizationGroup]
        (OrganizationId, GroupName, Description)
        VALUES
            (1, N'System Administrators', N'Ascensus System Administrators Group.')

        SET @OrganizationGroupId = SCOPE_IDENTITY()
    END

/* ======================================================
   LOGIN PROVIDER
====================================================== */
SELECT @LoginProviderId = LoginProviderId
FROM [Person].[LoginProvider]
WHERE ProviderKey = N'ascensus'

IF @LoginProviderId IS NULL
    BEGIN
        INSERT INTO [Person].[LoginProvider]
        (ProviderKey, ProviderName, DisplayName)
        VALUES
            (N'ascensus', N'Ascensus', N'Ascensus Identity Provider')

        SET @LoginProviderId = SCOPE_IDENTITY()
    END

/* ======================================================
   PROFILE
====================================================== */
SELECT @ProfileId = ProfileId
FROM [Person].[Profile]
WHERE Email = N'sysadmin@ascensus.com'

IF @ProfileId IS NULL
    BEGIN
        INSERT INTO [Person].[Profile]
        (
            LoginProviderId,
            Email,
            EmailConfirmed,
            PhoneNumber,
            PhoneNumberConfirmed,
            TwoFactorEnabled,
            FirstName,
            LastName,
            Suffix,
            DataOriginId
        )
        VALUES
            (
                @LoginProviderId,
                N'sysadmin@ascensus.com',
                1,
                N'+19999999999',
                1,
                0,
                N'System',
                N'Administrator',
                N'',
                @DataOriginId
            )

        SET @ProfileId = SCOPE_IDENTITY()
    END

IF @ProfileId IS NULL
    THROW 50001, 'Profile creation failed. Seed aborted.', 1;

/* ======================================================
   PROFILE → ORGANIZATION
====================================================== */
IF NOT EXISTS (
    SELECT 1 FROM [Person].[ProfileOrganization]
    WHERE ProfileId = @ProfileId AND OrganizationId = 1
)
    BEGIN
        INSERT INTO [Person].[ProfileOrganization]
        (ProfileId, OrganizationId)
        VALUES
            (@ProfileId, 1)
    END

/* ======================================================
   PROFILE → GROUP
====================================================== */
IF NOT EXISTS (
    SELECT 1 FROM [Person].[ProfileGroup]
    WHERE ProfileId = @ProfileId
      AND OrganizationGroupId = @OrganizationGroupId
)
    BEGIN
        INSERT INTO [Person].[ProfileGroup]
        (ProfileId, OrganizationGroupId)
        VALUES
            (@ProfileId, @OrganizationGroupId)
    END

/* ======================================================
   CREDENTIAL  ✅ FIXED FK COLUMN
====================================================== */
IF NOT EXISTS (
    SELECT 1 FROM [Person].[Credential]
    WHERE UserName = N'acsauthadmin'
)
    BEGIN
        INSERT INTO [Person].[Credential]
        (
            ProfileId,
            UserName,
            Password,
            CredentialLocked,
            AccessFailedCount,
            DataOriginId,
            ExternalId     -- ✅ CORRECT COLUMN
        )
        VALUES
            (
                @ProfileId,
                N'acsauthadmin',
                0x0D001063A74718C004305ACECC027C9FD386D2894F127C5D868ED20819D42C0B6D7A2767C9156FC4145AEA57719133C39945B2F8C8D10C5ED955D22272851409ABE97C93CFEA9F29EC33F6F,
                0,
                0,
                @DataOriginId,
                @ExternalSourceId
            )
    END

/* ======================================================
   APPLICATION TYPE
====================================================== */
SELECT @ApplicationTypeId = e.EnumId
FROM [dbo].[Enum] e
         JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
WHERE et.Name = N'ApplicationType'
  AND e.Code = N'Web'

/* ======================================================
   AUTH FLOW
====================================================== */
SELECT @AuthFlowId = e.EnumId
FROM [dbo].[Enum] e
         JOIN [dbo].[EnumType] et ON e.EnumTypeId = et.EnumTypeId
WHERE et.Name = N'AuthFlow'
  AND e.Code = N'AuthCode'

/* ======================================================
   APPLICATION
====================================================== */
IF NOT EXISTS (
    SELECT 1 FROM [Client].[Application]
    WHERE ClientId = N'82C8C1622E945CABB8AF14A40D30064'
)
    BEGIN
        INSERT INTO [Client].[Application]
        (
            OrganizationId,
            ClientId,
            Name,
            Description,
            ApplicationTypeId,
            AuthFlowId,
            Uri
        )
        VALUES
            (
                1,
                N'82C8C1622E945CABB8AF14A40D30064',
                N'Admin Portal',
                N'Admin Portal Application',
                @ApplicationTypeId,
                @AuthFlowId,
                N'https://agsup-auth-server-qc4.gs.ascensus.com/auth'
            )
    END

/* ======================================================
   AUTH SESSION STATUS ENUM
====================================================== */
SELECT @EnumTypeId = EnumTypeId
FROM [dbo].[EnumType]
WHERE Name = N'AuthSessionStatus'

IF @EnumTypeId IS NULL
    BEGIN
        INSERT INTO [dbo].[EnumType] (Name, Description)
        VALUES (N'AuthSessionStatus', N'Auth session status.')

        SET @EnumTypeId = SCOPE_IDENTITY()
    END

IF NOT EXISTS (
    SELECT 1 FROM [dbo].[Enum]
    WHERE EnumTypeId = @EnumTypeId AND Code = N'Session Active'
)
    BEGIN
        INSERT INTO [dbo].[Enum]
        (EnumTypeId, Code, Description)
        VALUES
            (@EnumTypeId, N'Session Active', N'Active server auth session status.')
    END

IF NOT EXISTS (
    SELECT 1 FROM [dbo].[Enum]
    WHERE EnumTypeId = @EnumTypeId AND Code = N'Session Inactive'
)
    BEGIN
        INSERT INTO [dbo].[Enum]
        (EnumTypeId, Code, Description)
        VALUES
            (@EnumTypeId, N'Session Inactive', N'Inactive server auth session status.')
    END

GO
