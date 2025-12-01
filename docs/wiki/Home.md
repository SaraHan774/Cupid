# Cupid - Real-time Chat Application

Spring Boot 3.5 + Kotlin 기반의 실시간 채팅 플랫폼

**Version:** 0.0.1-SNAPSHOT
**Base URL:** `http://localhost:8080`

## Quick Links

- [[Getting-Started]] - Setup and first API call
- [[Authentication]] - JWT authentication guide
- [[API-Reference]] - Complete endpoint reference
- [[Error-Codes]] - Error handling guide
- [[WebSocket-API]] - Real-time features

## API Domains

| Domain | Description |
|--------|-------------|
| [[Auth-API]] | Authentication and user registration |
| [[Channel-API]] | Chat channel management |
| [[Message-API]] | Messaging endpoints |
| [[Encryption-API]] | End-to-end encryption (Signal Protocol) |
| [[Notification-API]] | Push notification management |
| [[Profile-API]] | User profile management |
| [[Admin-API]] | Admin dashboard and monitoring |
| [[Health-API]] | Health checks and monitoring |

## Authentication

Most endpoints require JWT authentication. Include the token in the Authorization header:

```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" http://localhost:8080/api/v1/endpoint
```

See [[Authentication]] for detailed instructions.

## Quick Start

```bash
# 1. Register a new user
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"SecurePass123!","nickname":"user"}'

# 2. Login to get JWT token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"SecurePass123!"}'

# 3. Use token for authenticated requests
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/api/v1/profile
```

## Features

- Real-time messaging via WebSocket (STOMP over SockJS)
- End-to-end encryption using Signal Protocol
- Push notifications via Firebase
- Rate limiting and security controls
- Comprehensive admin dashboard

## Interactive Documentation

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **WebSocket Test:** http://localhost:8080/websocket-test.html

---

*Documentation auto-generated from OpenAPI specification*
