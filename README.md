[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

# Auth Server

A comprehensive OAuth 2.0 and OpenID Connect (OIDC) authorization server built with Spring Boot, featuring a modern Next.js admin portal for seamless management.

## Features

### Core Authentication & Authorization
- ✅ **OAuth 2.0 & OpenID Connect**: Full implementation of OAuth2 and OIDC specifications
- ✅ **Multiple Grant Types**: Authorization Code, Client Credentials, Refresh Token
- ✅ **Custom Authentication Providers**: Support for biometric authentication and custom auth flows
- ✅ **JWT & JWK Support**: Token-based authentication with JSON Web Keys
- ✅ **Multi-Factor Authentication (MFA)**: Enhanced security with MFA support
- ✅ **Session Management**: Redis-based distributed session management

### Admin Portal
- 🎨 **Modern UI**: Next.js 14+ with TypeScript and responsive design
- 🔐 **Secure Access**: OAuth2-authenticated admin interface
- 🏢 **Organization Management**: Create and manage partner organizations
- ⚙️ **Application Management**: Configure OAuth2 clients, redirect URIs, scopes, and settings
- 👥 **User Management**: Create users, assign permissions, manage credentials
- 📊 **Dashboard**: Overview of system resources and quick actions

### Security Features
- 🛡️ **Request Signing & Verification**: Body signature validation for API requests
- 🔒 **Client Fingerprinting**: Additional layer of client verification
- 🕐 **Request Datetime Validation**: Prevent replay attacks
- 🔑 **Client Secret JWT**: Multiple client authentication methods
- 📝 **Comprehensive Audit Trail**: Track all changes with temporal tables

## Quick Start

### Prerequisites
- Java 25+
- SQL Server
- Redis
- Node.js 18+ (for admin portal)

### Backend Setup

1. **Configure Database**

```properties
# application.properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true
spring.datasource.username=acsapp
spring.datasource.password=Ac$App@123
```

2. **Configure Redis**

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

3. **Initialize Admin Portal** (Optional - First Time Setup)

```properties
admin.portal.initialize=true
```

4. **Run the Application**

```bash
./mvnw spring-boot:run
```

The auth server will be available at `http://localhost:9080`.

### Admin Portal Setup

1. **Navigate to Frontend Directory**

```bash
cd frontend
```

2. **Install Dependencies**

```bash
npm install
```

3. **Configure Environment**

Create `.env.local`:

```env
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-generated-secret
AUTHSERVER_ISSUER=http://localhost:9080
AUTHSERVER_CLIENT_ID=your-client-id
AUTHSERVER_CLIENT_SECRET=your-client-secret
```

4. **Start Development Server**

```bash
npm run dev
```

The admin portal will be available at `http://localhost:3000`.

## Documentation

- 📖 **[Admin Portal Setup Guide](ADMIN_PORTAL_SETUP.md)**: Complete guide for setting up and using the admin portal
- 📖 **[Frontend README](frontend/README.md)**: Frontend-specific documentation and features

## Architecture

### Backend Stack
- **Framework**: Spring Boot 3.5.7
- **Security**: Spring Security, Spring Authorization Server
- **Database**: SQL Server with JDBC
- **Caching**: Redis
- **Session**: Spring Session with Redis
- **Cryptography**: BouncyCastle, Custom Crypto Library

### Frontend Stack  
- **Framework**: Next.js 14+ (App Router)
- **Authentication**: NextAuth.js v5
- **Language**: TypeScript
- **Data Fetching**: SWR + Axios
- **Styling**: CSS Modules

### Key Components

```
authserver/
├── src/main/
│   ├── java/com/chellavignesh/authserver/
│   │   ├── adminportal/          # Admin portal REST APIs
│   │   ├── security/              # Security configurations
│   │   ├── config/                # Spring configurations
│   │   ├── session/               # Session management
│   │   └── ...
│   └── resources/
│       ├── db/                    # Database scripts
│       │   ├── init/             # Initialization scripts
│       │   └── migration/        # Flyway migrations
│       └── application.properties
├── frontend/                      # Next.js admin portal
│   ├── src/
│   │   ├── app/                  # Pages and routes
│   │   ├── lib/                  # Utilities and API client
│   │   └── components/           # React components
│   └── package.json
└── pom.xml
```

## Configuration

### Essential Properties

```properties
# Server
server.port=9080
server.base-path=http://localhost:9080/services

# Security
spring.security.user.name=admin
spring.security.user.password=admin123

# Database
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth
spring.datasource.username=acsapp
spring.datasource.password=Ac$App@123

# Redis  
spring.data.redis.host=localhost
spring.data.redis.port=6379

# CORS
endpoints.web.cors.allowed-origins=http://localhost:3000,http://localhost:9080

# Admin Portal
admin.portal.initialize=false

# Flyway
spring.flyway.enabled=false

# Password Validation
validation.password.regex=^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$
```

## API Endpoints

### OAuth 2.0 & OIDC
- `GET /oauth2/authorize` - Authorization endpoint
- `POST /oauth2/token` - Token endpoint
- `POST /oauth2/introspect` - Token introspection
- `POST /oauth2/revoke` - Token revocation
- `GET /oauth2/jwks` - JSON Web Key Set
- `GET /.well-known/openid-configuration` - Discovery endpoint
- `GET /userinfo` - User information endpoint

### Admin API (Authentication Required)
- `GET|POST /api/v1/organizations` - Organization management
- `GET|POST /api/v1/applications` - Application/client management
- `GET|POST /api/v1/users` - User management
- `POST /api/v1/applications/{guid}/secrets` - Generate client secrets

## Security

### Best Practices Implemented
- ✅ Parameterized SQL queries (SQL injection prevention)
- ✅ CSRF protection
- ✅ Request signature verification
- ✅ Client fingerprinting
- ✅ Replay attack prevention
- ✅ Strong password requirements
- ✅ Token expiration and refresh
- ✅ Secure session management
- ✅ Comprehensive audit logging

### Production Checklist
- [ ] Enable HTTPS for all endpoints
- [ ] Configure proper CORS origins
- [ ] Use environment-specific secrets
- [ ] Enable Flyway migrations
- [ ] Configure proper logging
- [ ] Set up monitoring and alerting
- [ ] Implement rate limiting
- [ ] Regular security audits

## Development

### Building the Project

```bash
# Backend
./mvnw clean package

# Frontend
cd frontend
npm run build
```

### Running Tests

```bash
# Backend
./mvnw test

# Frontend
cd frontend
npm test
```

### Database Migrations

Database migrations are managed using stored procedures. Flyway is disabled by default.

To initialize the database:

```bash
# Execute migration scripts
sqlcmd -S localhost -U sa -d AGSAuth -i src/main/resources/db/migration/R__AuthDBProcs.sql
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

[Add your license information here]

## Support

For setup questions and troubleshooting, see:
- [Admin Portal Setup Guide](ADMIN_PORTAL_SETUP.md)
- [Frontend Documentation](frontend/README.md)
