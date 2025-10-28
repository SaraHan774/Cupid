# WebSocket Connection Troubleshooting Guide

## Problem Statement
WebSocket connections were failing with the error: "Whoops! Lost connection to http://localhost:8080/ws?userId=test-user-1"

## Systematic Troubleshooting Approach

### Step 1: Identify the Scope of the Problem
**Action:** Review all WebSocket-related configuration files
```bash
# Files examined:
- src/main/kotlin/com/august/cupid/config/WebSocketConfig.kt
- src/main/kotlin/com/august/cupid/websocket/ConnectionInterceptor.kt
- src/main/kotlin/com/august/cupid/websocket/WebSocketEventListener.kt
- src/main/kotlin/com/august/cupid/websocket/WebSocketMessageHandler.kt
- src/main/kotlin/com/august/cupid/config/SecurityConfig.kt
```

**Finding:** Code structure looked complete but configuration was incomplete.

### Step 2: Enable Debug Logging
**Action:** Modified `application.yml` to enable WebSocket and STOMP logging
```yaml
logging:
  level:
    root: INFO
    com.august.cupid: DEBUG
    org.springframework.web.socket: DEBUG  # Added
    org.springframework.messaging: DEBUG   # Added
```

**Result:** This revealed detailed error messages in the logs.

### Step 3: Analyze Application Startup Logs
**Action:** Started the application and checked logs for errors
```bash
./gradlew bootRun
# Then monitored logs for ERROR or WARN messages
```

**Finding:** Discovered three critical issues in sequence.

---

## Root Causes and Solutions

### Issue #1: ConnectionInterceptor Not Registered
**Location:** `src/main/kotlin/com/august/cupid/config/WebSocketConfig.kt`

**Symptom:** ConnectionInterceptor was imported but never used, meaning:
- User authentication wasn't happening during handshake
- Session attributes weren't being set
- userId wasn't extracted from query parameters

**Root Cause:**
```kotlin
// BEFORE (BROKEN):
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    // ConnectionInterceptor was imported but not injected

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .withSockJS()
        // Missing: .addInterceptors(connectionInterceptor)
    }
}
```

**Solution:**
```kotlin
// AFTER (FIXED):
class WebSocketConfig(
    private val connectionInterceptor: ConnectionInterceptor  // Inject
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .addInterceptors(connectionInterceptor)  // ✅ Register interceptor
            .setAllowedOrigins("*")
            .withSockJS()
    }
}
```

---

### Issue #2: CORS Configuration Conflict
**Location:** `src/main/kotlin/com/august/cupid/config/WebSocketConfig.kt`

**Symptom:** Application logs showed:
```
ERROR: When allowCredentials is true, allowedOrigins cannot contain the special
value "*" since that cannot be set on the "Access-Control-Allow-Origin" response header.
```

**Root Cause:**
The `SecurityConfig.kt` had `allowCredentials = true` in CORS configuration, which is incompatible with `setAllowedOrigins("*")`.

```kotlin
// BEFORE (BROKEN):
registry.addEndpoint("/ws")
    .setAllowedOrigins("*")  // ❌ Incompatible with allowCredentials=true
    .withSockJS()
```

**Solution:**
```kotlin
// AFTER (FIXED):
registry.addEndpoint("/ws")
    .setAllowedOriginPatterns("*")  // ✅ Compatible with allowCredentials
    .withSockJS()
```

**Why this works:**
- `setAllowedOrigins("*")` sets `Access-Control-Allow-Origin: *` (not allowed with credentials)
- `setAllowedOriginPatterns("*")` allows dynamic origin matching (allowed with credentials)

---

### Issue #3: User ID Not Propagated to STOMP Session
**Location:** Multiple files

**Symptom:** Logs showed:
```
WARN: 세션에 사용자 ID가 없습니다. sessionAttributes=null
WARN: WebSocket 연결됨: 사용자 ID 또는 세션 ID를 추출할 수 없습니다
```

**Root Cause:**
WebSocket handshake interceptor sets attributes on the WebSocket session, but STOMP events use a different session object. The userId wasn't being transferred from WebSocket session to STOMP session.

**Architecture Understanding:**
```
Client Connection Flow:
1. HTTP Handshake → ConnectionInterceptor.beforeHandshake()
   - Sets attributes["userId"] on WebSocket session
2. STOMP CONNECT frame → (No interceptor to transfer userId)
3. SessionConnectedEvent → WebSocketEventListener
   - Tries to read userId from STOMP session (empty!)
```

**Solution:** Created a STOMP Channel Interceptor

**New File:** `src/main/kotlin/com/august/cupid/websocket/StompChannelInterceptor.kt`
```kotlin
@Component
class StompChannelInterceptor : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            // Get userId from WebSocket session attributes
            val sessionAttributes = accessor.sessionAttributes
            val userId = sessionAttributes?.get("userId") as? String

            if (userId != null) {
                // Set userId as Principal in STOMP session
                accessor.user = java.security.Principal { userId }
                logger.info("STOMP 연결: userId={} 설정 완료", userId)
            }
        }

        return message
    }
}
```

**Register in WebSocketConfig:**
```kotlin
class WebSocketConfig(
    private val connectionInterceptor: ConnectionInterceptor,
    private val stompChannelInterceptor: StompChannelInterceptor  // Add
) : WebSocketMessageBrokerConfigurer {

    // Add this method:
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompChannelInterceptor)
        logger.info("STOMP 채널 인터셉터 등록 완료")
    }
}
```

**Update WebSocketEventListener:**
```kotlin
private fun extractUserIdFromQuery(headerAccessor: StompHeaderAccessor): String? {
    return try {
        // Extract from Principal (set by StompChannelInterceptor)
        val user = headerAccessor.user
        val userId = user?.name

        if (userId != null) {
            logger.debug("사용자 ID 추출 성공: userId={}", userId)
            return userId
        }

        // Fallback: try session attributes
        val sessionAttributes = headerAccessor.sessionAttributes
        val fallbackUserId = sessionAttributes?.get("userId") as? String

        if (fallbackUserId != null) {
            logger.debug("세션 속성에서 사용자 ID 추출 성공: userId={}", fallbackUserId)
            return fallbackUserId
        }

        logger.warn("사용자 ID를 추출할 수 없습니다.")
        null
    } catch (e: Exception) {
        logger.error("사용자 ID 추출 실패", e)
        null
    }
}
```

---

## Debugging Techniques Used

### 1. Log Analysis
**Command:**
```bash
# Start app and filter for WebSocket logs
./gradlew bootRun 2>&1 | grep -i "websocket\|error\|warn"
```

**What to look for:**
- ERROR messages with stack traces
- WARN messages about missing configurations
- INFO messages confirming proper initialization

### 2. Progressive Testing
**Approach:**
1. Fix one issue at a time
2. Restart application after each fix
3. Test connection attempt
4. Check logs for new errors
5. Repeat

### 3. Code Review Checklist
For WebSocket issues, verify:
- [ ] Interceptors are both created AND registered
- [ ] CORS configuration is compatible (allowedOriginPatterns with allowCredentials)
- [ ] Session attributes are transferred between WebSocket and STOMP layers
- [ ] Event listeners can access user information
- [ ] Security filters aren't blocking WebSocket endpoints

---

## Verification Steps

### 1. Check Application Startup
```bash
./gradlew bootRun
```

**Expected logs:**
```
✅ INFO: WebSocket 엔드포인트 등록 완료: /ws (with ConnectionInterceptor)
✅ INFO: STOMP 채널 인터셉터 등록 완료
✅ INFO: Message Broker 설정 완료: /topic, /queue
✅ INFO: Started CupidApplicationKt in X seconds
```

### 2. Test WebSocket Connection
**Browser Test:**
1. Open: http://localhost:8080/websocket-test.html
2. Enter userId: "test-user-1"
3. Click "연결" (Connect)

**Expected logs:**
```
✅ INFO: WebSocket 핸드셰이크 시작: URI=...?userId=test-user-1
✅ INFO: 사용자 ID 추출 성공: userId=test-user-1
✅ INFO: WebSocket 연결 성공: userId=test-user-1, sessionId=ws-session-...
✅ INFO: STOMP 연결: userId=test-user-1 설정 완료
✅ DEBUG: 사용자 ID 추출 성공: userId=test-user-1
```

**No errors should appear!**

### 3. Test Heartbeat
In the browser test page:
1. Click "하트비트 전송" (Send Heartbeat)
2. Check logs for heartbeat processing

**Expected logs:**
```
✅ DEBUG: 하트비트 처리 완료: userId=test-user-1
```

### 4. Verify Online Status in Redis
```bash
redis-cli
> KEYS user:online:*
> GET user:online:test-user-1
```

**Expected output:**
```
"true"
```

---

## Common Pitfalls to Avoid

### 1. Don't confuse WebSocket session and STOMP session
- **WebSocket Session:** Created during HTTP handshake upgrade
- **STOMP Session:** Created during STOMP CONNECT frame
- **Solution:** Use interceptors to transfer data between them

### 2. CORS misconfiguration
- `allowedOrigins("*")` + `allowCredentials(true)` = ERROR
- **Solution:** Use `allowedOriginPatterns("*")` instead

### 3. Importing but not registering components
- Just importing an interceptor class doesn't activate it
- **Solution:** Inject via constructor and call `.addInterceptors()`

### 4. Hardcoding test values
```kotlin
// ❌ BAD:
return "test-user-1"  // Always returns same value

// ✅ GOOD:
return user?.name  // Extracts actual user ID
```

---

## Files Modified Summary

| File | Changes | Purpose |
|------|---------|---------|
| `WebSocketConfig.kt` | Added ConnectionInterceptor injection<br>Added StompChannelInterceptor injection<br>Changed setAllowedOrigins to setAllowedOriginPatterns<br>Added configureClientInboundChannel method | Fix interceptor registration and CORS |
| `WebSocketEventListener.kt` | Updated extractUserIdFromQuery to use Principal<br>Added fallback to session attributes | Properly extract user ID from STOMP session |
| `StompChannelInterceptor.kt` | **New file**<br>Created channel interceptor to transfer userId | Bridge WebSocket and STOMP sessions |
| `application.yml` | Added WebSocket and messaging debug logging | Enable detailed logging for debugging |

---

## Testing Checklist

Before marking the issue as resolved:

- [ ] Application starts without errors
- [ ] WebSocket endpoint is registered in logs
- [ ] STOMP interceptor is registered in logs
- [ ] Browser can connect to WebSocket endpoint
- [ ] ConnectionInterceptor extracts userId from query parameter
- [ ] StompChannelInterceptor sets Principal with userId
- [ ] WebSocketEventListener extracts userId successfully
- [ ] OnlineStatusService marks user as online in Redis
- [ ] Heartbeat messages are processed correctly
- [ ] User appears in online users list API
- [ ] Disconnect event clears online status

---

## Lessons Learned

1. **Enable debug logging early** - Spring WebSocket has excellent debug logs that reveal configuration issues immediately

2. **Understand the component lifecycle** - WebSocket connections go through multiple phases (HTTP upgrade, STOMP connect, message handling), each with different session contexts

3. **Test incrementally** - Fix one issue at a time and verify before moving to the next

4. **Read error messages carefully** - The CORS error message explicitly suggested using `allowedOriginPatterns` instead of `allowedOrigins`

5. **Don't assume imports mean registration** - In Spring, beans must be explicitly wired and registered, not just imported

---

## Quick Reference: WebSocket Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Client: Connect to ws://localhost:8080/ws?userId=USER_ID   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. HTTP Handshake (Upgrade to WebSocket)                   │
│    ConnectionInterceptor.beforeHandshake()                  │
│    - Extract userId from query parameter                    │
│    - Store in WebSocket session: attributes["userId"]       │
│    - Store online status in Redis                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. STOMP CONNECT Frame                                      │
│    StompChannelInterceptor.preSend()                        │
│    - Read userId from WebSocket session attributes          │
│    - Set as Principal in STOMP session                      │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. SessionConnectedEvent                                    │
│    WebSocketEventListener.handleWebSocketConnectListener()  │
│    - Extract userId from Principal                          │
│    - Call OnlineStatusService.setUserOnline()               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Message Exchange                                         │
│    @MessageMapping("/heartbeat")                            │
│    - Process messages with userId from session              │
└─────────────────────────────────────────────────────────────┘
```

---

## For Future Reference

When debugging WebSocket issues:

1. **Start with logging** - Add DEBUG level for `org.springframework.web.socket` and `org.springframework.messaging`

2. **Verify the chain** - Ensure each step in the connection lifecycle works:
   - HTTP handshake
   - WebSocket upgrade
   - STOMP CONNECT
   - Session established
   - Message handling

3. **Check both client and server** - Browser console AND server logs

4. **Use the test page** - The provided websocket-test.html is invaluable for testing

5. **Monitor Redis** - Verify online status is being set/cleared correctly

6. **Follow the data** - Track how userId flows through:
   - Query parameter → ConnectionInterceptor
   - Session attributes → StompChannelInterceptor
   - Principal → Event listeners and message handlers

---

## End Result

After applying all fixes:
- ✅ WebSocket connections establish successfully
- ✅ User IDs are properly extracted and tracked
- ✅ Online status is stored in Redis with TTL
- ✅ Heartbeat mechanism keeps users online
- ✅ Disconnect events properly clean up online status
- ✅ No CORS errors
- ✅ Full user session tracking works end-to-end
