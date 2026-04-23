# Azure DevOps PR Dashboard

## Prerequisites

- Java 21+
- Node.js 18+
- Maven

## Setup

### 1. Environment variables

Create a `.env` file in the project root (and in `backend/`):

```
AZURE_DEVOPS_ORG_URL=https://your-org.visualstudio.com/YourProject
AZURE_DEVOPS_PAT=your-personal-access-token
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=your-azure-openai-api-key
```

> `.env` is listed in `.gitignore` — your secrets will not be committed.

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080`

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` with proxy to backend.

### Optional: Proxy

If you need an HTTP proxy, set these in `.env`:

```
PROXY_HOST=10.248.20.80
PROXY_PORT=8080
PROXY_ENABLED=true
```
