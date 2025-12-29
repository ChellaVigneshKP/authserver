[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

# Auth Server

A Spring Boot-based OAuth2 Authorization Server with support for multi-factor authentication (MFA), branding customization, biometric authentication, and session management.

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Key Features](#key-features)
- [API Endpoints](#api-endpoints)
- [Authentication Flows](#authentication-flows)
- [Troubleshooting](#troubleshooting)
- [Development](#development)

## Features

- **OAuth2 Authorization Server** - Full OAuth2/OIDC implementation
- **Multi-Factor Authentication (MFA)** - Support for OTP-based MFA
- **Branding Support** - Multi-tenant branding capabilities
- **Session Management** - Redis-based session storage with SSO support
- **Biometric Authentication** - Support for biometric login
- **Forgot Password/Username** - Self-service credential recovery
- **Database Migrations** - Flyway-based schema management
- **Security Features** - Client fingerprinting, PKCE support

## Prerequisites

- Java 25 or higher
- Maven 3.6+
- SQL Server database
- Redis server (for session management)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/ChellaVigneshKP/authserver.git
cd authserver
```

### 2. Configure Database

Update `src/main/resources/application.properties` with your database connection details:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 3. Configure Redis

Ensure Redis is running and update the connection details if needed:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 4. Build the Application

```bash
./mvnw clean package -DskipTests
```

### 5. Run Database Migrations

```bash
./mvnw flyway:migrate
```

### 6. Start the Application

```bash
./mvnw spring-boot:run
```

The server will start on `http://localhost:9080`

### 7. Onboard First Admin User

**IMPORTANT:** Before you can use the admin portal, you need to create your first admin user and client application.

```bash
# Run the onboarding script
sqlcmd -S localhost -d AGSAuth -U sa -P your_password -i src/main/resources/db/onboarding/manual_onboarding.sql
```

This will create:
- Default branding configuration
- Admin organization and group  
- Admin Portal OAuth2 client application
- First admin user (username: `admin`, password: `Admin@123`)

**📖 For detailed instructions, see [Admin Onboarding Guide](src/main/resources/db/onboarding/README.md)**

After running the script:
1. Copy the generated Client ID
2. Navigate to the authorization URL with your Client ID
3. Login with admin/Admin@123
4. **Change the password immediately!**

## Configuration

### Key Configuration Properties

#### Server Configuration
```properties
server.port=9080
server.base-path=http://localhost:9080/services
```

#### Session Configuration
```properties
spring.session.store-type=redis
spring.session.timeout=1800
spring.session.redis.namespace=agsup-auth:spring:session
```

#### Security Features
```properties
# Enable/disable fingerprinting
toggles.fingerprinting.enabled=true

# Enable/disable biometric authentication
toggles.biometric.enabled=true

# Password validation regex
validation.password.regex=^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$
```

#### CMS Configuration
```properties
cms.file.location=classpath:cms/
```

## Key Features

### Branding Support

The auth server supports multi-tenant branding through:

1. **Branding Parameter**: Pass `branding` parameter in OAuth2 authorize requests
2. **Default Branding**: If no branding is specified, the system uses "default" branding
3. **External Source**: Branding must be registered in the external sources table

Example authorization URL:
```
GET /oauth2/authorize?client_id=your_client_id&response_type=code&redirect_uri=your_redirect&scope=openid&branding=your_brand
```

### Session Management

- Sessions are stored in Redis for scalability
- Session timeout is configurable (default: 30 minutes)
- Single Sign-On (SSO) support via secure cookies
- Session includes:
  - Application ID
  - Subject ID (user)
  - Scope
  - Auth flow type
  - Client fingerprint (if enabled)
  - Branding information

### Multi-Factor Authentication

MFA is supported through:
- OTP delivery via SMS or Email
- Configurable MFA realms
- Secure authentication with SecureAuth integration
- PIN expiry time configuration

### Client Fingerprinting

When enabled, the server creates a fingerprint of the client based on:
- User Agent
- Accept headers
- Referer (optional)
- Other HTTP headers

This helps detect session hijacking attempts.

## API Endpoints

### OAuth2 Endpoints

- **Authorization Endpoint**: `GET /oauth2/authorize`
- **Token Endpoint**: `POST /oauth2/token`
- **Introspection Endpoint**: `POST /oauth2/introspect`
- **Revoke Endpoint**: `POST /oauth2/revoke`
- **JWKS Endpoint**: `GET /oauth2/jwks`
- **OIDC Configuration**: `GET /.well-known/openid-configuration`

### Authentication Endpoints

- **Login**: `GET /login`
- **Login (POST)**: `POST /login`
- **Logout**: `POST /logout`

### MFA Endpoints

- **MFA Challenge**: `GET /mfa/{flow}/request-pin`
- **Verify MFA**: `POST /mfa/{flow}/verify-pin`

### Account Recovery Endpoints

- **Forgot Password**: `GET /account-access/forgot-password`
- **Forgot Username**: `GET /forgot-username`

### Admin Portal Endpoints

- **User Management**: `/api/user/**`
- **Application Management**: `/api/application/**`
- **Organization Management**: `/api/organization/**`

## Authentication Flows

### 1. Standard OAuth2 Authorization Code Flow

```
1. Client redirects to: /oauth2/authorize?client_id=...&response_type=code&branding=...
2. User is redirected to login page with session initialized
3. User submits credentials
4. Server validates credentials
5. If MFA required, redirect to MFA page
6. Upon successful authentication, redirect back with authorization code
7. Client exchanges code for tokens at /oauth2/token
```

### 2. Direct Login (without OAuth2 flow)

```
1. User navigates to /login directly
2. System sets default branding if not present
3. User can login with credentials
4. Session is created with default branding
```

### 3. Biometric Authentication

```
1. Client initiates OAuth2 flow with biometric_type parameter
2. User is presented with biometric authentication option
3. User authenticates with biometric token
4. System validates token and creates session
```

## Troubleshooting

### Common Issues

#### 0. "Cannot login - No users or applications exist"

**Solution**: You need to onboard your first admin user and application.

See the **[Complete Admin Onboarding Guide](src/main/resources/db/onboarding/README.md)** for:
- Step-by-step setup instructions
- SQL scripts to create first admin user
- How to create the Admin Portal application
- Troubleshooting branding and authentication issues

Quick start:
```bash
sqlcmd -S localhost -d AGSAuth -U sa -P your_password \
  -i src/main/resources/db/onboarding/manual_onboarding.sql
```

#### 1. "Missing branding in session" error

**Fixed**: The system now automatically uses "default" branding when none is specified.

**Solution**: 
- Ensure you include `branding` parameter in authorization URL, OR
- The system will automatically use "default" branding

#### 2. "Application not found" error during session creation

**Fixed**: Added proper null checks to prevent NoSuchElementException.

**Solution**: Verify that:
- Client ID is registered in the database
- Application is active
- Correct client_id is being used

#### 3. Session creation fails

**Symptoms**: RuntimeException during authentication

**Solutions**:
- Check database connectivity
- Verify application configuration in database
- Check auth flow configuration
- Review application logs for specific errors

#### 4. Redis connection issues

**Symptoms**: Session not persisting, unable to login

**Solutions**:
- Verify Redis is running: `redis-cli ping`
- Check Redis connection details in application.properties
- Ensure Redis port is accessible
- Check Redis memory and eviction policies

#### 5. Database migration issues

**Solutions**:
```bash
# Check migration status
./mvnw flyway:info

# Repair failed migrations
./mvnw flyway:repair

# Re-run migrations
./mvnw flyway:migrate
```

## Development

### Running Tests

```bash
./mvnw test
```

### Code Style

The project uses:
- Lombok for reducing boilerplate
- SLF4J for logging
- MapStruct for DTO mapping

### Database Schema

The schema is managed by Flyway migrations located in `src/main/resources/db/migration/`.

### Building for Production

```bash
./mvnw clean package -DskipTests
java -jar target/authserver-0.0.1-SNAPSHOT.jar
```

### Environment-Specific Configuration

Create environment-specific property files:
- `application-dev.properties`
- `application-prod.properties`

Run with specific profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Security Considerations

1. **Always use HTTPS in production**
2. **Change default passwords** in application.properties
3. **Secure Redis instance** with password and firewall rules
4. **Regular security updates** via Dependabot
5. **Use strong password policies** via validation.password.regex
6. **Enable client fingerprinting** for enhanced security
7. **Review and monitor logs** for suspicious activity

## Recent Fixes

### Version Latest

- **Fixed**: Session creation logic now properly handles empty Optional to prevent NoSuchElementException
- **Fixed**: Branding filter no longer requires branding in session, uses default fallback
- **Fixed**: NullPointerException in LoginController when branding is null
- **Fixed**: CmsService now uses default branding instead of throwing exception
- **Improved**: Added default branding initialization in OAuth2 flow

## Support

For issues and questions:
- Create an issue on GitHub
- Check existing issues for solutions
- Review application logs for detailed error messages

## License

[Add your license information here]
