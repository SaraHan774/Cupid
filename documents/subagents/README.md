# Subagents Context Files 📚

이 디렉토리에는 Claude AI를 여러 전문가 서브에이전트로 활용하기 위한 컨텍스트 파일들이 있습니다.

## 📋 파일 목록

각 서브에이전트별 컨텍스트 파일:

1. **agent1-signal-protocol-context.md** 🔐
   - Signal Protocol & E2E 암호화 전문가
   - 담당: Task 1 - E2E 암호화 구현

2. **agent2-realtime-features-context.md** 🚀
   - WebSocket 및 실시간 기능 전문가
   - 담당: Task 2 - 타이핑 인디케이터, 읽음 표시

3. **agent3-media-processing-context.md** 📸
   - 이미지 최적화 및 미디어 처리 전문가
   - 담당: Task 3 - 프로필 이미지 업로드/최적화

4. **agent4-business-logic-context.md** 💝
   - 소개팅 앱 비즈니스 로직 전문가
   - 담당: Task 4, 5, 7 - 매칭 해제, 채널 삭제, 그룹 인원 제한

5. **agent5-messaging-features-context.md** 💬
   - 메시지 관리 기능 전문가
   - 담당: Task 6 - 메시지 수정/삭제

6. **agent6-notification-system-context.md** 🔔
   - 알림 시스템 전문가
   - 담당: Task 8 - 알림 고급 기능

## 🚀 사용 방법

### 1. 새 Claude 대화 시작
각 서브에이전트마다 별도의 Claude 대화를 시작합니다.

### 2. 컨텍스트 파일 복사
해당 작업의 컨텍스트 파일 전체를 복사하여 Claude 대화에 붙여넣습니다.

### 3. MEGA PROMPT 사용
각 컨텍스트 파일의 끝에 있는 "MEGA PROMPT" 섹션을 복사하여 추가로 전송합니다.

### 예시 워크플로우

```
1. Claude Desktop/Web에서 새 대화 시작
2. agent2-realtime-features-context.md 파일 전체 복사/붙여넣기
3. 파일 끝의 MEGA PROMPT 복사/붙여넣기
4. "Implement typing indicator feature" 요청
5. 생성된 코드 검토 및 통합
6. 다음 작업으로 이동
```

## 📖 참고 문서

- **how-to.md**: 서브에이전트 전략 및 워크플로우 설명
- **making-context-file.md**: 컨텍스트 파일 생성 가이드
- **today-tasks.md**: 전체 작업 목록 및 우선순위

## 🎯 권장 작업 순서

### 오늘의 우선순위 (4-6시간)
1. **Agent 2** → 타이핑 인디케이터 (2시간)
2. **Agent 2** → 읽음 표시 (2시간)
3. **Agent 4** → 매칭 해제 처리 (2-3시간)

### 다음 우선순위
4. **Agent 3** → 프로필 이미지 업로드 (4-5시간)
5. **Agent 5** → 메시지 수정/삭제 (3-4시간)
6. **Agent 6** → 알림 고급 기능 (4-5시간)
7. **Agent 1** → Signal Protocol (7-8시간)

## 💡 팁

1. **병렬 작업**: 가능하면 여러 Claude 대화를 동시에 열어 여러 Agent를 병렬로 작업하세요.
2. **컨텍스트 유지**: 각 Agent 대화는 별도로 유지하여 컨텍스트를 보존하세요.
3. **점진적 통합**: 각 Agent가 완료한 작업을 바로 통합하고 테스트하세요.
4. **진행 상황 추적**: `today-tasks.md`의 체크리스트를 업데이트하며 진행하세요.

---

**Happy Coding! 🚀**

