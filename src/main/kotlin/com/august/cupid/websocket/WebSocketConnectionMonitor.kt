package com.august.cupid.websocket

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * WebSocket 연결 상태 모니터링 스케줄러
 * 
 * 기능:
 * 1. 주기적으로 하트비트 타임아웃 확인
 * 2. 오래된 연결 정리
 * 3. 연결 상태 통계 수집
 */
@Component
class WebSocketConnectionMonitor(
    private val messageHandler: WebSocketMessageHandler
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // 연결 상태 확인 주기 (밀리초)
        private const val CONNECTION_CHECK_INTERVAL_MS = 60000L  // 1분
    }

    /**
     * 주기적으로 연결 상태 확인
     * 하트비트 타임아웃된 사용자들을 오프라인 처리
     */
    @Scheduled(fixedDelay = CONNECTION_CHECK_INTERVAL_MS)
    fun checkConnectionStatus() {
        try {
            logger.debug("WebSocket 연결 상태 확인 시작")
            
            // 하트비트 타임아웃 확인 및 정리
            messageHandler.checkConnectionStatus()
            
            logger.debug("WebSocket 연결 상태 확인 완료")
            
        } catch (e: Exception) {
            logger.error("WebSocket 연결 상태 확인 중 오류 발생", e)
        }
    }

    /**
     * 연결 상태 통계 수집
     * 주기적으로 연결 상태 통계를 로그로 출력
     */
    @Scheduled(fixedDelay = 300000L) // 5분마다
    fun collectConnectionStats() {
        try {
            // 현재 연결 상태 통계 수집
            val stats = messageHandler.getConnectionStats()
            
            logger.info("WebSocket 연결 상태 통계: {}", stats)
            
        } catch (e: Exception) {
            logger.error("WebSocket 연결 상태 통계 수집 중 오류 발생", e)
        }
    }
}
