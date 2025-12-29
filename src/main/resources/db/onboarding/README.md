# Admin User Onboarding Guide

This guide explains how to onboard your first admin user and access the admin portal.

## Problem Statement

The auth server has a chicken-and-egg problem:
- You need a user to login, but you can't create users without logging in
- You need a client application to initiate OAuth2 flow, but you can't create clients without logging in
- Branding is required throughout the authentication flow

## Solution

We provide SQL scripts to bootstrap the system with:
1. Default branding configuration
2. Admin organization and group
3. Admin Portal application (OAuth2 client)
4. First admin user account

## Prerequisites

- SQL Server database running
- Database created (default name: `AGSAuth`)
- All Flyway migrations executed (V2-V6)
- Database connection details configured in `application.properties`

## Onboarding Methods

### Method 1: Manual SQL Script (Recommended for First-Time Setup)

Use this method if you're setting up for the first time or need more control.

1. **Run the onboarding script:**
   ```bash
   sqlcmd -S localhost -d AGSAuth -U sa -P your_password -i src/main/resources/db/onboarding/manual_onboarding.sql
   ```

   Or use SQL Server Management Studio:
   - Open `src/main/resources/db/onboarding/manual_onboarding.sql`
   - Execute the script
   - Copy the generated Client ID from the output

2. **Save the Client ID:**
   The script will output a Client ID like:
   ```
   ╔════════════════════════════════════════════════╗
   ║     SAVE THIS CLIENT ID - YOU WILL NEED IT     ║
   ╠════════════════════════════════════════════════╣
   ║ Client ID: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6 ║
   ╚════════════════════════════════════════════════╝
   ```

3. **Start the Auth Server:**
   ```bash
   cd /path/to/authserver
   ./mvnw spring-boot:run
   ```

4. **Access the Login Page:**
   Navigate to this URL (replace `<CLIENT_ID>` with your actual client ID):
   ```
   http://localhost:9080/oauth2/authorize?client_id=<CLIENT_ID>&response_type=code&redirect_uri=http://localhost:9080/login&scope=openid&branding=default
   ```

5. **Login with Default Credentials:**
   - Username: `admin`
   - Password: `Admin@123456`

6. **⚠️ IMPORTANT: Change Password Immediately!**
   After first login, navigate to user settings and change the password.

### Method 2: Flyway Migration

Use this method for automated deployment scenarios.

1. **Copy the migration file:**
   ```bash
   cp src/main/resources/db/onboarding/V7__OnboardFirstAdmin.sql src/main/resources/db/migration/
   ```

2. **Run Flyway migration:**
   ```bash
   ./mvnw flyway:migrate
   ```

3. **Check migration output for Client ID:**
   Look for the Client ID in the console output.

4. **Follow steps 3-6 from Method 1 above.**

## What Gets Created

The onboarding script creates:

### 1. External Source (Branding)
- **Source Code:** `default`
- **Source Name:** `Default`
- **Branding:** `default`
- **Purpose:** Enables the default branding used when no specific branding is provided

### 2. Organization
- **Name:** `Ascensus`
- **Purpose:** The root organization for system administration

### 3. Organization Group
- **Name:** `Ascensus Admin`
- **Purpose:** Admin group with full system permissions

### 4. Application (OAuth2 Client)
- **Name:** `Admin Portal`
- **Client ID:** Auto-generated (save this!)
- **Auth Flow:** Authorization Code
- **Redirect URIs:**
  - `http://localhost:9080/login`
  - `http://localhost:9080/oauth2/authorized`
  - `https://localhost:9080/login`
  - `https://localhost:9080/oauth2/authorized`

### 5. Admin User
- **Username:** `admin`
- **Password:** `Admin@123456` (BCrypt encrypted)
- **Name:** System Administrator
- **Email:** `admin@localhost.local`
- **Assigned to:** Ascensus Admin group

## Customization

### Change Default Port
If your server runs on a different port, update the redirect URIs:

```sql
UPDATE [Client].[RedirectUri] 
SET [Uri] = 'http://localhost:YOUR_PORT/login' 
WHERE [Uri] = 'http://localhost:9080/login';
```

### Add Additional Redirect URIs
For production deployments:

```sql
INSERT INTO [Client].[RedirectUri] ([ApplicationId], [Uri], [Status])
VALUES 
    ((SELECT ApplicationId FROM [Client].[Application] WHERE Name = 'Admin Portal'),
     'https://your-domain.com/login', 
     1);
```

### Create Additional Admin Users
After logging in with the first admin:
1. Navigate to the Admin Portal user management
2. Create new users and assign to "Ascensus Admin" group

## Troubleshooting

### Issue: "Invalid branding not found in database"

**Cause:** The default branding was not created in the ExternalSource table.

**Solution:**
```sql
INSERT INTO [dbo].[ExternalSource] 
    ([SourceCode], [SourceName], [Branding], [Status])
VALUES 
    ('default', 'Default', 'default', 1);
```

### Issue: "Application not found"

**Cause:** The Admin Portal application was not created or Client ID is incorrect.

**Solution:**
1. Verify the application exists:
   ```sql
   SELECT * FROM [Client].[Application] WHERE [Name] = 'Admin Portal';
   ```
2. Use the correct Client ID from the output

### Issue: "Invalid username or password"

**Possible Causes:**
1. Password hash not correctly set
2. User not created
3. Password encoder version mismatch

**Solution:**
1. Verify user exists:
   ```sql
   SELECT * FROM [Person].[Credential] WHERE UserName = 'admin';
   ```

2. If the password hash doesn't work, generate a new one:
   ```java
   // Use your application's password encoder
   BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
   String hash = encoder.encode("Admin@123456");
   System.out.println(hash);
   ```

3. Update the password:
   ```sql
   UPDATE [Person].[Credential] 
   SET [Password] = CONVERT(VARBINARY(4000), '<new_hash_from_step_2>')
   WHERE UserName = 'admin';
   ```

### Issue: "Missing branding in session"

**Cause:** Session doesn't have branding attribute.

**Solution:** This should be handled automatically by the code. Make sure:
1. URL includes `branding=default` parameter
2. The `CustomOAuth2AuthorizationCodeRequestAuthenticationConverter` is properly configured
3. Check application logs for any filter errors

### Issue: Cannot access after first login

**Cause:** OAuth2 flow not completing properly.

**Solution:**
1. Check server logs for detailed error messages
2. Verify redirect URIs match exactly (including trailing slashes)
3. Ensure the session is maintained between redirect steps

## Security Considerations

### Password Requirements
The default password `Admin@123456` meets these criteria:
- At least 12 characters (exactly 12)
- Contains uppercase letters (A)
- Contains lowercase letters (dmin)
- Contains numbers (123456)
- Contains special characters (@)

### Post-Setup Security Tasks
1. **Change admin password immediately**
2. **Review and update redirect URIs** for production
3. **Configure proper SSL/TLS** certificates
4. **Enable MFA** for admin account
5. **Review admin group permissions**
6. **Set up password rotation policy**
7. **Monitor admin access logs**

## Next Steps

After successful onboarding:

1. **Create Your First Client Application:**
   - Navigate to Applications section
   - Click "Create Application"
   - Configure OAuth2 settings
   - Save Client ID and Client Secret

2. **Create Regular Users:**
   - Navigate to Users section
   - Create users with appropriate permissions
   - Assign to relevant organization groups

3. **Configure Branding:**
   - Add custom branding entries to ExternalSource table
   - Create corresponding CMS JSON files in `src/main/resources/cms/`

4. **Set Up Organizations:**
   - Create organizations for different tenants
   - Configure organization-specific settings

5. **Review Security Settings:**
   - Configure session timeouts
   - Set up MFA realms
   - Configure password policies

## Additional Resources

- [Main README](../../../../../README.md) - General application documentation
- [Migration Summary](../../../../../MIGRATION_SUMMARY.md) - Database schema details
- [Spring Security OAuth2](https://spring.io/projects/spring-security-oauth) - OAuth2 documentation

## Support

If you encounter issues:
1. Check application logs: Look for ERROR and WARN messages
2. Review database state: Verify all required tables and data exist
3. Check configuration: Ensure `application.properties` is correct
4. Create a GitHub issue with:
   - Steps to reproduce
   - Error messages
   - Relevant log excerpts
   - Database state (without sensitive data)
