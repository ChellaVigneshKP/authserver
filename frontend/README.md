# Auth Server Admin Portal - Frontend

Modern Next.js-based admin portal for managing the Auth Server OAuth2/OIDC infrastructure.

## Features

- 🔐 **OAuth2 Authentication**: Seamless integration with the Auth Server using NextAuth.js
- 🏢 **Organization Management**: Create and manage partner organizations
- ⚙️ **Application Management**: Configure OAuth2 clients, redirect URIs, scopes, and settings
- 👥 **User Management**: Create users, assign permissions, and manage credentials
- 🎨 **Modern UI**: Clean, responsive interface built with React and CSS modules
- 🔄 **Real-time Updates**: SWR for efficient data fetching and caching
- 🛡️ **Type Safe**: Full TypeScript support

## Tech Stack

- **Framework**: Next.js 14+ (App Router)
- **Authentication**: NextAuth.js v5
- **Language**: TypeScript
- **Data Fetching**: SWR + Axios
- **Styling**: CSS Modules
- **Icons**: React Icons

## Getting Started

### Prerequisites

- Node.js 18+ and npm
- Running Auth Server instance
- Admin Portal OAuth2 client configured in the Auth Server

### Installation

1. Install dependencies:

```bash
npm install
```

2. Create `.env.local` file:

```env
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-secret-key
AUTHSERVER_ISSUER=http://localhost:9080
AUTHSERVER_CLIENT_ID=your-client-id
AUTHSERVER_CLIENT_SECRET=your-client-secret
AUTHSERVER_API_URL=http://localhost:9080
```

3. Start development server:

```bash
npm run dev
```

The application will be available at `http://localhost:3000`.

### Building for Production

```bash
npm run build
npm start
```

## Project Structure

```
frontend/
├── src/
│   ├── app/                    # Next.js App Router pages
│   │   ├── api/               # API routes
│   │   │   └── auth/          # NextAuth configuration
│   │   ├── auth/              # Authentication pages
│   │   │   ├── signin/        # Sign in page
│   │   │   └── error/         # Error page
│   │   ├── dashboard/         # Main dashboard
│   │   │   ├── organizations/ # Organization management
│   │   │   ├── applications/  # Application management
│   │   │   └── users/         # User management
│   │   ├── layout.tsx         # Root layout
│   │   ├── page.tsx           # Home page (redirects to dashboard)
│   │   └── globals.css        # Global styles
│   ├── lib/                   # Shared utilities
│   │   ├── auth.ts           # NextAuth configuration
│   │   └── api.ts            # API client and helpers
│   ├── types/                 # TypeScript type definitions
│   │   ├── api.ts            # API response types
│   │   └── next-auth.d.ts    # NextAuth type extensions
│   └── components/            # Reusable components (future)
├── public/                    # Static assets
├── .env.example              # Environment variable template
├── next.config.js            # Next.js configuration
├── tsconfig.json             # TypeScript configuration
└── package.json              # Dependencies and scripts
```

## Features in Detail

### Authentication Flow

The portal uses OAuth2 Authorization Code flow:

1. User clicks "Sign in with Auth Server"
2. Redirected to Auth Server login page
3. User authenticates with credentials
4. Auth Server redirects back with authorization code
5. NextAuth exchanges code for access token
6. Token stored in secure HTTP-only cookie
7. Auto-refresh when token expires

### API Integration

All API calls go through the centralized API client (`src/lib/api.ts`):

- Automatic token injection
- Error handling and retry logic
- Type-safe request/response handling

### State Management

- **SWR**: Client-side data fetching with automatic revalidation
- **React State**: Local component state for forms and modals
- **NextAuth Session**: Authentication state management

## Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm start` - Start production server
- `npm run lint` - Run ESLint

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `NEXTAUTH_URL` | Frontend URL | Yes |
| `NEXTAUTH_SECRET` | Secret for signing tokens | Yes |
| `AUTHSERVER_ISSUER` | Auth Server base URL | Yes |
| `AUTHSERVER_CLIENT_ID` | OAuth2 Client ID | Yes |
| `AUTHSERVER_CLIENT_SECRET` | OAuth2 Client Secret | Yes |
| `AUTHSERVER_API_URL` | API base URL | No (defaults to issuer) |

## Security

### Production Checklist

- [ ] Use HTTPS for all URLs
- [ ] Generate secure NEXTAUTH_SECRET (32+ characters)
- [ ] Rotate client secrets regularly
- [ ] Enable Content Security Policy
- [ ] Implement rate limiting
- [ ] Use secure session cookies
- [ ] Enable CORS only for specific domains
- [ ] Implement proper error handling (no sensitive data in errors)

### Best Practices

1. Never commit `.env.local` files
2. Use environment-specific configurations
3. Implement proper logout handling
4. Monitor authentication failures
5. Log security events

## Troubleshooting

### Common Issues

**Issue**: Login redirects to error page with "Configuration" error

**Solution**: Verify `AUTHSERVER_ISSUER` has the `.well-known/openid-configuration` endpoint available.

**Issue**: API calls return 401 Unauthorized

**Solution**: Check that the access token is being sent correctly and hasn't expired.

**Issue**: CORS errors in browser console

**Solution**: Ensure frontend URL is in the Auth Server's CORS allowed origins.

## Future Enhancements

- [ ] Advanced filtering and search
- [ ] Bulk operations
- [ ] Activity logs and audit trail
- [ ] Role-based access control UI
- [ ] Client secret rotation
- [ ] Application settings editor
- [ ] User profile management
- [ ] Dashboard analytics
- [ ] Dark mode

## Contributing

This is part of the Auth Server project. Follow the main repository's contribution guidelines.

## License

Same as the Auth Server project.
