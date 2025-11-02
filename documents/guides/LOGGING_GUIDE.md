# 서버 로그 확인 가이드

## 📋 개요
이 문서는 Cupid 서버의 로그를 확인하는 다양한 방법을 설명합니다.

---

## 🔍 로그 확인 방법

### 1. 콘솔 로그 (터미널/IDE)
**가장 실시간으로 확인 가능한 방법**

- **터미널에서 실행한 경우**: 서버를 실행한 터미널 창에서 로그 확인
- **IDE에서 실행한 경우**: IDE의 콘솔/런 창에서 로그 확인

#### 예시:
```
2025-11-02 22:33:23.270 [nio-8080-exec-7] ERROR c.a.cupid.service.SignalProtocolService - 키 생성 실패: ...
```

---

### 2. 로그 파일
**서버를 백그라운드로 실행하거나 파일로 저장하고 싶을 때**

#### 파일 위치
```
프로젝트 루트/logs/cupid-server.log
```

#### 실시간 로그 확인 (터미널)
```bash
# 로그 파일 실시간 모니터링
tail -f logs/cupid-server.log

# 최근 100줄 확인
tail -n 100 logs/cupid-server.log

# 에러만 필터링
tail -f logs/cupid-server.log | grep -i error

# 특정 시간대 로그 확인
grep "2025-11-02 22:3" logs/cupid-server.log
```

#### macOS/Linux
```bash
# 에러 로그만 확인
grep -i error logs/cupid-server.log

# 특정 클래스의 로그만 확인
grep "SignalProtocolService" logs/cupid-server.log

# 키 생성 관련 로그만 확인
grep "키 생성" logs/cupid-server.log
```

---

### 3. Spring Boot Actuator (웹 브라우저)

#### 🔗 접속 URL
```
http://localhost:8080/actuator/logfile
```

#### 📊 사용 가능한 엔드포인트
- **로그 파일 조회**: `http://localhost:8080/actuator/logfile`
- **로그 레벨 확인/변경**: `http://localhost:8080/actuator/loggers`
- **건강 상태**: `http://localhost:8080/actuator/health`
- **메트릭**: `http://localhost:8080/actuator/metrics`
- **Prometheus**: `http://localhost:8080/actuator/prometheus`

#### 로그 레벨 변경 예시
```bash
# SignalProtocolService의 로그 레벨을 TRACE로 변경
curl -X POST http://localhost:8080/actuator/loggers/com.august.cupid.service.SignalProtocolService \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "TRACE"}'

# 전체 패키지의 로그 레벨을 DEBUG로 변경
curl -X POST http://localhost:8080/actuator/loggers/com.august.cupid \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

### 4. 로그 레벨 확인 (Actuator)
```bash
# 모든 로거 상태 확인
curl http://localhost:8080/actuator/loggers | jq

# 특정 로거 확인
curl http://localhost:8080/actuator/loggers/com.august.cupid.service.SignalProtocolService | jq
```

---

## 🎯 주요 로그 패턴

### 에러 로그 찾기
```bash
# 에러 로그만 필터링
grep -i "ERROR" logs/cupid-server.log

# 특정 예외 타입만 찾기
grep "SecurityException" logs/cupid-server.log
grep "ObjectOptimisticLockingFailureException" logs/cupid-server.log
```

### 키 생성 관련 로그
```bash
grep "키 생성" logs/cupid-server.log
grep "generateIdentityKeys" logs/cupid-server.log
```

### API 요청 로그
```bash
grep "POST\|GET\|DELETE" logs/cupid-server.log
```

### 트랜잭션 관련 로그
```bash
grep "Transaction" logs/cupid-server.log
```

---

## 📝 로그 형식 설명

### 기본 형식
```
YYYY-MM-DD HH:mm:ss.SSS [스레드명] LEVEL 로거명 - 메시지
```

### 예시
```
2025-11-02 22:33:23.270 [nio-8080-exec-7] ERROR c.a.cupid.service.SignalProtocolService - 키 생성 실패: ...
```

- **날짜/시간**: `2025-11-02 22:33:23.270`
- **스레드**: `nio-8080-exec-7` (HTTP 요청 처리 스레드)
- **레벨**: `ERROR` (ERROR, WARN, INFO, DEBUG, TRACE)
- **로거**: `c.a.cupid.service.SignalProtocolService` (클래스명)
- **메시지**: 실제 로그 내용

---

## 🔧 로그 레벨 변경

### application.yml에서 변경
```yaml
logging:
  level:
    com.august.cupid: DEBUG  # DEBUG, INFO, WARN, ERROR, TRACE
```

### 런타임에서 변경 (Actuator 사용)
```bash
# POST 요청으로 로그 레벨 변경
curl -X POST http://localhost:8080/actuator/loggers/com.august.cupid.service.SignalProtocolService \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "TRACE"}'
```

---

## 💡 유용한 팁

### 1. 실시간 로그 모니터링 (터미널)
```bash
tail -f logs/cupid-server.log | grep --color -E "ERROR|WARN|키 생성|키 생성 실패"
```

### 2. 에러 로그만 별도 파일로 저장
```bash
grep -i error logs/cupid-server.log > logs/errors-$(date +%Y%m%d).log
```

### 3. 특정 시간대의 로그 확인
```bash
# 22시 30분부터 35분까지의 로그
grep "2025-11-02 22:3[0-5]" logs/cupid-server.log
```

### 4. 로그 파일 크기 관리
- 최대 파일 크기: 10MB (설정 가능)
- 보관 파일 개수: 30개 (설정 가능)
- 로그 파일 자동 로테이션: 크기 초과 시 자동으로 새 파일 생성

---

## 🚨 문제 해결

### 로그 파일이 생성되지 않는 경우
1. `logs` 디렉토리가 존재하는지 확인
2. 서버 실행 권한 확인
3. `application.yml`의 로깅 설정 확인

### 로그가 너무 많은 경우
- 로그 레벨을 `INFO`나 `WARN`으로 변경
- 특정 패키지만 `DEBUG`로 설정

### 특정 로그만 보고 싶은 경우
- Actuator로 로그 레벨을 동적으로 변경
- `grep`으로 특정 키워드 필터링

---

## 📚 관련 문서
- [Spring Boot Actuator 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Logback 설정 가이드](https://logback.qos.ch/documentation.html)

