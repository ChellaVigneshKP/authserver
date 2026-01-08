# Quick Start: Generate Secret for Client ID DC0E82E58CBF4E2F8C6679CFD5074059

This document provides a quick walkthrough for generating a secret for your existing OAuth application.

## Step 1: Enable Secret Generator

Edit `src/main/resources/application.properties` and add this line:

```properties
secret.generator.enabled=true
```

## Step 2: Start the Application

```bash
./mvnw spring-boot:run
```

## Step 3: Interactive Prompts

You'll see output like this:

```
==========================================================
Secret Generator Command Line Runner is enabled
==========================================================
Enter the Client ID of the application (or 'exit' to quit):
```

**Enter:** `DC0E82E58CBF4E2F8C6679CFD5074059`

```
Found application: [Your Application Name]
Organization ID: [Org ID]
Application ID: [App ID]
Auth Flow: CLIENT_SECRET_JWT
Enter a name for the credential (default: 'Generated Secret'):
```

**Press Enter** (to use default) or enter a custom name like "Initial Bootstrap Secret"

```
Enter a description for the credential (optional):
```

**Press Enter** (to use default) or enter a description like "Generated for initial API setup"

## Step 4: Save Your Secret

You'll see output like:

```
==========================================================
Secret generated successfully!
==========================================================
Application Name: My OAuth Application
Client ID: DC0E82E58CBF4E2F8C6679CFD5074059
Credential ID: a1b2c3d4-e5f6-7890-a1b2-c3d4e5f67890
Credential Name: Initial Bootstrap Secret
Expires On: Thu Jan 08 15:10:42 UTC 2027
==========================================================
SECRET VALUE (save this securely, it will not be shown again):
AbCdEfGhIjKlMnOpQrStUvWxYz1234567890
==========================================================
```

**IMPORTANT:** 
- Copy the secret value immediately
- Store it securely (password manager, secrets vault, etc.)
- **You will NOT be able to retrieve it again**

## Step 5: Test Your Secret

Now you can authenticate with your OAuth application:

```bash
curl -X POST http://localhost:9080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=DC0E82E58CBF4E2F8C6679CFD5074059" \
  -d "client_secret=AbCdEfGhIjKlMnOpQrStUvWxYz1234567890"
```

Expected response:
```json
{
  "access_token": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

## Step 6: Disable Secret Generator

Edit `application.properties` and comment out or remove the line:

```properties
# secret.generator.enabled=true
```

Then restart your application.

## Step 7: Use Your Access Token

Use the access token to call protected API endpoints:

```bash
curl -X GET http://localhost:9080/api/v1/organizations/{orgGuid}/applications \
  -H "Authorization: Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9..."
```

## What If Something Goes Wrong?

### "Application with Client ID 'XXX' not found"
- Double-check the client ID
- Verify the application exists in the database
- Make sure the application is active

### "Application does not support shared secret authentication"
- The application must use `CLIENT_SECRET_JWT` auth flow
- Check the application's auth flow in the database
- You may need to update or recreate the application

### "Maximum number of credentials for application exceeded"
- Each application can have maximum 2 active credentials
- Delete or disable old credentials first
- Use the API to manage credentials after you can authenticate

## Next Steps

- Read the full [SECRET_GENERATOR_GUIDE.md](SECRET_GENERATOR_GUIDE.md) for detailed information
- Set up credential rotation before the secret expires (1 year)
- Use the credential management APIs to create additional secrets
- Secure your application.properties file (don't commit secrets!)

## Need Help?

If you encounter any issues:
1. Check the application logs for error messages
2. Verify database connectivity
3. Ensure Redis is running (required for session management)
4. Review the troubleshooting section in SECRET_GENERATOR_GUIDE.md
