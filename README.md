[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

# Auth Server

OAuth2 Authorization Server with administrative capabilities.

## Quick Start

### Server Initialization

For first-time setup, you can automatically create the initial admin user, organization, and client application:

```bash
export INITIALIZE_SERVER=true
./mvnw spring-boot:run
```

This will create:
- Default admin user with username: `chella` and password: `Admin@123456`
- Default organization
- Default client application
- Default external source (branding)

**⚠️ Important**: Change the default admin password immediately after first login!

For more details, see [Server Initialization Guide](src/main/java/com/chellavignesh/authserver/init/README.md).
