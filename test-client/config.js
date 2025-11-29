// ============================================
// API 설정 파일
// 환경에 따라 URL을 변경하세요
// ============================================

// 프로덕션 환경 설정
const PRODUCTION_CONFIG = {
    API_BASE: 'http://cupid-alb-378055256.us-east-1.elb.amazonaws.com/api/v1',
    WS_BASE: 'ws://cupid-alb-378055256.us-east-1.elb.amazonaws.com/ws'
};

// 로컬 개발 환경 설정
const LOCAL_CONFIG = {
    API_BASE: 'http://localhost:8080/api/v1',
    WS_BASE: 'ws://localhost:8080/ws'
};

// 현재 사용할 환경 선택 (true: 프로덕션, false: 로컬)
const USE_PRODUCTION = true;

// 현재 설정 적용
const CURRENT_CONFIG = USE_PRODUCTION ? PRODUCTION_CONFIG : LOCAL_CONFIG;

// 전역 변수로 내보내기
window.API_BASE = CURRENT_CONFIG.API_BASE;
window.WS_BASE = CURRENT_CONFIG.WS_BASE;

// 콘솔에 현재 환경 출력
console.log(`🌍 현재 환경: ${USE_PRODUCTION ? '프로덕션' : '로컬 개발'}`);
console.log(`📡 API Base URL: ${window.API_BASE}`);
console.log(`🔌 WebSocket URL: ${window.WS_BASE}`);

