# Secret Generator Guide

## Problem
When creating an OAuth application in the authserver, all API endpoints require authentication. If you haven't set up authentication yet, you can't generate secrets for your application, creating a chicken-and-egg problem.

## Solution
Use the `SecretGeneratorCommandLineRunner` to generate secrets directly from the command line without needing API authentication.

## How to Use

### Prerequisites
1. Your application must already be created in the database with a client ID
2. The application must support the `CLIENT_SECRET_JWT` authentication flow
3. You need access to the application's configuration

### Steps

#### 1. Enable the Secret Generator
Add the following property to your `application.properties` file:

```properties
secret.generator.enabled=true
```

#### 2. Start the Application
Run your Spring Boot application:

```bash
./mvnw spring-boot:run
```

Or if you have the JAR file:

```bash
java -jar authserver-0.0.1-SNAPSHOT.jar
```

#### 3. Provide the Client ID
When the application starts, you'll see a prompt in the console:

```
==========================================================
Secret Generator Command Line Runner is enabled
==========================================================
Enter the Client ID of the application (or 'exit' to quit):
```

Enter your application's client ID (e.g., `DC0E82E58CBF4E2F8C6679CFD5074059`).

#### 4. Provide Credential Details
The runner will ask for:

- **Credential name**: A friendly name for the secret (default: "Generated Secret")
- **Description**: An optional description (default: "Generated via Command Line Runner")

#### 5. Get Your Secret
If successful, you'll see output like:

```
==========================================================
Secret generated successfully!
==========================================================
Application Name: My Application
Client ID: DC0E82E58CBF4E2F8C6679CFD5074059
Credential ID: 12345678-1234-1234-1234-123456789012
Credential Name: Generated Secret
Expires On: Wed Jan 08 15:10:42 UTC 2027
==========================================================
SECRET VALUE (save this securely, it will not be shown again):
AbCdEfGhIjKlMnOpQrStUvWxYz1234567890
==========================================================
```

**IMPORTANT**: Copy and save the secret value immediately. It will not be displayed again!

#### 6. Disable the Secret Generator
After generating your secret, it's recommended to disable the runner for security. Comment out or remove the property from `application.properties`:

```properties
# secret.generator.enabled=true
```

## Use Your Secret
Now you can use your client ID and secret to authenticate API calls:

```bash
curl -X POST https://your-server/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=DC0E82E58CBF4E2F8C6679CFD5074059" \
  -d "client_secret=AbCdEfGhIjKlMnOpQrStUvWxYz1234567890"
```

## Troubleshooting

### Application Not Found
```
Application with Client ID 'XXX' not found.
Please verify the Client ID and try again.
```

**Solution**: Check that:
- The client ID is correct
- The application exists in the database
- The application is active

### Application Does Not Support Shared Secrets
```
Application does not support shared secret authentication.
Application Auth Flow: PKCE
```

**Solution**: The application must be configured to use `CLIENT_SECRET_JWT` authentication flow. Update the application in the database or create a new application with the correct auth flow.

### Too Many Credentials
```
Maximum number of credentials for application exceeded
```

**Solution**: Delete or expire old credentials for the application. Each application can have a maximum of 2 active credentials.

## Security Notes

1. **Keep the secret secure**: Treat the generated secret like a password. Never commit it to source control or share it publicly.

2. **Disable after use**: The secret generator should only be enabled when needed. Keep it disabled in production environments.

3. **Secret expiration**: By default, secrets expire after 1 year. Make sure to rotate them before expiration.

4. **Credential management**: Use the API to manage credentials after the initial setup:
   - `GET /api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials` - List credentials
   - `PUT /api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials/{credGuid}` - Update credential
   - `DELETE /api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials/{credGuid}` - Delete credential

## Example Session

```
==========================================================
Secret Generator Command Line Runner is enabled
==========================================================
Enter the Client ID of the application (or 'exit' to quit):
DC0E82E58CBF4E2F8C6679CFD5074059
Found application: My OAuth Application
Organization ID: 1
Application ID: 42
Auth Flow: CLIENT_SECRET_JWT
Enter a name for the credential (default: 'Generated Secret'):
Initial Admin Secret
Enter a description for the credential (optional):
Created via command line for initial setup
Generating secret for application: My OAuth Application
==========================================================
Secret generated successfully!
==========================================================
Application Name: My OAuth Application
Client ID: DC0E82E58CBF4E2F8C6679CFD5074059
Credential ID: a1b2c3d4-e5f6-7890-a1b2-c3d4e5f67890
Credential Name: Initial Admin Secret
Expires On: Thu Jan 08 15:10:42 UTC 2027
==========================================================
SECRET VALUE (save this securely, it will not be shown again):
xYz9876543210AbCdEfGhIjKlMnOpQrStUvWxYz
==========================================================
```

## Alternative: Database Direct Insert

If you cannot run the command line runner, you can also generate secrets by directly inserting into the database. However, this is not recommended as it bypasses proper secret hashing and keystore storage. Use the command line runner whenever possible.
