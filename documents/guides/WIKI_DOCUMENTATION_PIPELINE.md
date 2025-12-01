# GitHub Wiki Documentation Pipeline

This document describes the automated documentation pipeline that generates API documentation from Spring Boot code and syncs it to the GitHub Wiki.

## Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────┐
│  Spring Boot    │───▶│  OpenAPI JSON    │───▶│   Markdown      │───▶│  GitHub Wiki │
│  Controllers    │    │  (springdoc)     │    │  (openapi-gen)  │    │  (CI/CD)     │
└─────────────────┘    └──────────────────┘    └─────────────────┘    └──────────────┘
```

## Pipeline Components

### 1. Source of Truth: Spring Boot Code

The documentation is generated from:
- Controller annotations (`@Operation`, `@Tag`, `@ApiResponse`)
- DTO classes with validation annotations
- OpenAPI configuration in `OpenApiConfig.kt`

### 2. OpenAPI Specification Generation

SpringDoc OpenAPI (already configured) generates:
- JSON spec at `/v3/api-docs`
- YAML spec at `/v3/api-docs.yaml`

### 3. Markdown Conversion

OpenAPI Generator CLI converts the spec to GitHub-flavored Markdown:
- Organized by tags (domains)
- Includes request/response examples
- Generates curl commands

### 4. GitHub Wiki Sync

GitHub Actions workflow:
- Triggers on push to `master` or manual dispatch
- Generates fresh documentation
- Syncs to `<repo>.wiki.git`

## Wiki Page Structure

```
wiki/
├── Home.md                    # Overview and quick navigation
├── Getting-Started.md         # Setup and first API call
├── Authentication.md          # JWT auth flow and examples
├── API-Reference.md           # Complete API index
├── endpoints/
│   ├── Auth-API.md           # Authentication endpoints
│   ├── Channel-API.md        # Channel management
│   ├── Message-API.md        # Messaging endpoints
│   ├── Encryption-API.md     # E2E encryption
│   ├── Notification-API.md   # Push notifications
│   ├── Profile-API.md        # User profiles
│   ├── Admin-API.md          # Admin dashboard
│   └── Health-API.md         # Health checks
├── WebSocket-API.md          # Real-time WebSocket docs
├── Error-Codes.md            # Error response reference
├── Rate-Limiting.md          # Rate limit policies
└── Changelog.md              # API version history
```

## Page Content Guidelines

### Home.md
- Project description
- Key features
- Quick links to important sections
- API base URL and versioning info

### Getting-Started.md
- Prerequisites
- Authentication setup
- First API call example
- Common workflows

### Endpoint Pages (e.g., Auth-API.md)
Each endpoint page contains:

```markdown
# Auth API

Short description of the domain.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/v1/auth/signup | Register new user |
| POST | /api/v1/auth/login | User login |

## POST /api/v1/auth/signup

### Description
Register a new user account.

### Request

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| Content-Type | Yes | application/json |

**Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "nickname": "username"
}
```

### Response

**Success (201):**
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "accessToken": "eyJ..."
  }
}
```

**Error (400):**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid email format"
  }
}
```

### cURL Example
```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"SecurePass123!","nickname":"username"}'
```
```

### Error-Codes.md
- HTTP status code meanings
- Application-specific error codes
- How to handle errors

## File Naming Conventions

| Rule | Example | Reason |
|------|---------|--------|
| Use Title-Case with hyphens | `Auth-API.md` | GitHub Wiki displays filename as page title |
| No spaces in filenames | `Getting-Started.md` | URL compatibility |
| Use `.md` extension | `Home.md` | GitHub Wiki default |
| Prefix with number for ordering | Not needed | GitHub Wiki uses alphabetical, use sidebar |

## Internal Linking

```markdown
<!-- Link to another wiki page -->
See [[Authentication]] for login details.

<!-- Link to specific section -->
See [[Error-Codes#validation-errors]]

<!-- External link -->
See [Spring Boot Docs](https://spring.io/projects/spring-boot)
```

## Maintaining Documentation

### When to Regenerate
- New controller or endpoint added
- Request/Response DTOs modified
- API behavior changes
- Error codes updated

### Automatic Triggers
The CI pipeline regenerates docs on:
- Push to `master` branch
- Pull request merge
- Manual workflow dispatch

### Manual Generation
```bash
# Generate OpenAPI spec
./gradlew generateOpenApiDocs

# Convert to Markdown
./gradlew generateWikiDocs

# Preview locally
open docs/wiki/Home.md
```

## Preventing Pipeline Breaks

1. **Validate OpenAPI spec in CI**
   ```yaml
   - name: Validate OpenAPI
     run: ./gradlew validateOpenApi
   ```

2. **Use schema tests**
   - Test DTOs match expected structure
   - Verify required fields are documented

3. **Documentation as part of PR review**
   - Generated docs included in PR artifacts
   - Review changes before merge

## Integration with Development Workflow

### For Developers
1. Add Swagger annotations when creating endpoints
2. Run `./gradlew generateWikiDocs` locally to preview
3. Commit code changes (docs auto-generated in CI)

### For Reviewers
1. Check Swagger annotations in PR
2. Review generated docs artifact
3. Verify accuracy of examples

## Troubleshooting

### Docs not updating
1. Check GitHub Actions logs
2. Verify OpenAPI spec generates correctly
3. Ensure wiki repo permissions are correct

### Missing endpoints
1. Verify controller has `@RestController`
2. Check `@Tag` annotation exists
3. Ensure not excluded in OpenAPI config

### Broken links
1. Use relative wiki links `[[PageName]]`
2. Avoid hardcoded URLs
3. Run link checker in CI
