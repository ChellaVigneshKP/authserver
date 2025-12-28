# Auth Server Frontend

A modern React-based admin dashboard for managing the Auth Server.

## Features

- **Organization Management**: Create, update, and manage organizations
- **Application Management**: Full CRUD operations for OAuth2 applications/clients
  - Configure redirect URIs and logout URIs
  - Manage token settings
  - Assign resources/endpoints
- **User Management**: Complete user lifecycle management
  - Create and update user profiles
  - Manage user credentials and passwords
  - Update user status and metadata
- **Resource Library**: Manage API resources and endpoints
- **Certificate Management**: Upload and manage SSL/TLS certificates
- **Credential Management**: Manage API keys and authentication tokens

## Tech Stack

- **React 19** with TypeScript
- **Material-UI (MUI)** for UI components
- **React Router** for navigation
- **Axios** for API communication
- **Vite** for fast development and building

## Development

### Prerequisites

- Node.js 18+ and npm

### Setup

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

The development server will start on http://localhost:3000 and proxy API requests to the backend at http://localhost:9080.

### Environment Variables

Create a `.env` file in the frontend directory:

```env
VITE_API_BASE_URL=http://localhost:9080/services/api/v1
```

## Building for Production

```bash
# Build the frontend
npm run build
```

The production build will be output to `../src/main/resources/static/admin` and will be served by the Spring Boot application.

## Project Structure

```
frontend/
├── src/
│   ├── components/       # Reusable UI components
│   │   ├── auth/        # Authentication components
│   │   └── common/      # Common components (Layout, Loading, etc.)
│   ├── pages/           # Page components
│   ├── services/        # API service layer
│   ├── types/           # TypeScript type definitions
│   └── App.tsx          # Main application component
├── public/              # Static assets
└── package.json
```

## API Integration

The frontend communicates with the Spring Boot backend through REST APIs:

- **Organizations**: `/api/v1/organizations`
- **Applications**: `/api/v1/organizations/{orgGuid}/applications`
- **Users**: `/api/v1/users`
- **Resources**: `/api/v1/resources`
- **Certificates**: `/api/v1/organizations/{orgGuid}/certificates`
- **Credentials**: `/api/v1/credentials`

## Authentication

The application uses token-based authentication. After login, the auth token is stored in localStorage and automatically included in all API requests.

## Features by Module

### Organizations
- List all organizations
- Create new organizations
- Update organization details
- Manage primary and secondary contacts
- View organization groups and permissions

### Applications
- List applications per organization
- Create new applications (OAuth2 clients)
- Update application settings
- Configure redirect URIs and logout URIs
- Manage token settings (TTL, refresh token reuse)
- Assign/remove resources

### Users
- Paginated user list
- Create new users with validation
- Update user profiles and emails
- Change user passwords
- Manage user metadata
- Activate/deactivate users

### Resources
- Library of API resources
- Create and manage resource definitions
- Define endpoints and HTTP methods

## Security

- All API requests include authentication headers
- Passwords are base64 encoded before transmission
- Automatic redirect to login on authentication failure
- Form validation on all inputs

