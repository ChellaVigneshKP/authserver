[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

# AuthServer

A comprehensive OAuth 2.0 Authorization Server built with Spring Boot, featuring a modern Next.js admin portal for managing clients, users, and organizations.

## Features

### Backend (Spring Boot)
- **OAuth 2.0 Authorization Server** with full OpenID Connect support
- **Multi-factor Authentication (MFA)** with multiple factor options
- **Biometric Authentication** support
- **Session Management** with Redis
- **Token Introspection** and validation
- **Client Management** with JWT and secret-based authentication
- **User Management** with profile, metadata, and credential sync
- **Organization Management** with groups and permissions
- **Security Features**: Request signing, datetime validation, fingerprinting
- **Comprehensive Audit Logging**

### Frontend (Next.js Admin Portal) - NEW! 🎉
- **Modern, Secure Admin Interface** built with Next.js 14 and Material-UI
- **Dashboard** with key metrics and statistics
- **Application Management**: Full CRUD for OAuth2 clients with redirect URIs, token settings, and resource assignment
- **User Management**: Server-side paginated table with search, profile management, and status control
- **Organization Management**: Full CRUD with DataGrid, contact management, and permissions viewer
- **Secure Authentication**: NextAuth v5 with JWT strategy, httpOnly cookies, no localStorage
- **Resource Library**: API resource management (planned)
- **Certificate Management**: Credential and certificate interfaces (planned)

## Quick Start

### Backend Setup

1. **Configure Database** (SQL Server):
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth
   spring.datasource.username=your-username
   spring.datasource.password=your-password
   ```

2. **Start Redis**:
   ```bash
   redis-server
   ```

3. **Run the Application**:
   ```bash
   ./mvnw spring-boot:run
   ```

   Backend will be available at: `http://localhost:9080/services`

### Frontend Setup

1. **Navigate to frontend**:
   ```bash
   cd frontend
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Configure environment**:
   ```bash
   cp .env.example .env.local
   # Edit .env.local with your settings
   ```

4. **Start development server**:
   ```bash
   npm run dev
   ```

   Frontend will be available at: `http://localhost:3000`

For detailed setup instructions, see [Frontend Setup Guide](./frontend/SETUP.md).

## Documentation

- **Frontend Setup**: [frontend/SETUP.md](./frontend/SETUP.md) - Comprehensive guide for the admin portal
- **Frontend README**: [frontend/README.md](./frontend/README.md) - Feature documentation and architecture
- **Backend API**: Available at `/actuator` endpoints

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Next.js Admin Frontend (Port 3000)                         │
│  - NextAuth v5 (Secure Sessions)                            │
│  - Material-UI Components                                   │
│  - React Query for Data Fetching                            │
└─────────────────────────────────────────────────────────────┘
                           ▼ HTTPS + Cookies
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot OAuth2 Server (Port 9080)                      │
│  - OAuth 2.0 / OpenID Connect                               │
│  - Session Management (Redis)                               │
│  - REST APIs (/api/v1/*)                                    │
└─────────────────────────────────────────────────────────────┘
                           ▼
        ┌──────────────────┴──────────────────┐
        ▼                                      ▼
   SQL Server                               Redis
   (User/Client Data)                   (Sessions)
```

## Technology Stack

### Backend
- **Java 25**
- **Spring Boot 3.5.7**
- **Spring Security OAuth2 Authorization Server**
- **SQL Server** (Database)
- **Redis** (Session Storage)
- **Flyway** (Database Migrations)

### Frontend
- **Next.js 14** (App Router)
- **TypeScript**
- **NextAuth v5** (Authentication)
- **Material-UI (MUI)** (UI Components)
- **TanStack React Query** (Data Fetching)
- **Axios** (HTTP Client)

## Security

### Frontend Security Features
- ✅ Secure session management with JWT and httpOnly cookies
- ✅ No localStorage usage for sensitive data
- ✅ CSRF protection built-in
- ✅ Request datetime headers for backend validation
- ✅ Password base64 encoding per backend requirements
- ✅ Automatic session refresh
- ✅ Secure cookie configuration (httpOnly, secure, sameSite)

### Backend Security Features
- ✅ Request body signature validation
- ✅ Response body signing
- ✅ Request datetime validation
- ✅ Client fingerprinting
- ✅ MFA support
- ✅ Biometric authentication
- ✅ Password validation with complexity rules
- ✅ Session management with Redis
- ✅ Token introspection
- ✅ CORS configuration

## Default Credentials

**Admin User** (configured in `application.properties`):
- Username: `admin`
- Password: `admin123`

⚠️ **Important**: Change these credentials in production!

## API Endpoints

### OAuth2 Endpoints
- `POST /login` - User authentication
- `GET /oauth2/authorize` - Authorization endpoint
- `POST /oauth2/token` - Token endpoint
- `POST /oauth2/introspect` - Token introspection
- `POST /oauth2/revoke` - Token revocation
- `GET /oauth2/connect/logout` - Logout endpoint
- `GET /.well-known/openid-configuration` - OpenID configuration

### Admin API Endpoints
- `GET /api/v1/organizations` - List organizations
- `GET /api/v1/organizations/{orgGuid}/applications` - List applications
- `GET /api/v1/users` - List users (paginated)
- And many more... (see Frontend SETUP.md for complete API list)

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

[Add your license here]

## Support

For issues or questions:
1. Check the [Frontend Setup Guide](./frontend/SETUP.md)
2. Review application logs
3. Check browser console and network tab
4. Open an issue on GitHub
