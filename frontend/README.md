# AuthServer Admin Frontend

A modern, secure admin portal for managing OAuth2 clients, users, and organizations in the AuthServer.

## Features

### 🎯 Core Management Features

- **Dashboard**: Overview with key metrics and statistics
- **Application Management** (`/applications`):
  - Full CRUD operations for OAuth2 clients
  - Configure redirect URIs and post-logout redirect URIs
  - Token settings management (TTL, refresh tokens, etc.)
  - Resource assignment
  - Status management (active/inactive)

- **User Management** (`/users`):
  - Server-side paginated table with search
  - Create, read, update, delete users
  - Profile management (name, email, phone)
  - Password updates
  - Metadata management
  - Status control

- **Organization Management** (`/organizations`):
  - Full CRUD with DataGrid
  - Primary and secondary contact management
  - View groups and permissions
  - Status management

- **Resource Library** (`/resources`):
  - API resource management (planned)

- **Certificates & Credentials** (`/credentials`):
  - Certificate management interfaces (planned)

### 🔒 Security Features

- **Session Management**: Uses Next-Auth v5 with JWT strategy
- **Secure Cookie Storage**: Backend session cookies stored securely (httpOnly, secure in production)
- **No LocalStorage**: Sensitive data never stored in browser localStorage
- **CSRF Protection**: Built-in with Next.js and proper session handling
- **Request Validation**: Includes x-request-datetime headers as per backend requirements
- **CORS Compliance**: Configured to work with backend CORS policies

## Tech Stack

- **Next.js 14**: React framework with App Router
- **TypeScript**: Type safety throughout
- **NextAuth.js v5**: Authentication and session management
- **Material-UI (MUI)**: Modern, accessible UI components
- **TanStack React Query**: Data fetching and caching
- **Axios**: HTTP client with interceptors
- **MUI DataGrid**: Advanced data tables with server-side pagination

## Getting Started

### Prerequisites

- Node.js 18+ and npm
- AuthServer backend running on `http://localhost:9080/services`

### Installation

```bash
# Install dependencies
npm install

# Copy environment template
cp .env.example .env.local

# Edit .env.local with your configuration
# NEXTAUTH_SECRET should be a random string (generate with: openssl rand -base64 32)
```

### Configuration

Edit `.env.local`:

```env
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-randomly-generated-secret-key
NEXT_PUBLIC_API_URL=http://localhost:9080/services
```

### Development

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Production Build

```bash
npm run build
npm run start
```

## Authentication Flow

1. User accesses protected route → redirected to `/auth/signin`
2. User enters credentials
3. NextAuth calls backend `/login` endpoint with credentials
4. Backend validates and returns session cookie
5. Session cookie stored securely in httpOnly cookie
6. JWT token stored in NextAuth session
7. All API calls include session cookie automatically

## Security Best Practices Implemented

1. **No localStorage**: Tokens never stored in localStorage
2. **HttpOnly Cookies**: Session cookies are httpOnly and secure
3. **JWT Strategy**: Short-lived JWT tokens (30 min)
4. **CSRF Protection**: Built into Next.js forms and NextAuth
5. **Secure Headers**: CSP and security headers configured
6. **Input Validation**: All forms validated on client and server
7. **Password Handling**: Passwords base64 encoded as per backend requirement
