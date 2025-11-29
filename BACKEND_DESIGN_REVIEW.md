# Backend Design Review: Cupid Chat SDK

## Executive Summary

This review analyzes the Cupid backend against traditional/common Spring Boot backend practices. The codebase demonstrates solid foundational architecture but has several patterns that deviate from conventions and could be improved.

---

## Table of Contents

1. [What's Different from Common Practices](#whats-different-from-common-practices)
2. [Recommended Improvements](#recommended-improvements)
3. [What's Done Well](#whats-done-well)

---

## What's Different from Common Practices

### 1. Dual Error Handling (Anti-pattern)

**Location:** `MessageController.kt:79-101`, `MessageService.kt:40-110`

**Issue:** Both controller and service layers have try-catch blocks, creating redundant error handling.

```kotlin
// Controller has try-catch
fun getChannelMessages(...): ResponseEntity<Map<String, Any>> {
    return try {
        val result = messageService.getChannelMessages(...)  // Service also has try-catch
        if (result.success) { ... }
    } catch (e: Exception) { ... }  // Duplicate handling
}

// Service also wraps everything in try-catch
fun sendMessage(...): ApiResponse<MessageResponse> {
    return try {
        // business logic
    } catch (e: Exception) {
        ApiResponse(false, error = "...")  // Returns error response instead of throwing
    }
}
```

**Standard Practice:**
- Services should throw exceptions for business errors
- `GlobalExceptionHandler` handles all exceptions centrally
- Controllers should be thin with minimal error handling

---

### 2. Service Layer Returns `ApiResponse<T>` (Anti-pattern)

**Location:** All service classes

**Issue:** Services return HTTP-oriented response wrappers (`ApiResponse<T>`) instead of domain objects.

```kotlin
// Current - Service knows about API responses
fun sendMessage(request: SendMessageRequest, senderId: UUID): ApiResponse<MessageResponse>

// Standard Practice - Service returns domain objects
fun sendMessage(request: SendMessageRequest, senderId: UUID): Message
// Or throws BusinessException on failure
```

**Why This Matters:**
- Couples service layer to HTTP/REST concerns
- Makes services harder to reuse (e.g., in WebSocket handlers, scheduled jobs)
- Violates separation of concerns

---

### 3. Untyped Controller Responses

**Location:** `MessageController.kt` (all methods)

**Issue:** Controllers return `ResponseEntity<Map<String, Any>>` instead of typed responses.

```kotlin
// Current - Loses type safety
fun getChannelMessages(...): ResponseEntity<Map<String, Any>> {
    return ResponseEntity.ok(mapOf(
        "success" to true,
        "messages" to result.data
    ))
}

// Standard Practice - Type-safe responses
fun getChannelMessages(...): ResponseEntity<ApiResponse<PagedResponse<MessageResponse>>> {
    return ResponseEntity.ok(ApiResponse.success(result))
}
```

**Impact:**
- No compile-time type checking
- Swagger/OpenAPI documentation is less accurate
- Harder to maintain consistent response structure

---

### 4. Manual Authentication Extraction in Every Method

**Location:** `MessageController.kt:45-53` (repeated 15+ times)

**Issue:** Authentication extraction repeated in every controller method with null checks.

```kotlin
// Current - Repeated in every method
private fun getUserIdFromAuthentication(authentication: Authentication): UUID? {
    return try {
        UUID.fromString(authentication.name)
    } catch (e: Exception) { null }
}

// Then checked manually everywhere:
val userId = getUserIdFromAuthentication(authentication)
if (userId == null) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(...)
}
```

**Standard Practice:** Use `@AuthenticationPrincipal` or custom `HandlerMethodArgumentResolver`:

```kotlin
// With custom resolver
fun getChannelMessages(
    @CurrentUser userId: UUID,  // Injected automatically, throws if invalid
    @PathVariable channelId: UUID
): ResponseEntity<ApiResponse<...>>
```

---

### 5. Manual UUID Path Variable Parsing

**Location:** Throughout controllers

**Issue:** UUID path variables declared as `String`, manually parsed with try-catch.

```kotlin
// Current
@GetMapping("/channels/{channelId}/messages")
fun getChannelMessages(@PathVariable channelId: String, ...) {
    val channelIdUuid = UUID.fromString(channelId)  // Manual parsing
}

// Standard Practice - Let Spring handle it
@GetMapping("/channels/{channelId}/messages")
fun getChannelMessages(@PathVariable channelId: UUID, ...)  // Auto-converted
```

Spring automatically handles the conversion and returns 400 Bad Request for invalid UUIDs.

---

### 6. MongoDB Repository @Query Syntax Issues

**Location:** `MessageRepository.kt:95-113`

**Issue:** The `@Query` annotations use incorrect MongoDB update syntax:

```kotlin
// Current - This won't work correctly
@Query("{ 'id': ?0 }, { '\$set': { 'status': 'DELETED', 'deletedAt': ?1 } }")
fun deleteMessage(messageId: UUID, ...): Void
```

This is update syntax, not query syntax. For updates, you need `MongoTemplate` or `@Update` annotations.

**Standard Practice:**
```kotlin
// Option 1: Use MongoTemplate in a custom repository implementation
@Query("{ '_id': ?0 }")
fun findMessageById(id: UUID): Message?

// Then use MongoTemplate for updates
mongoTemplate.updateFirst(
    Query.query(Criteria.where("_id").`is`(messageId)),
    Update().set("status", MessageStatus.DELETED),
    Message::class.java
)
```

---

### 7. Controller Contains Business Logic

**Location:** `MessageController.kt:403-432`

**Issue:** WebSocket notification logic is in the controller:

```kotlin
// In controller - should be in service
val message = messageService.getMessageById(messageIdUuid, userId)
if (message.success && message.data != null) {
    messagingTemplate.convertAndSendToUser(
        senderId.toString(),
        "/queue/read-receipts",
        readEvent
    )
}
```

**Standard Practice:**
- Controller calls service
- Service handles business logic including notifications
- Or use Spring Events for decoupled notification

---

### 8. Overly Permissive CORS Configuration

**Location:** `SecurityConfig.kt:79-99`

**Issue:** CORS allows all origins with credentials:

```kotlin
configuration.allowedOriginPatterns = listOf("*")
configuration.allowCredentials = true  // This combination is problematic
```

**Standard Practice:**
- Explicitly list allowed origins in production
- Use environment-specific configuration
- Never use `*` with `allowCredentials = true` in production

---

### 9. Missing Validation Exception Handler

**Location:** `GlobalExceptionHandler.kt`

**Issue:** No handler for `MethodArgumentNotValidException` (validation failures).

```kotlin
// Missing - needed for @Valid annotations
@ExceptionHandler(MethodArgumentNotValidException::class)
fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
    val errors = e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
    return ResponseEntity.badRequest().body(ApiResponse(
        success = false,
        errorCode = "VALIDATION_ERROR",
        validationErrors = errors
    ))
}
```

---

### 10. Inconsistent `toResponse()` Placement

**Location:** `MessageService.kt:373-394` vs `AuthService.kt:238-249`

**Issue:** Entity-to-DTO conversion methods (`toResponse()`) are defined in service classes as private extension functions, not on entities or in mappers.

**Standard Practices:**
```kotlin
// Option 1: Extension function in dedicated mapper file
// file: MessageMapper.kt
fun Message.toResponse(): MessageResponse = ...

// Option 2: MapStruct/ModelMapper for complex mappings
@Mapper
interface MessageMapper {
    fun toResponse(message: Message): MessageResponse
}

// Option 3: In DTO companion object (for simple cases)
data class MessageResponse(...) {
    companion object {
        fun from(message: Message): MessageResponse = ...
    }
}
```

---

## Recommended Improvements

### Priority 1: Critical Architecture Fixes

| Issue | Solution | Effort |
|-------|----------|--------|
| Remove try-catch from services | Throw exceptions, let GlobalExceptionHandler handle | Medium |
| Services return domain objects | Remove ApiResponse from service layer | Medium |
| Type controller responses | Change to `ResponseEntity<ApiResponse<T>>` | Low |
| Fix MongoDB @Query | Use MongoTemplate for updates | Medium |

### Priority 2: Code Quality

| Issue | Solution | Effort |
|-------|----------|--------|
| Manual auth extraction | Create `@CurrentUser` resolver | Low |
| UUID path variables | Change to `UUID` type | Low |
| Add validation handler | Implement `MethodArgumentNotValidException` handler | Low |
| Centralize mappers | Create dedicated mapper classes or use MapStruct | Medium |

### Priority 3: Security & Production Readiness

| Issue | Solution | Effort |
|-------|----------|--------|
| CORS configuration | Environment-specific allowed origins | Low |
| PII in logs | Mask or remove user IDs from logs | Low |
| Add idempotency | Implement idempotency keys for POST operations | Medium |

---

## What's Done Well

### Architectural Strengths

1. **Multi-database strategy**: PostgreSQL for relational data, MongoDB for messages, Redis for caching - appropriate technology choices for each use case.

2. **Constructor injection**: Consistently uses constructor-based dependency injection, which is the recommended approach.

3. **Read-only transactions**: Proper use of `@Transactional(readOnly = true)` for query methods.

4. **Rate limiting**: Well-implemented with Bucket4j and Redis, including different limits for different endpoints.

5. **Custom exception hierarchy**: The sealed class `EncryptionException` with specific subtypes is a clean pattern.

6. **OpenAPI documentation**: Comprehensive Swagger annotations on controller methods.

7. **Soft deletes**: Messages use soft delete pattern, preserving data integrity.

8. **Security filter chain**: Proper ordering of JWT and rate limit filters.

9. **Metrics collection**: Prometheus metrics for encryption operations enable observability.

10. **WebSocket with SockJS fallback**: Good pattern for browser compatibility.

---

## Suggested Refactored Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Controller Layer                         │
│  - Thin controllers with @Valid, @CurrentUser                   │
│  - Return ResponseEntity<ApiResponse<T>>                        │
│  - No try-catch (except for very specific cases)                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Service Layer                            │
│  - Returns domain objects                                        │
│  - Throws BusinessException for errors                          │
│  - Contains all business logic including notifications          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Repository Layer                           │
│  - Spring Data repositories for simple CRUD                     │
│  - Custom implementations for complex operations                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    GlobalExceptionHandler                        │
│  - Catches all exceptions                                        │
│  - Maps to consistent ApiResponse format                        │
│  - Collects metrics                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Example Refactored Code

### Before (Current)
```kotlin
// Controller
fun sendMessage(authentication: Authentication, ...): ResponseEntity<Map<String, Any>> {
    val userId = getUserIdFromAuthentication(authentication)
    if (userId == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("success" to false, ...))
    }
    return try {
        val result = messageService.sendMessage(request, userId)
        if (!result.success) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(...))
        }
        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, ...))
    } catch (e: Exception) {
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(...))
    }
}

// Service
fun sendMessage(request: SendMessageRequest, senderId: UUID): ApiResponse<MessageResponse> {
    return try {
        // logic
        ApiResponse(true, data = message.toResponse())
    } catch (e: Exception) {
        ApiResponse(false, error = "...")
    }
}
```

### After (Recommended)
```kotlin
// Controller - thin, delegates everything
@PostMapping("/channels/{channelId}/messages")
fun sendMessage(
    @CurrentUser userId: UUID,
    @PathVariable channelId: UUID,
    @Valid @RequestBody request: SendMessageRequest
): ResponseEntity<ApiResponse<MessageResponse>> {
    val message = messageService.sendMessage(request.copy(channelId = channelId), userId)
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(message.toResponse(), "Message sent successfully"))
}

// Service - returns domain object, throws on error
fun sendMessage(request: SendMessageRequest, senderId: UUID): Message {
    val sender = userRepository.findById(senderId)
        .orElseThrow { UserNotFoundException(senderId) }

    val membership = channelMembersRepository.findByChannelIdAndUserId(request.channelId, senderId)
        ?: throw ChannelAccessDeniedException(request.channelId, senderId)

    if (!membership.isActive) {
        throw ChannelAccessDeniedException(request.channelId, senderId)
    }

    val message = Message(...)
    val savedMessage = messageRepository.save(message)

    // Notification logic stays in service
    notifyChannelMembers(savedMessage)

    return savedMessage
}

// GlobalExceptionHandler handles all exceptions
@ExceptionHandler(UserNotFoundException::class)
fun handleUserNotFound(e: UserNotFoundException) =
    ResponseEntity.status(NOT_FOUND).body(ApiResponse.error(e.message, "USER_NOT_FOUND"))

@ExceptionHandler(ChannelAccessDeniedException::class)
fun handleAccessDenied(e: ChannelAccessDeniedException) =
    ResponseEntity.status(FORBIDDEN).body(ApiResponse.error(e.message, "ACCESS_DENIED"))
```

---

## Conclusion

The Cupid backend has a solid foundation with good technology choices (Spring Boot + Kotlin, multi-database architecture, Signal Protocol E2E encryption). The main areas for improvement are:

1. **Separation of concerns**: Move API response handling out of service layer
2. **Centralized error handling**: Remove duplicate try-catch blocks, rely on GlobalExceptionHandler
3. **Type safety**: Use typed responses instead of Map<String, Any>
4. **Boilerplate reduction**: Use custom resolvers for authentication, proper path variable types

These changes would make the codebase more maintainable, testable, and aligned with Spring Boot best practices.
