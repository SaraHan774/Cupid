# 암호화 메트릭 조회 가이드

## 📊 메트릭 조회 방법

### 1. 기본 엔드포인트

#### Prometheus 형식 (전체 메트릭)
```bash
curl http://localhost:8080/actuator/prometheus
```

또는 브라우저에서:
```
http://localhost:8080/actuator/prometheus
```

#### JSON 형식 (메트릭 목록)
```bash
curl http://localhost:8080/actuator/metrics
```

### 2. 특정 메트릭 조회

#### 키 생성 메트릭
```bash
# 전체 정보
curl http://localhost:8080/actuator/metrics/encryption.key.generation

# 태그 필터링
curl "http://localhost:8080/actuator/metrics/encryption.key.generation?tag=operation:generate"
```

#### 암호화 메트릭
```bash
curl http://localhost:8080/actuator/metrics/encryption.message.encrypt
curl "http://localhost:8080/actuator/metrics/encryption.message.encrypt?tag=operation:encrypt"
```

#### 복호화 메트릭
```bash
curl http://localhost:8080/actuator/metrics/encryption.message.decrypt
curl "http://localhost:8080/actuator/metrics/encryption.message.decrypt?tag=operation:decrypt"
```

#### 세션 초기화 메트릭
```bash
curl http://localhost:8080/actuator/metrics/encryption.session.initialize
```

### 3. Prometheus 형식 메트릭 예시

#### Timer 메트릭 (시간 측정)
```prometheus
# 키 생성 시간 (초 단위)
encryption_key_generation_seconds_count{operation="generate",user_id="..."} 15
encryption_key_generation_seconds_sum{operation="generate",user_id="..."} 2.5
encryption_key_generation_seconds_max{operation="generate",user_id="..."} 0.8
encryption_key_generation_seconds{operation="generate",user_id="...",quantile="0.5"} 0.15
encryption_key_generation_seconds{operation="generate",user_id="...",quantile="0.95"} 0.6
encryption_key_generation_seconds{operation="generate",user_id="...",quantile="0.99"} 0.8

# 암호화 시간
encryption_message_encrypt_seconds_count{operation="encrypt",sender_id="...",recipient_id="..."} 1200
encryption_message_encrypt_seconds_sum{operation="encrypt",sender_id="...",recipient_id="..."} 45.2
encryption_message_encrypt_seconds_max{operation="encrypt",sender_id="...",recipient_id="..."} 0.12
encryption_message_encrypt_seconds{operation="encrypt",quantile="0.5"} 0.035
encryption_message_encrypt_seconds{operation="encrypt",quantile="0.95"} 0.085
encryption_message_encrypt_seconds{operation="encrypt",quantile="0.99"} 0.105

# 복호화 시간
encryption_message_decrypt_seconds_count{operation="decrypt",recipient_id="...",sender_id="..."} 1180
encryption_message_decrypt_seconds_sum{operation="decrypt",recipient_id="...",sender_id="..."} 38.7
encryption_message_decrypt_seconds_max{operation="decrypt",recipient_id="...",sender_id="..."} 0.15
```

#### Counter 메트릭 (카운트)
```prometheus
# 키 생성 카운트
encryption_key_generation_count_total{operation="generate",user_id="..."} 15

# 암호화 카운트
encryption_message_encrypt_count_total{operation="encrypt",sender_id="...",recipient_id="..."} 1200

# 복호화 카운트
encryption_message_decrypt_count_total{operation="decrypt",recipient_id="...",sender_id="..."} 1180

# 에러 카운트
encryption_errors_total{error_type="SecurityException",operation="encrypt",sender_id="...",recipient_id="..."} 5
encryption_errors_total{error_type="IllegalStateException",operation="decrypt",recipient_id="...",sender_id="..."} 2
```

#### Gauge 메트릭 (현재 값)
```prometheus
# 활성 세션 수
encryption_sessions_active{application="chat-sdk-server"} 42

# 사용 가능한 pre-key 수 (사용자별)
encryption_prekeys_available{user_id="123e4567-e89b-12d3-a456-426614174000",application="chat-sdk-server"} 87
encryption_prekeys_available{user_id="987e6543-e21b-43d2-a654-321098765432",application="chat-sdk-server"} 95
```

### 4. Prometheus 쿼리 예시

#### 평균 키 생성 시간
```promql
rate(encryption_key_generation_seconds_sum[5m]) / rate(encryption_key_generation_seconds_count[5m])
```

#### 초당 암호화 작업 수
```promql
rate(encryption_message_encrypt_count_total[1m])
```

#### 에러율 계산
```promql
rate(encryption_errors_total[5m]) / rate(encryption_message_encrypt_count_total[5m])
```

#### P95 암호화 시간
```promql
encryption_message_encrypt_seconds{quantile="0.95"}
```

#### 활성 세션 수
```promql
encryption_sessions_active
```

#### Pre-key 부족 사용자 찾기 (20개 미만)
```promql
encryption_prekeys_available < 20
```

### 5. Grafana 대시보드 설정 예시

#### 패널 1: 키 생성 시간 (Line Chart)
```
Query: encryption_key_generation_seconds{quantile="0.95"}
Legend: {{operation}} - P95
```

#### 패널 2: 암호화/복호화 처리량 (Graph)
```
Query A: rate(encryption_message_encrypt_count_total[1m])
Query B: rate(encryption_message_decrypt_count_total[1m])
```

#### 패널 3: 에러율 (Stat)
```
Query: sum(rate(encryption_errors_total[5m])) / sum(rate(encryption_message_encrypt_count_total[5m])) * 100
Unit: Percent (0-100)
```

#### 패널 4: 활성 세션 수 (Gauge)
```
Query: encryption_sessions_active
Min: 0
Max: 1000
```

### 6. 주요 메트릭 목록

#### Timer 메트릭 (시간 측정)
| 메트릭 이름 | 설명 | 주요 태그 |
|-----------|------|----------|
| `encryption.key.generation` | 키 생성 시간 | `operation=generate`, `user_id` |
| `encryption.message.encrypt` | 암호화 시간 | `operation=encrypt`, `sender_id`, `recipient_id` |
| `encryption.message.decrypt` | 복호화 시간 | `operation=decrypt`, `recipient_id`, `sender_id` |
| `encryption.session.initialize` | 세션 초기화 시간 | `operation=initialize`, `sender_id`, `recipient_id` |

#### Counter 메트릭 (카운트)
| 메트릭 이름 | 설명 | 주요 태그 |
|-----------|------|----------|
| `encryption.key.generation.count` | 키 생성 총 횟수 | `operation=generate`, `user_id` |
| `encryption.message.encrypt.count` | 암호화 총 횟수 | `operation=encrypt`, `sender_id`, `recipient_id` |
| `encryption.message.decrypt.count` | 복호화 총 횟수 | `operation=decrypt`, `recipient_id`, `sender_id` |
| `encryption.errors` | 에러 총 횟수 | `error_type`, `operation`, `sender_id`, `recipient_id` |

#### Gauge 메트릭 (현재 값)
| 메트릭 이름 | 설명 | 주요 태그 |
|-----------|------|----------|
| `encryption.sessions.active` | 활성 세션 수 | `application` |
| `encryption.prekeys.available` | 사용 가능한 pre-key 수 | `user_id`, `application` |

### 7. 실용적인 모니터링 쿼리

#### 1분당 평균 암호화 시간
```promql
rate(encryption_message_encrypt_seconds_sum[1m]) / rate(encryption_message_encrypt_seconds_count[1m])
```

#### 시간대별 키 생성 횟수
```promql
sum(increase(encryption_key_generation_count_total[1h])) by (operation)
```

#### 에러 타입별 분류
```promql
sum(rate(encryption_errors_total[5m])) by (error_type, operation)
```

#### 가장 느린 암호화 작업 (Top 10)
```promql
topk(10, encryption_message_encrypt_seconds_max)
```

#### Pre-key 부족 사용자 목록
```promql
encryption_prekeys_available < 20
```

### 8. 알림 설정 예시

#### 높은 에러율 알림 (1% 이상)
```yaml
- alert: HighEncryptionErrorRate
  expr: |
    (sum(rate(encryption_errors_total[5m])) by (error_type) 
     / 
     sum(rate(encryption_message_encrypt_count_total[5m]))) > 0.01
  for: 5m
  annotations:
    summary: "암호화 에러율이 1%를 초과했습니다"
```

#### 느린 키 생성 알림 (P95가 1초 이상)
```yaml
- alert: SlowKeyGeneration
  expr: encryption_key_generation_seconds{quantile="0.95"} > 1
  for: 5m
  annotations:
    summary: "키 생성 시간이 느립니다 (P95 > 1초)"
```

#### Pre-key 부족 알림 (20개 미만)
```yaml
- alert: LowPreKeyCount
  expr: encryption_prekeys_available < 20
  for: 10m
  annotations:
    summary: "사용자 {{ $labels.user_id }}의 pre-key가 부족합니다"
```

### 9. cURL 명령어 모음

```bash
# 전체 Prometheus 메트릭 내보내기
curl http://localhost:8080/actuator/prometheus > metrics.txt

# 특정 메트릭만 필터링 (grep 사용)
curl http://localhost:8080/actuator/prometheus | grep encryption_key_generation

# JSON 형식으로 메트릭 목록 조회
curl http://localhost:8080/actuator/metrics | jq

# 특정 메트릭 상세 정보
curl http://localhost:8080/actuator/metrics/encryption.key.generation | jq

# 에러 메트릭만 조회
curl http://localhost:8080/actuator/prometheus | grep encryption_errors
```

### 10. Postman/브라우저 사용

1. **메트릭 목록**: `GET http://localhost:8080/actuator/metrics`
2. **특정 메트릭**: `GET http://localhost:8080/actuator/metrics/encryption.key.generation`
3. **Prometheus 형식**: `GET http://localhost:8080/actuator/prometheus`

---

## 📝 참고사항

- 모든 메트릭은 `/actuator/prometheus` 엔드포인트에서 Prometheus 형식으로 제공됩니다
- Percentiles (p50, p95, p99)는 자동으로 계산되어 `quantile` 태그로 제공됩니다
- Counter 메트릭은 `_total` 접미사가 붙습니다
- Timer 메트릭은 `_seconds` 접미사가 붙습니다
- 메트릭 이름의 점(`.`)은 Prometheus에서 언더스코어(`_`)로 변환됩니다

