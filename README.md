# Auth Server

[![Dependabot Updates](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/ChellaVigneshKP/authserver/actions/workflows/github-code-scanning/codeql)

An OAuth 2.0 and OpenID Connect authorization server built with Spring Boot.

## Quick Start

### Generating Application Secrets

If you've created an OAuth application but need to generate a secret before you can authenticate with the API, see the [Secret Generator Guide](SECRET_GENERATOR_GUIDE.md).

This is particularly useful when:
- You've just set up the server and need to bootstrap authentication
- All API endpoints require authentication but you don't have credentials yet
- You need to generate secrets without accessing the API

### Prerequisites

- Java 25
- Maven 3.x
- SQL Server database
- Redis (for session management)

### Building

```bash
./mvnw clean install
```

### Running

```bash
./mvnw spring-boot:run
```

## Documentation

- [Secret Generator Guide](SECRET_GENERATOR_GUIDE.md) - Generate OAuth secrets from the command line

## License

[Add license information here]
