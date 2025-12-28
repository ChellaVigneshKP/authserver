# Auth Server - Full Stack Application

This repository contains a comprehensive OAuth2 Authentication Server with a modern React-based admin frontend.

## Project Structure

```
authserver/
├── src/                        # Spring Boot Backend
│   ├── main/
│   │   ├── java/              # Java source code
│   │   └── resources/         # Application resources
│   └── test/                  # Backend tests
├── frontend/                   # React Frontend
│   ├── src/                   # Frontend source code
│   └── package.json          # Frontend dependencies
└── pom.xml                    # Maven configuration
```

## Backend (Spring Boot Auth Server)

### Prerequisites
- Java 25
- Maven 3.x
- SQL Server (for production) or H2 (for development)
- Redis (for session storage)

### Running the Backend

```bash
# Start the Spring Boot application
./mvnw spring-boot:run
```

The backend will start on http://localhost:9080

### Key Backend Features
- OAuth2 Authorization Server
- Multi-factor Authentication (MFA)
- Biometric Authentication
- Single Sign-On (SSO)
- Session Management with Redis
- JWT Token Management
- User & Organization Management APIs
- Application/Client Management
- Certificate Management

## Frontend (React Admin Dashboard)

### Prerequisites
- Node.js 18+ and npm

### Running the Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

The frontend development server will start on http://localhost:3000

### Frontend Features
- **Dashboard** - Overview with key metrics
- **Organization Management** - CRUD operations for organizations
- **Application/Client Management** - Complete OAuth2 client configuration
- **User Management** - Full user lifecycle management
- **Resource Library** - API resource management
- **Certificate Management** - SSL/TLS certificate handling
- **Credential Management** - API keys and tokens

### Frontend Tech Stack
- React 19 with TypeScript
- Material-UI (MUI) for UI components
- React Router for navigation
- Axios for API communication (with security patches)
- Vite for fast development

## Running Both Together

### Development Mode

Terminal 1 - Backend:
```bash
./mvnw spring-boot:run
```

Terminal 2 - Frontend:
```bash
cd frontend
npm run dev
```

Then open http://localhost:3000 in your browser. The frontend will proxy API requests to the backend.

## Default Credentials

Based on application.properties:
- **Username**: admin
- **Password**: admin123

## API Endpoints

The backend exposes REST APIs at `http://localhost:9080/services/api/v1/`:

- `/organizations` - Organization CRUD
- `/organizations/{orgGuid}/applications` - Application/Client CRUD
- `/users` - User management
- `/resources` - Resource library
- `/credentials` - Credential management
- `/organizations/{orgGuid}/certificates` - Certificate management

## Configuration

### Backend Configuration
Edit `src/main/resources/application.properties` to configure:
- Database connection
- Redis connection
- Server ports
- Security settings
- CORS origins

### Frontend Configuration
Edit `frontend/.env` to configure:
- API base URL
- Other environment variables

## Production Build

### Backend
```bash
./mvnw clean package
java -jar target/authserver-0.0.1-SNAPSHOT.jar
```

### Frontend
```bash
cd frontend
npm run build
```

The frontend build will be output to `src/main/resources/static/admin` and can be served by the Spring Boot application.

**Note**: There are currently some TypeScript configuration issues with the production build. The development server works perfectly. The build issue is being resolved.

## Development Workflow

1. Make backend changes in `src/main/java/`
2. Make frontend changes in `frontend/src/`
3. Both support hot reload during development
4. Test changes using the dev servers
5. Commit changes when ready

## Security Notes

- The frontend uses secure axios configuration (version 1.12.0 with security patches)
- All API requests include authentication headers
- Passwords are base64 encoded before transmission
- CORS is configured for development (localhost:3000, localhost:4200, localhost:9080)
- Production deployments should use HTTPS

## Database Setup

The application uses Flyway for database migrations. Migrations are located in `src/main/resources/db/migration/`.

For SQL Server setup:
```sql
CREATE DATABASE AGSAuth;
```

Then configure the connection in application.properties.

## Contributing

When contributing:
1. Make minimal, focused changes
2. Test thoroughly in development mode
3. Update documentation as needed
4. Follow existing code style and patterns

## License

[Add your license information here]
