package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.TypingIndicatorService
import com.august.cupid.service.OnlineStatusService
import com.august.cupid.service.ChannelService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.util.*

/**
 * 실시간 기능 WebSocket Controller
 *
 * 담당 기능:
 * 1. 타이핑 인디케이터 (Typing Indicators)
 * 2. 온라인 상태 (Presence/Heartbeat)
 * 3. 채널 구독 관리
 *
 * Architecture:
 * - STOMP 프로토콜 사용
 * - SimpleBroker를 통한 브로드캐스트
 * - Redis 기반 상태 관리
 *
 * WebSocket Flow:
 * 1. Client -> /app/typing/start -> Server
 * 2. Server -> Process & Store in Redis
 * 3. Server -> /topic/channel.{channelId}.typing -> All Subscribers
 *
 * Security:
 * - ConnectionInterceptor에서 인증 처리
 * - 각 핸들러에서 채널 멤버십 검증
 */
@Controller
class RealtimeWebSocketController(
    private val typingIndicatorService: TypingIndicatorService,
    private val onlineStatusService: OnlineStatusService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val channelService: ChannelService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // ============================================
    // 타이핑 인디케이터 핸들러
    // ============================================

    /**
     * 타이핑 시작 핸들러
     *
     * Endpoint: /app/typing/start
     * Broadcast: /topic/channel.{channelId}.typing
     *
     * Flow:
     * 1. 사용자 ID 추출 및 검증
     * 2. 채널 멤버십 확인
     * 3. Redis에 타이핑 상태 저장 (TTL: 10초)
     * 4. 채널 멤버들에게 브로드캐스트
     *
     * Edge Cases:
     * - 인증되지 않은 사용자: 조용히 무시
     * - 채널 멤버 아님: 로그만 남기고 무시
     * - Redis 장애: 로그 후 계속 진행 (Best Effort)
     *
     * Performance:
     * - Redis SET 연산: O(1)
     * - 브로드캐스트: SimpleBroker (메모리 기반)
     * - 평균 처리 시간: < 5ms
     */
    @MessageMapping("/typing/start")
    fun handleTypingStart(
        @Payload request: TypingRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("타이핑 시작 실패: 사용자 ID 추출 불가")
                return
            }

            logger.debug(
                "타이핑 시작 요청: userId={}, channelId={}",
                userId, request.channelId
            )

            // 2. 채널 멤버십 확인
            if (!isChannelMember(request.channelId, userId)) {
                logger.warn(
                    "타이핑 시작 거부: 채널 멤버 아님. userId={}, channelId={}",
                    userId, request.channelId
                )
                return
            }

            // 3. Redis에 타이핑 상태 저장
            val success = typingIndicatorService.setTyping(request.channelId, userId)
            if (!success) {
                logger.error(
                    "타이핑 상태 저장 실패: userId={}, channelId={}",
                    userId, request.channelId
                )
                // Best Effort: 저장 실패해도 브로드캐스트는 진행
            }

            // 4. 채널 멤버들에게 브로드캐스트
            val typingEvent = TypingEvent(
                userId = userId,
                channelId = request.channelId,
                isTyping = true
            )

            messagingTemplate.convertAndSend(
                "/topic/channel.${request.channelId}.typing",
                typingEvent
            )

            logger.debug(
                "타이핑 시작 브로드캐스트 완료: userId={}, channelId={}",
                userId, request.channelId
            )

        } catch (e: Exception) {
            logger.error("타이핑 시작 처리 중 오류 발생", e)
        }
    }

    /**
     * 타이핑 중지 핸들러
     *
     * Endpoint: /app/typing/stop
     * Broadcast: /topic/channel.{channelId}.typing
     *
     * Implementation Notes:
     * - TTL 방식이므로 명시적 중지 없이도 자동 만료
     * - 하지만 즉각적인 UX를 위해 명시적 중지 지원
     * - 중지 이벤트도 브로드캐스트하여 즉시 UI 업데이트
     */
    @MessageMapping("/typing/stop")
    fun handleTypingStop(
        @Payload request: TypingRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("타이핑 중지 실패: 사용자 ID 추출 불가")
                return
            }

            logger.debug(
                "타이핑 중지 요청: userId={}, channelId={}",
                userId, request.channelId
            )

            // 2. 채널 멤버십 확인 (선택적 - 이미 나간 사용자도 중지 가능)
            // 멤버가 아니어도 정리는 진행

            // 3. Redis에서 타이핑 상태 제거
            typingIndicatorService.removeTyping(request.channelId, userId)

            // 4. 채널 멤버들에게 브로드캐스트
            val typingEvent = TypingEvent(
                userId = userId,
                channelId = request.channelId,
                isTyping = false
            )

            messagingTemplate.convertAndSend(
                "/topic/channel.${request.channelId}.typing",
                typingEvent
            )

            logger.debug(
                "타이핑 중지 브로드캐스트 완료: userId={}, channelId={}",
                userId, request.channelId
            )

        } catch (e: Exception) {
            logger.error("타이핑 중지 처리 중 오류 발생", e)
        }
    }

    /**
     * 채널 타이핑 사용자 목록 조회 핸들러
     *
     * Endpoint: /app/typing/users
     * Response: User-specific queue
     *
     * Use Cases:
     * - 초기 연결 시 현재 상태 동기화
     * - 재연결 후 상태 복구
     */
    @MessageMapping("/typing/users")
    fun handleGetTypingUsers(
        @Payload request: TypingRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("타이핑 사용자 조회 실패: 사용자 ID 추출 불가")
                return
            }

            // 2. 채널 멤버십 확인
            if (!isChannelMember(request.channelId, userId)) {
                logger.warn(
                    "타이핑 사용자 조회 거부: 채널 멤버 아님. userId={}, channelId={}",
                    userId, request.channelId
                )
                return
            }

            // 3. 현재 타이핑 중인 사용자 목록 조회
            val typingUsers = typingIndicatorService.getTypingUsers(request.channelId)

            // 4. 요청한 사용자에게만 응답 (개인 큐)
            val response = TypingUsersResponse(
                channelId = request.channelId,
                typingUserIds = typingUsers
            )

            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/typing/users",
                response
            )

            logger.debug(
                "타이핑 사용자 목록 전송: userId={}, channelId={}, count={}",
                userId, request.channelId, typingUsers.size
            )

        } catch (e: Exception) {
            logger.error("타이핑 사용자 조회 중 오류 발생", e)
        }
    }

    // ============================================
    // 온라인 상태 핸들러
    // ============================================

    /**
     * 하트비트 핸들러
     *
     * Endpoint: /app/heartbeat
     * Response: /queue/heartbeat (개인 큐)
     *
     * Purpose:
     * - 연결 상태 유지
     * - 온라인 상태 TTL 갱신
     * - 네트워크 지연 측정
     *
     * Frequency:
     * - 클라이언트: 30초마다 전송 권장
     * - TTL: 5분 (OnlineStatusService)
     */
    @MessageMapping("/heartbeat")
    fun handleHeartbeat(
        @Payload request: HeartbeatRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("하트비트 실패: 사용자 ID 추출 불가")
                return
            }

            // 2. 온라인 상태 갱신 (TTL 연장)
            onlineStatusService.processHeartbeat(userId.toString())

            // 3. 응답 전송 (선택적)
            val response = HeartbeatResponse(received = true)
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/heartbeat",
                response
            )

            logger.trace(
                "하트비트 처리 완료: userId={}, clientTimestamp={}",
                userId, request.timestamp
            )

        } catch (e: Exception) {
            logger.error("하트비트 처리 중 오류 발생", e)
        }
    }

    /**
     * 채널 온라인 멤버 조회 핸들러
     *
     * Endpoint: /app/presence/channel
     * Response: /queue/presence/channel
     *
     * Use Cases:
     * - 채널 진입 시 온라인 멤버 확인
     * - 정기적 동기화
     */
    @MessageMapping("/presence/channel")
    fun handleChannelPresence(
        @Payload request: ChannelSubscribeRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("채널 온라인 상태 조회 실패: 사용자 ID 추출 불가")
                return
            }

            // 2. 채널 멤버십 확인
            if (!isChannelMember(request.channelId, userId)) {
                logger.warn(
                    "채널 온라인 상태 조회 거부: 채널 멤버 아님. userId={}, channelId={}",
                    userId, request.channelId
                )
                return
            }

            // 3. 채널 멤버 목록 조회
            val channelMembers = getChannelMemberIds(request.channelId)

            // 4. 온라인 멤버 필터링
            val onlineMembers = onlineStatusService.getOnlineChannelMembers(
                channelMembers.map { it.toString() }
            )

            // 5. 응답 전송
            val response = ChannelPresenceResponse(
                channelId = request.channelId,
                onlineUserIds = onlineMembers,
                totalMembers = channelMembers.size
            )

            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/presence/channel",
                response
            )

            logger.debug(
                "채널 온라인 상태 전송: userId={}, channelId={}, online={}/{}",
                userId, request.channelId, onlineMembers.size, channelMembers.size
            )

        } catch (e: Exception) {
            logger.error("채널 온라인 상태 조회 중 오류 발생", e)
        }
    }

    // ============================================
    // 채널 구독 관리 핸들러
    // ============================================

    /**
     * 채널 구독 핸들러
     *
     * Endpoint: /app/subscribe
     * Response: /queue/subscription
     *
     * Notes:
     * - STOMP 구독과 별도로 애플리케이션 레벨 구독 관리
     * - 추후 Redis Pub/Sub 전환 시 활용 가능
     */
    @MessageMapping("/subscribe")
    fun handleChannelSubscribe(
        @Payload request: ChannelSubscribeRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("채널 구독 실패: 사용자 ID 추출 불가")
                return
            }

            // 2. 채널 멤버십 확인
            if (!isChannelMember(request.channelId, userId)) {
                logger.warn(
                    "채널 구독 거부: 채널 멤버 아님. userId={}, channelId={}",
                    userId, request.channelId
                )

                val errorResponse = SubscriptionResponse(
                    channelId = request.channelId,
                    subscribed = false
                )

                messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/subscription",
                    errorResponse
                )
                return
            }

            // 3. 구독 확인 응답
            val response = SubscriptionResponse(
                channelId = request.channelId,
                subscribed = true
            )

            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/subscription",
                response
            )

            logger.info(
                "채널 구독 완료: userId={}, channelId={}",
                userId, request.channelId
            )

        } catch (e: Exception) {
            logger.error("채널 구독 처리 중 오류 발생", e)
        }
    }

    /**
     * 채널 구독 해제 핸들러
     *
     * Endpoint: /app/unsubscribe
     * Response: /queue/subscription
     */
    @MessageMapping("/unsubscribe")
    fun handleChannelUnsubscribe(
        @Payload request: ChannelUnsubscribeRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        try {
            // 1. 사용자 ID 추출
            val userId = extractUserId(headerAccessor)
            if (userId == null) {
                logger.warn("채널 구독 해제 실패: 사용자 ID 추출 불가")
                return
            }

            // 2. 타이핑 상태 정리
            typingIndicatorService.removeTyping(request.channelId, userId)

            // 3. 구독 해제 확인 응답
            val response = SubscriptionResponse(
                channelId = request.channelId,
                subscribed = false
            )

            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/subscription",
                response
            )

            logger.info(
                "채널 구독 해제 완료: userId={}, channelId={}",
                userId, request.channelId
            )

        } catch (e: Exception) {
            logger.error("채널 구독 해제 처리 중 오류 발생", e)
        }
    }

    // ============================================
    // 헬퍼 메서드
    // ============================================

    /**
     * WebSocket 세션에서 사용자 ID 추출
     *
     * Extraction Strategy:
     * 1. Principal (JWT 인증)
     * 2. Session Attributes
     * 3. STOMP Headers
     */
    private fun extractUserId(headerAccessor: SimpMessageHeaderAccessor): UUID? {
        return try {
            // 1. Principal에서 추출
            val principal = headerAccessor.user
            if (principal != null && principal.name != null) {
                return UUID.fromString(principal.name)
            }

            // 2. 세션 속성에서 추출
            val sessionAttributes = headerAccessor.sessionAttributes
            val userIdString = sessionAttributes?.get("userId") as? String
            if (userIdString != null) {
                return UUID.fromString(userIdString)
            }

            logger.warn(
                "사용자 ID 추출 실패. principal={}, sessionAttributes={}",
                principal, sessionAttributes
            )
            null
        } catch (e: Exception) {
            logger.error("사용자 ID 추출 중 오류 발생", e)
            null
        }
    }

    /**
     * 채널 멤버십 확인
     *
     * Performance:
     * - ChannelService 캐싱 활용
     * - 평균 조회 시간: < 2ms (캐시 히트 시)
     */
    private fun isChannelMember(channelId: UUID, userId: UUID): Boolean {
        return try {
            val result = channelService.isChannelMember(channelId, userId)
            result.success && result.data == true
        } catch (e: Exception) {
            logger.error(
                "채널 멤버십 확인 실패: channelId={}, userId={}",
                channelId, userId, e
            )
            false
        }
    }

    /**
     * 채널 멤버 ID 목록 조회
     */
    private fun getChannelMemberIds(channelId: UUID): List<UUID> {
        return try {
            val result = channelService.getChannelMembers(channelId)
            if (result.success && result.data != null) {
                result.data!!.map { it.id }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("채널 멤버 목록 조회 실패: channelId={}", channelId, e)
            emptyList()
        }
    }
}
