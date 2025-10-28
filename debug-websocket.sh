#!/bin/bash

echo "🔍 WebSocket 연결 문제 진단"
echo "=========================="

echo "1. 서버 상태 확인"
if curl -s http://localhost:8080/api/v1/health > /dev/null; then
    echo "✅ 서버가 정상 작동 중"
else
    echo "❌ 서버가 응답하지 않음"
    exit 1
fi

echo ""
echo "2. SockJS 엔드포인트 확인"
sockjs_response=$(curl -s "http://localhost:8080/ws/info")
if [[ $sockjs_response == *"websocket"* ]]; then
    echo "✅ SockJS 엔드포인트 정상: $sockjs_response"
else
    echo "❌ SockJS 엔드포인트 문제: $sockjs_response"
fi

echo ""
echo "3. WebSocket 연결 테스트 (SockJS 사용)"
echo "브라우저에서 http://localhost:8080/websocket-test.html 을 열어 수동 테스트를 진행하세요"
echo "사용자 ID: test-user-1"
echo "연결 버튼을 클릭하여 연결 상태를 확인하세요"

echo ""
echo "4. 온라인 상태 API 테스트"
online_users=$(curl -s http://localhost:8080/api/v1/online-status/users)
echo "온라인 사용자 목록: $online_users"

echo ""
echo "5. Redis 연결 확인"
if redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis 연결 정상"
    redis-cli keys "user:online:*" | head -5
else
    echo "❌ Redis 연결 실패"
fi

echo ""
echo "🎯 진단 완료"
echo "브라우저에서 WebSocket 테스트를 진행해주세요."
