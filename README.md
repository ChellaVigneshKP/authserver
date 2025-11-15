# Spring Authorization Server with OAuth2 Client Management UI

[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

A comprehensive Spring Boot-based OAuth2 Authorization Server with a modern React-based admin UI for managing OAuth2/OIDC clients.

## 🚀 Features

### Core Features
- **OAuth2 Authorization Server** - Built on Spring Security OAuth2
- **OIDC Support** - OpenID Connect implementation
- **Multi-tenant** - Organization-based client management
- **Secure Sessions** - Spring Session with JDBC backend
- **Certificate Management** - X.509 certificate handling with Bouncy Castle
- **Flyway Migrations** - Database version control

### 🆕 New: Client Management UI
- **Modern React UI** - Clean, responsive admin interface
- **Full CRUD Operations** - Create, read, update, and delete OAuth2 clients
- **Token Configuration** - Manage access token, refresh token, and auth code TTLs
- **Redirect URI Management** - Add/remove redirect and post-logout URIs
- **Client Secret Rotation** - Secure credential management
- **Real-time Validation** - Form validation and error handling

## 📚 Documentation

- **[Setup Guide](SETUP_GUIDE.md)** - Detailed installation and configuration
- **[Analysis Report](ANALYSIS_REPORT.md)** - Comprehensive code analysis and recommendations
- **[Integration Guide](INTEGRATION_GUIDE.md)** - How to integrate with existing systems

## 🔒 Security Notice

⚠️ **The current API is not authenticated. Implement OAuth2 resource server for production use.**

See [ANALYSIS_REPORT.md](ANALYSIS_REPORT.md#security-considerations) for detailed security recommendations.
