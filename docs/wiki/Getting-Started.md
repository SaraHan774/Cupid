# Getting Started

This guide will help you make your first API call to the Cupid backend.

## Prerequisites

- HTTP client (curl, Postman, or similar)
- The server running locally or access to deployed instance
- Docker (for local database services)

## Step 1: Start the Server

```bash
# Start database services
docker-compose up -d

# Run the application
./gradlew bootRun
```

## Step 2: Health Check

Verify the server is running:

```bash
curl http://localhost:8080/api/v1/health
```

Expected response:
```json
{
  "success": true,
  "data": {
    "status": "UP",
    "timestamp": "2024-01-01T00:00:00Z"
  }
}
```

## Step 3: Create an Account

Register a new user account:

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your@email.com",
    "password": "YourSecurePassword123!",
    "nickname": "yourname"
  }'
```

**Password Requirements:**
- Minimum 12 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one number
- At least one special character

## Step 4: Login

Authenticate to get your JWT token:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your@email.com",
    "password": "YourSecurePassword123!"
  }'
```

Response:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600
  }
}
```

## Step 5: Make Authenticated Requests

Use the `accessToken` in subsequent requests:

```bash
# Get your profile
curl http://localhost:8080/api/v1/profile \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Token Refresh

When your access token expires, use the refresh token:

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

## Using Swagger UI

For interactive API exploration:

1. Open http://localhost:8080/swagger-ui.html
2. Click "Authorize" button
3. Enter your JWT token with "Bearer " prefix
4. Explore and test endpoints

## Next Steps

- [[Authentication]] - Detailed auth flow
- [[API-Reference]] - Full endpoint list
- [[WebSocket-API]] - Real-time features
- [[Encryption-API]] - End-to-end encryption

---

*See [[Home]] for navigation*
