-- Authorized management credential writes stay available for an inactive target profile.
-- Self-service path keeps its own active-profile checks; V15/V17 stay untouched so published
-- migrations remain immutable. This definition differs from V17 only by the removed
-- "AND p.Status=1" profile gate.
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
          AND c.Status=1 AND c.CredentialLocked=0;
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
