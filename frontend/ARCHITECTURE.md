# Frontend Architecture Overview

## Tech Stack

- **React 19**: Latest React with TypeScript for type safety
- **Material-UI (MUI) 7**: Modern UI component library
- **React Router 7**: Client-side routing
- **Axios 1.12.0**: HTTP client with security patches
- **Vite 7**: Fast build tool and dev server

## Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── auth/              # Authentication components
│   │   │   └── Login.tsx      # Login page
│   │   └── common/             # Reusable components
│   │       ├── Layout.tsx      # Main app layout with navigation
│   │       ├── Loading.tsx     # Loading spinner
│   │       └── ErrorDisplay.tsx # Error message display
│   ├── pages/                  # Page components
│   │   ├── Dashboard.tsx       # Dashboard with statistics
│   │   ├── Organizations.tsx   # Organization management
│   │   ├── Applications.tsx    # OAuth2 client management
│   │   ├── Users.tsx           # User management
│   │   ├── Resources.tsx       # Resource library
│   │   ├── Certificates.tsx    # Certificate management
│   │   └── Credentials.tsx     # Credential management
│   ├── services/              # API service layer
│   │   ├── apiClient.ts       # Axios client with interceptors
│   │   ├── authService.ts     # Authentication APIs
│   │   ├── organizationService.ts  # Organization APIs
│   │   ├── applicationService.ts   # Application APIs
│   │   ├── userService.ts     # User APIs
│   │   ├── resourceService.ts # Resource APIs
│   │   ├── certificateService.ts   # Certificate APIs
│   │   └── credentialService.ts    # Credential APIs
│   ├── types/                 # TypeScript type definitions
│   │   └── index.ts           # All type definitions
│   ├── App.tsx                # Main app component with routing
│   └── main.tsx               # Application entry point
├── public/                    # Static assets
├── .env                       # Environment variables
├── vite.config.ts             # Vite configuration
├── tsconfig.json              # TypeScript configuration
└── package.json               # Dependencies and scripts
```

## Key Features

### 1. Authentication & Authorization
- Token-based authentication
- Automatic token injection in API requests
- Redirect to login on 401 errors
- Protected routes with authentication check

### 2. Service Layer Architecture
All API calls go through dedicated service modules:
- Centralized error handling
- Request/response interceptors
- Type-safe API calls
- Automatic authentication header injection

### 3. Reusable Components
- `Layout`: Responsive sidebar navigation
- `Loading`: Consistent loading states
- `ErrorDisplay`: Standardized error messages
- Protected routes for authentication

### 4. Pages with Full CRUD Operations

#### Dashboard
- Overview statistics
- Quick access cards
- Real-time metrics display

#### Organizations
- DataGrid with pagination
- Create/Edit dialog forms
- Organization groups and permissions
- Contact management

#### Applications (OAuth2 Clients)
- Complete client lifecycle management
- URL configuration (redirect & logout URIs)
- Token settings management
- Resource assignment
- Active/Inactive status management

#### Users
- Server-side paginated table
- User creation with validation
- Profile updates
- Password management
- Email updates
- Username changes
- Metadata management
- Status toggle (Active/Inactive)

#### Resources
- API resource library
- Endpoint configuration
- HTTP method specification

#### Certificates & Credentials
- Placeholder pages ready for implementation
- Consistent UI patterns

## API Integration

### Base Configuration
- Dev API: `http://localhost:9080/services/api/v1`
- Configurable via `VITE_API_BASE_URL` environment variable

### API Client Features
```typescript
// Automatic authentication
const token = localStorage.getItem('authToken');
config.headers.Authorization = `Bearer ${token}`;

// Automatic error handling
if (error.response?.status === 401) {
  localStorage.removeItem('authToken');
  window.location.href = '/login';
}
```

### Service Pattern
```typescript
export const organizationService = {
  getAll: async (): Promise<Organization[]> => {...},
  getById: async (id: string): Promise<Organization> => {...},
  create: async (data: Partial<Organization>): Promise<Organization> => {...},
  update: async (id: string, data: Partial<Organization>): Promise<Organization> => {...},
};
```

## Type Safety

All entities have TypeScript interfaces:
- `Organization`
- `Application` / `ApplicationDetail`
- `User`
- `Resource` / `ResourceLibrary`
- `Certificate`
- `Credential`
- `TokenSettings`
- `PaginatedResponse<T>`

## Responsive Design

- Mobile-first approach using Material-UI's Grid system
- Responsive sidebar (drawer) navigation
- Collapsible navigation on small screens
- Touch-friendly UI components

## State Management

Currently using React's built-in state management:
- `useState` for component state
- Async state handling in useEffect
- Loading and error states for all API calls

## Development Server

```bash
npm run dev
```

Features:
- Hot Module Replacement (HMR)
- Fast refresh
- Proxy to backend API
- Source maps for debugging

## Production Build (In Progress)

The application runs perfectly in development mode. There's a TypeScript/Rollup module resolution issue with the production build that needs to be resolved. The build configuration issue is being worked on.

Workaround: Use the development server for now, or manually copy built assets.

## Security Considerations

1. **Axios Security**: Updated to version 1.12.0 to patch vulnerabilities
2. **Password Handling**: Base64 encoded before transmission
3. **Token Storage**: localStorage (consider httpOnly cookies for production)
4. **CORS**: Configured for development environments
5. **Form Validation**: Client-side validation on all forms

## Future Enhancements

Potential improvements:
1. Complete Certificate management UI
2. Complete Credential management UI
3. Add form validation library (e.g., React Hook Form + Yup)
4. Add state management library if needed (Redux, Zustand)
5. Add end-to-end tests (Cypress, Playwright)
6. Add unit tests (Jest, React Testing Library)
7. Implement caching strategies
8. Add WebSocket support for real-time updates
9. Resolve TypeScript build configuration issues

## Best Practices

- All API calls are type-safe
- Consistent error handling across the app
- Loading states for better UX
- Confirmation dialogs for destructive actions
- Responsive design patterns
- Material-UI theming for consistent look
- Code splitting via React Router
- Environment-based configuration

## Troubleshooting

### Common Issues

**Port 3000 already in use:**
```bash
# Kill the process using port 3000
lsof -ti:3000 | xargs kill -9
```

**API connection refused:**
- Ensure backend is running on port 9080
- Check CORS configuration in backend
- Verify API base URL in .env file

**Module resolution errors in build:**
- Currently being resolved
- Use development server instead

## Scripts

```json
{
  "dev": "vite",              // Start dev server
  "build": "vite build",      // Production build
  "type-check": "tsc -b",     // Type checking only
  "preview": "vite preview",  // Preview production build
  "lint": "eslint ."          // Run ESLint
}
```
