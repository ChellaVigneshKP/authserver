CREATE OR ALTER PROCEDURE CleanupPartitionAuthSession
(
    @TokenRetention INT,
    @KeepPartitionFrom INT,
    @KeepPartitionTo INT,
    @Result BIT OUTPUT
)
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @PartitionNumber INT;

    DECLARE @TABSRC TABLE
                    (
                        ObjectName NVARCHAR(100),
                        IndexName NVARCHAR(100),
                        PartitionNumber INT,
                        RowsCount INT,
                        Processed BIT
                    );

    DECLARE @SQL NVARCHAR(1000);

    INSERT INTO @TABSRC (ObjectName, IndexName, PartitionNumber, RowsCount, Processed)
    SELECT
        o.name objectname,
        i.name indexname,
        partition_number,
        [rows],
        0
    FROM sys.partitions p
             INNER JOIN sys.objects o
                        ON o.object_id = p.object_id
             INNER JOIN sys.indexes i
                        ON i.object_id = p.object_id
                            AND p.index_id = i.index_id
    WHERE o.name LIKE 'AuthSession'
      AND i.name = 'PK_TokenAuthSession'
      AND p.[rows] > 0;

    IF (SELECT COUNT(1) FROM @TABSRC) > 0
        BEGIN
            ALTER TABLE [Token].[AuthSession]
                SET (SYSTEM_VERSIONING = OFF);

            BEGIN TRY
                BEGIN TRANSACTION;

                IF EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_SSO_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.SSO')
                )
                    BEGIN
                        ALTER TABLE [Token].[SSO]
                            DROP CONSTRAINT [FK_SSO_SessionId];
                    END

                IF EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_TokenPkce_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.Pkce')
                )
                    BEGIN
                        ALTER TABLE [Token].[Pkce]
                            DROP CONSTRAINT [FK_TokenPkce_SessionId];
                    END

                IF EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_TokenAuthCode_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.AuthCode')
                )
                    BEGIN
                        ALTER TABLE [Token].[AuthCode]
                            DROP CONSTRAINT [FK_TokenAuthCode_SessionId];
                    END

                IF EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_Token_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.Token')
                )
                    BEGIN
                        ALTER TABLE [Token].[Token]
                            DROP CONSTRAINT [FK_Token_SessionId];
                    END

                WHILE (SELECT COUNT(1) FROM @TABSRC WHERE Processed = 0) > 0
                    BEGIN
                        SELECT TOP 1
                            @PartitionNumber = PartitionNumber
                        FROM @TABSRC
                        WHERE Processed = 0;

                        IF (
                            (@KeepPartitionFrom > @KeepPartitionTo
                                AND @PartitionNumber < @KeepPartitionFrom
                                AND @PartitionNumber > @KeepPartitionTo)
                                OR
                            (@KeepPartitionFrom < @KeepPartitionTo
                                AND (@PartitionNumber < @KeepPartitionFrom
                                    OR @PartitionNumber > @KeepPartitionTo))
                            )
                            BEGIN
                                SET @SQL = N'
                        TRUNCATE TABLE [Token].[AuthSession]
                        WITH (PARTITIONS (@P0));
                    ';
                                EXEC sp_executesql @SQL, N'@P0 INT', @PartitionNumber;

                                SET @SQL = N'
                        TRUNCATE TABLE [Token].[AuthSessionHistory]
                        WITH (PARTITIONS (@P0));
                    ';
                                EXEC sp_executesql @SQL, N'@P0 INT', @PartitionNumber;
                            END

                        UPDATE @TABSRC
                        SET Processed = 1
                        WHERE PartitionNumber = @PartitionNumber;
                    END

                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_TokenAuthCode_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.AuthCode')
                )
                    BEGIN
                        ALTER TABLE [Token].[AuthCode] WITH CHECK
                            ADD CONSTRAINT [FK_TokenAuthCode_SessionId]
                                FOREIGN KEY ([SessionId], [DAYNUMBER])
                                    REFERENCES [Token].[AuthSession] ([SessionId], [DAYNUMBER]);
                    END

                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_SSO_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.SSO')
                )
                    BEGIN
                        ALTER TABLE [Token].[SSO] WITH CHECK
                            ADD CONSTRAINT [FK_SSO_SessionId]
                                FOREIGN KEY ([SessionId], [DAYNUMBER])
                                    REFERENCES [Token].[AuthSession] ([SessionId], [DAYNUMBER]);
                    END

                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_TokenPkce_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.Pkce')
                )
                    BEGIN
                        ALTER TABLE [Token].[Pkce] WITH CHECK
                            ADD CONSTRAINT [FK_TokenPkce_SessionId]
                                FOREIGN KEY ([SessionId], [DAYNUMBER])
                                    REFERENCES [Token].[AuthSession] ([SessionId], [DAYNUMBER]);
                    END

                IF NOT EXISTS (
                    SELECT 1
                    FROM sys.objects
                    WHERE name = 'FK_Token_SessionId'
                      AND type = 'F'
                      AND parent_object_id = OBJECT_ID('Token.Token')
                )
                    BEGIN
                        ALTER TABLE [Token].[Token] WITH CHECK
                            ADD CONSTRAINT [FK_Token_SessionId]
                                FOREIGN KEY ([SessionId], [DAYNUMBER])
                                    REFERENCES [Token].[AuthSession] ([SessionId], [DAYNUMBER]);
                    END

                COMMIT TRANSACTION;
                SET @Result = 1;
            END TRY
            BEGIN CATCH
                ROLLBACK TRANSACTION;
                SET @Result = 0;

                DECLARE @ErrorMessage NVARCHAR(4000),
                    @ErrorSeverity INT,
                    @ErrorState INT;

                SELECT
                    @ErrorMessage = ERROR_MESSAGE(),
                    @ErrorSeverity = ERROR_SEVERITY(),
                    @ErrorState = ERROR_STATE();

                RAISERROR (@ErrorMessage, @ErrorSeverity, @ErrorState);
            END CATCH

            ALTER TABLE [Token].[AuthSession]
                SET (SYSTEM_VERSIONING = ON (HISTORY_TABLE = [Token].[AuthSessionHistory]));
        END

    RETURN;
END
GO
