# AuthServer Admin Frontend - Setup and Deployment Guide

## Overview

This document provides comprehensive setup instructions for the AuthServer Admin Frontend, a secure Next.js-based administration portal for managing OAuth2 applications, users, and organizations.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Browser (Client)                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Next.js Frontend (http://localhost:3000)            │  │
│  │  - NextAuth v5 (JWT Session)                         │  │
│  │  - Material-UI Components                            │  │
│  │  - React Query for Data Fetching                     │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                           ▼
              (HTTPS with Cookies)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              AuthServer Backend                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Spring Boot OAuth2 Server                           │  │
│  │  (http://localhost:9080/services)                    │  │
│  │  - Session Management (Redis)                        │  │
│  │  - Security Filters (Signature, Datetime, etc.)      │  │
│  │  - REST APIs (/api/v1/*)                             │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Prerequisites

### Backend Requirements
1. **Java 25** - Required for the AuthServer backend
2. **SQL Server** - Database for storing OAuth2 data
3. **Redis** - Session storage
4. **Maven** - Build tool for Java project

### Frontend Requirements
1. **Node.js 18+** - JavaScript runtime
2. **npm** - Package manager

## Installation Steps

### Step 1: Start the Backend

#### 1.1 Configure Database

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true
spring.datasource.username=your-username
spring.datasource.password=your-password
```

#### 1.2 Configure Redis

Ensure Redis is running on `localhost:6379` or update the properties:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

#### 1.3 Start the Backend

```bash
# From the project root directory
./mvnw spring-boot:run
```

The backend will start on `http://localhost:9080/services`

### Step 2: Configure the Frontend

#### 2.1 Navigate to Frontend Directory

```bash
cd frontend
```

#### 2.2 Install Dependencies

```bash
npm install
```

#### 2.3 Configure Environment Variables

Copy the example environment file:

```bash
cp .env.example .env.local
```

Edit `.env.local`:

```env
# Frontend URL
NEXTAUTH_URL=http://localhost:3000

# Generate a secure random secret (use: openssl rand -base64 32)
NEXTAUTH_SECRET=your-generated-secret-here

# Backend API URL
NEXT_PUBLIC_API_URL=http://localhost:9080/services
```

**Important**: Generate a secure NEXTAUTH_SECRET:
```bash
openssl rand -base64 32
```

### Step 3: Start the Frontend

#### Development Mode

```bash
npm run dev
```

The frontend will be available at `http://localhost:3000`

#### Production Mode

```bash
npm run build
npm run start
```

## First-Time Setup

### 1. Create Initial Organization

After starting the application, you'll need to create at least one organization before you can create applications or users.

1. Log in with backend credentials (configured in `application.properties`):
   - Username: `admin`
   - Password: `admin123`

2. Navigate to **Organizations** → Click **Create Organization**

3. Fill in the organization details:
   - Name: Your organization name
   - Description: Optional description

### 2. Create Your First Application (OAuth2 Client)

1. Navigate to **Applications**
2. Click **Create Application**
3. Configure:
   - Select the organization
   - Client Name: e.g., "My Web App"
   - Application URL: e.g., "https://myapp.example.com"
   - Configure PKCE and Authorization Consent as needed

### 3. Create Users

1. Navigate to **Users**
2. Click **Create User**
3. Fill in user details:
   - Username
   - Password (will be base64 encoded automatically)
   - First Name, Last Name
   - Email
   - Organization

## Features and Usage

### Dashboard
- View metrics for users, applications, and organizations
- Quick stats on active vs. total counts

### Application Management
- **Create/Edit/Delete** OAuth2 client applications
- **Configure URIs**: Redirect URIs and post-logout redirect URIs
- **Token Settings**: Access token TTL, refresh token TTL, etc.
- **Resource Assignment**: Assign API resources to applications
- **Status Management**: Activate/deactivate applications

### User Management
- **Paginated Table**: Server-side pagination with search
- **CRUD Operations**: Create, read, update, delete users
- **Profile Management**: Update name, email, phone number
- **Password Management**: Secure password updates with validation
- **Metadata**: Custom user metadata
- **Status Control**: Activate/deactivate users

### Organization Management
- **DataGrid View**: Advanced table with sorting and filtering
- **CRUD Operations**: Full organization management
- **Contact Management**: Primary and secondary contacts
- **Groups & Permissions**: View organizational groups and their permissions

## Security Features

### 1. Secure Session Management
- **JWT Tokens**: Short-lived (30 min) JWT tokens stored in NextAuth
- **HttpOnly Cookies**: Backend session cookies are httpOnly and secure
- **No LocalStorage**: Sensitive data never stored in browser localStorage

### 2. Authentication Flow
```
User Login → NextAuth → Backend /login → Session Cookie
                ↓
         JWT Token (30min)
                ↓
    All API Requests include session cookie
```

### 3. Request Security
- **CSRF Protection**: Built-in Next.js CSRF tokens
- **Request Headers**: `x-request-datetime` header included
- **Cookie Security**: Secure, HttpOnly, SameSite=Lax
- **CORS**: Properly configured for `localhost:3000`

### 4. Password Handling
- Passwords are base64 encoded before transmission
- Backend performs additional validation and hashing
- Never stored in plain text

## API Integration

The frontend integrates with these backend endpoints:

```
GET    /api/v1/organizations
POST   /api/v1/organizations
GET    /api/v1/organizations/{orgGuid}
PUT    /api/v1/organizations/{orgGuid}
GET    /api/v1/organizations/{orgGuid}/groups
GET    /api/v1/organizations/{orgGuid}/groups/{groupGuid}/permissions

GET    /api/v1/organizations/{orgGuid}/applications
POST   /api/v1/organizations/{orgGuid}/applications
GET    /api/v1/organizations/{orgGuid}/applications/{appGuid}
PUT    /api/v1/organizations/{orgGuid}/applications/{appGuid}
DELETE /api/v1/organizations/{orgGuid}/applications/{appGuid}
PUT    /api/v1/organizations/{orgGuid}/applications/{appGuid}/urls
PUT    /api/v1/organizations/{orgGuid}/applications/{appGuid}/settings

GET    /api/v1/users
POST   /api/v1/users
GET    /api/v1/users/{userGuid}
PUT    /api/v1/users/{userGuid}/profile
PUT    /api/v1/users/{userGuid}/email
PUT    /api/v1/users/{userGuid}/password
PUT    /api/v1/users/{userGuid}/status
DELETE /api/v1/users/{userGuid}
GET    /api/v1/users/{userGuid}/metadata
PUT    /api/v1/users/{userGuid}/metadata
```

## Troubleshooting

### Issue: Cannot connect to backend

**Solution**: Verify backend is running on `http://localhost:9080/services`

```bash
curl http://localhost:9080/services/actuator/health
```

### Issue: Authentication fails

**Possible causes**:
1. Backend not running
2. Wrong credentials
3. Session cookie not being set

**Check**:
- Browser console for errors
- Network tab for `/login` request
- Backend logs for authentication errors

### Issue: CORS errors

**Solution**: Ensure backend CORS configuration includes `http://localhost:3000`:

In `application.properties`:
```properties
endpoints.web.cors.allowed-origins=http://localhost:3000,http://localhost:4200,http://localhost:9080
```

### Issue: Build fails

**Solution**: Clear cache and reinstall:

```bash
rm -rf .next node_modules package-lock.json
npm install
npm run build
```

## Production Deployment

### Environment Variables for Production

```env
NEXTAUTH_URL=https://your-domain.com
NEXTAUTH_SECRET=your-production-secret
NEXT_PUBLIC_API_URL=https://api.your-domain.com
NODE_ENV=production
```

### Security Checklist

- [ ] Generate new NEXTAUTH_SECRET for production
- [ ] Use HTTPS for both frontend and backend
- [ ] Configure proper CORS origins (no wildcards)
- [ ] Enable secure cookies (`secure: true`)
- [ ] Configure CSP headers
- [ ] Set up rate limiting
- [ ] Enable request signing if required
- [ ] Configure proper session timeouts
- [ ] Use environment-specific Redis instance
- [ ] Enable backend security features (signature validation, etc.)

### Build for Production

```bash
npm run build
```

### Deploy

Deploy the `.next` folder and `node_modules` to your server, then:

```bash
npm run start
```

Or use a process manager like PM2:

```bash
pm2 start npm --name "authserver-frontend" -- start
```

## Directory Structure

```
frontend/
├── app/                          # Next.js App Router
│   ├── api/auth/[...nextauth]/   # NextAuth API routes
│   ├── auth/signin/              # Sign-in page
│   ├── dashboard/                # Dashboard
│   ├── applications/             # Application management
│   ├── users/                    # User management
│   ├── organizations/            # Organization management
│   ├── resources/                # Resource library
│   └── credentials/              # Credentials management
├── components/                   # React components
│   ├── layout/                   # Navigation, etc.
│   ├── applications/             # Application components
│   ├── users/                    # User components
│   └── organizations/            # Organization components
├── lib/                          # Utilities
│   ├── api/                      # API client and functions
│   └── types/                    # TypeScript types
├── auth.ts                       # NextAuth configuration
├── middleware.ts                 # Auth middleware
└── .env.local                    # Environment variables (gitignored)
```

## Support and Contributing

For issues or questions:
1. Check the troubleshooting section above
2. Review backend logs in the AuthServer console
3. Check browser console for frontend errors
4. Verify network requests in browser DevTools

## License

Same as the main AuthServer project.
