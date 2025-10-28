# Cupid - Real-time Chat Application

Spring Boot 3.5 + Kotlin 기반의 실시간 채팅 플랫폼

**모든 문서는 자동 생성됩니다** → http://localhost:8080/swagger-ui.html

## 🚀 Quick Start (3 Commands)

```bash
docker-compose up -d              # 1. DB 시작
./gradlew bootRun                 # 2. 앱 실행
open http://localhost:8080/swagger-ui.html  # 3. 문서 보기
```

## 📚 Where to Find Information

| 정보 | 위치 |
|-----|-----|
| **API 문서** (자동 생성) | http://localhost:8080/swagger-ui.html |
| **WebSocket 테스트** | http://localhost:8080/websocket-test.html |
| **Health Check** | http://localhost:8080/api/v1/health |
| **코드 설명** | 각 클래스의 KDoc 주석 |
| **설정** | `application.yml` |

## 🛠️ Stack

- **Backend**: Kotlin + Spring Boot 3.5.7
- **Database**: PostgreSQL, MongoDB, Redis
- **Real-time**: WebSocket (STOMP over SockJS)
- **Auth**: JWT
- **Docs**: SpringDoc OpenAPI (자동 생성)

## 🐛 문제 해결

### WebSocket 연결 안됨?
```bash
redis-cli ping  # PONG이 나와야 함
```

### Database 연결 안됨?
```bash
docker-compose ps  # 모두 Up 상태여야 함
```

자세한 내용은 로그 확인: 애플리케이션 시작 시 콘솔에 모든 정보가 나옵니다.

---

**That's it!**

- API 문서: Swagger UI가 자동 생성 (항상 최신)
- 코드 설명: 각 클래스의 KDoc 주석
- 설정: `application.yml` 파일

**별도 문서 파일 없음. 유지보수 불필요.**
