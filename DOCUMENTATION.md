# AuthServer - Comprehensive Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Features](#core-features)
4. [Technology Stack](#technology-stack)
5. [Key Components](#key-components)
6. [Database Schema](#database-schema)
7. [API Endpoints](#api-endpoints)
8. [Authentication Flows](#authentication-flows)
9. [Security Features](#security-features)
10. [Configuration](#configuration)
11. [Deployment](#deployment)
12. [Extension & Customization](#extension--customization)

---

## Overview

### What is AuthServer?

AuthServer is a **production-grade OAuth 2.0 and OpenID Connect (OIDC) Authorization Server** built with Spring Boot and Spring Authorization Server. It provides comprehensive identity and access management (IAM) capabilities for modern applications.

### Purpose

This server is designed to:
- Provide centralized authentication and authorization for multiple applications
- Support multiple organizations (multi-tenancy) with isolated authentication realms
- Enable secure token-based authentication using industry standards (OAuth 2.0, OIDC)
- Offer flexible authentication methods including MFA, biometric authentication, and SSO
- Manage user profiles, credentials, and permissions across organizations
- Provide admin portal for managing organizations, applications, users, and configurations

### Key Characteristics

- **Multi-tenant**: Support multiple organizations with isolated data and configurations
- **Standards-compliant**: Full OAuth 2.0 and OpenID Connect implementation
- **Highly configurable**: Extensive configuration options for authentication flows, security policies, and branding
- **Production-ready**: Includes monitoring, caching, session management, and security hardening
- **Extensible**: Modular architecture allows customization and extension

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Applications                      │
│              (Mobile, Web, Server-to-Server)                    │
└────────────────────────┬───────────────────────────────────────┘
                         │
                         │ OAuth 2.0 / OIDC Protocols
                         │
┌────────────────────────▼───────────────────────────────────────┐
│                      AuthServer                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  OAuth 2.0 Authorization Server (Spring Security)       │  │
│  ├─────────────────────────────────────────────────────────┤  │
│  │  • Authorization Endpoint    • Token Endpoint            │  │
│  │  • UserInfo Endpoint         • Introspection Endpoint    │  │
│  │  • JWKS Endpoint            • Logout Endpoint           │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────┐ │
│  │ Admin Portal     │  │ MFA/OTP Service  │  │ Session Mgmt│ │
│  │ (Organization,   │  │ (Email, SMS,     │  │ (Redis)     │ │
│  │  App, User Mgmt) │  │  TOTP, Biometric)│  │             │ │
│  └──────────────────┘  └──────────────────┘  └─────────────┘ │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────┐ │
│  │ Token Service    │  │ Signature Service│  │ KeyStore    │ │
│  │ (JWT Generation, │  │ (Request/Response│  │ Management  │ │
│  │  Validation)     │  │  Signing)        │  │             │ │
│  └──────────────────┘  └──────────────────┘  └─────────────┘ │
└────────────────────────┬───────────────────────────────────────┘
                         │
                         │
┌────────────────────────▼───────────────────────────────────────┐
│                    Data Layer                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────┐ │
│  │ SQL Server       │  │ Redis Cache      │  │ External    │ │
│  │ (Organizations,  │  │ (Sessions,       │  │ Services    │ │
│  │  Users, Tokens,  │  │  Cache)          │  │ (Crypto,    │ │
│  │  Applications)   │  │                  │  │  Notify)    │ │
│  └──────────────────┘  └──────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Component Layers

1. **Protocol Layer**: OAuth 2.0 / OIDC endpoints and handlers
2. **Business Logic Layer**: Services for authentication, authorization, user management
3. **Security Layer**: Request validation, signature verification, fingerprinting
4. **Data Access Layer**: Repositories for database operations
5. **Infrastructure Layer**: Caching, session management, external integrations

---

## Core Features

### 1. OAuth 2.0 & OpenID Connect

#### Supported Grant Types
- **Authorization Code Flow** with PKCE (Proof Key for Code Exchange)
- **Client Credentials Flow** (machine-to-machine)
- **Refresh Token Flow**

#### Authentication Methods
- **Client Secret JWT**: Client authenticates using JWT signed with shared secret
- **Private Key JWT**: Client authenticates using JWT signed with private key
- **PKCE**: For public clients (mobile, SPA) without client secret

#### Token Types
- **Access Tokens**: JWT or opaque tokens for API authorization
- **ID Tokens**: JWT containing user identity claims (OIDC)
- **Refresh Tokens**: Long-lived tokens for obtaining new access tokens


### 2. Multi-Factor Authentication (MFA)

#### Supported MFA Methods
- **Email OTP**: One-time password sent via email
- **SMS OTP**: One-time password sent via SMS
- **TOTP**: Time-based one-time password (authenticator apps)
- **Security Questions**: Challenge-response authentication
- **MFA PIN**: Organization-specific PIN authentication

#### MFA Flows
- **Login MFA**: Additional verification during login
- **Forgot Password MFA**: Identity verification for password reset
- **Account Activation MFA**: Verification during account activation
- **Mobile MFA**: Optimized flows for mobile applications

#### MFA Configuration
- Per-organization MFA policies
- Per-application MFA requirements
- Configurable MFA factor selection
- MFA session timeout and expiry settings

### 3. Biometric Authentication

- **Fingerprint Authentication**: Mobile biometric authentication
- **Face Recognition**: Facial biometric authentication
- Secure biometric token exchange
- Biometric enrollment and management

### 4. Single Sign-On (SSO)

- **Organization-wide SSO**: Users authenticate once for all applications within an organization
- **Session Management**: Distributed session management with Redis
- **Session Fingerprinting**: Device and browser fingerprinting for enhanced security
- **Cross-application Sessions**: Seamless authentication across multiple applications

### 5. User Management

#### User Lifecycle
- **Registration**: User account creation with validation
- **Activation**: Account activation via email/SMS verification
- **Profile Management**: Users can update personal information
- **Password Management**: 
  - Password change (authenticated users)
  - Password reset via forgot-password flow
  - Password strength validation with configurable policies
- **Account Locking**: Automatic account locking after failed attempts
- **Username Recovery**: Forgot username flow with identity verification

#### User Attributes
- Profile information (first name, last name, email, phone)
- Multiple username types (email, phone, custom)
- External user identifiers (for federation)
- User groups and permissions
- Organization associations

### 6. Organization & Application Management

#### Organization Management
- Multi-tenant architecture with data isolation
- Organization creation and configuration
- Organization-specific branding and customization
- Organization groups and permissions
- Certificate management per organization

#### Application Management
- Multiple applications per organization
- Application types: Mobile, Web, Server-to-Server
- Application-specific OAuth 2.0 settings:
  - Redirect URIs
  - Post-logout redirect URIs
  - Token lifetimes
  - Allowed scopes
  - Client authentication methods
- Application credentials and secrets management
- Application-specific MFA policies

### 7. Admin Portal

Full-featured web portal for administrators to:
- Manage organizations, groups, and permissions
- Create and configure applications
- Manage users and their profiles
- View and manage user sessions
- Configure global settings and policies
- Manage certificates and keys
- Access resource library (shared resources across applications)
- Configure branding and CMS content

### 8. Advanced Security Features

#### Request/Response Security
- **Body Signature Verification**: Optional signing of request/response bodies
- **Request Timestamp Validation**: Prevent replay attacks
- **Client Fingerprinting**: Browser and device fingerprinting
- **CORS Configuration**: Configurable cross-origin resource sharing
- **Rate Limiting**: Protection against brute force attacks

#### Cryptographic Features
- **Key Management**: Secure key generation and storage
- **JWT Signing**: RS256, ES256 algorithms for token signing
- **Certificate Management**: X.509 certificate storage and rotation
- **Password Hashing**: PBKDF2 with configurable iterations and pepper
- **Integration with External Crypto Service**: Optional integration with lib-crypto library

#### Token Security
- Token introspection for validation
- Token revocation support
- Configurable token lifetimes
- Refresh token rotation
- Token binding to client fingerprint

### 9. Session Management

- **Distributed Sessions**: Redis-backed session storage
- **Session Timeout**: Configurable inactivity timeout
- **Session Cleanup**: Automatic expired session cleanup
- **Session Fingerprinting**: Device-based session validation
- **Concurrent Session Control**: Limit concurrent sessions per user

### 10. Content Management System (CMS)

- **Branding**: Customizable branding per organization/application
- **Content Files**: Serve static content for login pages, emails, etc.
- **URL Mapping**: Map custom URLs to branding configurations
- **Template Management**: Thymeleaf-based templates for UI pages

### 11. External Integrations

#### Notification Services
- Email notifications for OTP, password reset, account activation
- SMS notifications for OTP
- Configurable notification templates

#### Account Synchronization
- Sync user accounts with external systems
- Configurable sync strategies (none, push, pull)

#### SecureAuth Integration
- Integration with SecureAuth MFA realms
- MFA delegation to SecureAuth for specific organizations

---

## Technology Stack

### Backend Framework
- **Spring Boot 3.5.7**: Application framework
- **Spring Security 6**: Authentication and authorization
- **Spring Authorization Server**: OAuth 2.0 / OIDC implementation
- **Java 25**: Programming language

### Database & Persistence
- **Microsoft SQL Server**: Primary relational database
- **H2**: In-memory database for testing
- **Spring JDBC**: Database access
- **Flyway**: Database migration management

### Caching & Session
- **Redis**: Distributed cache and session store
- **Spring Session**: Session management with Redis backend
- **Spring Cache**: Caching abstraction

### Security & Cryptography
- **Bouncy Castle**: Cryptographic operations
- **Nimbus JOSE+JWT**: JWT and JWK handling
- **lib-crypto**: Custom cryptographic library integration

### Web & API
- **Spring WebFlux**: Reactive web client
- **Spring MVC**: REST API and web controllers
- **Thymeleaf**: Server-side template engine
- **Spring REST Docs**: API documentation

### Monitoring & Observability
- **Spring Actuator**: Health checks and metrics
- **Micrometer**: Metrics and tracing
- **Logback**: Logging framework with access logs

### Build & Development
- **Maven**: Build and dependency management
- **Lombok**: Reduce boilerplate code
- **MapStruct**: Object mapping
- **Spring DevTools**: Development-time features

### Testing
- **JUnit 5**: Unit testing
- **Mockito**: Mocking framework
- **REST Assured**: API testing
- **Testcontainers**: Integration testing with containers
- **Spring Security Test**: Security testing utilities

### Validation & Utilities
- **Jakarta Validation**: Bean validation
- **Apache Commons Validator**: Additional validation utilities
- **Apache Commons Lang**: Utility methods
- **Apache Commons Pool**: Connection pooling
- **Jackson**: JSON serialization/deserialization

---

## Key Components

### 1. OAuth 2.0 Components

#### AuthServerConfig
Main security configuration for OAuth 2.0 Authorization Server:
- Configures OAuth 2.0 endpoints (authorization, token, introspection, userinfo, jwks)
- Sets up authentication providers (client_secret_jwt, private_key_jwt, PKCE)
- Configures security filters for request validation and signature verification
- Defines CORS policy

#### JdbcRegisteredClientRepository
Manages OAuth 2.0 registered clients (applications):
- Stores client configurations in database
- Supports multiple authentication methods per client
- Caches client data for performance

#### Authorization Filters
- **PreAuthorizationFilter**: Pre-processes authorization requests
- **TokenEndpointBodySignatureFilter**: Validates signatures on token requests
- **OAuth2ResponseBodySignatureFilter**: Signs OAuth 2.0 responses

### 2. Authentication Components

#### CustomUserDetailsService
Implements Spring Security's UserDetailsService:
- Loads user details from database
- Supports multiple username types (email, phone, custom)
- Integrates with account search service

#### Password Encoders
- **LibCryptoPasswordEncoder**: Integrates with external crypto library
- **KPCVPasswordEncoder**: Legacy password encoding support
- **PasswordEncoderFactory**: Factory for creating appropriate encoder

#### Authentication Providers
- **ClientSecretJWTAuthenticationProvider**: Validates client_secret_jwt authentication
- **PrivateJWTAuthenticationProvider**: Validates private_key_jwt authentication
- **IntrospectionAuthenticationProvider**: Custom token introspection

### 3. MFA Components

#### MFAService
Core MFA functionality:
- Generates and validates OTP codes
- Manages MFA factors (email, SMS, TOTP)
- Integrates with notification services
- Handles MFA realm delegation (SecureAuth)

#### MFAController
Web endpoints for MFA flows:
- `/mfa` - MFA challenge page
- `/mfa/{flow}/request-pin` - Request OTP/PIN
- `/mfa/{flow}/verify-pin` - Verify OTP/PIN

#### MFA Realms
- **Organization MFA Realms**: Custom MFA configurations per organization
- **SecureAuth Integration**: Delegate MFA to SecureAuth service

### 4. Session Components

#### AuthSessionService
Manages authentication sessions:
- Creates sessions after successful authentication
- Tracks session status (active, MFA pending, expired)
- Associates sessions with users and applications
- Stores session fingerprints

#### SessionConfig
Configures Redis-based session management:
- Session timeout (30 minutes default)
- Session cleanup schedule
- Session serialization

#### Session Fingerprinting
- **RequestClientFingerprintFilter**: Captures client fingerprint
- **ClientFingerprintService**: Generates and validates fingerprints based on:
  - IP address
  - User-Agent
  - Accept headers
  - Referer (optional)

### 5. Token Components

#### TokenService
Manages token lifecycle:
- Issues access tokens and refresh tokens
- Validates tokens
- Revokes tokens
- Tracks token usage and expiry

#### SignatureService
Handles request/response signing:
- Signs response bodies with application's private key
- Verifies request signatures
- Manages signing keys per application

#### SigningKeyGenerator
Generates cryptographic keys:
- RSA key pairs for JWT signing
- EC key pairs for JWT signing
- Stores keys in database

### 6. Admin Portal Components

#### Organization Management
- **OrganizationController**: REST API for organizations
- **OrganizationService**: Business logic for organization operations
- **OrganizationRepository**: Database access for organizations

#### Application Management
- **ApplicationController**: REST API for applications
- **ApplicationService**: Application configuration and management
- Manages client credentials, redirect URIs, token settings

#### User Management
- **UserController**: REST API for user operations
- **UserService**: User CRUD operations, password management
- **ChangeUserPasswordController**: Password change functionality
- **ChangeUserProfileController**: Profile update functionality

#### Credential Management
- **CredentialController**: Manage application credentials (client secrets)
- **CredentialSecretService**: Generate and rotate client secrets

#### Account Access Flows
- **AccountAccessController**: Forgot password and account activation
- **ForgotUsernameController**: Username recovery

### 7. Data Access Components

#### Repositories
All repositories use Spring JDBC with custom row mappers:
- AuthCodeRepository: Authorization codes
- TokenRepository: Access and refresh tokens
- AuthSessionRepository: Authentication sessions
- UserRepository: User accounts
- ApplicationRepository: Client applications
- OrganizationRepository: Organizations
- And 20+ more specialized repositories

### 8. Configuration Components

#### DataSourceConfig
Database configuration:
- HikariCP connection pool settings
- Query timeout configuration
- Transaction management

#### RedisConfig
Redis configuration:
- Connection settings
- Serialization configuration
- Cache and session namespaces

#### CacheConfig
Cache configuration:
- Cache names and TTLs (time-to-live)
- Separate caches for organizations, applications, tokens, sessions, etc.

#### LibCryptoWebClientConfig
External crypto service integration:
- WebClient configuration for HTTP calls
- Connection pool settings

### 9. Security Components

#### Request Validation
- **RequestDatetimeValidationFilter**: Validates request timestamp
- **RequestBodySignatureFilter**: Verifies request signatures
- **BrandingRequestBodyFilter**: Captures request body for branding

#### Permission Management
- **PermissionsService**: Checks user permissions
- **CustomPermissionEvaluator**: SpEL-based permission evaluation
- Organization group-based permissions

#### Password Validation
- **PasswordValidatorService**: Validates password strength
- Configurable regex patterns
- Password history tracking

### 10. Utility Components

#### EnumService
Manages database-driven enumerations:
- AuthFlowEnum: Authentication flows
- ApplicationTypeEnum: Application types
- TokenTypeEnum: Token types
- AlgorithmEnum: Cryptographic algorithms
- And more

#### GlobalConfigCache
Caches global configuration settings:
- Feature toggles
- System-wide settings
- Cached for performance

#### CMSService
Content management:
- Serves static files from classpath or filesystem
- Branding and customization support


---

## Database Schema

### Core Schema Structure

The database is organized into several schemas:

#### 1. **Partner Schema** - Organization Management
- **Organization**: Organizations (tenants)
- **OrganizationGroup**: User groups within organizations
- **OrganizationGroupPermission**: Permissions assigned to groups

#### 2. **Client Schema** - Application Management
- **Application**: OAuth 2.0 client applications
- **ApplicationCredential**: Client secrets and credentials
- **ApplicationEndpoint**: Redirect URIs and endpoints
- **ApplicationTokenSettings**: Token lifetime configurations

#### 3. **Person Schema** - User Management
- **Profile**: User profiles (first name, last name, etc.)
- **ProfileCredential**: User credentials (passwords)
- **ProfileOrganization**: User-organization associations
- **ProfileOrganizationGroup**: User group memberships

#### 4. **Security Schema** - Authentication & Authorization
- **AuthCode**: Authorization codes (short-lived)
- **Token**: Access and refresh tokens
- **AuthSession**: Authentication sessions
- **PasswordHistory**: Historical passwords for validation

#### 5. **Certificate Schema**
- **OrganizationCertificate**: X.509 certificates per organization
- **ApplicationKeyStore**: Cryptographic keys for applications

#### 6. **MFA Schema**
- **MFARealm**: MFA realm configurations
- **MFAPinTracking**: Tracks MFA attempts and lockouts

#### 7. **Configuration Schema**
- **GlobalConfig**: System-wide configuration
- **ExternalSource**: External system integrations
- **Range**: IP/network ranges for access control

### Key Tables

#### Application (OAuth 2.0 Client)
```
- ApplicationId (PK)
- ClientId (Unique)
- ClientName
- OrganizationId (FK)
- ApplicationTypeEnumId (Mobile/Web/Server)
- RequireAuthCodeFlow (boolean)
- RequirePrivateKeyJwtAuth (boolean)
- RequireClientSecretJwtAuth (boolean)
- RequireMFA (boolean)
- EnableBiometric (boolean)
```

#### Profile (User)
```
- ProfileId (PK)
- FirstName
- LastName
- Email
- PhoneNumber
- ExternalUserId
- AccountLocked (boolean)
- FailedAttempts
```

#### AuthSession
```
- AuthSessionId (PK)
- ProfileId (FK)
- ApplicationId (FK)
- SessionToken
- Status (Active, Expired, MFA Pending)
- CreatedDate
- ExpiryDate
- Fingerprint
```

#### Token
```
- TokenId (PK)
- TokenValue (hashed)
- TokenTypeEnumId (Access/Refresh)
- ProfileId (FK)
- ApplicationId (FK)
- IssuedAt
- ExpiresAt
- Revoked (boolean)
- Scopes
```

### Database Migrations

The database schema is managed via **Flyway**:
- Migration scripts in `src/main/resources/db/migration/`
- Repeatable migrations for stored procedures: `R__AuthDBProcs.sql`
- Version-specific migrations in `db/v1.0/`, `db/v1.14/`, etc.

### Stored Procedures

Over 100 stored procedures for:
- User CRUD operations
- Organization and application management
- Token and session management
- Permission checks
- Enum value loading
- MFA operations

Example procedures:
- `[Partner].[CreateOrganization]`: Creates organization with admin group
- `[Person].[CreateUserProfile]`: Creates user with credentials
- `[Client].[CreateApplication]`: Creates OAuth 2.0 client
- `[dbo].[ValidateUserCredentials]`: Validates username/password
- `[dbo].[GetUserPermissions]`: Retrieves user permissions

---

## API Endpoints

### OAuth 2.0 / OIDC Endpoints

#### Authorization Endpoint
```
GET /oauth2/authorize
```
Parameters:
- `response_type`: "code"
- `client_id`: Application client ID
- `redirect_uri`: Callback URL
- `scope`: Requested scopes (openid, profile, email, etc.)
- `state`: CSRF token
- `code_challenge`: PKCE challenge (for PKCE flow)
- `code_challenge_method`: "S256"

#### Token Endpoint
```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
```
Grant types:
- `authorization_code`: Exchange auth code for tokens
- `refresh_token`: Refresh access token
- `client_credentials`: Machine-to-machine authentication

Authentication methods:
- `client_secret_jwt`: JWT signed with shared secret
- `private_key_jwt`: JWT signed with private key

#### Token Introspection
```
POST /oauth2/introspect
Content-Type: application/x-www-form-urlencoded
```
Parameters:
- `token`: Token to introspect
- Client authentication required

#### UserInfo Endpoint
```
GET /oauth2/userinfo
Authorization: Bearer {access_token}
```
Returns user profile claims (sub, name, email, etc.)

#### JWKS Endpoint
```
GET /oauth2/.well-known/organizations/{orgId}/jwks.json
```
Returns public keys for JWT signature verification

#### OpenID Configuration
```
GET /oauth2/.well-known/organizations/{orgId}/openid-configuration
```
Returns OpenID Connect discovery metadata

#### Logout Endpoint
```
POST /oauth2/connect/logout
Content-Type: application/x-www-form-urlencoded
```
Parameters:
- `id_token_hint`: ID token
- `post_logout_redirect_uri`: Redirect after logout

### Admin Portal Endpoints

#### Organization Management
```
GET    /api/v1/organizations/{orgGuid}
GET    /api/v1/organizations/{orgGuid}/groups
GET    /api/v1/organizations/{orgGuid}/groups/{groupGuid}/permissions
POST   /api/v1/organizations/{orgGuid}/certificates
GET    /api/v1/organizations/{orgGuid}/certificates/{certId}
```

#### Application Management
```
GET    /api/v1/organizations/{orgGuid}/applications/{appGuid}
GET    /api/v1/organizations/{orgGuid}/applications/{appGuid}/urls
POST   /api/v1/organizations/{orgGuid}/applications/{appGuid}/endpoints
GET    /api/v1/organizations/{orgGuid}/applications/{appGuid}/endpoints
```

#### Credential Management
```
POST   /api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials
GET    /api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials
PUT    /api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials/{credId}
DELETE /api/v1/organizations/{orgGuid}/applications/{appGuid}/credentials/{credId}
```

#### User Management
```
GET    /api/v1/users
POST   /api/v1/users
GET    /api/v1/users/{userId}
PUT    /api/v1/users/{userId}
DELETE /api/v1/users/{userId}
```

### Authentication Flow Endpoints

#### Login
```
GET  /login
POST /login
```

#### MFA
```
GET  /mfa
POST /mfa
GET  /mfa/{flow}/request-pin
POST /mfa/{flow}/request-pin
GET  /mfa/{flow}/verify-pin
POST /mfa/{flow}/verify-pin
```
Flows: `login`, `forgot-password`, `activate-account`, `mobile-forgot-password`

#### Account Access
```
GET  /forgot-password
POST /forgot-password/identify-yourself
GET  /forgot-password/update-password
POST /forgot-password/update-password

GET  /forgot-username
POST /forgot-username/identify-yourself

GET  /activate-account
POST /activate-account/identify-yourself
POST /activate-account/update-password
```

#### Profile Management
```
GET  /user/change-password
POST /user/change-password
GET  /user/change-profile
POST /user/change-profile
```

### Utility Endpoints

#### Signature Verification
```
POST /oauth2/signature/verify
```

#### SSO
```
GET /oauth2/sso
```
Parameters:
- `token`: Session token for SSO

#### Resource Library
```
GET /api/v1/endpoints/{resourcePath}
```


---

## Authentication Flows

### 1. Authorization Code Flow with PKCE

**Use Case**: Web and mobile applications

**Flow**:
1. **Client initiates authorization**:
   - Generates `code_verifier` and `code_challenge`
   - Redirects user to `/oauth2/authorize` with parameters

2. **User authentication**:
   - User lands on `/login` page
   - Enters username and password
   - If MFA is enabled, redirected to `/mfa`

3. **MFA challenge** (if enabled):
   - User selects MFA method (email, SMS, TOTP)
   - Requests OTP via `/mfa/login/request-pin`
   - Validates OTP via `/mfa/login/verify-pin`

4. **Authorization code issued**:
   - Server generates short-lived authorization code
   - Redirects back to client's `redirect_uri` with code

5. **Token exchange**:
   - Client calls `/oauth2/token` with:
     - `grant_type=authorization_code`
     - `code={authorization_code}`
     - `code_verifier={verifier}`
   - Server validates code and verifier
   - Issues access token, ID token, and refresh token

6. **Access protected resources**:
   - Client includes `Authorization: Bearer {access_token}` header
   - Calls `/oauth2/userinfo` or other APIs

### 2. Client Credentials Flow

**Use Case**: Server-to-server authentication

**Flow**:
1. **Client authenticates with JWT**:
   - Creates JWT assertion signed with client secret or private key
   - Calls `/oauth2/token` with:
     - `grant_type=client_credentials`
     - `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer`
     - `client_assertion={signed_jwt}`

2. **Server validates assertion**:
   - Verifies JWT signature
   - Validates claims (iss, sub, aud, exp)
   - Checks client is authorized for client_credentials

3. **Access token issued**:
   - Server issues access token (no refresh token)
   - Client uses access token for API calls

### 3. Refresh Token Flow

**Use Case**: Renew expired access tokens

**Flow**:
1. **Client requests new access token**:
   - Calls `/oauth2/token` with:
     - `grant_type=refresh_token`
     - `refresh_token={refresh_token}`
   - Client authentication required

2. **Server validates refresh token**:
   - Checks token is not expired or revoked
   - Validates client ownership

3. **New access token issued**:
   - Server issues new access token
   - Optionally rotates refresh token

### 4. Biometric Authentication Flow

**Use Case**: Mobile biometric authentication

**Flow**:
1. **Biometric enrollment**:
   - User logs in with password and completes MFA
   - Client generates biometric token
   - Stores biometric token locally

2. **Biometric authentication**:
   - User authenticates with fingerprint/face on device
   - Client sends biometric token to `/oauth2/authorize`
   - Server validates biometric token
   - Issues authorization code

3. **Token exchange**:
   - Standard PKCE token exchange

### 5. SSO Flow

**Use Case**: Single Sign-On across applications

**Flow**:
1. **User logs into Application A**:
   - Completes full authentication (password + MFA)
   - Server creates `AuthSession` with session token

2. **User accesses Application B**:
   - Application B redirects to `/oauth2/authorize`
   - Server detects active session via cookie/session
   - Skips authentication, directly issues authorization code

3. **Logout**:
   - User calls `/oauth2/connect/logout` from any application
   - Server invalidates session across all applications

### 6. Forgot Password Flow

**Use Case**: Password reset for locked-out users

**Flow**:
1. **User initiates password reset**:
   - Accesses `/forgot-password`
   - Enters username/email

2. **Identity verification**:
   - User provides additional identifying information
   - System validates user existence

3. **MFA challenge**:
   - Redirected to `/mfa/forgot-password/request-pin`
   - Receives OTP via email/SMS
   - Validates OTP

4. **Password reset**:
   - After successful MFA, redirected to `/forgot-password/update-password`
   - Enters new password
   - Password validated against policy
   - Password updated in database

---

## Security Features

### 1. Cryptographic Security

#### Key Management
- **RSA Keys**: 2048-bit or 4096-bit RSA keys for JWT signing
- **EC Keys**: P-256, P-384 curves for ECDSA signing
- **Key Rotation**: Support for key rotation with grace period
- **Key Storage**: Encrypted storage in database

#### JWT Security
- **Algorithm Support**: RS256, RS384, RS512, ES256, ES384, ES512
- **Token Signing**: All tokens signed with organization-specific keys
- **Token Validation**: Signature, expiration, issuer, audience checks
- **Claims Validation**: Custom claim validation logic

#### Password Security
- **Hashing Algorithm**: PBKDF2 with SHA-256
- **Iterations**: Configurable (default: 20,000)
- **Salt**: Random per-user salt
- **Pepper**: Global pepper for additional security
- **Password History**: Prevent reuse of recent passwords
- **Strength Validation**: Configurable regex pattern enforcement

### 2. Request Security

#### Request Signature Verification
- **Optional Signing**: Configurable per application
- **Signature Algorithm**: RS256
- **Signature Location**: Custom header
- **Body Signing**: Signs entire request body
- **Timestamp Validation**: Rejects old requests

#### Response Signing
- **OAuth 2.0 Response Signing**: Signs token endpoint responses
- **Custom Header**: Signature in `X-Signature` header
- **Verification**: Clients can verify response authenticity

#### Client Fingerprinting
- **Fingerprint Components**:
  - IP address
  - User-Agent
  - Accept headers
  - Accept-Language
  - Accept-Encoding
  - Referer (optional)
- **Fingerprint Storage**: Stored with session
- **Validation**: Optional validation on subsequent requests
- **Purpose**: Detect session hijacking

### 3. Attack Prevention

#### CSRF Protection
- **State Parameter**: Required in authorization flow
- **CSRF Tokens**: Spring Security CSRF tokens on forms
- **SameSite Cookies**: Cookies marked as SameSite=Lax

#### Replay Attack Prevention
- **Request Timestamps**: All requests include timestamp
- **Timestamp Validation**: Rejects requests older than threshold
- **Nonce Support**: One-time use authorization codes

#### Brute Force Protection
- **Account Locking**: Lock account after N failed attempts
- **MFA Lockout**: Lock MFA after N failed OTP attempts
- **Lockout Duration**: Configurable lockout period
- **IP-based Rate Limiting**: Optional IP-based rate limits

#### Token Security
- **Short-lived Access Tokens**: Configurable, typically 15-60 minutes
- **Refresh Token Rotation**: Optional rotation on use
- **Token Revocation**: Explicit token revocation support
- **Token Binding**: Bind tokens to client fingerprint

### 4. Session Security

#### Session Management
- **Session Timeout**: Configurable inactivity timeout
- **Absolute Timeout**: Maximum session lifetime
- **Concurrent Sessions**: Limit concurrent sessions per user
- **Session Fixation Protection**: New session ID after login

#### Session Storage
- **Redis Backend**: Distributed session storage
- **Session Encryption**: Encrypted session data
- **Secure Cookies**: HttpOnly, Secure flags on cookies

---

## Configuration

### Application Properties

The server is configured via `application.properties`:

#### Server Configuration
```properties
spring.application.name=authserver
server.port=9080
server.base-path=http://localhost:9080/services
```

#### Database Configuration
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;...
spring.datasource.username=acsapp
spring.datasource.password=Ac$App@123
app.database.query-timeout=15
app.datasource.hikari.maximum-pool-size=40
app.datasource.hikari.minimum-idle=20
app.datasource.hikari.connection-timeout=30000
```

#### Redis Configuration
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.session.store-type=redis
spring.session.timeout=1800
spring.session.redis.namespace=agsup-auth:spring:session
spring.cache.type=redis
spring.cache.redis.time-to-live=180000
```

#### Cache Configuration
```properties
cache.organization.ttl=300000
cache.application.ttl=180000
cache.token.ttl=120000
cache.session.ttl=90000
cache.registered-client.ttl=180000
```

#### Security Configuration
```properties
key-store.password=local-password
hasher.password=myStrongPassword
hasher.keystorefile=keystore.jks
hasher.iterations=20000
validation.password.regex=^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$
```

#### Feature Toggles
```properties
toggles.signature.required=false
toggles.fingerprinting.enabled=true
toggles.biometric.enabled=true
toggles.login.try-harder-enabled=true
toggles.account.sync.type=none
```

#### CORS Configuration
```properties
endpoints.web.cors.allowed-origins=http://localhost:3000,http://localhost:4200,http://localhost:9080
```

---

## Deployment

### System Requirements

#### Minimum Requirements
- **Java**: Java 25 or higher
- **CPU**: 2 cores
- **Memory**: 4 GB RAM
- **Disk**: 10 GB

#### Recommended Requirements
- **Java**: Java 25
- **CPU**: 4+ cores
- **Memory**: 8+ GB RAM
- **Disk**: 50+ GB (for logs and database)

### Dependencies

#### Required External Services
1. **Microsoft SQL Server**: 2019 or higher
   - Database: `AGSAuth`
   - User with full permissions on database
   
2. **Redis**: 6.0 or higher
   - For session and cache storage
   - Persistence recommended

#### Optional External Services
1. **Crypto Service** (lib-crypto): For password hashing and encryption
2. **Notification Service**: For sending email/SMS OTP
3. **Account Service**: For account synchronization
4. **SecureAuth**: For delegated MFA

### Database Setup

1. **Create Database**:
```sql
CREATE DATABASE AGSAuth;
```

2. **Create Application User**:
```sql
CREATE LOGIN acsapp WITH PASSWORD = 'Ac$App@123';
USE AGSAuth;
CREATE USER acsapp FOR LOGIN acsapp;
ALTER ROLE db_owner ADD MEMBER acsapp;
```

3. **Run Migrations**:
Flyway will automatically run migrations on startup, or manually:
```bash
mvn flyway:migrate
```

### Building the Application

#### Build with Maven
```bash
mvn clean package -DskipTests
```

Output: `target/authserver-0.0.1-SNAPSHOT.jar`

### Running the Application

#### Run Locally
```bash
java -jar target/authserver-0.0.1-SNAPSHOT.jar
```

#### Run with External Configuration
```bash
java -jar authserver.jar --spring.config.location=file:/etc/authserver/application.properties
```

### Health Checks

Spring Actuator endpoints for monitoring:

```bash
# Health check
curl http://localhost:9080/actuator/health

# Metrics
curl http://localhost:9080/actuator/metrics
```

---

## Extension & Customization

### 1. Custom Authentication Methods

Add custom authentication providers by implementing Spring Security's `AuthenticationProvider` interface and registering in `AuthServerConfig`.

### 2. Custom User Attributes

Extend user profile with custom attributes by modifying the database schema and updating entity classes.

### 3. Custom MFA Methods

Add custom MFA factors by implementing MFA providers and registering in `MFAService`.

### 4. Custom Token Claims

Add custom claims to access tokens using OAuth2TokenCustomizer.

### 5. Custom Scopes

Define custom OAuth 2.0 scopes by configuring them in the RegisteredClient.

### 6. External User Federation

Integrate with external identity providers by implementing user lookup services and linking accounts.

### 7. Custom Branding

Customize login pages and emails by creating brand configurations and Thymeleaf templates.

### 8. Custom Validators

Add custom validation logic by implementing validator components and registering them in services.

### 9. Event Listeners

React to authentication events by implementing event listener components.

### 10. Custom Endpoints

Add custom REST endpoints by creating controller classes and securing them with Spring Security.

---

## Summary

AuthServer is a **comprehensive, production-ready OAuth 2.0 and OpenID Connect authorization server** that provides:

✅ **Standards-Compliant**: Full OAuth 2.0 and OIDC implementation
✅ **Multi-Tenant**: Support for multiple organizations with data isolation
✅ **Flexible Authentication**: Multiple auth flows, MFA, biometric, SSO
✅ **Admin Portal**: Full-featured management interface
✅ **Highly Secure**: Request signing, fingerprinting, token binding, encryption
✅ **Scalable**: Redis-backed sessions and caching
✅ **Extensible**: Modular architecture for customization
✅ **Production-Ready**: Monitoring, logging, health checks

This documentation provides a complete blueprint for understanding, deploying, customizing, and extending the AuthServer to meet your specific requirements.

---

## Next Steps

Based on this documentation, you can now:

1. **Assess Current Features**: Understand what the server currently does
2. **Identify Gaps**: Determine what features are missing for your needs
3. **Plan Customization**: Design extensions and modifications
4. **Build New Version**: Create a customized authserver based on requirements

For questions or clarifications on any specific component, refer to the source code in the respective packages mentioned throughout this documentation.
