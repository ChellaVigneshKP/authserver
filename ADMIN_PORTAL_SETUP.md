# Admin Portal Setup Guide

This guide explains how to set up and use the Auth Server Admin Portal, including backend initialization, frontend configuration, and user management.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Backend Setup](#backend-setup)
4. [Database Initialization](#database-initialization)
5. [Frontend Setup](#frontend-setup)
6. [Creating Admin Users](#creating-admin-users)
7. [Registering New Clients](#registering-new-clients)
8. [Security Considerations](#security-considerations)
9. [Troubleshooting](#troubleshooting)

## Overview

The Admin Portal provides a web-based interface to manage:
- **Organizations**: Partner organizations using the auth server
- **Applications**: OAuth2 clients and their configurations
- **Users**: User accounts with permissions and credentials
- **Settings**: Application-specific settings like redirect URIs, scopes, and grant types

## Prerequisites

Before setting up the admin portal, ensure you have:

- SQL Server database running and accessible
- Redis server running (for session management)
- Java 25+ installed
- Node.js 18+ and npm installed
- Database schema initialized (all migration scripts executed)

## Backend Setup

### 1. Database Initialization

The admin portal requires initial database setup to create the admin organization, application, and permissions structure.

#### Option 1: Automatic Initialization (Recommended)

Set the environment variable or application property:

```properties
admin.portal.initialize=true
```

Or set the environment variable:

```bash
export ADMIN_PORTAL_INITIALIZE=true
```

When the application starts, it will automatically:
- Create the "Ascensus" admin organization
- Create the "Admin Portal" application with proper OAuth2 configuration
- Set up the "Ascensus Admin" group with full permissions
- Configure redirect URIs, scopes, and grant types

#### Option 2: Manual Initialization

If you prefer manual initialization, execute the SQL script:

```bash
sqlcmd -S localhost -U sa -P your_password -d AGSAuth -i src/main/resources/db/init/001_InitAdminPortal.sql
```

### 2. Get Admin Portal Client ID

After initialization, retrieve the Client ID:

```sql
SELECT ClientId FROM [Client].[Application] WHERE [Name] = 'Admin Portal'
```

### 3. Generate Client Secret

You need to generate a client secret for the Admin Portal application. This can only be done after the application is running.

**Using REST API:**

```bash
curl -X POST http://localhost:9080/api/v1/applications/{APP_GUID}/secrets \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Admin Portal Secret",
    "expiresAt": null
  }'
```

Replace `{APP_GUID}` with the RowGuid from the Application table.

The response will include the client secret (save this securely, it won't be shown again).

### 4. Configure CORS

Ensure the frontend URL is in the allowed origins. Update `application.properties`:

```properties
endpoints.web.cors.allowed-origins=http://localhost:3000,http://localhost:4200,http://localhost:9080
```

## Frontend Setup

### 1. Install Dependencies

Navigate to the frontend directory:

```bash
cd frontend
npm install
```

### 2. Configure Environment Variables

Create a `.env.local` file in the `frontend` directory:

```env
# NextAuth Configuration
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-secret-key-here-change-in-production

# Auth Server Configuration
AUTHSERVER_ISSUER=http://localhost:9080
AUTHSERVER_CLIENT_ID=your-client-id-from-step-2
AUTHSERVER_CLIENT_SECRET=your-client-secret-from-step-3
AUTHSERVER_API_URL=http://localhost:9080
```

**Generate NEXTAUTH_SECRET:**

```bash
openssl rand -base64 32
```

### 3. Start the Frontend

```bash
npm run dev
```

The admin portal will be available at `http://localhost:3000`.

### 4. Build for Production

```bash
npm run build
npm start
```

## Creating Admin Users

Once the system is initialized and running, you need to create an admin user account.

### Method 1: Using the Admin Portal UI

1. Navigate to `http://localhost:3000`
2. You won't be able to log in yet (no users exist)
3. Use Method 2 to create the first admin user

### Method 2: Using the REST API

Create an admin user via the API:

```bash
curl -X POST http://localhost:9080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "orgId": 1,
    "firstName": "Admin",
    "lastName": "User",
    "username": "admin@authserver.local",
    "email": "admin@authserver.local",
    "password": "YourSecurePassword123!",
    "branding": "default",
    "phoneNumber": "1234567890"
  }'
```

**Note:** Replace the values with your desired admin credentials. The password must meet complexity requirements:
- Minimum 12 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character (@$!%*?&)

### Method 3: Using SQL

If API access is not available, you can create the user directly in SQL (not recommended for production):

```sql
-- This is simplified - in production, use the API to ensure proper password hashing
DECLARE @AdminOrgId INT
DECLARE @AdminGroupId INT

SELECT @AdminOrgId = OrganizationId FROM [Partner].[Organization] WHERE [Name] = 'Ascensus'
SELECT @AdminGroupId = OrganizationGroupId FROM [Partner].[OrganizationGroup] 
WHERE [OrganizationId] = @AdminOrgId AND [GroupName] = 'Ascensus Admin'

-- Use the CreateUser stored procedure via the API instead
```

## Registering New Clients

### Via Admin Portal UI

1. Log in to the admin portal
2. Navigate to "Applications"
3. Click "Create Application"
4. Fill in the required fields:
   - **Name**: Application name
   - **Description**: Optional description
   - **URI**: Base URL of the application
   - **Application Type**: Web, Native, or SPA
   - **Auth Flow**: Authorization Code, Implicit, or Client Credentials
5. Click "Create"
6. After creation, click the key icon to generate a client secret
7. Copy and securely store the client secret

### Via REST API

```bash
curl -X POST http://localhost:9080/api/v1/applications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "organizationId": 1,
    "name": "My Application",
    "description": "My OAuth2 Application",
    "applicationTypeId": 1,
    "authFlowId": 1,
    "uri": "http://localhost:4000"
  }'
```

### Configure Client Settings

After creating an application, you need to configure:

1. **Redirect URIs**: Where the auth server can redirect after authentication
2. **Scopes**: What information the client can access (openid, profile, email)
3. **Grant Types**: authorization_code, refresh_token, etc.
4. **Authentication Methods**: client_secret_basic, client_secret_post

These can be configured via the admin portal or REST API.

## Security Considerations

### 1. Secure Storage of Secrets

- **Never commit** `.env.local` or `.env` files containing secrets to version control
- Store client secrets in a secure secret management system (e.g., Azure Key Vault, AWS Secrets Manager)
- Rotate client secrets periodically

### 2. Password Requirements

The system enforces strong password requirements:
- Minimum 12 characters
- Must contain uppercase, lowercase, digit, and special character
- Configure in `application.properties`: `validation.password.regex`

### 3. HTTPS in Production

Always use HTTPS in production:
- Configure SSL certificates for both frontend and backend
- Update `NEXTAUTH_URL` to use `https://`
- Update `AUTHSERVER_ISSUER` to use `https://`

### 4. Environment-Specific Configuration

Use different configurations for development, staging, and production:

```properties
# Development
endpoints.web.cors.allowed-origins=http://localhost:3000

# Production
endpoints.web.cors.allowed-origins=https://admin.yourdomain.com
```

### 5. Database Security

- Use dedicated service accounts with minimal required permissions
- Never use `sa` account in production
- Enable SQL Server audit logging
- Implement row-level security where appropriate

## Stored Procedures Review

### Key Procedures Used by Admin Portal

1. **GetAdminConfig**: Retrieves admin portal configuration
2. **Partner.CreateOrganization**: Creates a new organization
3. **Partner.CreateAdminGroup**: Creates an admin group with permissions
4. **Client.CreateApplication**: Creates a new OAuth2 client
5. **Person.CreateUser**: Creates a new user with credentials
6. **Person.CreateProfileOrganization**: Links a user to an organization
7. **Person.AssignUserToGroup**: Assigns a user to a group

### Recommendations

- ✅ All procedures use parameterized queries (SQL injection safe)
- ✅ Proper transaction management with rollback on errors
- ✅ Status fields for soft deletes
- ✅ Audit fields (CreatedOn, ModifiedOn, ModifiedBy)
- ⚠️ Consider adding stored procedures for:
  - Rotating client secrets
  - Bulk user operations
  - Activity logging

## Troubleshooting

### Problem: "Admin Portal Application already exists" but can't log in

**Solution**: The application exists but no admin user was created. Follow the "Creating Admin Users" section to create an admin user.

### Problem: CORS errors when accessing API

**Solution**: Verify that the frontend URL is in `endpoints.web.cors.allowed-origins` in `application.properties`.

### Problem: "Invalid redirect URI" during OAuth flow

**Solution**: Ensure the redirect URI is registered for the client:

```sql
INSERT INTO [Client].[RedirectUri] ([ApplicationId], [Uri], [Type])
VALUES (@ApplicationId, 'http://localhost:3000/api/auth/callback/authserver', 1)
```

### Problem: Session expires immediately

**Solution**: Check Redis connectivity and session configuration:

```properties
spring.session.store-type=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Problem: Password validation fails

**Solution**: Ensure password meets requirements or adjust the regex in `application.properties`:

```properties
validation.password.regex=^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$
```

### Problem: "Branding not found" error when creating user

**Solution**: Ensure an ExternalSource with the matching branding code exists:

```sql
SELECT * FROM [Partner].[ExternalSource] WHERE [SourceCode] = 'default'
```

If not exists, create one:

```sql
INSERT INTO [Partner].[ExternalSource] ([SourceCode], [Name], [Status])
VALUES ('default', 'Default Branding', 1)
```

## Environment Variables Summary

### Backend (Spring Boot)

| Variable | Description | Example |
|----------|-------------|---------|
| `admin.portal.initialize` | Enable automatic initialization | `true` |
| `spring.datasource.url` | Database connection string | `jdbc:sqlserver://localhost:1433;...` |
| `spring.data.redis.host` | Redis host | `localhost` |
| `endpoints.web.cors.allowed-origins` | Allowed CORS origins | `http://localhost:3000` |

### Frontend (Next.js)

| Variable | Description | Example |
|----------|-------------|---------|
| `NEXTAUTH_URL` | Frontend URL | `http://localhost:3000` |
| `NEXTAUTH_SECRET` | NextAuth secret key | `generated-secret-key` |
| `AUTHSERVER_ISSUER` | Auth server URL | `http://localhost:9080` |
| `AUTHSERVER_CLIENT_ID` | OAuth2 client ID | `your-client-id` |
| `AUTHSERVER_CLIENT_SECRET` | OAuth2 client secret | `your-client-secret` |
| `AUTHSERVER_API_URL` | API base URL | `http://localhost:9080` |

## Additional Resources

- [Spring Authorization Server Documentation](https://docs.spring.io/spring-authorization-server/docs/current/reference/html/)
- [NextAuth.js Documentation](https://next-auth.js.org/)
- [OAuth 2.0 RFC](https://oauth.net/2/)
- [OpenID Connect Specification](https://openid.net/connect/)

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review application logs for detailed error messages
3. Check database logs for SQL errors
4. Verify network connectivity between components
