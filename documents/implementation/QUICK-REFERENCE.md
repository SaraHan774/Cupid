# Quick Reference: Typing Indicators & Read Receipts

## File Locations

```
src/main/kotlin/com/august/cupid/
├── service/
│   ├── TypingIndicatorService.kt      ✅ Redis-based typing status
│   └── ReadReceiptService.kt          ✅ MongoDB + Redis read receipts
├── controller/
│   ├── RealtimeWebSocketController.kt ✅ WebSocket endpoints
│   └── MessageController.kt           ✅ REST API (enhanced)
├── model/dto/
│   └── RealtimeDto.kt                 ✅ All WebSocket DTOs
└── config/
    └── RedisConfig.kt                 ✅ Redis connection config
```

---

## WebSocket Endpoints

| Endpoint | Purpose | Payload | Broadcast Topic |
|----------|---------|---------|-----------------|
| `/app/typing/start` | Start typing | `{ channelId }` | `/topic/channel.{id}.typing` |
| `/app/typing/stop` | Stop typing | `{ channelId }` | `/topic/channel.{id}.typing` |
| `/app/typing/users` | Get typing users | `{ channelId }` | `/user/queue/typing/users` |
| `/app/heartbeat` | Keep-alive | `{ timestamp }` | `/user/queue/heartbeat` |
| `/app/presence/channel` | Online members | `{ channelId }` | `/user/queue/presence/channel` |
| `/app/subscribe` | Subscribe | `{ channelId }` | `/user/queue/subscription` |
| `/app/unsubscribe` | Unsubscribe | `{ channelId }` | `/user/queue/subscription` |

---

## REST API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/v1/channels/{channelId}/messages/{messageId}/read` | Mark single message as read |
| `POST` | `/api/v1/channels/{channelId}/messages/read-batch` | Mark multiple messages as read |
| `GET` | `/api/v1/messages/{messageId}/read-count` | Get message read count |
| `GET` | `/api/v1/channels/{channelId}/unread` | Get channel unread count |
| `GET` | `/api/v1/messages/unread/total` | Get total unread count |

---

## Redis Key Patterns

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `typing:{channelId}` | Set | 10s | Users currently typing |
| `typing:user:{channelId}:{userId}` | String | 10s | Individual typing status |
| `unread:{channelId}:{userId}` | String | 1h | Cached unread count |
| `user:online:{userId}` | String | 5min | Online presence |

---

## Key Service Methods

### TypingIndicatorService
```kotlin
setTyping(channelId, userId): Boolean
removeTyping(channelId, userId): Boolean
getTypingUsers(channelId): List<String>
isUserTyping(channelId, userId): Boolean
clearUserTypingStatus(userId)
```

### ReadReceiptService
```kotlin
markAsRead(messageId, userId, channelId): ApiResponse<ReadReceiptResponse>
markMultipleAsRead(messageIds, userId, channelId): ApiResponse<BatchReadReceiptResponse>
getMessageReadCount(messageId): ApiResponse<MessageReadCountResponse>
getUnreadMessageCount(channelId, userId): ApiResponse<UnreadCountResponse>
getTotalUnreadMessageCount(userId): ApiResponse<Long>
incrementUnreadCount(channelId, recipientUserIds)
```

---

## Client Examples

### JavaScript WebSocket
```javascript
// Connect
const socket = new SockJS('/ws');
const stomp = Stomp.over(socket);

// Start typing
stomp.send('/app/typing/start', {}, JSON.stringify({ channelId }));

// Subscribe to typing
stomp.subscribe(`/topic/channel.${channelId}.typing`, (msg) => {
  const event = JSON.parse(msg.body);
  // event.userId, event.isTyping
});

// Mark as read
fetch(`/api/v1/channels/${channelId}/messages/${messageId}/read`, {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}` }
});
```

### cURL
```bash
# Mark as read
curl -X POST "http://localhost:8080/api/v1/channels/{channelId}/messages/{messageId}/read" \
  -H "Authorization: Bearer {token}"

# Get unread count
curl "http://localhost:8080/api/v1/channels/{channelId}/unread" \
  -H "Authorization: Bearer {token}"
```

---

## Common Operations

### Redis CLI
```bash
# View typing users
redis-cli SMEMBERS typing:{channelId}

# View unread count
redis-cli GET unread:{channelId}:{userId}

# Clear all typing
redis-cli KEYS "typing:*" | xargs redis-cli DEL
```

### MongoDB
```javascript
// View read receipts
db.message_reads.find({ channelId: UUID("...") })

// Count reads
db.message_reads.count({ messageId: UUID("...") })
```

---

## Performance Targets

| Operation | Target |
|-----------|--------|
| Typing start/stop | < 5ms |
| Single read receipt | < 10ms |
| Batch read (100) | < 50ms |
| Unread count (cached) | < 2ms |
| WebSocket broadcast | < 3ms |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Typing not clearing | Auto-expires in 10s (TTL) |
| Read not showing | Check MongoDB, WebSocket is best-effort |
| Unread count wrong | Cache TTL 1h, will auto-refresh |
| High memory | All keys have TTL, auto-cleanup |

---

## Architecture Decisions

| Question | Decision | Reason |
|----------|----------|--------|
| Typing storage? | Redis | TTL support, fast |
| Read storage? | MongoDB | High write throughput |
| Caching? | Redis | Low latency reads |
| WebSocket? | STOMP/SimpleBroker | Built-in Spring support |
| Serialization? | String | Simple, debuggable |

---

## Next Steps

1. ✅ Implementation complete
2. ⏳ Load testing (JMeter)
3. ⏳ Add rate limiting
4. ⏳ User privacy settings
5. ⏳ Production deployment

---

**Quick Start**:
1. Start Redis: `redis-server`
2. Start MongoDB: `mongod`
3. Run app: `./gradlew bootRun`
4. Open test client: `http://localhost:8080/test-client.html`
5. Test typing and read receipts!
