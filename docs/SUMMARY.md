# Summary: User Onboarding Documentation

This PR provides comprehensive documentation and tools to help you onboard your first user into the AGSAuth database.

## What's Included

### 📚 Documentation (3 files)

1. **[USER_ONBOARDING_GUIDE.md](USER_ONBOARDING_GUIDE.md)** - Complete reference guide
   - Detailed database structure explanation
   - Step-by-step instructions with SQL queries
   - Password hashing information
   - Troubleshooting guide
   - Security recommendations
   - ~650 lines of comprehensive documentation

2. **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** - Quick start guide
   - TL;DR instructions to get started fast
   - Default credentials reference
   - Common commands and queries
   - Troubleshooting checklist
   - ~300 lines of quick reference

3. **[onboard-first-user.sql](onboard-first-user.sql)** - Ready-to-run SQL script
   - Complete automated user creation
   - Transaction-based with error handling
   - Progress messages and validation
   - Creates all required database records
   - ~300 lines of SQL with extensive comments

### 🎯 What Gets Created

When you run the SQL script, it automatically creates:

| Component | Description |
|-----------|-------------|
| **DataOrigin** | Tracks data source (AGSAuth database) |
| **Organization** | Creates "MyCompany" organization |
| **Group Template** | Sets up "Admin" group template |
| **Organization Group** | Creates "MyCompany Admin" group |
| **Login Provider** | Sets up "Local" authentication provider |
| **User Profile** | Creates admin user profile |
| **User Credential** | Creates login credentials |
| **Profile-Org Link** | Associates user with organization |
| **Profile-Group Link** | Assigns user to admin group |

### 🔑 Default Credentials

The script creates a user with these credentials (customizable in the script):

- **Username**: admin
- **Password**: TempPassword123!
- **Email**: admin@mycompany.com
- **Organization**: MyCompany
- **Group**: MyCompany Admin

## How to Use

### Quick Start (3 steps)

```bash
# 1. Run the SQL script
sqlcmd -S localhost -U your_user -P "YourPassword" -d AGSAuth -i docs/onboard-first-user.sql

# 2. Start the application
./mvnw spring-boot:run

# 3. Login at http://localhost:9080/login
```

### Detailed Instructions

For step-by-step guidance, see:
- **Quick users**: [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
- **Detailed setup**: [USER_ONBOARDING_GUIDE.md](USER_ONBOARDING_GUIDE.md)

## Security Warnings

⚠️ **IMPORTANT**: This documentation and script are for **DEVELOPMENT/TESTING ONLY**

### Security Limitations

1. **Password Hashing**: Uses SHA-256 without salt
   - ❌ Vulnerable to rainbow table attacks
   - ❌ No salt means identical passwords = identical hashes
   - ❌ Fast hashing allows brute force attacks

2. **Default Credentials**: Hard-coded in script
   - ❌ Should be changed before running
   - ❌ Must be changed immediately after first login

3. **Database Credentials**: Example values in documentation
   - ❌ Replace with your actual secure credentials
   - ❌ Use environment variables or secure config management

### For Production

✅ **DO** use these methods instead:
1. Application's Admin Portal
2. User Registration API endpoints
3. Ensure crypto service is running for proper hashing

❌ **DO NOT** use the SQL script for production users!

## Customization

Before running the script, customize these values:

```sql
-- In docs/onboard-first-user.sql

-- Step 6: User Profile
DECLARE @UserEmail NVARCHAR(255) = 'admin@mycompany.com';  -- TODO: Change
DECLARE @UserPhone NVARCHAR(128) = '+1234567890';          -- TODO: Change
DECLARE @UserFirstName NVARCHAR(255) = 'Admin';            -- TODO: Change
DECLARE @UserLastName NVARCHAR(255) = 'User';              -- TODO: Change

-- Step 7: Credentials
DECLARE @Username NVARCHAR(255) = 'admin';                 -- TODO: Change
DECLARE @PlaceholderPassword NVARCHAR(255) = 'TempPassword123!';  -- TODO: Change
```

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Can't connect to database | Check SQL Server is running, verify connection string |
| User not found | Verify user exists: `SELECT * FROM [Person].[Credential] WHERE [UserName] = 'admin'` |
| Invalid credentials | Check password, try exact value from script |
| Account locked | Run: `UPDATE [Person].[Credential] SET [CredentialLocked] = 0 WHERE [UserName] = 'admin'` |
| Redis connection failed | Start Redis: `redis-server` or Docker: `docker run -d -p 6379:6379 redis` |

See [USER_ONBOARDING_GUIDE.md](USER_ONBOARDING_GUIDE.md#troubleshooting) for detailed troubleshooting.

## Verification

After running the script, verify the user was created:

```sql
-- Check user exists
SELECT p.[ProfileId], p.[Email], p.[FirstName], p.[LastName], 
       c.[UserName], c.[CredentialLocked]
FROM [Person].[Profile] p
INNER JOIN [Person].[Credential] c ON p.[ProfileId] = c.[ProfileId]
WHERE c.[UserName] = 'admin';

-- Check user-organization link
SELECT po.[ProfileId], o.[Name] AS Organization
FROM [Person].[ProfileOrganization] po
INNER JOIN [Partner].[Organization] o ON po.[OrganizationId] = o.[OrganizationId];

-- Check user-group assignment
SELECT pg.[ProfileId], og.[GroupName]
FROM [Person].[ProfileGroup] pg
INNER JOIN [Partner].[OrganizationGroup] og ON pg.[OrganizationGroupId] = og.[OrganizationGroupId];
```

## Next Steps

After first successful login:

1. ✅ Change the default password immediately
2. ✅ Update email to a real address
3. ✅ Update phone number
4. ✅ Enable two-factor authentication
5. ✅ Create additional users through the admin portal
6. ✅ Configure OAuth2 client applications
7. ✅ Review and adjust permissions

## Files Modified/Added

- ✅ `docs/USER_ONBOARDING_GUIDE.md` - New comprehensive guide
- ✅ `docs/QUICK_REFERENCE.md` - New quick start guide
- ✅ `docs/onboard-first-user.sql` - New SQL script
- ✅ `README.md` - Updated with quick start section and links

## Support

For more information:
- **Full Documentation**: [USER_ONBOARDING_GUIDE.md](USER_ONBOARDING_GUIDE.md)
- **Quick Start**: [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
- **SQL Script**: [onboard-first-user.sql](onboard-first-user.sql)
- **Main README**: [../README.md](../README.md)

## Feedback

If you encounter issues or have suggestions:
1. Check the troubleshooting section
2. Review application logs
3. Consult database audit tables
4. Open a GitHub issue with details

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Purpose**: Help users quickly onboard their first user for authentication testing

**Remember**: This is for development/testing. For production, use the application's API!
