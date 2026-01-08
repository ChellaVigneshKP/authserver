# AuthServer

[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

A Spring Boot-based OAuth2 Authorization Server with support for multiple authentication flows, user management, and organization-based access control.

## Features

- 🔐 OAuth2 Authorization Server with multiple grant types
- 👥 Multi-tenant organization support
- 🔑 Multiple authentication flows (Authorization Code, Client Secret, JWT-based)
- 👤 Comprehensive user management
- 🏢 Organization and group-based permissions
- 📱 Two-factor authentication support
- 🔄 Session management with Redis
- 📊 Audit logging and history tracking
- 🗄️ SQL Server database with Flyway migrations

## Quick Start

### Prerequisites

- Java 25 (configured in pom.xml)
- Maven 3.x
- SQL Server (localhost:1433)
- Redis (localhost:6379)

### Getting Started for First User

📚 **New User?** Follow our comprehensive onboarding guide:

#### Option 1: Quick Start (Recommended)
```bash
# 1. Execute the SQL script to create your first user
# Replace with your database credentials
sqlcmd -S localhost -U acsapp -P "YourSecurePassword" -d AGSAuth -i docs/onboard-first-user.sql

# 2. Start the application
./mvnw spring-boot:run

# 3. Login at http://localhost:9080/login
# Username: admin
# Password: TempPassword123!
```

#### Option 2: Detailed Setup

For detailed step-by-step instructions, see:
- 📖 [Quick Reference Guide](docs/QUICK_REFERENCE.md) - Fast track to first authentication
- 📋 [Complete User Onboarding Guide](docs/USER_ONBOARDING_GUIDE.md) - Detailed documentation with all SQL queries and explanations

### Database Setup

1. **Create Database**: Ensure AGSAuth database exists in SQL Server
2. **Run Migrations**: Flyway will automatically run migrations on application startup
3. **Create First User**: Use the provided SQL script or follow the onboarding guide

### Configuration

Key configuration in `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true
spring.datasource.username=acsapp
spring.datasource.password=Ac$App@123

# Server
server.port=9080

# Redis (Session Management)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Password Validation
validation.password.regex=^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$
```

### Running the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or build and run
./mvnw clean package
java -jar target/authserver-0.0.1-SNAPSHOT.jar
```

The application will start on http://localhost:9080

## Documentation

- 📖 [Quick Reference](docs/QUICK_REFERENCE.md) - TL;DR for getting started
- 📋 [User Onboarding Guide](docs/USER_ONBOARDING_GUIDE.md) - Complete guide with SQL queries and troubleshooting
- 🔧 [SQL Onboarding Script](docs/onboard-first-user.sql) - Ready-to-run SQL script for first user creation

## Architecture

### Database Schema

The application uses a multi-schema SQL Server database:

- **dbo**: Core system tables (enums, configurations)
- **Partner**: Organization and group management
- **Person**: User profiles, credentials, and authentication
- **Client**: OAuth2 client applications and settings
- **Resource**: API resources and scopes
- **Token**: Authorization codes, tokens, and sessions

### Key Components

- **Spring Security**: Authentication and authorization
- **Spring OAuth2 Authorization Server**: OAuth2/OIDC support
- **Flyway**: Database migrations
- **Redis**: Session storage
- **Thymeleaf**: Login/consent page templates
- **Custom Password Encoder**: Integration with external crypto service

## Authentication Flows

Supported OAuth2 grant types:

1. **Authorization Code Flow with PKCE** - For web and mobile applications
2. **Client Credentials** - For server-to-server authentication
3. **Client Secret JWT** - JWT-based client authentication
4. **Private Key JWT** - Certificate-based client authentication

## User Management

### User Structure

Each user consists of:
- **Profile**: Personal information, contact details
- **Credential**: Username, hashed password, lockout settings
- **Organization Links**: Association with one or more organizations
- **Group Memberships**: Roles and permissions through groups

### Password Requirements

- Minimum 12 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character (@$!%*?&)

## Development

### Building

```bash
# Clean and build
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Run tests
./mvnw test
```

### Database Migrations

Migrations are located in `src/main/resources/db/migration/` and run automatically via Flyway.

To run migrations manually:
```bash
./mvnw flyway:migrate
```

### Checking Logs

Application logs include:
- Authentication events
- Token generation
- Session management
- Database operations

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Verify SQL Server is running
   - Check connection string in application.properties
   - Ensure database user has correct permissions

2. **Redis Connection Failed**
   - Ensure Redis is running on localhost:6379
   - Check Redis configuration in application.properties

3. **Login Failed**
   - Verify user exists and is active (Status = 1)
   - Check if account is locked
   - Ensure password hash is correct
   - See [User Onboarding Guide](docs/USER_ONBOARDING_GUIDE.md) for detailed troubleshooting

4. **Flyway Migration Failed**
   - Check database permissions
   - Review migration scripts for errors
   - Verify schema_version table

## Security

### Best Practices

- ✅ Change default passwords immediately
- ✅ Enable HTTPS in production
- ✅ Configure secure session settings
- ✅ Regular security audits via CodeQL
- ✅ Review audit logs regularly
- ✅ Use strong password policies
- ✅ Enable two-factor authentication
- ✅ Regular database backups

### Audit Logging

All tables with system versioning store historical changes in *History tables for audit purposes.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## License

[License information to be added]

## Support

For issues and questions:
- Check the [documentation](docs/)
- Review application logs
- Consult database audit tables
- Check GitHub Issues

## Tech Stack

- **Framework**: Spring Boot 3.5.7
- **Java**: 25
- **Database**: SQL Server with Flyway migrations
- **Session Store**: Redis
- **Security**: Spring Security, OAuth2 Authorization Server
- **Template Engine**: Thymeleaf
- **Build Tool**: Maven

---

**Quick Links:**
- 🚀 [Quick Start Guide](docs/QUICK_REFERENCE.md)
- 📖 [User Onboarding Documentation](docs/USER_ONBOARDING_GUIDE.md)
- 🔧 [SQL Setup Script](docs/onboard-first-user.sql)
