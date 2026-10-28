-- Preserve compatibility with existing callers while persisting the names shown as editable.
CREATE OR ALTER PROCEDURE [Person].[UpdateUserProfileForChangeProfilePage]
    @UserGuid UNIQUEIDENTIFIER,
    @Email NVARCHAR(255),
    @PhoneNumber NVARCHAR(128),
    @SecondaryPhoneNumber NVARCHAR(128)=NULL,
    @ModifiedBy NVARCHAR(255)=NULL,
    @FirstName NVARCHAR(255)=NULL,
    @LastName NVARCHAR(255)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    UPDATE Person.Profile
    SET FirstName=COALESCE(@FirstName,FirstName), LastName=COALESCE(@LastName,LastName),
        Email=@Email, PhoneNumber=@PhoneNumber, SecondaryPhoneNumber=@SecondaryPhoneNumber,
        ModifiedOn=SYSUTCDATETIME(), ModifiedBy=@ModifiedBy
    WHERE RowGuid=@UserGuid;
    SELECT ProfileId AS ID FROM Person.Profile WHERE RowGuid=@UserGuid;
END
GO
