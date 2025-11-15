# OAuth2 Client Management UI - Setup Guide

This guide provides step-by-step instructions to install, configure, run, and test the newly added OAuth2 Client Management UI.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Backend Setup](#backend-setup)
4. [Frontend Setup](#frontend-setup)
5. [Running the Application](#running-the-application)
6. [Testing](#testing)
7. [Production Deployment](#production-deployment)
8. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software

- **Java Development Kit (JDK) 25** or higher
  ```bash
  java -version  # Should show 25 or higher
  ```

- **Node.js 14+** and **npm**
  ```bash
  node --version  # Should show v14 or higher
  npm --version
  ```

- **Maven 3.6+** (or use included `mvnw`)
  ```bash
  mvn --version
  ```

- **SQL Server** (local or remote instance)
  - SQL Server 2019 or higher recommended
  - Or use Docker: `docker run -e "ACCEPT_EULA=Y" -e "SA_PASSWORD=YourStrong@Passw0rd" -p 1433:1433 -d mcr.microsoft.com/mssql/server:2019-latest`

### Optional Tools

- **Git** for version control
- **Postman** or **curl** for API testing
- **VS Code** or **IntelliJ IDEA** for development

## Project Structure

```
authserver/
├── src/main/java/                         # Backend Java code
│   └── com/chellavignesh/authserver/
│       ├── controller/                    # NEW: REST API controllers
│       │   ├── ClientManagementController.java
│       │   └── dto/                       # REST DTOs
│       ├── adminportal/                   # Existing admin logic
│       └── ...
├── client-admin-ui/                       # NEW: React frontend
│   ├── public/
│   ├── src/
│   │   ├── pages/                         # UI pages
│   │   ├── services/                      # API services
│   │   └── ...
│   └── package.json
├── pom.xml                                # Maven configuration
├── ANALYSIS_REPORT.md                     # Code analysis report
└── SETUP_GUIDE.md                         # This file
```

## Backend Setup

### Step 1: Database Configuration

1. **Create Database:**
   ```sql
   CREATE DATABASE AGSAuth;
   GO
   ```

2. **Configure Database Connection:**
   
   Edit `src/main/resources/application.properties`:
   
   ```properties
   # IMPORTANT: Use environment variables for production!
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true
   spring.datasource.username=${DB_USERNAME:acsapp}
   spring.datasource.password=${DB_PASSWORD:Ac$App@123}
   ```

3. **Run Database Migrations:**
   
   Flyway will automatically run migrations on startup, or manually:
   
   ```bash
   ./mvnw flyway:migrate
   ```

### Step 2: Install Custom Dependencies

The project uses a custom library `lib-crypto`. If you encounter build failures:

**Option A: Skip Tests (Quick Start)**
```bash
./mvnw clean install -DskipTests
```

**Option B: Install from Custom Repository**

Add to `pom.xml` if needed:
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/ChellaVigneshKP/lib-crypto</url>
    </repository>
</repositories>
```

### Step 3: Build Backend

```bash
# Clean and compile
./mvnw clean compile

# Or build JAR
./mvnw clean package -DskipTests
```

### Step 4: Verify Backend Build

```bash
# Check if JAR is created
ls -lh target/authserver-*.jar
```

## Frontend Setup

### Step 1: Navigate to Frontend Directory

```bash
cd client-admin-ui
```

### Step 2: Install Dependencies

```bash
npm install
```

This will install:
- React 18.2
- React Router DOM 6.20
- Axios 1.6.2
- React Scripts 5.0.1

**Expected Output:**
```
added 1200+ packages in 30s
```

### Step 3: Verify Frontend Setup

```bash
# Check installed packages
npm list --depth=0
```

## Running the Application

### Development Mode (Recommended for Testing)

#### Terminal 1: Start Backend

```bash
# From project root
./mvnw spring-boot:run
```

**Expected Output:**
```
Started AuthserverApplication in X.XXX seconds
Tomcat started on port(s): 9080 (http)
```

**Backend is now running at:** `http://localhost:9080`

#### Terminal 2: Start Frontend

```bash
# From client-admin-ui directory
npm start
```

**Expected Output:**
```
Compiled successfully!
You can now view client-admin-ui in the browser.
  Local:            http://localhost:3000
```

**Frontend is now running at:** `http://localhost:3000`

The React app will automatically proxy API requests to `http://localhost:9080`

### Production Mode

#### Build Frontend for Production

```bash
cd client-admin-ui
npm run build
```

**Output:** Production-ready files in `client-admin-ui/build/`

#### Serve Frontend with Backend

**Option 1: Copy to Spring Boot static resources**

```bash
# Copy built files to Spring Boot static directory
mkdir -p src/main/resources/static
cp -r client-admin-ui/build/* src/main/resources/static/

# Rebuild backend
./mvnw clean package
```

**Option 2: Use Nginx or Apache**

Serve the `build/` directory with your web server and configure reverse proxy to backend.

## Testing

### Manual Testing Workflow

1. **Open Browser:**
   - Navigate to `http://localhost:3000`
   - You should see "OAuth2 Client Management" header

2. **Test Client List:**
   - Should show empty list or existing clients
   - Click "Create New Client" button

3. **Test Create Client:**
   - Fill in the form:
     - Name: "Test Application"
     - Description: "My test OAuth2 client"
     - Application Type: "Web Application"
     - Auth Method: "Client Secret Basic"
     - Add redirect URI: "http://localhost:3000/callback"
   - Click "Create Client"
   - Should redirect to client details page

4. **Test View Client:**
   - Should display all client details
   - Client ID should be visible
   - Token settings should be shown

5. **Test Edit Client:**
   - Click "Edit Client"
   - Modify description
   - Click "Update Client"
   - Verify changes are saved

6. **Test Delete Client:**
   - Click "Delete Client"
   - Confirm deletion
   - Should redirect to client list
   - Client should be marked as inactive

### API Testing with curl

#### Get All Clients
```bash
curl -X GET http://localhost:9080/api/clients?orgId=1
```

#### Get Client by ID
```bash
curl -X GET http://localhost:9080/api/clients/1
```

#### Create Client
```bash
curl -X POST http://localhost:9080/api/clients?orgId=1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Client",
    "description": "API test client",
    "applicationType": "WEB",
    "authMethod": "CLIENT_SECRET_BASIC",
    "redirectUris": ["http://localhost:3000/callback"],
    "accessTokenTtl": 3600,
    "refreshTokenTtl": 86400
  }'
```

#### Update Client
```bash
curl -X PUT http://localhost:9080/api/clients/1?orgId=1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Client Name",
    "description": "Updated description",
    "applicationType": "WEB",
    "authMethod": "CLIENT_SECRET_BASIC"
  }'
```

#### Delete Client
```bash
curl -X DELETE http://localhost:9080/api/clients/1?orgId=1
```

### API Testing with Postman

1. **Import Collection:**
   - Create new collection "OAuth2 Client Management"
   - Add requests for each endpoint

2. **Environment Variables:**
   ```
   baseUrl: http://localhost:9080
   orgId: 1
   ```

3. **Test Scenarios:**
   - Create client → Get client → Update client → Delete client

### Automated Testing

#### Backend Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=ClientManagementControllerTest
```

**Note:** You'll need to create test classes:

```java
// Example test structure
@SpringBootTest
@AutoConfigureMockMvc
class ClientManagementControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testGetAllClients() throws Exception {
        mockMvc.perform(get("/api/clients?orgId=1"))
            .andExpect(status().isOk());
    }
}
```

#### Frontend Tests

```bash
cd client-admin-ui

# Run tests
npm test

# Run tests with coverage
npm test -- --coverage
```

## Production Deployment

### Option 1: Standalone Deployment

#### Step 1: Build Both Applications

```bash
# Build backend
./mvnw clean package -DskipTests

# Build frontend
cd client-admin-ui
npm run build
cd ..
```

#### Step 2: Deploy Backend

```bash
# Copy JAR to deployment server
scp target/authserver-*.jar user@server:/opt/authserver/

# Run on server
java -jar authserver-*.jar \
  --spring.datasource.url=jdbc:sqlserver://prod-db:1433;databaseName=AGSAuth \
  --spring.datasource.username=$DB_USER \
  --spring.datasource.password=$DB_PASS
```

#### Step 3: Deploy Frontend with Nginx

```nginx
# /etc/nginx/sites-available/authserver-admin
server {
    listen 80;
    server_name admin.authserver.example.com;
    
    root /var/www/authserver-admin;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://localhost:9080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Option 2: Docker Deployment

#### Dockerfile for Backend

```dockerfile
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app
COPY target/authserver-*.jar app.jar
EXPOSE 9080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Dockerfile for Frontend

```dockerfile
# Build stage
FROM node:18-alpine AS build
WORKDIR /app
COPY client-admin-ui/package*.json ./
RUN npm ci
COPY client-admin-ui/ ./
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

#### Docker Compose

```yaml
version: '3.8'
services:
  backend:
    build: .
    ports:
      - "9080:9080"
    environment:
      - DB_URL=jdbc:sqlserver://db:1433;databaseName=AGSAuth
      - DB_USERNAME=sa
      - DB_PASSWORD=${DB_PASSWORD}
    depends_on:
      - db
  
  frontend:
    build:
      context: .
      dockerfile: Dockerfile.frontend
    ports:
      - "80:80"
    depends_on:
      - backend
  
  db:
    image: mcr.microsoft.com/mssql/server:2019-latest
    environment:
      - ACCEPT_EULA=Y
      - SA_PASSWORD=${DB_PASSWORD}
    ports:
      - "1433:1433"
```

**Run with Docker Compose:**

```bash
export DB_PASSWORD=YourSecurePassword123!
docker-compose up -d
```

### Option 3: Kubernetes Deployment

```yaml
# kubernetes/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: authserver-backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: authserver-backend
  template:
    metadata:
      labels:
        app: authserver-backend
    spec:
      containers:
      - name: authserver
        image: authserver:latest
        ports:
        - containerPort: 9080
        env:
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
---
apiVersion: v1
kind: Service
metadata:
  name: authserver-backend
spec:
  selector:
    app: authserver-backend
  ports:
  - port: 9080
    targetPort: 9080
```

## Troubleshooting

### Backend Issues

#### Issue: Database Connection Failed

**Symptoms:**
```
Cannot connect to SQL Server
```

**Solution:**
1. Verify SQL Server is running
2. Check connection string in `application.properties`
3. Verify firewall allows port 1433
4. Test with: `telnet localhost 1433`

#### Issue: Build Fails - Missing Dependency

**Symptoms:**
```
Could not find artifact com.chellavignesh:lib-crypto:jar:1.3.1
```

**Solution:**
```bash
# Build without tests
./mvnw clean package -DskipTests

# Or install dependency locally
# (if you have the JAR file)
mvn install:install-file \
  -Dfile=lib-crypto-1.3.1.jar \
  -DgroupId=com.chellavignesh \
  -DartifactId=lib-crypto \
  -Dversion=1.3.1 \
  -Dpackaging=jar
```

#### Issue: Port 9080 Already in Use

**Solution:**
```bash
# Find process using port
lsof -i :9080

# Kill process
kill -9 <PID>

# Or change port in application.properties
server.port=8080
```

### Frontend Issues

#### Issue: npm install Fails

**Solution:**
```bash
# Clear npm cache
npm cache clean --force

# Remove node_modules and reinstall
rm -rf node_modules package-lock.json
npm install
```

#### Issue: API Requests Fail (CORS)

**Symptoms:**
```
Access to XMLHttpRequest blocked by CORS policy
```

**Solution:**

Verify backend CORS configuration in `ClientManagementController.java`:
```java
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:9080"})
```

Add your domain if deploying to production.

#### Issue: Blank Page After Build

**Solution:**

If deploying to subdirectory, update `package.json`:
```json
{
  "homepage": "/admin"
}
```

### Database Issues

#### Issue: Flyway Migration Failed

**Solution:**
```bash
# Check migration history
./mvnw flyway:info

# Repair if needed
./mvnw flyway:repair

# Re-run migrations
./mvnw flyway:migrate
```

## Security Checklist

Before deploying to production:

- [ ] Change all default passwords
- [ ] Use environment variables for sensitive data
- [ ] Enable HTTPS/TLS
- [ ] Implement authentication for admin UI
- [ ] Configure CORS for production domains only
- [ ] Enable SQL Server encryption
- [ ] Set up database backups
- [ ] Configure firewall rules
- [ ] Enable rate limiting
- [ ] Set up monitoring and logging
- [ ] Review and test security settings

## Next Steps

1. **Implement Authentication:**
   - Add login page to React app
   - Configure OAuth2 resource server
   - Protect API endpoints

2. **Add Monitoring:**
   - Spring Boot Actuator endpoints
   - Application performance monitoring
   - Error tracking (Sentry, Rollbar)

3. **Documentation:**
   - API documentation with Swagger
   - User manual
   - Administrator guide

4. **Testing:**
   - Write unit tests
   - Integration tests
   - E2E tests with Cypress

## Support and Resources

- **Code Repository:** GitHub - ChellaVigneshKP/authserver
- **Spring Boot Docs:** https://spring.io/projects/spring-boot
- **Spring Authorization Server:** https://spring.io/projects/spring-authorization-server
- **React Documentation:** https://react.dev
- **Issues:** Report on GitHub Issues

## Conclusion

You now have a fully functional OAuth2 Client Management UI integrated with the Spring Authorization Server. The setup provides a modern, secure way to manage OAuth2 clients through an intuitive web interface.

For questions or issues, please refer to the `ANALYSIS_REPORT.md` for detailed architecture information and recommendations.
