# Quick Start - Admin Login

After running the onboarding script, use this URL template to login:

## Step 1: Get Your Client ID

When you run the onboarding script, it will output something like:
```
╔════════════════════════════════════════════════╗
║     SAVE THIS CLIENT ID - YOU WILL NEED IT     ║
╠════════════════════════════════════════════════╣
║ Client ID: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6 ║
╚════════════════════════════════════════════════╝
```

**Save this Client ID!**

## Step 2: Start the Server

```bash
./mvnw spring-boot:run
```

Wait until you see: `Started AuthserverApplication`

## Step 3: Open Login URL

Replace `YOUR_CLIENT_ID_HERE` with your actual Client ID:

### For Local Development (HTTP):
```
http://localhost:9080/oauth2/authorize?client_id=YOUR_CLIENT_ID_HERE&response_type=code&redirect_uri=http://localhost:9080/login&scope=openid&branding=default
```

### For Production (HTTPS):
```
https://your-domain.com/oauth2/authorize?client_id=YOUR_CLIENT_ID_HERE&response_type=code&redirect_uri=https://your-domain.com/login&scope=openid&branding=default
```

## Step 4: Login

Use these credentials:
- **Username:** `admin`
- **Password:** `Admin@123456`

## Step 5: Change Password Immediately!

This is a default password. You MUST change it after first login for security.

## Troubleshooting

### "Application not found"
- Double-check your Client ID is correct
- Verify the Admin Portal application was created: `SELECT * FROM [Client].[Application] WHERE [Name] = 'Admin Portal'`

### "Invalid branding not found in database"  
- Verify default branding exists: `SELECT * FROM [dbo].[ExternalSource] WHERE [SourceCode] = 'default'`
- If missing, run: `INSERT INTO [dbo].[ExternalSource] ([SourceCode], [SourceName], [Branding], [Status]) VALUES ('default', 'Default', 'default', 1)`

### "Invalid username or password"
- Verify admin user exists: `SELECT * FROM [Person].[Credential] WHERE UserName = 'admin'`
- The default password is case-sensitive: `Admin@123456`

### Cannot see login page
- Check server is running on port 9080
- Check Redis is running
- Check database connection in application.properties

---

For complete documentation, see [README.md](README.md)
