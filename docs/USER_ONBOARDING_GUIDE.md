# User Onboarding Guide - First User Setup

This guide provides all the required SQL queries and data to onboard your first user into the AGSAuth database and enable successful authentication.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Database Structure Overview](#database-structure-overview)
3. [Step-by-Step User Creation](#step-by-step-user-creation)
4. [Password Hashing](#password-hashing)
5. [Complete Example](#complete-example)
6. [Testing Authentication](#testing-authentication)
7. [Troubleshooting](#troubleshooting)

## Prerequisites

Before creating a user, ensure the following:

1. **Database**: AGSAuth database is created and all schema migrations have been applied via Flyway
2. **Connection**: You have access to SQL Server with appropriate permissions
3. **Configuration**: The application is configured to connect to the database (see `application.properties`)

### Required Database Connection Details
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true
spring.datasource.username=acsapp
spring.datasource.password=Ac$App@123
```

## Database Structure Overview

The authentication system uses several interconnected tables:

### Core Tables for User Authentication

1. **dbo.DataOrigin** - Tracks the source/origin of data
2. **Partner.Organization** - Organization/tenant information
3. **Partner.OrganizationGroup** - User groups within organizations
4. **Person.LoginProvider** - Authentication provider information
5. **Person.Profile** - User profile information
6. **Person.Credential** - User credentials (username/password)
7. **Person.ProfileOrganization** - Links users to organizations
8. **Person.ProfileGroup** - Links users to groups

### Supporting Tables

- **dbo.EnumType** - Enum type definitions
- **dbo.Enum** - Enum values (suffixes, etc.)
- **Client.Application** - OAuth2 client applications
- **dbo.GroupTemplate** - Template definitions for groups
- **dbo.GroupPermissionTemplate** - Permission templates for groups

## Step-by-Step User Creation

### Step 1: Create or Verify Data Origin

The DataOrigin table tracks where the data comes from. For a local user:

```sql
-- Check if DataOrigin exists
SELECT * FROM [dbo].[DataOrigin];

-- If not exists, create one
INSERT INTO [dbo].[DataOrigin] 
    ([DBName], [TableName], [SyncEnabled], [Status])
VALUES 
    ('AGSAuth', 'Person.Profile', 0, 1);

-- Get the DataOriginId for later use
SELECT [DataOriginId], [DBName], [TableName] 
FROM [dbo].[DataOrigin] 
WHERE [DBName] = 'AGSAuth' AND [TableName] = 'Person.Profile';
-- Note: Typically this will be DataOriginId = 1
```

### Step 2: Create or Verify Organization

Organizations represent tenants in the system:

```sql
-- Check existing organizations
SELECT * FROM [Partner].[Organization];

-- Create a new organization if needed
INSERT INTO [Partner].[Organization] 
    ([Name], [Note], [Status])
VALUES 
    ('MyCompany', 'Default organization for first user', 1);

-- Get the OrganizationId
SELECT [OrganizationId], [Name], [RowGuid] 
FROM [Partner].[Organization] 
WHERE [Name] = 'MyCompany';
-- Note: OrganizationId typically starts at 2 due to IDENTITY(2,1)
```

### Step 3: Create Organization Group

Groups define roles and permissions:

```sql
-- Using the OrganizationId from Step 2 (e.g., 2)
DECLARE @OrgId INT = 2; -- Replace with your OrganizationId
DECLARE @GroupName NVARCHAR(255) = 'MyCompany Admin';

-- Check if GroupTemplate exists
SELECT * FROM [dbo].[GroupTemplate] WHERE [GroupName] = 'Admin';

-- If GroupTemplate doesn't exist, create it first
IF NOT EXISTS (SELECT 1 FROM [dbo].[GroupTemplate] WHERE [GroupName] = 'Admin')
BEGIN
    INSERT INTO [dbo].[GroupTemplate] 
        ([GroupName], [Description], [PartnerUse], [Status])
    VALUES 
        ('Admin', 'Administrator group with full permissions', 1, 1);
END

-- Create Organization Group
INSERT INTO [Partner].[OrganizationGroup] 
    ([OrganizationId], [GroupName], [Description], [Status])
VALUES 
    (@OrgId, 
     @GroupName, 
     'Administrator group with full permissions', 
     1);

-- Get the OrganizationGroupId
SELECT [OrganizationGroupId], [OrganizationId], [GroupName] 
FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @OrgId AND [GroupName] = @GroupName;
-- Note the OrganizationGroupId for later use
```

### Step 4: Create Login Provider

The login provider defines how users authenticate:

```sql
-- Check existing login providers
SELECT * FROM [Person].[LoginProvider];

-- Create a local authentication provider if not exists
IF NOT EXISTS (SELECT 1 FROM [Person].[LoginProvider] WHERE [ProviderName] = 'Local')
BEGIN
    INSERT INTO [Person].[LoginProvider] 
        ([ProviderKey], [ProviderName], [DisplayName], [Status])
    VALUES 
        ('local-auth', 'Local', 'Local Authentication', 1);
END

-- Get the LoginProviderId
SELECT [LoginProviderId], [ProviderName], [DisplayName] 
FROM [Person].[LoginProvider] 
WHERE [ProviderName] = 'Local';
-- Note: Typically LoginProviderId = 1
```

### Step 5: Get Suffix Enum (Optional)

Suffixes are stored as enums:

```sql
-- Get available suffixes
SELECT e.[EnumId], e.[Code], e.[Description]
FROM [dbo].[Enum] e
INNER JOIN [dbo].[EnumType] et ON e.[EnumTypeId] = et.[EnumTypeId]
WHERE et.[Name] = 'Suffix';

-- If no suffix, use 0 or create one
-- For no suffix, you can use EnumId = 0 or NULL
```

### Step 6: Create User Profile

```sql
-- Declare variables (replace with your values)
DECLARE @LoginProviderId INT = 1;  -- From Step 4
DECLARE @DataOriginId INT = 1;     -- From Step 1
DECLARE @Email NVARCHAR(255) = 'admin@mycompany.com';
DECLARE @PhoneNumber NVARCHAR(128) = '+1234567890';
DECLARE @FirstName NVARCHAR(255) = 'Admin';
DECLARE @LastName NVARCHAR(255) = 'User';
DECLARE @SuffixEnumId INT = 0;     -- 0 for no suffix

-- Insert Profile
INSERT INTO [Person].[Profile] 
    ([LoginProviderId], [Email], [EmailConfirmed], [PhoneNumber], 
     [PhoneNumberConfirmed], [TwoFactorEnabled], [FirstName], 
     [LastName], [Suffix], [DataOriginId], [SyncFlag], [Status])
VALUES 
    (@LoginProviderId, @Email, 1, @PhoneNumber, 
     1, 0, @FirstName, 
     @LastName, @SuffixEnumId, @DataOriginId, 0, 1);

-- Get the ProfileId
DECLARE @ProfileId INT = SCOPE_IDENTITY();
SELECT @ProfileId AS ProfileId;

-- Also get the RowGuid for reference
SELECT [ProfileId], [RowGuid], [Email], [FirstName], [LastName]
FROM [Person].[Profile]
WHERE [ProfileId] = @ProfileId;
```

### Step 7: Create User Credentials

**Important**: Passwords must be hashed before storing. See the [Password Hashing](#password-hashing) section below.

```sql
-- Declare variables
DECLARE @ProfileId INT = 1;  -- From Step 6
DECLARE @DataOriginId INT = 1;
DECLARE @Username NVARCHAR(255) = 'admin';
-- Password hash - see Password Hashing section for how to generate
-- This is a placeholder - you MUST generate a proper hash
DECLARE @PasswordHash VARBINARY(4000) = CONVERT(VARBINARY(4000), 'PLACEHOLDER_HASH');

-- Insert Credential
INSERT INTO [Person].[Credential] 
    ([ProfileId], [UserName], [Password], [LockoutEnd], 
     [CredentialLocked], [AccessFailedCount], [DataOriginId], 
     [SyncFlag], [Status])
VALUES 
    (@ProfileId, @Username, @PasswordHash, NULL, 
     0, 0, @DataOriginId, 
     0, 1);

-- Verify credential creation
SELECT [Id], [ProfileId], [UserName], [CredentialLocked], [AccessFailedCount]
FROM [Person].[Credential]
WHERE [ProfileId] = @ProfileId;
```

### Step 8: Link User to Organization

```sql
-- Declare variables
DECLARE @ProfileId INT = 1;      -- From Step 6
DECLARE @OrganizationId INT = 2; -- From Step 2

-- Insert ProfileOrganization link
INSERT INTO [Person].[ProfileOrganization] 
    ([ProfileId], [OrganizationId], [Status])
VALUES 
    (@ProfileId, @OrganizationId, 1);

-- Get the ProfileOrganizationId
DECLARE @ProfileOrganizationId INT = SCOPE_IDENTITY();
SELECT @ProfileOrganizationId AS ProfileOrganizationId;
```

### Step 9: Assign User to Group

```sql
-- Declare variables
DECLARE @ProfileId INT = 1;              -- From Step 6
DECLARE @OrganizationGroupId INT = 1;    -- From Step 3

-- Insert ProfileGroup link
INSERT INTO [Person].[ProfileGroup] 
    ([ProfileId], [OrganizationGroupId])
VALUES 
    (@ProfileId, @OrganizationGroupId);

-- Verify the assignment
SELECT pg.[ProfileGroupId], pg.[ProfileId], pg.[OrganizationGroupId],
       og.[GroupName], o.[Name] AS OrganizationName
FROM [Person].[ProfileGroup] pg
INNER JOIN [Partner].[OrganizationGroup] og ON pg.[OrganizationGroupId] = og.[OrganizationGroupId]
INNER JOIN [Partner].[Organization] o ON og.[OrganizationId] = o.[OrganizationId]
WHERE pg.[ProfileId] = @ProfileId;
```

## Password Hashing

The authentication server uses a custom password encoder (`LibCryptoPasswordEncoder`) that integrates with an external crypto service. For manual user creation, you have several options:

### Option 1: Use the Crypto Service (Recommended)

The application uses a crypto service at `http://localhost:9001` (configurable via `crypto.web.base.url`). The service:
- Takes a plain text password
- Returns a cipher index and encrypted cipher
- Format: `{0}0x[4-byte-cipher-index][encrypted-password-hex]`

**Note**: You need the crypto service running to generate proper hashes.

### Option 2: Local Fallback Hash (For Testing)

If the crypto service is unavailable, the system uses SHA-256:

```java
// Format: {0}[base64-encoded-sha256-hash]
// Example in Java:
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest("MyPassword123!".getBytes());
String encoded = "{0}" + Base64.getEncoder().encodeToString(hash);
```

**SQL Server Example for SHA-256 Hash:**
```sql
-- Generate SHA-256 hash in SQL Server
DECLARE @Password NVARCHAR(255) = 'MyPassword123!';
DECLARE @PasswordBytes VARBINARY(MAX) = CAST(@Password AS VARBINARY(MAX));
DECLARE @HashBytes VARBINARY(32) = HASHBYTES('SHA2_256', @PasswordBytes);

-- Convert to the format expected by the application
-- Format: {0}[base64-encoded-hash]
-- For manual insertion, you can use the raw hash bytes:
SELECT @HashBytes AS PasswordHash;

-- To create the full formatted string (conceptual - would need CLR or external tool):
-- The application expects: "{0}" + Base64Encode(@HashBytes)
```

### Option 3: Generate Through Application API

The recommended approach is to use the application's API to create users, which will handle password hashing automatically.

### Important Password Requirements

Check `application.properties` for password validation rules:
```properties
validation.password.regex=^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$
```

This requires:
- At least 12 characters
- At least one uppercase letter (A-Z)
- At least one lowercase letter (a-z)
- At least one digit (0-9)
- At least one special character (@$!%*?&)

**Example valid passwords:**
- `MyP@ssw0rd123`
- `SecureP@ss123!`
- `Admin$2024Pass`

## Complete Example

Here's a complete script to create your first user:

```sql
-- ============================================================================
-- Complete User Onboarding Script
-- ============================================================================
USE [AGSAuth];
GO

BEGIN TRANSACTION;

-- Variables to store IDs
DECLARE @DataOriginId INT;
DECLARE @OrganizationId INT;
DECLARE @OrganizationGroupId INT;
DECLARE @LoginProviderId INT;
DECLARE @ProfileId INT;
DECLARE @ProfileOrganizationId INT;

-- Step 1: Create or Get DataOrigin
IF NOT EXISTS (SELECT 1 FROM [dbo].[DataOrigin] WHERE [DBName] = 'AGSAuth' AND [TableName] = 'Person.Profile')
BEGIN
    INSERT INTO [dbo].[DataOrigin] ([DBName], [TableName], [SyncEnabled], [Status])
    VALUES ('AGSAuth', 'Person.Profile', 0, 1);
END
SELECT @DataOriginId = [DataOriginId] 
FROM [dbo].[DataOrigin] 
WHERE [DBName] = 'AGSAuth' AND [TableName] = 'Person.Profile';

PRINT 'DataOriginId: ' + CAST(@DataOriginId AS NVARCHAR(10));

-- Step 2: Create Organization
INSERT INTO [Partner].[Organization] ([Name], [Note], [Status])
VALUES ('MyCompany', 'Default organization for first user', 1);
SET @OrganizationId = SCOPE_IDENTITY();

PRINT 'OrganizationId: ' + CAST(@OrganizationId AS NVARCHAR(10));

-- Step 3: Create Admin GroupTemplate if not exists
IF NOT EXISTS (SELECT 1 FROM [dbo].[GroupTemplate] WHERE [GroupName] = 'Admin')
BEGIN
    INSERT INTO [dbo].[GroupTemplate] ([GroupName], [Description], [PartnerUse], [Status])
    VALUES ('Admin', 'Administrator group with full permissions', 1, 1);
END

-- Step 4: Create Organization Group
INSERT INTO [Partner].[OrganizationGroup] ([OrganizationId], [GroupName], [Description], [Status])
VALUES (@OrganizationId, 'MyCompany Admin', 'Administrator group with full permissions', 1);
SET @OrganizationGroupId = SCOPE_IDENTITY();

PRINT 'OrganizationGroupId: ' + CAST(@OrganizationGroupId AS NVARCHAR(10));

-- Step 5: Create or Get Login Provider
IF NOT EXISTS (SELECT 1 FROM [Person].[LoginProvider] WHERE [ProviderName] = 'Local')
BEGIN
    INSERT INTO [Person].[LoginProvider] ([ProviderKey], [ProviderName], [DisplayName], [Status])
    VALUES ('local-auth', 'Local', 'Local Authentication', 1);
END
SELECT @LoginProviderId = [LoginProviderId] 
FROM [Person].[LoginProvider] 
WHERE [ProviderName] = 'Local';

PRINT 'LoginProviderId: ' + CAST(@LoginProviderId AS NVARCHAR(10));

-- Step 6: Create User Profile
INSERT INTO [Person].[Profile] 
    ([LoginProviderId], [Email], [EmailConfirmed], [PhoneNumber], 
     [PhoneNumberConfirmed], [TwoFactorEnabled], [FirstName], 
     [LastName], [Suffix], [DataOriginId], [SyncFlag], [Status])
VALUES 
    (@LoginProviderId, 'admin@mycompany.com', 1, '+1234567890', 
     1, 0, 'Admin', 
     'User', 0, @DataOriginId, 0, 1);
SET @ProfileId = SCOPE_IDENTITY();

PRINT 'ProfileId: ' + CAST(@ProfileId AS NVARCHAR(10));

-- Step 7: Create User Credentials
-- **IMPORTANT**: Replace this hash with a properly generated password hash
-- This is a placeholder SHA-256 hash of "TempPassword123!"
DECLARE @Username NVARCHAR(255) = 'admin';
DECLARE @PlaceholderPassword NVARCHAR(255) = 'TempPassword123!';
DECLARE @PasswordHash VARBINARY(4000) = HASHBYTES('SHA2_256', CAST(@PlaceholderPassword AS VARBINARY(MAX)));

INSERT INTO [Person].[Credential] 
    ([ProfileId], [UserName], [Password], [LockoutEnd], 
     [CredentialLocked], [AccessFailedCount], [DataOriginId], 
     [SyncFlag], [Status])
VALUES 
    (@ProfileId, @Username, @PasswordHash, NULL, 
     0, 0, @DataOriginId, 
     0, 1);

PRINT 'Credential created for username: ' + @Username;
PRINT 'WARNING: Using placeholder password hash! Update password through application.';

-- Step 8: Link User to Organization
INSERT INTO [Person].[ProfileOrganization] ([ProfileId], [OrganizationId], [Status])
VALUES (@ProfileId, @OrganizationId, 1);
SET @ProfileOrganizationId = SCOPE_IDENTITY();

PRINT 'ProfileOrganizationId: ' + CAST(@ProfileOrganizationId AS NVARCHAR(10));

-- Step 9: Assign User to Group
INSERT INTO [Person].[ProfileGroup] ([ProfileId], [OrganizationGroupId])
VALUES (@ProfileId, @OrganizationGroupId);

PRINT 'User assigned to group';

-- Commit the transaction
COMMIT TRANSACTION;

-- Display the created user information
SELECT 
    p.[ProfileId],
    p.[RowGuid] AS ProfileGuid,
    p.[Email],
    p.[FirstName],
    p.[LastName],
    c.[UserName],
    c.[CredentialLocked],
    o.[Name] AS OrganizationName,
    og.[GroupName]
FROM [Person].[Profile] p
INNER JOIN [Person].[Credential] c ON p.[ProfileId] = c.[ProfileId]
INNER JOIN [Person].[ProfileOrganization] po ON p.[ProfileId] = po.[ProfileId]
INNER JOIN [Partner].[Organization] o ON po.[OrganizationId] = o.[OrganizationId]
INNER JOIN [Person].[ProfileGroup] pg ON p.[ProfileId] = pg.[ProfileId]
INNER JOIN [Partner].[OrganizationGroup] og ON pg.[OrganizationGroupId] = og.[OrganizationGroupId]
WHERE p.[ProfileId] = @ProfileId;

PRINT 'User onboarding complete!';
PRINT 'Username: admin';
PRINT 'Temporary Password: TempPassword123!';
PRINT 'IMPORTANT: Change the password immediately through the application!';
GO
```

## Testing Authentication

After creating the user, test authentication:

### 1. Start the Application

```bash
./mvnw spring-boot:run
```

The application should start on port 9080 (configurable in `application.properties`).

### 2. Access the Login Page

Navigate to: `http://localhost:9080/login`

### 3. Login with Credentials

Use the credentials created:
- Username: `admin`
- Password: `TempPassword123!` (or the password you used)

### 4. Verify Authentication

If successful, you should be redirected to the authenticated area.

### 5. Check Logs

Monitor the application logs for authentication events:
```
INFO  c.c.a.session.CustomUserDetailsService - Loading user by username: admin
INFO  c.c.a.config.CustomAuthenticationSuccessHandler - User authenticated successfully
```

## Troubleshooting

### Common Issues

#### 1. "User not found" Error
- Verify the user exists: `SELECT * FROM [Person].[Credential] WHERE [UserName] = 'admin'`
- Check user status: `SELECT * FROM [Person].[Profile] WHERE [ProfileId] = 1` - Status should be 1 (active)

#### 2. "Invalid credentials" Error
- Password hash may be incorrect
- Try resetting the password through the application API
- Verify password hash format matches what the encoder expects

#### 3. "Account locked" Error
- Check: `SELECT [CredentialLocked], [AccessFailedCount] FROM [Person].[Credential] WHERE [UserName] = 'admin'`
- Unlock: `UPDATE [Person].[Credential] SET [CredentialLocked] = 0, [AccessFailedCount] = 0 WHERE [UserName] = 'admin'`

#### 4. Foreign Key Constraint Errors
- Ensure all parent records exist before inserting child records
- Verify IDs are correct (DataOriginId, LoginProviderId, OrganizationId, etc.)

### Verification Queries

```sql
-- Check user exists and is active
SELECT p.[ProfileId], p.[Email], p.[FirstName], p.[LastName], p.[Status]
FROM [Person].[Profile] p
WHERE p.[Email] = 'admin@mycompany.com';

-- Check credentials
SELECT c.[Id], c.[UserName], c.[CredentialLocked], c.[AccessFailedCount]
FROM [Person].[Credential] c
WHERE c.[UserName] = 'admin';

-- Check user-organization link
SELECT po.[ProfileOrganizationId], po.[ProfileId], po.[OrganizationId], po.[Status]
FROM [Person].[ProfileOrganization] po
INNER JOIN [Person].[Profile] p ON po.[ProfileId] = p.[ProfileId]
WHERE p.[Email] = 'admin@mycompany.com';

-- Check user-group assignment
SELECT pg.[ProfileGroupId], og.[GroupName], o.[Name] AS OrganizationName
FROM [Person].[ProfileGroup] pg
INNER JOIN [Partner].[OrganizationGroup] og ON pg.[OrganizationGroupId] = og.[OrganizationGroupId]
INNER JOIN [Partner].[Organization] o ON og.[OrganizationId] = o.[OrganizationId]
INNER JOIN [Person].[Profile] p ON pg.[ProfileId] = p.[ProfileId]
WHERE p.[Email] = 'admin@mycompany.com';

-- Get complete user information
EXEC [Person].[GetUserByUsername] @username = 'admin';
```

### Database Connection Issues

If you can't connect to the database:

1. **Verify SQL Server is running**:
   ```bash
   # On Linux/Mac
   docker ps | grep sqlserver
   
   # On Windows
   services.msc  # Check SQL Server service
   ```

2. **Check connection string** in `application.properties`:
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true
   spring.datasource.username=acsapp
   spring.datasource.password=Ac$App@123
   ```

3. **Test connection** with a SQL client (SSMS, Azure Data Studio, DBeaver, etc.)

### Application Configuration Issues

1. **Ensure Redis is running** (required for session management):
   ```bash
   # Check Redis
   redis-cli ping
   # Should return: PONG
   ```

2. **Verify Flyway migrations** have been applied:
   ```sql
   SELECT * FROM [dbo].[flyway_schema_history] ORDER BY [installed_rank] DESC;
   ```

3. **Check application logs** for startup errors

## Additional Resources

- **API Documentation**: Check for Swagger/OpenAPI docs at `http://localhost:9080/swagger-ui.html`
- **Admin Portal**: If available, use the admin portal to create users instead of manual SQL
- **Password Change**: Users should change their password on first login
- **Two-Factor Authentication**: Enable 2FA in the profile settings for additional security

## Security Recommendations

1. **Change Default Passwords**: Immediately change any default or temporary passwords
2. **Enable HTTPS**: Configure SSL/TLS for production environments
3. **Strong Password Policy**: Enforce the password requirements defined in `application.properties`
4. **Regular Backups**: Back up the AGSAuth database regularly
5. **Audit Logging**: Monitor the audit tables (*History tables) for security events
6. **Principle of Least Privilege**: Assign users only the permissions they need

## Summary Checklist

Before first authentication, ensure:

- [ ] AGSAuth database exists and is accessible
- [ ] All Flyway migrations have been applied successfully
- [ ] DataOrigin record exists
- [ ] Organization record exists
- [ ] OrganizationGroup record exists  
- [ ] LoginProvider record exists
- [ ] User Profile record created with valid email and phone
- [ ] User Credential record created with properly hashed password
- [ ] ProfileOrganization link created
- [ ] ProfileGroup link created
- [ ] User status is 1 (active)
- [ ] Credential is not locked
- [ ] Redis is running for session management
- [ ] Application started successfully on port 9080
- [ ] Login page is accessible

After successful first authentication:

- [ ] User can log in with username and password
- [ ] Session is created and maintained
- [ ] User has appropriate permissions based on group assignment
- [ ] Change temporary password to a secure one
- [ ] Consider enabling two-factor authentication

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Maintained By**: Development Team  

For questions or issues, consult the application logs and database audit tables for detailed information.
