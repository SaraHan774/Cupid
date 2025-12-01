#!/bin/bash
# =============================================================================
# GitHub Wiki Documentation Generator
# Converts OpenAPI specification to GitHub-flavored Markdown
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OPENAPI_FILE="${PROJECT_ROOT}/build/openapi/openapi.json"
WIKI_DIR="${PROJECT_ROOT}/docs/wiki"
ENDPOINTS_DIR="${WIKI_DIR}/endpoints"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# Check prerequisites
# =============================================================================

check_prerequisites() {
    log_info "Checking prerequisites..."

    if [ ! -f "$OPENAPI_FILE" ]; then
        log_error "OpenAPI specification not found at: $OPENAPI_FILE"
        log_info "Run './gradlew generateOpenApiDocs' first"
        exit 1
    fi

    # Check for jq (JSON processor)
    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed."
        log_info "Install with: brew install jq (macOS) or apt-get install jq (Linux)"
        exit 1
    fi

    log_info "Prerequisites check passed"
}

# =============================================================================
# Create wiki directory structure
# =============================================================================

setup_directories() {
    log_info "Setting up wiki directories..."
    mkdir -p "$WIKI_DIR"
    mkdir -p "$ENDPOINTS_DIR"
}

# =============================================================================
# Extract API information from OpenAPI spec
# =============================================================================

extract_api_info() {
    log_info "Extracting API information..."

    # Extract basic info
    API_TITLE=$(jq -r '.info.title // "API"' "$OPENAPI_FILE")
    API_VERSION=$(jq -r '.info.version // "1.0.0"' "$OPENAPI_FILE")
    API_DESCRIPTION=$(jq -r '.info.description // ""' "$OPENAPI_FILE")

    # Extract server URL
    SERVER_URL=$(jq -r '.servers[0].url // "http://localhost:8080"' "$OPENAPI_FILE")

    # Extract tags (API domains)
    TAGS=$(jq -r '.tags[]?.name // empty' "$OPENAPI_FILE" | sort -u)

    log_info "Found API: $API_TITLE v$API_VERSION"
}

# =============================================================================
# Generate Home.md
# =============================================================================

generate_home() {
    log_info "Generating Home.md..."

    cat > "${WIKI_DIR}/Home.md" << EOF
# ${API_TITLE}

${API_DESCRIPTION}

**Version:** ${API_VERSION}
**Base URL:** \`${SERVER_URL}\`

## Quick Links

- [[Getting-Started]] - Setup and first API call
- [[Authentication]] - JWT authentication guide
- [[API-Reference]] - Complete endpoint reference
- [[Error-Codes]] - Error handling guide
- [[WebSocket-API]] - Real-time features

## API Domains

| Domain | Description |
|--------|-------------|
$(for tag in $TAGS; do
    desc=$(jq -r --arg tag "$tag" '.tags[] | select(.name == $tag) | .description // "API endpoints"' "$OPENAPI_FILE")
    echo "| [[$tag-API]] | $desc |"
done)

## Authentication

Most endpoints require JWT authentication. Include the token in the Authorization header:

\`\`\`bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" ${SERVER_URL}/api/v1/endpoint
\`\`\`

See [[Authentication]] for detailed instructions.

## Quick Start

\`\`\`bash
# 1. Register a new user
curl -X POST ${SERVER_URL}/api/v1/auth/signup \\
  -H "Content-Type: application/json" \\
  -d '{"email":"user@example.com","password":"SecurePass123!","nickname":"user"}'

# 2. Login to get JWT token
curl -X POST ${SERVER_URL}/api/v1/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"email":"user@example.com","password":"SecurePass123!"}'

# 3. Use token for authenticated requests
curl -H "Authorization: Bearer YOUR_TOKEN" ${SERVER_URL}/api/v1/profile
\`\`\`

---

*Documentation auto-generated from OpenAPI specification*
*Last updated: $(date -u +"%Y-%m-%d %H:%M UTC")*
EOF
}

# =============================================================================
# Generate Getting-Started.md
# =============================================================================

generate_getting_started() {
    log_info "Generating Getting-Started.md..."

    cat > "${WIKI_DIR}/Getting-Started.md" << 'EOF'
# Getting Started

This guide will help you make your first API call to the Cupid backend.

## Prerequisites

- HTTP client (curl, Postman, or similar)
- The server running at the base URL

## Step 1: Health Check

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

## Step 2: Create an Account

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

## Step 3: Login

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

## Step 4: Make Authenticated Requests

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

## Next Steps

- [[Authentication]] - Detailed auth flow
- [[API-Reference]] - Full endpoint list
- [[WebSocket-API]] - Real-time features
- [[Encryption-API]] - End-to-end encryption

---

*See [[Home]] for navigation*
EOF
}

# =============================================================================
# Generate Authentication.md
# =============================================================================

generate_authentication() {
    log_info "Generating Authentication.md..."

    cat > "${WIKI_DIR}/Authentication.md" << 'EOF'
# Authentication

The API uses JWT (JSON Web Tokens) for authentication.

## Authentication Flow

```
┌─────────┐                              ┌─────────┐
│ Client  │                              │ Server  │
└────┬────┘                              └────┬────┘
     │                                        │
     │  POST /api/v1/auth/login               │
     │  {email, password}                     │
     │───────────────────────────────────────▶│
     │                                        │
     │  {accessToken, refreshToken}           │
     │◀───────────────────────────────────────│
     │                                        │
     │  GET /api/v1/protected                 │
     │  Authorization: Bearer {accessToken}   │
     │───────────────────────────────────────▶│
     │                                        │
     │  {protected data}                      │
     │◀───────────────────────────────────────│
     │                                        │
```

## Token Types

| Token | Purpose | Expiration |
|-------|---------|------------|
| Access Token | API authentication | 1 hour |
| Refresh Token | Get new access token | 7 days |

## Request Headers

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

## Token Payload

The JWT contains:

```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "roles": ["USER"],
  "iat": 1704067200,
  "exp": 1704070800
}
```

## Error Responses

### 401 Unauthorized

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or expired token"
  }
}
```

### 403 Forbidden

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "Insufficient permissions"
  }
}
```

## Security Best Practices

1. **Store tokens securely** - Use secure storage (Keychain, encrypted preferences)
2. **Don't log tokens** - Avoid printing tokens in logs
3. **Refresh proactively** - Refresh before expiration
4. **Handle expiration** - Redirect to login when refresh fails

---

*See [[Auth-API]] for endpoint details*
EOF
}

# =============================================================================
# Generate API-Reference.md (index of all endpoints)
# =============================================================================

generate_api_reference() {
    log_info "Generating API-Reference.md..."

    cat > "${WIKI_DIR}/API-Reference.md" << EOF
# API Reference

Complete list of all API endpoints organized by domain.

**Base URL:** \`${SERVER_URL}\`

## Endpoints by Domain

EOF

    # Group endpoints by tag
    for tag in $TAGS; do
        echo "### $tag" >> "${WIKI_DIR}/API-Reference.md"
        echo "" >> "${WIKI_DIR}/API-Reference.md"
        echo "| Method | Endpoint | Description |" >> "${WIKI_DIR}/API-Reference.md"
        echo "|--------|----------|-------------|" >> "${WIKI_DIR}/API-Reference.md"

        # Extract paths for this tag
        jq -r --arg tag "$tag" '
            .paths | to_entries[] |
            .key as $path |
            .value | to_entries[] |
            select(.value.tags[]? == $tag) |
            "| \(.key | ascii_upcase) | `\($path)` | \(.value.summary // .value.operationId // "N/A") |"
        ' "$OPENAPI_FILE" >> "${WIKI_DIR}/API-Reference.md" 2>/dev/null || true

        echo "" >> "${WIKI_DIR}/API-Reference.md"
        echo "See [[$tag-API]] for detailed documentation." >> "${WIKI_DIR}/API-Reference.md"
        echo "" >> "${WIKI_DIR}/API-Reference.md"
    done

    cat >> "${WIKI_DIR}/API-Reference.md" << 'EOF'
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
EOF
}

# =============================================================================
# Generate endpoint pages for each tag/domain
# =============================================================================

generate_endpoint_pages() {
    log_info "Generating endpoint pages..."

    for tag in $TAGS; do
        local filename="${ENDPOINTS_DIR}/${tag}-API.md"
        local tag_description=$(jq -r --arg tag "$tag" '.tags[] | select(.name == $tag) | .description // "API endpoints"' "$OPENAPI_FILE")

        log_info "  Generating ${tag}-API.md"

        cat > "$filename" << EOF
# ${tag} API

${tag_description}

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
EOF

        # List endpoints for this tag
        jq -r --arg tag "$tag" '
            .paths | to_entries[] |
            .key as $path |
            .value | to_entries[] |
            select(.value.tags[]? == $tag) |
            "| \(.key | ascii_upcase) | `\($path)` | \(.value.summary // "-") |"
        ' "$OPENAPI_FILE" >> "$filename" 2>/dev/null || true

        echo "" >> "$filename"

        # Generate detailed endpoint documentation
        jq -r --arg tag "$tag" '
            .paths | to_entries[] |
            .key as $path |
            .value | to_entries[] |
            select(.value.tags[]? == $tag) |
            {
                method: .key,
                path: $path,
                summary: .value.summary,
                description: .value.description,
                operationId: .value.operationId,
                parameters: .value.parameters,
                requestBody: .value.requestBody,
                responses: .value.responses
            }
        ' "$OPENAPI_FILE" 2>/dev/null | jq -s '.' > /tmp/endpoints.json

        # Process each endpoint
        python3 - "$filename" "$SERVER_URL" << 'PYTHON_SCRIPT'
import json
import sys

filename = sys.argv[1]
server_url = sys.argv[2]

try:
    with open('/tmp/endpoints.json', 'r') as f:
        endpoints = json.load(f)
except:
    endpoints = []

with open(filename, 'a') as out:
    for ep in endpoints:
        method = ep.get('method', 'GET').upper()
        path = ep.get('path', '/')
        summary = ep.get('summary', '')
        description = ep.get('description', '')

        out.write(f"\n## {method} {path}\n\n")

        if summary:
            out.write(f"**{summary}**\n\n")

        if description:
            out.write(f"{description}\n\n")

        # Parameters
        params = ep.get('parameters', []) or []
        if params:
            out.write("### Parameters\n\n")
            out.write("| Name | In | Type | Required | Description |\n")
            out.write("|------|----|------|----------|-------------|\n")
            for p in params:
                name = p.get('name', '')
                loc = p.get('in', '')
                ptype = p.get('schema', {}).get('type', 'string')
                required = 'Yes' if p.get('required', False) else 'No'
                desc = p.get('description', '-')
                out.write(f"| {name} | {loc} | {ptype} | {required} | {desc} |\n")
            out.write("\n")

        # Request body
        req_body = ep.get('requestBody', {})
        if req_body:
            out.write("### Request Body\n\n")
            content = req_body.get('content', {})
            for content_type, schema in content.items():
                out.write(f"**Content-Type:** `{content_type}`\n\n")
                # Try to get example
                example = schema.get('example') or schema.get('schema', {}).get('example')
                if example:
                    out.write("```json\n")
                    out.write(json.dumps(example, indent=2))
                    out.write("\n```\n\n")

        # Responses
        responses = ep.get('responses', {})
        if responses:
            out.write("### Responses\n\n")
            for code, resp in responses.items():
                desc = resp.get('description', '')
                out.write(f"**{code}** - {desc}\n\n")

        # cURL example
        out.write("### cURL Example\n\n")
        curl_cmd = f"curl -X {method} {server_url}{path}"
        if method in ['POST', 'PUT', 'PATCH']:
            curl_cmd += ' \\\n  -H "Content-Type: application/json"'
        curl_cmd += ' \\\n  -H "Authorization: Bearer YOUR_TOKEN"'
        out.write(f"```bash\n{curl_cmd}\n```\n\n")
        out.write("---\n")
PYTHON_SCRIPT

        # Create symlink in wiki root for easier navigation
        ln -sf "endpoints/${tag}-API.md" "${WIKI_DIR}/${tag}-API.md" 2>/dev/null || \
            cp "$filename" "${WIKI_DIR}/${tag}-API.md"
    done
}

# =============================================================================
# Generate Error-Codes.md
# =============================================================================

generate_error_codes() {
    log_info "Generating Error-Codes.md..."

    cat > "${WIKI_DIR}/Error-Codes.md" << 'EOF'
# Error Codes

This document lists all error codes returned by the API.

## HTTP Status Codes

| Code | Meaning | When Used |
|------|---------|-----------|
| 200 | OK | Successful GET/PUT/PATCH |
| 201 | Created | Successful POST (resource created) |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Invalid request format/parameters |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Valid auth but insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Resource state conflict (duplicate, etc.) |
| 422 | Unprocessable Entity | Validation errors |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server-side error |

## Application Error Codes

### Authentication Errors

| Code | Message | Resolution |
|------|---------|------------|
| `INVALID_CREDENTIALS` | Email or password incorrect | Check credentials |
| `TOKEN_EXPIRED` | JWT has expired | Refresh token |
| `TOKEN_INVALID` | JWT is malformed | Re-authenticate |
| `ACCOUNT_LOCKED` | Too many failed attempts | Wait or contact support |

### Validation Errors

| Code | Message | Resolution |
|------|---------|------------|
| `VALIDATION_ERROR` | Request validation failed | Check request body |
| `INVALID_EMAIL` | Email format invalid | Use valid email |
| `WEAK_PASSWORD` | Password too weak | Use stronger password |
| `MISSING_FIELD` | Required field missing | Include all required fields |

### Resource Errors

| Code | Message | Resolution |
|------|---------|------------|
| `USER_NOT_FOUND` | User doesn't exist | Check user ID |
| `CHANNEL_NOT_FOUND` | Channel doesn't exist | Check channel ID |
| `MESSAGE_NOT_FOUND` | Message doesn't exist | Check message ID |
| `ALREADY_EXISTS` | Resource already exists | Use different identifier |

### Encryption Errors

| Code | Message | Resolution |
|------|---------|------------|
| `KEY_NOT_FOUND` | Encryption key missing | Generate keys first |
| `SESSION_NOT_FOUND` | E2E session missing | Initiate key exchange |
| `DECRYPTION_FAILED` | Cannot decrypt message | Re-establish session |

### Rate Limiting

| Code | Message | Resolution |
|------|---------|------------|
| `RATE_LIMIT_EXCEEDED` | Too many requests | Wait and retry |

## Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description",
    "details": {
      "field": "Additional context"
    }
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

## Handling Errors

### JavaScript/TypeScript

```typescript
try {
  const response = await fetch('/api/v1/endpoint');
  const data = await response.json();

  if (!data.success) {
    switch (data.error.code) {
      case 'TOKEN_EXPIRED':
        await refreshToken();
        // Retry request
        break;
      case 'RATE_LIMIT_EXCEEDED':
        await delay(1000);
        // Retry request
        break;
      default:
        showError(data.error.message);
    }
  }
} catch (error) {
  showError('Network error');
}
```

### Retry Strategy

For rate limits and transient errors:

1. Wait for `Retry-After` header duration
2. Use exponential backoff: 1s, 2s, 4s, 8s
3. Maximum 3 retry attempts
4. Show error after max retries

---

*See [[API-Reference]] for endpoint documentation*
EOF
}

# =============================================================================
# Generate WebSocket-API.md
# =============================================================================

generate_websocket_docs() {
    log_info "Generating WebSocket-API.md..."

    cat > "${WIKI_DIR}/WebSocket-API.md" << 'EOF'
# WebSocket API

Real-time communication via WebSocket using STOMP over SockJS.

## Connection

### Endpoint

```
ws://localhost:8080/ws
```

### JavaScript Client

```javascript
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect(
  { Authorization: 'Bearer YOUR_JWT_TOKEN' },
  (frame) => {
    console.log('Connected:', frame);

    // Subscribe to channels
    stompClient.subscribe('/user/queue/messages', (message) => {
      const data = JSON.parse(message.body);
      console.log('Received:', data);
    });
  },
  (error) => {
    console.error('Connection error:', error);
  }
);
```

## Subscription Channels

| Channel | Purpose | Message Format |
|---------|---------|----------------|
| `/user/queue/messages` | Private messages | `{senderId, content, timestamp}` |
| `/topic/channel/{id}` | Channel messages | `{senderId, channelId, content}` |
| `/user/queue/notifications` | Notifications | `{type, title, body}` |
| `/user/queue/typing` | Typing indicators | `{userId, channelId, isTyping}` |
| `/user/queue/presence` | Online status | `{userId, status, lastSeen}` |

## Message Types

### Chat Message

```json
{
  "type": "MESSAGE",
  "data": {
    "id": "msg-uuid",
    "senderId": "user-uuid",
    "channelId": "channel-uuid",
    "content": "Hello!",
    "encrypted": false,
    "timestamp": "2024-01-01T00:00:00Z"
  }
}
```

### Typing Indicator

```json
{
  "type": "TYPING",
  "data": {
    "userId": "user-uuid",
    "channelId": "channel-uuid",
    "isTyping": true
  }
}
```

### Read Receipt

```json
{
  "type": "READ_RECEIPT",
  "data": {
    "messageId": "msg-uuid",
    "readerId": "user-uuid",
    "readAt": "2024-01-01T00:00:00Z"
  }
}
```

### Presence Update

```json
{
  "type": "PRESENCE",
  "data": {
    "userId": "user-uuid",
    "status": "ONLINE",
    "lastSeen": "2024-01-01T00:00:00Z"
  }
}
```

## Sending Messages

### To Channel

```javascript
stompClient.send('/app/chat/channel/{channelId}', {}, JSON.stringify({
  content: 'Hello channel!',
  encrypted: false
}));
```

### Typing Indicator

```javascript
stompClient.send('/app/typing', {}, JSON.stringify({
  channelId: 'channel-uuid',
  isTyping: true
}));
```

## Error Handling

### Connection Errors

```javascript
stompClient.onWebSocketError = (error) => {
  console.error('WebSocket error:', error);
};

stompClient.onStompError = (frame) => {
  console.error('STOMP error:', frame.headers['message']);
};
```

### Reconnection

```javascript
let reconnectAttempts = 0;
const maxReconnectAttempts = 5;

function connect() {
  // ... connection code ...
}

stompClient.onDisconnect = () => {
  if (reconnectAttempts < maxReconnectAttempts) {
    reconnectAttempts++;
    setTimeout(connect, 1000 * reconnectAttempts);
  }
};
```

## Testing

Use the built-in test page:

```
http://localhost:8080/websocket-test.html
```

---

*See [[Message-API]] for REST messaging endpoints*
EOF
}

# =============================================================================
# Generate Changelog.md
# =============================================================================

generate_changelog() {
    log_info "Generating Changelog.md..."

    cat > "${WIKI_DIR}/Changelog.md" << EOF
# Changelog

API version history and changes.

## Current Version

**Version:** ${API_VERSION}
**Last Updated:** $(date -u +"%Y-%m-%d")

## Version History

### v${API_VERSION} (Current)

- Current stable release
- See [[API-Reference]] for complete endpoint list

### Versioning Policy

The API follows semantic versioning:

- **Major** (v2.x.x): Breaking changes
- **Minor** (v1.x.x): New features, backward compatible
- **Patch** (v1.0.x): Bug fixes

### API Stability

| Stability | Meaning |
|-----------|---------|
| Stable | No breaking changes |
| Beta | May change |
| Deprecated | Will be removed |

### Deprecation Policy

1. Deprecated endpoints remain for 2 major versions
2. Deprecation notices in response headers
3. Migration guides provided

---

*See [[Home]] for navigation*
EOF
}

# =============================================================================
# Generate _Sidebar.md for wiki navigation
# =============================================================================

generate_sidebar() {
    log_info "Generating _Sidebar.md..."

    cat > "${WIKI_DIR}/_Sidebar.md" << EOF
### Navigation

**[Home](Home)**

### Getting Started
- [[Getting-Started]]
- [[Authentication]]

### API Reference
- [[API-Reference]]

### Endpoints
$(for tag in $TAGS; do
    echo "- [[$tag-API]]"
done)

### Guides
- [[WebSocket-API]]
- [[Error-Codes]]

### About
- [[Changelog]]

---

*v${API_VERSION}*
EOF
}

# =============================================================================
# Generate _Footer.md
# =============================================================================

generate_footer() {
    log_info "Generating _Footer.md..."

    cat > "${WIKI_DIR}/_Footer.md" << EOF
---

**${API_TITLE}** v${API_VERSION} | [Swagger UI](${SERVER_URL}/swagger-ui.html) | Auto-generated $(date -u +"%Y-%m-%d")
EOF
}

# =============================================================================
# Main execution
# =============================================================================

main() {
    log_info "Starting wiki documentation generation..."
    log_info "OpenAPI file: $OPENAPI_FILE"
    log_info "Wiki output: $WIKI_DIR"

    check_prerequisites
    setup_directories
    extract_api_info
    generate_home
    generate_getting_started
    generate_authentication
    generate_api_reference
    generate_endpoint_pages
    generate_error_codes
    generate_websocket_docs
    generate_changelog
    generate_sidebar
    generate_footer

    log_info "Wiki documentation generated successfully!"
    log_info "Output directory: $WIKI_DIR"
    log_info ""
    log_info "Files generated:"
    find "$WIKI_DIR" -name "*.md" -type f | sort | while read -r f; do
        echo "  - $(basename "$f")"
    done
}

main "$@"
