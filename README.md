# AuthServer

[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

## Overview

AuthServer is a production-grade OAuth 2.0 and OpenID Connect (OIDC) Authorization Server built with Spring Boot and Spring Authorization Server. It provides comprehensive identity and access management (IAM) capabilities for modern applications.

### Key Features

- **Multi-Tenant Architecture**: Support for multiple organizations with isolated data and configurations
- **OAuth 2.0 & OpenID Connect**: Full standards-compliant implementation
- **Multiple Authentication Flows**: Authorization Code with PKCE, Client Credentials, Refresh Token
- **Multi-Factor Authentication**: Email OTP, SMS OTP, TOTP, Security Questions, MFA PIN
- **Biometric Authentication**: Fingerprint and face recognition support for mobile apps
- **Single Sign-On (SSO)**: Organization-wide SSO with distributed session management
- **Admin Portal**: Full-featured management interface for organizations, applications, and users
- **Advanced Security**: Request/response signing, client fingerprinting, token binding
- **Scalable**: Redis-backed sessions and caching
- **Extensible**: Modular architecture for customization

## Documentation

For complete documentation, including architecture, features, API endpoints, deployment guide, and customization options, see:

**📚 [DOCUMENTATION.md](DOCUMENTATION.md)**

The comprehensive documentation covers:
- System architecture and components
- Core features and capabilities
- Technology stack
- Database schema
- API endpoints and authentication flows
- Security features
- Configuration options
- Deployment instructions
- Extension and customization guide

## Quick Start

### Prerequisites

- Java 25 or higher
- Microsoft SQL Server 2019 or higher
- Redis 6.0 or higher
- Maven 3.6 or higher

### Build

```bash
mvn clean package -DskipTests
```

### Run

```bash
java -jar target/authserver-0.0.1-SNAPSHOT.jar
```

The server will start on `http://localhost:9080`

### Configuration

Configure the application via `application.properties` or environment variables. Key settings:

- Database connection (SQL Server)
- Redis connection
- Security settings (key store, password hashing)
- Feature toggles (MFA, biometric, fingerprinting)
- CORS allowed origins

See [DOCUMENTATION.md](DOCUMENTATION.md) for complete configuration details.

## Technology Stack

- **Spring Boot 3.5.7** with Spring Security 6
- **Spring Authorization Server** for OAuth 2.0/OIDC
- **Microsoft SQL Server** for data persistence
- **Redis** for caching and session management
- **Flyway** for database migrations
- **Java 25**

## License

[Add your license information here]

## Support

For questions or issues, please refer to the [DOCUMENTATION.md](DOCUMENTATION.md) or open an issue on GitHub.
