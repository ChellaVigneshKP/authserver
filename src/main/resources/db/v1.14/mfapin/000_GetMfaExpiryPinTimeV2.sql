CREATE OR ALTER PROCEDURE [Client].[GetMfaExpiryPinTimeV2] @SessionId UNIQUEIDENTIFIER,
                                                           @PinTimeToLice INT
AS
BEGIN
    DECLARE @Pin NVARCHAR(512)=NULL,
        @issueTime DATETIME2,
        @ExpiryTime DATETIME2,
        @MillisTpExpiryTime INT,
        @utcTime datetime;
    SELECT @Pin = Pin
    FROM Token.AuthSession
    WHERE SessionId = @SessionId
    IF @Pin IS NOT NULL
        BEGIN
            SET @issueTime = CONVERT(datetime2, LTRIM(SUBSTRING(@Pin, CHARINDEX(',', @Pin) + 1, LEN(@Pin))));
            SET @ExpiryTime = DATEADD(s, @PinTimeToLice, @issueTime);
            SET @utcTime = GETUTCDATE();
            IF DATEDIFF(y, GETUTCDATE(), @issueTime) = 0 AND DATEDIFF(m, GETUTCDATE(), @issueTime) = 0 AND
               DATEDIFF(d, GETUTCDATE(), @issueTime) = 0
                BEGIN
                    SET @MillisTpExpiryTime = DATEDIFF(ms, @utcTime, @ExpiryTime);
                    SELECT @MillisTpExpiryTime TimeToEpireMs, @issueTime IssueTime, @ExpiryTime ExpiryTime
                END
            ELSE
                BEGIN
                    SELECT -1 TimeToEpireMs, @issueTime IssueTime, @ExpiryTime ExpiryTime
                END
        END
    ELSE
        BEGIN
            SELECT NULL TimeToEpireMs, NULL IssueTime, NULL ExpiryTime
        END
END

GO

GRANT EXECUTE ON OBJECT::[Client].[GetMfaExpiryPinTimeV2] TO [db_spexec]
GO