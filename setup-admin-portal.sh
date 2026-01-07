#!/bin/bash

# Admin Portal Quick Start Script
# This script helps set up the admin portal for first-time use

set -e

echo "======================================"
echo "Auth Server Admin Portal Quick Start"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
echo "Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Java found"

# Check Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}Error: Node.js is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Node.js found ($(node --version))"

# Check if we're in the project root
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: Please run this script from the project root directory${NC}"
    exit 1
fi

echo ""
echo "======================================"
echo "Step 1: Backend Configuration"
echo "======================================"
echo ""

# Check if application.properties exists
if [ ! -f "src/main/resources/application.properties" ]; then
    echo -e "${RED}Error: application.properties not found${NC}"
    exit 1
fi

# Check if admin.portal.initialize is enabled
if grep -q "admin.portal.initialize=true" src/main/resources/application.properties; then
    echo -e "${GREEN}✓${NC} admin.portal.initialize is enabled"
else
    echo -e "${YELLOW}Warning: admin.portal.initialize is not set to true${NC}"
    echo "Do you want to enable it? (y/n)"
    read -r enable_init
    if [ "$enable_init" = "y" ]; then
        echo "admin.portal.initialize=true" >> src/main/resources/application.properties
        echo -e "${GREEN}✓${NC} Enabled admin.portal.initialize"
    fi
fi

echo ""
echo "======================================"
echo "Step 2: Frontend Setup"
echo "======================================"
echo ""

cd frontend

# Install dependencies
if [ ! -d "node_modules" ]; then
    echo "Installing frontend dependencies..."
    npm install
    echo -e "${GREEN}✓${NC} Dependencies installed"
else
    echo -e "${GREEN}✓${NC} Dependencies already installed"
fi

# Check for .env.local
if [ ! -f ".env.local" ]; then
    echo ""
    echo -e "${YELLOW}Creating .env.local file...${NC}"
    echo "You'll need to fill in the values after the backend is running."
    
    cat > .env.local << 'EOF'
# NextAuth Configuration
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=REPLACE_WITH_GENERATED_SECRET

# Auth Server Configuration
AUTHSERVER_ISSUER=http://localhost:9080
AUTHSERVER_CLIENT_ID=REPLACE_WITH_CLIENT_ID
AUTHSERVER_CLIENT_SECRET=REPLACE_WITH_CLIENT_SECRET
AUTHSERVER_API_URL=http://localhost:9080
EOF
    
    # Generate a random secret
    RANDOM_SECRET=$(openssl rand -base64 32 2>/dev/null || echo "PLEASE_GENERATE_A_SECRET")
    
    # Update the .env.local with the generated secret
    if [ "$RANDOM_SECRET" != "PLEASE_GENERATE_A_SECRET" ]; then
        sed -i "s/REPLACE_WITH_GENERATED_SECRET/$RANDOM_SECRET/" .env.local
        echo -e "${GREEN}✓${NC} Generated NEXTAUTH_SECRET"
    fi
    
    echo -e "${GREEN}✓${NC} Created .env.local"
    echo -e "${YELLOW}Note: You still need to add AUTHSERVER_CLIENT_ID and AUTHSERVER_CLIENT_SECRET${NC}"
else
    echo -e "${GREEN}✓${NC} .env.local already exists"
fi

cd ..

echo ""
echo "======================================"
echo "Setup Complete!"
echo "======================================"
echo ""
echo "Next steps:"
echo ""
echo "1. Start the backend:"
echo "   ./mvnw spring-boot:run"
echo ""
echo "2. Get the Admin Portal Client ID from the logs or database:"
echo "   SELECT ClientId FROM [Client].[Application] WHERE [Name] = 'Admin Portal'"
echo ""
echo "3. Generate a client secret using the API (after backend is running):"
echo "   curl -X POST http://localhost:9080/api/v1/applications/{APP_GUID}/secrets"
echo ""
echo "4. Update frontend/.env.local with the Client ID and Secret"
echo ""
echo "5. Create an admin user using the API:"
echo "   curl -X POST http://localhost:9080/api/v1/users \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{...}'"
echo ""
echo "6. Start the frontend:"
echo "   cd frontend && npm run dev"
echo ""
echo "7. Access the admin portal at http://localhost:3000"
echo ""
echo "For detailed instructions, see ADMIN_PORTAL_SETUP.md"
echo ""
