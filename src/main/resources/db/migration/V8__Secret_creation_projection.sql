CREATE OR ALTER PROCEDURE [Client].[CreateSecret] @OrganizationId INTEGER,
                                                  @ApplicationId INTEGER,
                                                  @Description NVARCHAR(1024),
                                                  @SecretHash VARBINARY(MAX),
                                                  @Expiration DATETIME2(0),
                                                  @MainKeyStore VARBINARY(MAX),
                                                  @PasswordKeyStore VARBINARY(MAX),
                                                  @PasswordKeyId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION
            DECLARE @KeyStorePasswordId INT;

            EXEC [Partner].[SavePasswordKeyStore]
                 @PasswordKeyStore,
                 @PasswordKeyId,
                 @KeyStorePasswordId OUT;

            INSERT INTO [Client].[Secret] (OrganizationId,
                                           ApplicationId,
                                           Description,
                                           SecretHashValue,
                                           KeyStore,
                                           PasswordKeyId,
                                           ExpireOn)
            VALUES (@OrganizationId,
                    @ApplicationId,
                    @Description,
                    @SecretHash,
                    @MainKeyStore,
                    @PasswordKeyId,
                    @Expiration);

            SELECT s.*, @PasswordKeyStore AS PasswordKeyStore
            FROM [Client].[Secret] s
            WHERE SecretId = SCOPE_IDENTITY();
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK;

        THROW;
    END CATCH
END
GO
