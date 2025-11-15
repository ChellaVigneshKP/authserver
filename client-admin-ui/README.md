# OAuth2 Client Management Admin UI

This is a React-based admin UI for managing OAuth2/OIDC clients in the Spring Authorization Server.

## Features

- View all OAuth2 clients
- Create new OAuth2 clients
- Edit existing clients
- Delete/deactivate clients
- Manage redirect URIs
- Manage post-logout redirect URIs
- Configure token settings
- Rotate client secrets

## Development

### Prerequisites

- Node.js 14+ and npm
- Backend auth server running on http://localhost:9080

### Installation

```bash
npm install
```

### Running locally

```bash
npm start
```

The app will open at http://localhost:3000

### Building for production

```bash
npm run build
```

The production-ready build will be in the `build/` directory.

## Project Structure

```
client-admin-ui/
├── public/
│   └── index.html
├── src/
│   ├── components/      # Reusable React components
│   ├── pages/          # Page components
│   ├── services/       # API service layer
│   ├── App.js          # Main app component
│   ├── App.css         # Global styles
│   └── index.js        # Entry point
├── package.json
└── README.md
```

## Configuration

The backend API proxy is configured in `package.json`:

```json
"proxy": "http://localhost:9080"
```

Update this if your backend runs on a different port.
