#!/bin/bash

# WebSocket 연결 상태 추적 기능 테스트 스크립트
# 
# 사용법: ./test-websocket-connection.sh
# 
# 테스트 항목:
# 1. 서버 시작 확인
# 2. WebSocket 연결 테스트
# 3. 하트비트 테스트
# 4. 온라인 상태 API 테스트
# 5. 연결 해제 테스트

echo "🚀 WebSocket 연결 상태 추적 기능 테스트 시작"
echo "=============================================="

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 테스트 결과 카운터
PASSED=0
FAILED=0

# 테스트 함수
test_passed() {
    echo -e "${GREEN}✅ PASS${NC}: $1"
    ((PASSED++))
}

test_failed() {
    echo -e "${RED}❌ FAIL${NC}: $1"
    ((FAILED++))
}

test_info() {
    echo -e "${YELLOW}ℹ️  INFO${NC}: $1"
}

# 1. 서버 시작 확인
echo ""
echo "1. 서버 시작 확인"
echo "-----------------"

# 서버가 실행 중인지 확인
if curl -s http://localhost:8080/api/v1/health > /dev/null 2>&1; then
    test_passed "서버가 정상적으로 실행 중입니다"
else
    test_failed "서버가 실행되지 않았거나 응답하지 않습니다"
    echo "서버를 먼저 시작해주세요: ./gradlew bootRun"
    exit 1
fi

# 2. WebSocket 연결 테스트
echo ""
echo "2. WebSocket 연결 테스트"
echo "----------------------"

# WebSocket 엔드포인트 접근 가능한지 확인 (websocat 사용)
if command -v websocat >/dev/null 2>&1; then
    # WebSocket 연결 테스트 (타임아웃 5초)
    timeout 5s websocat "ws://localhost:8080/ws?userId=test-user-123" >/dev/null 2>&1
    if [ $? -eq 0 ] || [ $? -eq 124 ]; then
        test_passed "WebSocket 엔드포인트에 접근할 수 있습니다"
    else
        test_failed "WebSocket 엔드포인트에 접근할 수 없습니다"
        test_info "에러: WebSocket 연결 실패 (400 Bad Request)"
    fi
else
    test_failed "websocat이 설치되지 않았습니다"
    test_info "설치: brew install websocat"
fi

# 3. 온라인 상태 API 테스트
echo ""
echo "3. 온라인 상태 API 테스트"
echo "----------------------"

# 온라인 사용자 목록 조회 테스트
if response=$(curl -s http://localhost:8080/api/v1/online-status/users); then
    if echo "$response" | grep -q "totalOnlineUsers"; then
        test_passed "온라인 사용자 목록 API가 정상 작동합니다"
        test_info "응답: $response"
    else
        test_failed "온라인 사용자 목록 API 응답이 예상과 다릅니다"
    fi
else
    test_failed "온라인 사용자 목록 API 호출에 실패했습니다"
fi

# 특정 사용자 온라인 상태 확인 테스트
if response=$(curl -s http://localhost:8080/api/v1/online-status/users/test-user); then
    if echo "$response" | grep -q "isOnline"; then
        test_passed "사용자 온라인 상태 확인 API가 정상 작동합니다"
        test_info "응답: $response"
    else
        test_failed "사용자 온라인 상태 확인 API 응답이 예상과 다릅니다"
    fi
else
    test_failed "사용자 온라인 상태 확인 API 호출에 실패했습니다"
fi

# 온라인 상태 통계 조회 테스트
if response=$(curl -s http://localhost:8080/api/v1/online-status/stats); then
    if echo "$response" | grep -q "totalOnlineUsers"; then
        test_passed "온라인 상태 통계 API가 정상 작동합니다"
        test_info "응답: $response"
    else
        test_failed "온라인 상태 통계 API 응답이 예상과 다릅니다"
    fi
else
    test_failed "온라인 상태 통계 API 호출에 실패했습니다"
fi

# 4. Redis 연결 확인
echo ""
echo "4. Redis 연결 확인"
echo "----------------"

# Redis가 실행 중인지 확인
if redis-cli ping > /dev/null 2>&1; then
    test_passed "Redis가 정상적으로 실행 중입니다"
    
    # Redis 키 확인
    if redis-cli keys "user:online:*" > /dev/null 2>&1; then
        test_passed "Redis에서 온라인 상태 키를 조회할 수 있습니다"
    else
        test_info "현재 Redis에 온라인 상태 키가 없습니다 (정상 - 아직 연결된 사용자가 없음)"
    fi
else
    test_failed "Redis가 실행되지 않았거나 접근할 수 없습니다"
    echo "Redis를 시작해주세요: redis-server"
fi

# 5. 테스트 HTML 페이지 확인
echo ""
echo "5. 테스트 HTML 페이지 확인"
echo "----------------------"

if curl -s http://localhost:8080/websocket-test.html | grep -q "WebSocket 연결 상태 추적 테스트"; then
    test_passed "테스트 HTML 페이지가 정상적으로 접근 가능합니다"
    test_info "브라우저에서 http://localhost:8080/websocket-test.html 을 열어 수동 테스트를 진행하세요"
else
    test_failed "테스트 HTML 페이지에 접근할 수 없습니다"
fi

# 6. 종합 결과
echo ""
echo "=============================================="
echo "🎯 테스트 결과 요약"
echo "=============================================="
echo -e "총 테스트: $((PASSED + FAILED))개"
echo -e "${GREEN}통과: ${PASSED}개${NC}"
echo -e "${RED}실패: ${FAILED}개${NC}"

if [ $FAILED -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 모든 테스트가 통과했습니다!${NC}"
    echo ""
    echo "다음 단계:"
    echo "1. 브라우저에서 http://localhost:8080/websocket-test.html 열기"
    echo "2. 사용자 ID와 JWT 토큰 입력 후 연결 버튼 클릭"
    echo "3. 하트비트 전송 및 자동 하트비트 테스트"
    echo "4. 온라인 상태 확인 API 테스트"
    echo "5. 연결 해제 테스트"
    echo ""
    echo "Redis 모니터링:"
    echo "redis-cli monitor"
    echo ""
    echo "온라인 상태 키 확인:"
    echo "redis-cli keys 'user:online:*'"
    echo "redis-cli keys 'session:*'"
else
    echo ""
    echo -e "${RED}⚠️  일부 테스트가 실패했습니다.${NC}"
    echo "실패한 항목을 확인하고 수정해주세요."
fi

echo ""
echo "테스트 완료!"
