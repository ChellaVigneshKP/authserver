# Quick Reference - First User Authentication

## TL;DR - Fastest Way to Get Started

### 1. Run the SQL Script

Execute the provided script in your SQL Server:

```bash
# Using sqlcmd (Linux/Mac/Windows)
sqlcmd -S localhost -U sa -P "YourPassword" -d AGSAuth -i docs/onboard-first-user.sql

# Or using SQL Server Management Studio (SSMS)
# Open docs/onboard-first-user.sql and execute it
```

### 2. Start the Application

```bash
./mvnw spring-boot:run
```

### 3. Login

- URL: http://localhost:9080/login
- Username: `admin`
- Password: `TempPassword123!`

### 4. Change Password Immediately!

---

## What the Script Does

The `onboard-first-user.sql` script automatically creates:

| Step | What It Creates | Key Info |
|------|----------------|----------|
| 1 | DataOrigin | Tracks data source (AGSAuth database) |
| 2 | Organization | Creates "MyCompany" organization |
| 3 | Group Template | Sets up "Admin" group template |
| 4 | Organization Group | Creates "MyCompany Admin" group |
| 5 | Login Provider | Sets up "Local" authentication provider |
| 6 | User Profile | Creates admin user profile with email |
| 7 | User Credential | Creates login credentials (username/password) |
| 8 | Profile-Organization Link | Associates user with organization |
| 9 | Profile-Group Link | Assigns user to admin group |

---

## Default Credentials

| Field | Value |
|-------|-------|
| **Username** | admin |
| **Password** | TempPassword123! |
| **Email** | admin@mycompany.com |
| **Phone** | +1234567890 |
| **Organization** | MyCompany |
| **Group** | MyCompany Admin |

---

## Password Requirements

Passwords must meet these criteria:
- ✅ At least 12 characters
- ✅ One uppercase letter (A-Z)
- ✅ One lowercase letter (a-z)
- ✅ One digit (0-9)
- ✅ One special character (@$!%*?&)

**Valid Examples:**
- `MyP@ssw0rd123`
- `SecureP@ss123!`
- `Admin$2024Pass`

---

## Verification Queries

### Check if user was created successfully:

```sql
-- Get user details
SELECT p.[ProfileId], p.[Email], p.[FirstName], p.[LastName], 
       c.[UserName], c.[CredentialLocked], c.[AccessFailedCount]
FROM [Person].[Profile] p
INNER JOIN [Person].[Credential] c ON p.[ProfileId] = c.[ProfileId]
WHERE c.[UserName] = 'admin';

-- Check user organization
SELECT po.[ProfileId], o.[Name] AS Organization, po.[Status]
FROM [Person].[ProfileOrganization] po
INNER JOIN [Partner].[Organization] o ON po.[OrganizationId] = o.[OrganizationId]
WHERE po.[ProfileId] = 1;

-- Check user group assignment
SELECT pg.[ProfileId], og.[GroupName], o.[Name] AS Organization
FROM [Person].[ProfileGroup] pg
INNER JOIN [Partner].[OrganizationGroup] og ON pg.[OrganizationGroupId] = og.[OrganizationGroupId]
INNER JOIN [Partner].[Organization] o ON og.[OrganizationId] = o.[OrganizationId]
WHERE pg.[ProfileId] = 1;
```

---

## Troubleshooting

### Issue: Can't connect to database

**Solution:**
```bash
# Check if SQL Server is running
docker ps | grep sqlserver  # If using Docker
# Or check Windows services

# Verify connection string in application.properties:
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;...
```

### Issue: "User not found" when logging in

**Solution:**
```sql
-- Check if user exists
SELECT * FROM [Person].[Credential] WHERE [UserName] = 'admin';

-- Check if user is active
SELECT * FROM [Person].[Profile] WHERE [ProfileId] = 1;
-- Status should be 1 (active)
```

### Issue: "Invalid credentials" error

**Solution:**
- The password hash might not match
- Try using the exact password: `TempPassword123!`
- If still failing, check application logs for detailed error

### Issue: "Account locked" error

**Solution:**
```sql
-- Unlock the account
UPDATE [Person].[Credential] 
SET [CredentialLocked] = 0, [AccessFailedCount] = 0 
WHERE [UserName] = 'admin';
```

### Issue: Application won't start

**Solution:**
```bash
# Check if Redis is running (required for sessions)
redis-cli ping  # Should return: PONG

# If not, start Redis:
redis-server  # or: docker run -d -p 6379:6379 redis
```

---

## Environment Requirements

Before running the script, ensure:

- ✅ SQL Server is running and accessible
- ✅ AGSAuth database exists
- ✅ Flyway migrations have been applied
- ✅ You have INSERT permissions on the database
- ✅ Redis is running (for session management)

---

## Application Configuration

Key settings in `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;...
spring.datasource.username=acsapp
spring.datasource.password=Ac$App@123

# Server
server.port=9080

# Redis (for sessions)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Password validation
validation.password.regex=^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$
```

---

## Security Checklist

After first login:

- [ ] Change the default password immediately
- [ ] Verify email address
- [ ] Consider enabling two-factor authentication
- [ ] Review and update user permissions
- [ ] Set up additional users as needed
- [ ] Configure HTTPS for production environments
- [ ] Review audit logs regularly

---

## Next Steps

1. ✅ Run the onboarding script
2. ✅ Start the application
3. ✅ Login with default credentials
4. ✅ Change password
5. ⏭️ Create additional users through the admin portal
6. ⏭️ Configure OAuth2 client applications
7. ⏭️ Set up organization-specific settings
8. ⏭️ Configure email notifications
9. ⏭️ Enable monitoring and logging

---

## Additional Resources

- **Full Guide**: See `docs/USER_ONBOARDING_GUIDE.md` for detailed explanations
- **SQL Script**: `docs/onboard-first-user.sql` - Complete onboarding script
- **Database Schema**: `src/main/resources/db/v1.0/prod/002_AuthDBSchema.sql`
- **Stored Procedures**: `src/main/resources/db/migration/R__AuthDBProcs.sql`

---

## Support

If you encounter issues:

1. Check application logs for detailed error messages
2. Review database audit tables (*History tables)
3. Verify all prerequisites are met
4. Consult the full onboarding guide for detailed troubleshooting
5. Check SQL Server error logs

---

## Common Commands

```bash
# Start application
./mvnw spring-boot:run

# Run SQL script
sqlcmd -S localhost -U sa -P "YourPassword" -d AGSAuth -i docs/onboard-first-user.sql

# Check Redis
redis-cli ping

# View application logs
tail -f logs/application.log  # or check console output

# Run Flyway migrations
./mvnw flyway:migrate

# Check database connection
./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug
```

---

**Last Updated**: 2024  
**Version**: 1.0

For more detailed information, refer to `docs/USER_ONBOARDING_GUIDE.md`
