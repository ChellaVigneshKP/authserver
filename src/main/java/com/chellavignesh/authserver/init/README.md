# Server Initialization

## Overview

This module provides automatic server initialization functionality that creates the first admin user, organization, and client application when the server starts for the first time.

## Usage

### Environment Variable

Set the `INITIALIZE_SERVER` environment variable to `true` to enable server initialization:

```bash
export INITIALIZE_SERVER=true
java -jar authserver.jar
```

### Application Property

Alternatively, you can set the property in `application.properties`:

```properties
server.initialize=true
```

## What Gets Created

When initialization runs, the following entities are created:

1. **External Source (Branding)**
   - Source Code: `default`
   - Used for user authentication and branding

2. **Organization**
   - Name: `Default Organization`
   - Description: `Default organization created during server initialization`

3. **Admin User**
   - Username: `chella`
   - First Name: `Admin`
   - Last Name: `User`
   - Email: `chella@example.com`
   - Phone: `+11234567890`
   - Default Password: `Admin@123456`
   - **⚠️ IMPORTANT: Change the password immediately after first login!**

4. **Application/Client**
   - Name: `Default Client Application`
   - Type: `WEB`
   - Auth Method: `PKCE`

## Safety Features

- **Idempotent**: The initialization checks if the admin user already exists. If it does, initialization is skipped.
- **Error Handling**: If initialization fails at any step, a detailed error message is logged and the application throws a RuntimeException.
- **Logging**: Comprehensive logging at each step helps track the initialization progress.

## Security Considerations

1. **Default Password**: The default admin password (`Admin@123456`) should be changed immediately after the first login.
2. **Production Use**: In production environments, consider:
   - Using a more secure method to set the initial admin password
   - Creating the external source and organization through database migrations
   - Disabling the initialization after the first run

## Example Startup Logs

When initialization is enabled and runs successfully:

```
INFO  Server initialization skipped. Set INITIALIZE_SERVER=true to initialize.
```

Or:

```
INFO  Starting server initialization...
INFO  External source ensured: default
INFO  Organization created: Default Organization (ID: 1)
INFO  Admin user created: chella (ID: 1)
INFO  Application created: Default Client Application (ID: 1)
INFO  Server initialization completed successfully!
INFO  =================================================
INFO  Default Admin User Details:
INFO    Username: chella
INFO    Password: Admin@123456
INFO    Email: chella@example.com
INFO  =================================================
WARN  IMPORTANT: Please change the default admin password immediately!
```

## Troubleshooting

### Initialization Fails with Database Error

Make sure:
1. Database is running and accessible
2. Database schema has been created (run migrations first)
3. Required tables exist: `ExternalSource`, `Partner.Organization`, `Person.Profile`, `Person.Credential`, `Client.Application`

### Initialization Skipped

If initialization is skipped even when `INITIALIZE_SERVER=true`:
1. Check if a user with username `chella` already exists in the database
2. Review the logs for any error messages during the initialization check

### External Source Creation Fails

The initialization attempts to create a default external source if it doesn't exist. If this fails:
1. Manually insert an external source with code `default` into the database
2. Or ensure the `ExternalSource` table has the correct structure

## Development

To run tests:

```bash
./mvnw test -Dtest=ServerInitializationRunnerTest
```
