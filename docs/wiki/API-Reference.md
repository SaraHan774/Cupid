# API Reference

Complete list of all API endpoints organized by domain.

**Base URL:** `http://localhost:8080`
**API Version:** v1

## Authentication

### Auth API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/auth/signup` | Register new user | No |
| POST | `/api/v1/auth/login` | User login | No |
| POST | `/api/v1/auth/refresh` | Refresh access token | No |
| POST | `/api/v1/auth/logout` | User logout | Yes |

See [[Auth-API]] for detailed documentation.

## User Management

### Profile API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/profile` | Get current user profile | Yes |
| PUT | `/api/v1/profile` | Update profile | Yes |
| DELETE | `/api/v1/profile` | Delete account | Yes |

See [[Profile-API]] for detailed documentation.

## Messaging

### Channel API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/chat/channels` | List user's channels | Yes |
| POST | `/api/v1/chat/channels` | Create new channel | Yes |
| GET | `/api/v1/chat/channels/{id}` | Get channel details | Yes |
| PUT | `/api/v1/chat/channels/{id}` | Update channel | Yes |
| DELETE | `/api/v1/chat/channels/{id}` | Delete channel | Yes |
| POST | `/api/v1/chat/channels/{id}/members` | Add member | Yes |
| DELETE | `/api/v1/chat/channels/{id}/members/{userId}` | Remove member | Yes |

See [[Channel-API]] for detailed documentation.

### Message API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/chat/messages/{channelId}` | Get channel messages | Yes |
| POST | `/api/v1/chat/messages` | Send message | Yes |
| PUT | `/api/v1/chat/messages/{id}` | Edit message | Yes |
| DELETE | `/api/v1/chat/messages/{id}` | Delete message | Yes |

See [[Message-API]] for detailed documentation.

## Encryption

### Encryption API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/encryption/keys/generate` | Generate encryption keys | Yes |
| GET | `/api/v1/encryption/keys/{userId}` | Get user's public keys | Yes |
| POST | `/api/v1/encryption/key-exchange/initiate` | Start key exchange | Yes |
| GET | `/api/v1/encryption/session/{recipientId}/status` | Check session status | Yes |

See [[Encryption-API]] for detailed documentation.

## Notifications

### Notification API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/notifications` | List notifications | Yes |
| PUT | `/api/v1/notifications/{id}/read` | Mark as read | Yes |
| PUT | `/api/v1/notifications/read-all` | Mark all as read | Yes |
| POST | `/api/v1/notifications/device-token` | Register device token | Yes |

See [[Notification-API]] for detailed documentation.

## Admin

### Admin Dashboard API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/admin/dashboard/keys/statistics` | Key statistics | Admin |
| GET | `/api/v1/admin/dashboard/users/encryption-status` | User encryption status | Admin |
| GET | `/api/v1/admin/dashboard/metrics` | Service metrics | Admin |

See [[Admin-API]] for detailed documentation.

## Health & Monitoring

### Health API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/health` | Health check | No |
| GET | `/actuator/health` | Actuator health | No |
| GET | `/actuator/metrics` | Prometheus metrics | No |

See [[Health-API]] for detailed documentation.

## Response Format

All responses follow this structure:

### Success Response

```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### Error Response

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "details": { ... }
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

---

*See [[Error-Codes]] for error code reference*
*See [[Swagger UI](http://localhost:8080/swagger-ui.html) for interactive documentation*
