package com.august.cupid.exception

import org.springframework.http.HttpStatus

/**
 * 암호화 관련 커스텀 예외 클래스들
 * 
 * 목적:
 * - 사용자 친화적인 오류 메시지 제공
 * - 적절한 HTTP 상태 코드 매핑
 * - 오류 타입별 구분
 * - 디버깅을 위한 상세 정보 제공
 */

/**
 * 기본 암호화 예외 클래스
 */
sealed class EncryptionException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String,
    val httpStatus: HttpStatus
) : Exception(message, cause)

/**
 * 키 생성 관련 예외
 */
class KeyGenerationException(
    message: String,
    cause: Throwable? = null,
    val userId: String? = null
) : EncryptionException(
    message = message,
    cause = cause,
    errorCode = "KEY_GENERATION_ERROR",
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
)

/**
 * 키 등록 관련 예외
 */
class KeyRegistrationException(
    message: String,
    cause: Throwable? = null,
    val userId: String? = null
) : EncryptionException(
    message = message,
    cause = cause,
    errorCode = "KEY_REGISTRATION_ERROR",
    httpStatus = HttpStatus.BAD_REQUEST
)

/**
 * 키를 찾을 수 없을 때 발생하는 예외
 */
class KeysNotFoundException(
    message: String = "User keys not found. User may not have registered keys yet.",
    val userId: String? = null
) : EncryptionException(
    message = message,
    errorCode = "KEYS_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND
)

/**
 * 세션 초기화 관련 예외
 */
class SessionInitializationException(
    message: String,
    cause: Throwable? = null,
    val senderId: String? = null,
    val recipientId: String? = null
) : EncryptionException(
    message = message,
    cause = cause,
    errorCode = "SESSION_INIT_ERROR",
    httpStatus = HttpStatus.BAD_REQUEST
)

/**
 * 세션이 존재하지 않을 때 발생하는 예외
 */
class SessionNotFoundException(
    message: String = "Session does not exist. Please initialize session first.",
    val userId: String? = null,
    val recipientId: String? = null
) : EncryptionException(
    message = message,
    errorCode = "SESSION_NOT_FOUND",
    httpStatus = HttpStatus.BAD_REQUEST
)

/**
 * 메시지 암호화 관련 예외
 */
class MessageEncryptionException(
    message: String,
    cause: Throwable? = null,
    val senderId: String? = null,
    val recipientId: String? = null
) : EncryptionException(
    message = message,
    cause = cause,
    errorCode = "MESSAGE_ENCRYPTION_ERROR",
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
)

/**
 * 메시지 복호화 관련 예외
 */
class MessageDecryptionException(
    message: String,
    cause: Throwable? = null,
    val senderId: String? = null,
    val recipientId: String? = null
) : EncryptionException(
    message = message,
    errorCode = "MESSAGE_DECRYPTION_ERROR",
    httpStatus = HttpStatus.BAD_REQUEST
)

/**
 * 키 번들 조회 관련 예외
 */
class KeyBundleException(
    message: String,
    cause: Throwable? = null,
    val userId: String? = null
) : EncryptionException(
    message = message,
    cause = cause,
    errorCode = "KEY_BUNDLE_ERROR",
    httpStatus = HttpStatus.NOT_FOUND
)

/**
 * 키 회전 관련 예외
 */
class KeyRotationException(
    message: String,
    cause: Throwable? = null,
    val userId: String? = null
) : EncryptionException(
    message = message,
    cause = cause,
    errorCode = "KEY_ROTATION_ERROR",
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
)

/**
 * 보안 관련 예외 (MITM 감지 등)
 */
class SecurityException(
    message: String,
    cause: Throwable? = null,
    val securityEvent: String? = null
) : EncryptionException(
    message = message,
    cause = cause,
    errorCode = "SECURITY_ERROR",
    httpStatus = HttpStatus.FORBIDDEN
)

/**
 * 비밀번호 검증 관련 예외
 */
class PasswordValidationException(
    message: String,
    val validationErrors: List<String>? = null
) : EncryptionException(
    message = message,
    errorCode = "PASSWORD_VALIDATION_ERROR",
    httpStatus = HttpStatus.BAD_REQUEST
)

