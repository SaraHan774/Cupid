# Cupid - 채팅 SDK 서버

Spring Boot 3.5 + Kotlin 기반의 채팅 SDK 서버 프로젝트입니다.

## 프로젝트 정보

- **Spring Boot**: 3.5.7
- **Kotlin**: 1.9.25
- **Java**: 17
- **Build Tool**: Gradle

## 사전 요구사항

- Java 17+
- Docker Desktop
- Firebase CLI

## 로컬 개발 환경 설정

### 1. Docker 컨테이너 실행

```bash
# PostgreSQL
docker run -d --name postgres-cupid -p 5433:5432 \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=chatsdk \
  postgres:15

# MongoDB
docker run -d --name mongodb-cupid -p 27017:27017 \
  mongo:7

# Redis
docker run -d --name redis-cupid -p 6379:6379 \
  redis:7
```

### 2. Firebase 프로젝트 설정

#### 2.1 Firebase CLI 로그인
```bash
firebase login
```

#### 2.2 Firebase 프로젝트 생성
```bash
# Firebase Console에서 프로젝트 생성: https://console.firebase.google.com/
# 프로젝트 생성 후 아래 명령어로 초기화
firebase init

# 또는 프로젝트 연결
firebase use --add
```

#### 2.3 서비스 계정 키 다운로드
1. Firebase Console > 프로젝트 설정 > 서비스 계정
2. "새 비공개 키 생성" 클릭
3. 다운로드한 JSON 파일을 `src/main/resources/firebase-service-account.json`에 저장

### 3. 애플리케이션 실행

```bash
# 의존성 다운로드 및 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

### 4. Health Check API 테스트

```bash
curl http://localhost:8080/api/v1/health
```

## 프로젝트 구조

```
com.august.cupid/
├── config/          # 설정 클래스
│   ├── FirebaseConfig.kt
│   └── WebSocketConfig.kt
├── controller/      # REST 컨트롤러
│   ├── HealthController.kt
│   └── ChatController.kt
├── service/         # 비즈니스 로직
│   └── notification/
├── repository/      # 데이터 접근 계층
├── model/           # 데이터 모델
│   ├── entity/
│   │   └── notification/
│   └── dto/
│       └── notification/
├── websocket/       # WebSocket 핸들러
├── security/        # 보안 설정
└── fcm/             # FCM 관련 코드
```

## API 엔드포인트

### Health Check
- **GET** `/api/v1/health`
- 서버 상태, FCM 상태, 데이터베이스 연결 상태 확인

### WebSocket
- **WebSocket** `ws://localhost:8080/ws`
- **STOMP Endpoints**:
  - Send: `/app/echo`
  - Subscribe: `/topic/echo`

## 데이터베이스

- **PostgreSQL**: 관계형 데이터 (사용자, 채널, 설정 등)
- **MongoDB**: 메시지 데이터
- **Redis**: 캐시 및 실시간 상태

## 참고 문서

- `documents/specifications/` - 프로젝트 스펙
- `documents/tasks/` - 작업 목록

## 문제 해결

### Docker 컨테이너가 시작되지 않는 경우
```bash
# 기존 컨테이너 확인 및 제거
docker ps -a
docker rm <container_id>

# 포트 충돌 확인
lsof -i :5433
lsof -i :27017
lsof -i :6379
```

### Firebase 연결 오류
- `firebase-service-account.json` 파일이 올바른 위치에 있는지 확인
- 파일 권한 확인: `chmod 600 src/main/resources/firebase-service-account.json`
- Firebase Console에서 서비스 계정 키가 유효한지 확인

## 다음 단계

현재 Phase 1의 기본 설정이 완료되었습니다. 다음 작업:
1. Entity 모델 생성 (User, Channel, Message, Notification 등)
2. Repository 및 Service 계층 구현
3. JWT 인증 구현
4. 실시간 메시징 구현
