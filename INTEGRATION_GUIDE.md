# Integration Guide - OAuth2 Client Management UI

This guide explains how to integrate the new Client Management UI with your existing Spring Authorization Server.

## Overview

The Client Management module consists of:
1. **Backend REST API** - Controllers and DTOs in `src/main/java/.../controller/`
2. **React Frontend** - Standalone React app in `client-admin-ui/`
3. **Integration Points** - Connects to existing services and repositories

## Architecture Integration

```
┌─────────────────────────────────────────────────────────────┐
│                     React Frontend                          │
│                   (client-admin-ui/)                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │  List    │  │  Create  │  │   Edit   │  │  Detail  │  │
│  │  Clients │  │  Client  │  │  Client  │  │  Client  │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
│        │              │              │              │       │
└────────┼──────────────┼──────────────┼──────────────┼───────┘
         │              │              │              │
         └──────────────┴──────────────┴──────────────┘
                        │ HTTP REST API
                        │ (CORS enabled)
                        ▼
┌─────────────────────────────────────────────────────────────┐
│               Backend REST API Layer (NEW)                  │
│                ClientManagementController                   │
│  ┌────────────────────────────────────────────────────────┐│
│  │ GET /api/clients      - List all clients              ││
│  │ POST /api/clients     - Create client                 ││
│  │ PUT /api/clients/{id} - Update client                 ││
│  │ DELETE /api/clients   - Delete client                 ││
│  └────────────────────────────────────────────────────────┘│
└─────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│              Existing Business Layer                        │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐│
│  │ Application     │  │ TokenSettings    │  │ Redirect   ││
│  │ Service         │  │ Service          │  │ UriService ││
│  └─────────────────┘  └──────────────────┘  └────────────┘│
└─────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│              Existing Repository Layer                      │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐│
│  │ Application     │  │ TokenSettings    │  │ Redirect   ││
│  │ Repository      │  │ Repository       │  │ UriRepo    ││
│  └─────────────────┘  └──────────────────┘  └────────────┘│
└─────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│                    SQL Server Database                      │
│               (Existing Stored Procedures)                  │
└─────────────────────────────────────────────────────────────┘
```

## Integration Steps

### 1. Backend Integration

The REST API controller integrates seamlessly with existing services:

#### ApplicationService
- `create()` - Used for creating new clients
- Already handles client creation logic

#### ApplicationRepository
- `getAll()` - List all clients
- `getById()` - Get client by ID
- `getApplicationDetailById()` - Get detailed client info
- `updateApplication()` - Update client
- `inactivateApplication()` - Delete/deactivate client
- `getApplicationRedirectUris()` - Get redirect URIs
- `getApplicationLogoutRedirectUris()` - Get logout URIs

#### TokenSettingsService
- `getTokenSettingsByApplicationId()` - Get token settings
- `updateTokenSettings()` - Update token settings

#### RedirectUriService
- `createRedirectUri()` - Add redirect URI
- `deleteRedirectUri()` - Remove redirect URI

### 2. API Endpoints Integration

All endpoints are under `/api/clients` prefix:

```java
@RestController
@RequestMapping("/api/clients")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:9080"})
public class ClientManagementController {
    // Autowired services
}
```

**CORS Configuration:**
- Configured for local development
- Update for production domains

### 3. Frontend Integration

The React app is completely standalone and communicates via REST API:

```javascript
// client-admin-ui/src/services/clientService.js
const API_BASE_URL = '/api/clients';

// Proxied to backend via package.json
"proxy": "http://localhost:9080"
```

## Configuration Changes Needed

### For Development

**No configuration changes needed!** The new code:
- Uses existing services and repositories
- No database schema changes
- No breaking changes to existing functionality

### For Production

1. **Update CORS Origins:**

```java
// In ClientManagementController.java
@CrossOrigin(origins = {"https://your-production-domain.com"})
```

2. **Configure Frontend Build:**

```json
// client-admin-ui/package.json
{
  "proxy": "https://your-backend-api.com"
}
```

Or build and serve with Nginx:

```nginx
location /api/ {
    proxy_pass http://backend-server:9080;
}

location / {
    root /var/www/client-admin-ui;
    try_files $uri /index.html;
}
```

## Security Integration

### Current State
- API endpoints are **not authenticated**
- Suitable for development and internal networks only

### Production Security (Recommended)

#### Option 1: OAuth2 Resource Server

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/clients/**").hasAuthority("SCOPE_admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            );
        return http.build();
    }
}
```

#### Option 2: Basic Authentication

```java
http
    .securityMatcher("/api/**")
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/clients/**").hasRole("ADMIN")
        .anyRequest().authenticated()
    )
    .httpBasic(Customizer.withDefaults());
```

### Frontend Authentication

Add authentication to React app:

```javascript
// src/services/authService.js
export const getAuthToken = () => {
    return localStorage.getItem('access_token');
};

// src/services/clientService.js
axios.interceptors.request.use(config => {
    const token = getAuthToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});
```

## Database Integration

**No database changes required!**

The REST API uses existing:
- Tables (via existing repositories)
- Stored procedures (Client.CreateApplication, etc.)
- Schema (no new tables needed)

## Testing Integration

### Backend Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class ClientManagementIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ApplicationRepository applicationRepository;
    
    @Test
    @Transactional
    void testCreateAndGetClient() throws Exception {
        // Create client
        String jsonRequest = """
            {
                "name": "Test Client",
                "applicationType": "WEB",
                "authMethod": "CLIENT_SECRET_JWT"
            }
            """;
        
        MvcResult result = mockMvc.perform(post("/api/clients?orgId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isCreated())
            .andReturn();
        
        // Parse response and get client
        ClientResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), 
            ClientResponse.class
        );
        
        mockMvc.perform(get("/api/clients/" + response.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test Client"));
    }
}
```

### Frontend Tests

```javascript
// client-admin-ui/src/pages/__tests__/ClientList.test.js
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import ClientList from '../ClientList';
import clientService from '../../services/clientService';

jest.mock('../../services/clientService');

test('renders client list', async () => {
    clientService.getAllClients.mockResolvedValue({
        data: [
            { id: 1, name: 'Test Client', clientId: 'test-123' }
        ]
    });
    
    render(
        <BrowserRouter>
            <ClientList />
        </BrowserRouter>
    );
    
    await waitFor(() => {
        expect(screen.getByText('Test Client')).toBeInTheDocument();
    });
});
```

## Deployment Integration

### Option 1: Separate Deployment

**Backend:**
```bash
java -jar authserver.jar
```

**Frontend (with Nginx):**
```nginx
server {
    listen 80;
    server_name admin.example.com;
    
    root /var/www/client-admin-ui;
    index index.html;
    
    location /api/ {
        proxy_pass http://backend:9080;
    }
    
    location / {
        try_files $uri /index.html;
    }
}
```

### Option 2: Bundled Deployment

1. **Build frontend:**
```bash
cd client-admin-ui
npm run build
```

2. **Copy to Spring Boot resources:**
```bash
mkdir -p src/main/resources/static/admin
cp -r client-admin-ui/build/* src/main/resources/static/admin/
```

3. **Update Spring configuration:**
```properties
# application.properties
spring.web.resources.static-locations=classpath:/static/
```

4. **Build backend:**
```bash
./mvnw clean package
```

5. **Deploy single JAR:**
```bash
java -jar authserver.jar
```

Access UI at: `http://localhost:9080/admin/`

### Option 3: Docker Compose

```yaml
version: '3.8'
services:
  backend:
    build: .
    ports:
      - "9080:9080"
    environment:
      - DB_URL=${DB_URL}
      - DB_USERNAME=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
  
  frontend:
    build:
      context: ./client-admin-ui
    ports:
      - "3000:80"
    depends_on:
      - backend
    environment:
      - BACKEND_URL=http://backend:9080
```

## Monitoring Integration

### Backend Metrics

Use Spring Boot Actuator (already included):

```properties
# application.properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized
```

Access metrics:
- Health: `http://localhost:9080/actuator/health`
- Metrics: `http://localhost:9080/actuator/metrics`

### Frontend Monitoring

Add error tracking:

```javascript
// src/index.js
import * as Sentry from '@sentry/react';

if (process.env.NODE_ENV === 'production') {
    Sentry.init({
        dsn: 'your-sentry-dsn',
        integrations: [new Sentry.BrowserTracing()],
        tracesSampleRate: 1.0,
    });
}
```

## Migration Guide

### From No Admin UI → With Admin UI

1. **Deploy backend changes** (no breaking changes)
2. **Test REST API endpoints** with Postman/curl
3. **Deploy frontend** separately or bundled
4. **Train users** on new UI
5. **Monitor for issues**

### Rollback Plan

If issues occur:
1. Frontend can be taken down (backend API still works)
2. Backend changes are additive only (no breaking changes)
3. Remove REST controller if needed:
   ```bash
   # Remove controller class and rebuild
   rm src/main/java/.../controller/ClientManagementController.java
   ./mvnw clean package
   ```

## Troubleshooting Integration

### Issue: API 404 Not Found

**Cause:** Controller not loaded by Spring

**Solution:**
```java
// Verify @RestController is present
// Verify package is scanned:
@SpringBootApplication(scanBasePackages = "com.chellavignesh.authserver")
```

### Issue: CORS Error

**Cause:** Frontend domain not in CORS origins

**Solution:**
```java
@CrossOrigin(origins = {"http://localhost:3000", "your-domain.com"})
```

### Issue: Database Access Error

**Cause:** Missing enum initialization

**Solution:** Enums are loaded on startup. Verify database has enum tables populated.

### Issue: Service Autowiring Failed

**Cause:** Missing dependencies

**Solution:**
```bash
# Rebuild with all dependencies
./mvnw clean install
```

## Best Practices

1. **Version Control:**
   - Keep frontend and backend in same repository
   - Tag releases consistently

2. **API Versioning:**
   - Consider adding `/v1/` to API paths for future versions
   - Example: `/api/v1/clients`

3. **Configuration Management:**
   - Use environment variables for sensitive data
   - Document all configuration options

4. **Logging:**
   - Log all client modifications
   - Include user context in logs

5. **Backup:**
   - Regular database backups
   - Test restore procedures

## Next Steps

1. **Add Authentication:**
   - Implement OAuth2 or JWT authentication
   - Secure all endpoints

2. **Add Audit Logging:**
   - Track who creates/modifies clients
   - Store audit trail in database

3. **Enhance UI:**
   - Add search/filter functionality
   - Add bulk operations
   - Add export/import functionality

4. **Performance:**
   - Add caching for frequently accessed data
   - Optimize database queries
   - Implement pagination

5. **Documentation:**
   - Add Swagger/OpenAPI docs
   - Create user manual
   - Document API contracts

## Support

For integration issues:
1. Check logs in `logs/application.log`
2. Review browser console for frontend errors
3. Test API endpoints with curl/Postman
4. Verify database connectivity
5. Check CORS configuration

## Conclusion

The Client Management UI integrates seamlessly with the existing Spring Authorization Server infrastructure. No database or schema changes are required, and the REST API is built on top of existing services, ensuring consistency and maintainability.
