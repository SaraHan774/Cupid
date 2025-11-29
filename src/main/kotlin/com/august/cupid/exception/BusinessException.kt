package com.august.cupid.exception

import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * 비즈니스 로직 관련 커스텀 예외 클래스들
 *
 * 목적:
 * - 서비스 레이어에서 발생하는 비즈니스 예외 정의
 * - GlobalExceptionHandler에서 일관된 응답으로 변환
 * - 적절한 HTTP 상태 코드 매핑
 */

/**
 * 기본 비즈니스 예외 클래스
 */
open class BusinessException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String,
    val httpStatus: HttpStatus
) : RuntimeException(message, cause)

/**
 * 인증되지 않은 요청 예외
 */
class UnauthorizedException(
    message: String = "인증이 필요합니다",
    cause: Throwable? = null
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "UNAUTHORIZED",
    httpStatus = HttpStatus.UNAUTHORIZED
)

/**
 * 접근 권한 없음 예외
 */
class AccessDeniedException(
    message: String = "접근 권한이 없습니다",
    cause: Throwable? = null,
    val resourceType: String? = null,
    val resourceId: UUID? = null
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ACCESS_DENIED",
    httpStatus = HttpStatus.FORBIDDEN
)

/**
 * 리소스를 찾을 수 없음 예외
 */
class ResourceNotFoundException(
    message: String,
    val resourceType: String,
    val resourceId: Any? = null
) : BusinessException(
    message = message,
    errorCode = "${resourceType.uppercase()}_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND
)

/**
 * 사용자를 찾을 수 없음 예외
 */
class UserNotFoundException(
    userId: UUID? = null,
    username: String? = null
) : BusinessException(
    message = when {
        userId != null -> "사용자를 찾을 수 없습니다: $userId"
        username != null -> "사용자를 찾을 수 없습니다: $username"
        else -> "사용자를 찾을 수 없습니다"
    },
    errorCode = "USER_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND
)

/**
 * 채널을 찾을 수 없음 예외
 */
class ChannelNotFoundException(
    channelId: UUID
) : BusinessException(
    message = "채널을 찾을 수 없습니다: $channelId",
    errorCode = "CHANNEL_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND
)

/**
 * 채널 접근 권한 없음 예외
 */
class ChannelAccessDeniedException(
    channelId: UUID,
    userId: UUID? = null
) : BusinessException(
    message = "채널에 접근할 권한이 없습니다",
    errorCode = "CHANNEL_ACCESS_DENIED",
    httpStatus = HttpStatus.FORBIDDEN
)

/**
 * 메시지를 찾을 수 없음 예외
 */
class MessageNotFoundException(
    messageId: UUID
) : BusinessException(
    message = "메시지를 찾을 수 없습니다: $messageId",
    errorCode = "MESSAGE_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND
)

/**
 * 메시지 권한 없음 예외
 */
class MessageAccessDeniedException(
    message: String = "메시지에 대한 권한이 없습니다"
) : BusinessException(
    message = message,
    errorCode = "MESSAGE_ACCESS_DENIED",
    httpStatus = HttpStatus.FORBIDDEN
)

/**
 * 잘못된 요청 예외
 */
class BadRequestException(
    message: String,
    val field: String? = null
) : BusinessException(
    message = message,
    errorCode = "BAD_REQUEST",
    httpStatus = HttpStatus.BAD_REQUEST
)

/**
 * 중복 리소스 예외
 */
class DuplicateResourceException(
    message: String,
    val resourceType: String,
    val field: String? = null
) : BusinessException(
    message = message,
    errorCode = "${resourceType.uppercase()}_DUPLICATE",
    httpStatus = HttpStatus.CONFLICT
)

/**
 * 인증 실패 예외 (로그인 실패 등)
 */
class AuthenticationFailedException(
    message: String = "인증에 실패했습니다"
) : BusinessException(
    message = message,
    errorCode = "AUTHENTICATION_FAILED",
    httpStatus = HttpStatus.UNAUTHORIZED
)

/**
 * 토큰 관련 예외
 */
class InvalidTokenException(
    message: String = "유효하지 않은 토큰입니다"
) : BusinessException(
    message = message,
    errorCode = "INVALID_TOKEN",
    httpStatus = HttpStatus.UNAUTHORIZED
)
