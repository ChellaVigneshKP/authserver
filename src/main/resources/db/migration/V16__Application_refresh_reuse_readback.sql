-- Add persisted refresh reuse to the scoped detail read; preserve both existing result sets.
CREATE OR ALTER PROCEDURE Client.GetApplicationDetailById @ApplicationId INT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT a.*,t.AuthCodeTimeToLive,t.AccessTokenTimeToLive,t.DeviceCodeTimeToLive,
           t.RefreshTokenTimeToLive,t.MaxRequestTransitTime,t.ReuseRefreshTokens
    FROM Client.Application a JOIN Client.TokenSetting t ON t.ApplicationId=a.ApplicationId
    WHERE a.ApplicationId=@ApplicationId;
    SELECT OrganizationId,ApplicationId,ParamPriority,ParamJson
    FROM Client.ForgetUserNameSetting WHERE ApplicationId=@ApplicationId ORDER BY ParamPriority;
END
GO
