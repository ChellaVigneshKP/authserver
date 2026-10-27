-- Existing sessions deliberately remain unbound. Human sessions require a fresh sign-in.
-- No username backfill: a historical name may already belong to another credential.
ALTER TABLE Token.AuthSession ADD LoginId UNIQUEIDENTIFIER NULL;
GO
CREATE INDEX IX_AuthSession_LoginId ON Token.AuthSession(LoginId) WHERE LoginId IS NOT NULL;
GO
CREATE OR ALTER PROCEDURE [Token].[CreateAuthSession]
    @ApplicationId INTEGER,
    @SubjectId NVARCHAR(100),
    @Scope NVARCHAR(1024),
    @AuthFlowId INTEGER,
    @ClientFingerprint VARBINARY(MAX),
    @ClientId NCHAR(32),
    @Branding NVARCHAR(100),
    @LoginId UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @AuthSessionActiveId INT;

    SET @AuthSessionActiveId = (
        SELECT EnumId
        FROM [dbo].[Enum] e
        WHERE e.Code = 'Session Active'
          AND e.EnumTypeId = (
            SELECT EnumTypeId
            FROM [dbo].[EnumType] et
            WHERE et.Name = 'AuthSessionStatus'
        )
    );

    BEGIN TRY
        BEGIN TRANSACTION;

        INSERT INTO [Token].[AuthSession]
        (
            [ApplicationId],
            [SubjectId],
            [Scope],
            [AuthFlowId],
            [AuthSessionStatusId],
            [ClientFingerprint],
            [ClientId],
            [Branding],
            [LoginId]
        )
        VALUES
            (
                @ApplicationId,
                @SubjectId,
                @Scope,
                @AuthFlowId,
                @AuthSessionActiveId,
                @ClientFingerprint,
                @ClientId,
                @Branding,
                @LoginId
            );

        COMMIT;

        DECLARE @AuthSessionId INT;
        SET @AuthSessionId = SCOPE_IDENTITY();

        SELECT @AuthSessionId AS ID;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO

-- Keep exact target/history semantics; administrative writes supply the actual actor separately.
CREATE OR ALTER PROCEDURE Person.UpdateHostedAccountPassword
    @LoginId UNIQUEIDENTIFIER,
    @MemberId UNIQUEIDENTIFIER,
    @SourceCode NVARCHAR(255),
    @Password VARBINARY(4000),
    @Version INT,
    @PasswordAuditLimit INT,
    @ActorGuid UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @PasswordAuditLimit < 0 OR @PasswordAuditLimit > 1000
        THROW 51015, 'Invalid hosted password configuration', 1;
    SET @ActorGuid=COALESCE(@ActorGuid,@MemberId);
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @CredentialId INT;
        SELECT @CredentialId=c.Id
        FROM Person.Credential c WITH (UPDLOCK,HOLDLOCK)
        JOIN Person.Profile p ON p.ProfileId=c.ProfileId
        JOIN dbo.ExternalSource source ON source.SourceId=c.ExternalId
        WHERE c.RowGuid=@LoginId AND p.RowGuid=@MemberId AND source.SourceCode=@SourceCode
          AND c.Status=1 AND c.CredentialLocked=0 AND p.Status=1;
        IF @@ROWCOUNT<>1 THROW 51016, 'Hosted credential identity unavailable', 1;

        UPDATE Person.Credential
        SET Password=@Password,Version=@Version,ModifiedOn=SYSUTCDATETIME(),ModifiedBy=CONVERT(NVARCHAR(36),@ActorGuid)
        WHERE Id=@CredentialId AND RowGuid=@LoginId;
        IF @@ROWCOUNT<>1 THROW 51016, 'Hosted credential identity unavailable', 1;

        INSERT Person.CredentialAudit(CredentialId,Password,Version,ModifiedBy)
        VALUES(@CredentialId,@Password,@Version,CONVERT(NVARCHAR(36),@ActorGuid));
        ;WITH TargetHistory AS (
            SELECT CredentialAuditId,ROW_NUMBER() OVER(ORDER BY CredentialAuditId DESC) AS Position
            FROM Person.CredentialAudit WHERE CredentialId=@CredentialId
        )
        DELETE history FROM Person.CredentialAudit history JOIN TargetHistory target
          ON target.CredentialAuditId=history.CredentialAuditId
        WHERE target.Position>@PasswordAuditLimit;
        COMMIT;
        SELECT @CredentialId AS CredentialId;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO

-- Internal imported-account reactivation preserves existing identifiers and changes one login.
-- The Java caller holds management -> credential locks; this procedure also scopes every row.
CREATE OR ALTER PROCEDURE Person.ReactivateUserWithPassword
 @UserGuid UNIQUEIDENTIFIER,@FirstName NVARCHAR(255),@LastName NVARCHAR(255),
 @PhoneNumber NVARCHAR(128),@Email NVARCHAR(255),@MemberGuid UNIQUEIDENTIFIER,@LoginGuid UNIQUEIDENTIFIER,
 @SecondaryPhoneNumber NVARCHAR(128),@Username NVARCHAR(255),@ExternalId UNIQUEIDENTIFIER,
 @Password VARBINARY(4000),@Version INT,@UnlockAccount BIT,@PasswordAuditLimit INT,
 @ModifiedOn DATETIME2(7),@ModifiedBy NVARCHAR(255)
AS
BEGIN
 SET NOCOUNT ON;
 SET XACT_ABORT ON;
 BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @ProfileId INT,@CredentialId INT,@Matches INT;
  IF @MemberGuid IS NOT NULL AND @MemberGuid<>@UserGuid
    THROW 51018,'Existing identity identifiers must be preserved.',1;
  SELECT @Matches=COUNT(*),@ProfileId=MIN(p.ProfileId),@CredentialId=MIN(c.Id)
  FROM Person.Profile p WITH(UPDLOCK,HOLDLOCK)
  JOIN Person.Credential c WITH(UPDLOCK,HOLDLOCK) ON c.ProfileId=p.ProfileId
  WHERE p.RowGuid=@UserGuid AND p.Status=0 AND c.ExternalId=@ExternalId
    AND (@LoginGuid IS NULL OR c.RowGuid=@LoginGuid);
  IF @Matches<>1 THROW 51018,'Existing credential requires review.',1;
  SELECT @LoginGuid=RowGuid FROM Person.Credential WHERE Id=@CredentialId;
  IF (SELECT COUNT(*) FROM Person.Credential WHERE RowGuid=@LoginGuid)<>1
    THROW 51018,'Existing credential requires review.',1;
  IF @PasswordAuditLimit<0 OR @PasswordAuditLimit>1000
    THROW 51015,'Invalid password history configuration.',1;
  UPDATE Person.Profile SET FirstName=@FirstName,LastName=@LastName,Email=@Email,
    PhoneNumber=ISNULL(@PhoneNumber,''),SecondaryPhoneNumber=@SecondaryPhoneNumber,
    Status=1,ModifiedOn=@ModifiedOn,ModifiedBy=@ModifiedBy
    WHERE ProfileId=@ProfileId AND RowGuid=@UserGuid;
  UPDATE Person.Credential SET UserName=@Username,Password=@Password,Version=@Version,Status=1,
    CredentialLocked=CASE WHEN @UnlockAccount=1 THEN 0 ELSE CredentialLocked END,
    AccessFailedCount=CASE WHEN @UnlockAccount=1 THEN 0 ELSE AccessFailedCount END,
    ModifiedOn=@ModifiedOn,ModifiedBy=@ModifiedBy WHERE Id=@CredentialId AND RowGuid=@LoginGuid;
  INSERT Person.CredentialAudit(CredentialId,Password,Version,ModifiedBy)
    VALUES(@CredentialId,@Password,@Version,@ModifiedBy);
  ;WITH TargetHistory AS (
    SELECT CredentialAuditId,ROW_NUMBER() OVER(ORDER BY CredentialAuditId DESC) AS Position
    FROM Person.CredentialAudit WHERE CredentialId=@CredentialId
  ) DELETE a FROM Person.CredentialAudit a JOIN TargetHistory h ON h.CredentialAuditId=a.CredentialAuditId
    WHERE h.Position>@PasswordAuditLimit;
  COMMIT;
  SELECT @ProfileId AS ID;
 END TRY
 BEGIN CATCH
  IF @@TRANCOUNT>0 ROLLBACK;
  THROW;
 END CATCH;
END;
GO
