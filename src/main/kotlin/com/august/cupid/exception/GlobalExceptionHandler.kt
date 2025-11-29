package com.august.cupid.exception

import com.august.cupid.model.dto.ApiResponse
import com.august.cupid.service.EncryptionMetricsService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 전역 예외 처리기
 * 
 * 기능:
 * - 암호화 관련 예외를 적절한 HTTP 응답으로 변환
 * - 사용자 친화적인 오류 메시지 제공
 * - 일관된 오류 응답 형식 유지
 * - 에러 메트릭 수집 (Prometheus)
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val encryptionMetricsService: EncryptionMetricsService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 암호화 예외 처리
     */
    @ExceptionHandler(EncryptionException::class)
    fun handleEncryptionException(e: EncryptionException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("암호화 예외 발생: ${e.errorCode} - ${e.message}", e.cause)
        
        // 에러 메트릭 기록
        encryptionMetricsService.incrementErrorCount(
            errorType = "EncryptionException",
            operation = e.errorCode ?: "unknown"
        )
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 키 생성 예외 처리
     */
    @ExceptionHandler(KeyGenerationException::class)
    fun handleKeyGenerationException(e: KeyGenerationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("키 생성 예외: userId=${e.userId}, message=${e.message}", e.cause)
        
        // 에러 메트릭 기록
        encryptionMetricsService.incrementErrorCount(
            errorType = "KeyGenerationException",
            operation = "generate",
            tags = if (e.userId != null) {
                mapOf("user_id" to e.userId.toString())
            } else {
                emptyMap()
            }
        )
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = if (e.userId != null) {
                "Key generation failed for user ${e.userId}: ${e.message}"
            } else {
                "Key generation failed: ${e.message}"
            },
            errorCode = e.errorCode
        ))
    }

    /**
     * 키를 찾을 수 없을 때
     */
    @ExceptionHandler(KeysNotFoundException::class)
    fun handleKeysNotFoundException(e: KeysNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("키를 찾을 수 없음: userId=${e.userId}")
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 세션 초기화 예외 처리
     */
    @ExceptionHandler(SessionInitializationException::class)
    fun handleSessionInitializationException(e: SessionInitializationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("세션 초기화 예외: senderId=${e.senderId}, recipientId=${e.recipientId}, message=${e.message}", e.cause)
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 세션 없음 예외 처리
     */
    @ExceptionHandler(SessionNotFoundException::class)
    fun handleSessionNotFoundException(e: SessionNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("세션을 찾을 수 없음: userId=${e.userId}, recipientId=${e.recipientId}")
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 메시지 암호화 예외 처리
     */
    @ExceptionHandler(MessageEncryptionException::class)
    fun handleMessageEncryptionException(e: MessageEncryptionException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("메시지 암호화 예외: senderId=${e.senderId}, recipientId=${e.recipientId}, message=${e.message}", e.cause)
        
        // 에러 메트릭 기록
        encryptionMetricsService.incrementErrorCount(
            errorType = "MessageEncryptionException",
            operation = "encrypt",
            tags = mapOf(
                "sender_id" to (e.senderId?.toString() ?: "unknown"),
                "recipient_id" to (e.recipientId?.toString() ?: "unknown")
            )
        )
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = "Failed to encrypt message: ${e.message}",
            errorCode = e.errorCode
        ))
    }

    /**
     * 메시지 복호화 예외 처리
     */
    @ExceptionHandler(MessageDecryptionException::class)
    fun handleMessageDecryptionException(e: MessageDecryptionException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("메시지 복호화 예외: senderId=${e.senderId}, recipientId=${e.recipientId}, message=${e.message}", e.cause)
        
        // 에러 메트릭 기록
        encryptionMetricsService.incrementErrorCount(
            errorType = "MessageDecryptionException",
            operation = "decrypt",
            tags = mapOf(
                "sender_id" to (e.senderId?.toString() ?: "unknown"),
                "recipient_id" to (e.recipientId?.toString() ?: "unknown")
            )
        )
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = "Failed to decrypt message: ${e.message}",
            errorCode = e.errorCode
        ))
    }

    /**
     * 비밀번호 검증 예외 처리
     */
    @ExceptionHandler(PasswordValidationException::class)
    fun handlePasswordValidationException(e: PasswordValidationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("비밀번호 검증 실패: ${e.validationErrors?.joinToString(", ")}")
        
        val errorMessage = if (e.validationErrors.isNullOrEmpty()) {
            e.message
        } else {
            "${e.message}: ${e.validationErrors.joinToString(", ")}"
        }
        
        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = errorMessage,
            errorCode = e.errorCode,
            validationErrors = e.validationErrors
        ))
    }

    /**
     * SecurityException 처리 (키 생성 실패 등)
     */
    @ExceptionHandler(SecurityException::class)
    fun handleSecurityException(e: SecurityException): ResponseEntity<ApiResponse<Nothing>> {
        // 원인 예외 추출
        val cause = e.cause
        val rootCause = getRootCause(e)
        
        logger.error(
            "보안 예외 발생: ${e.message}${if (cause != null) " -> 원인: ${cause.javaClass.simpleName}: ${cause.message}" else ""}",
            e
        )
        
        // 원인 예외가 있으면 더 자세한 로그
        if (cause != null) {
            logger.error("원인 예외 스택 트레이스:", cause)
        }
        
        // 에러 메트릭 기록
        encryptionMetricsService.incrementErrorCount(
            errorType = "SecurityException",
            operation = "unknown"
        )
        
        // 사용자에게 반환할 에러 메시지 구성
        val errorMessage = when {
            // IllegalArgumentException은 원인으로 전달
            cause is IllegalArgumentException -> {
                "키 생성 실패: ${cause.message}"
            }
            // 다른 특정 예외들도 처리
            cause != null -> {
                "${e.message ?: "보안 관련 오류"}: ${cause.javaClass.simpleName} - ${cause.message}"
            }
            else -> {
                e.message ?: "보안 관련 오류가 발생했습니다."
            }
        }
        
        // 에러 메시지에 원인 정보 포함
        val fullErrorMessage = if (cause != null && cause.message != null && cause.message != errorMessage) {
            "$errorMessage (원인: ${cause.javaClass.simpleName})"
        } else {
            errorMessage
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
            success = false,
            error = fullErrorMessage,
            errorCode = "SECURITY_ERROR"
        ))
    }
    
    /**
     * 예외의 최상위 원인을 찾는 헬퍼 메서드
     */
    private fun getRootCause(throwable: Throwable): Throwable {
        var cause = throwable.cause
        while (cause != null && cause != throwable && cause.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        return cause ?: throwable
    }

    // ============================================
    // 비즈니스 예외 처리
    // ============================================

    /**
     * 비즈니스 예외 처리 (기본 클래스)
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("비즈니스 예외 발생: ${e.errorCode} - ${e.message}")

        return ResponseEntity.status(e.httpStatus).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 인증되지 않은 요청 예외 처리
     */
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(e: UnauthorizedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("인증 실패: ${e.message}")

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 접근 권한 없음 예외 처리
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("접근 거부: ${e.message}, resourceType=${e.resourceType}, resourceId=${e.resourceId}")

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 리소스를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(e: ResourceNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("리소스를 찾을 수 없음: ${e.resourceType}, id=${e.resourceId}")

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 사용자를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(e: UserNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("사용자를 찾을 수 없음: ${e.message}")

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 채널을 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ChannelNotFoundException::class)
    fun handleChannelNotFoundException(e: ChannelNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("채널을 찾을 수 없음: ${e.message}")

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 채널 접근 권한 없음 예외 처리
     */
    @ExceptionHandler(ChannelAccessDeniedException::class)
    fun handleChannelAccessDeniedException(e: ChannelAccessDeniedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("채널 접근 거부: ${e.message}")

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 메시지를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(MessageNotFoundException::class)
    fun handleMessageNotFoundException(e: MessageNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.debug("메시지를 찾을 수 없음: ${e.message}")

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 메시지 권한 없음 예외 처리
     */
    @ExceptionHandler(MessageAccessDeniedException::class)
    fun handleMessageAccessDeniedException(e: MessageAccessDeniedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("메시지 접근 거부: ${e.message}")

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 잘못된 요청 예외 처리
     */
    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequestException(e: BadRequestException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("잘못된 요청: ${e.message}, field=${e.field}")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 중복 리소스 예외 처리
     */
    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResourceException(e: DuplicateResourceException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("중복 리소스: ${e.message}, type=${e.resourceType}, field=${e.field}")

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 인증 실패 예외 처리
     */
    @ExceptionHandler(AuthenticationFailedException::class)
    fun handleAuthenticationFailedException(e: AuthenticationFailedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("인증 실패: ${e.message}")

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    /**
     * 유효하지 않은 토큰 예외 처리
     */
    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidTokenException(e: InvalidTokenException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("유효하지 않은 토큰: ${e.message}")

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse(
            success = false,
            error = e.message,
            errorCode = e.errorCode
        ))
    }

    // ============================================
    // 검증 및 입력 오류 처리
    // ============================================

    /**
     * @Valid 어노테이션 검증 실패 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errors = e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        logger.warn("입력값 검증 실패: ${errors.joinToString(", ")}")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(
            success = false,
            error = "입력값 검증에 실패했습니다",
            errorCode = "VALIDATION_ERROR",
            validationErrors = errors
        ))
    }

    /**
     * 요청 파라미터 타입 불일치 처리 (예: UUID 파싱 실패)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> {
        val message = "잘못된 파라미터 형식: ${e.name}"
        logger.warn("타입 불일치: ${e.name} = ${e.value}, 예상 타입: ${e.requiredType?.simpleName}")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(
            success = false,
            error = message,
            errorCode = "INVALID_PARAMETER_TYPE"
        ))
    }

    /**
     * 필수 파라미터 누락 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameterException(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Nothing>> {
        val message = "필수 파라미터가 누락되었습니다: ${e.parameterName}"
        logger.warn("필수 파라미터 누락: ${e.parameterName}")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(
            success = false,
            error = message,
            errorCode = "MISSING_PARAMETER"
        ))
    }

    /**
     * JSON 파싱 오류 처리
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("요청 본문 파싱 오류: ${e.message}")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(
            success = false,
            error = "요청 본문을 파싱할 수 없습니다",
            errorCode = "INVALID_REQUEST_BODY"
        ))
    }

    /**
     * 일반적인 IllegalArgumentException 처리 (비밀번호 강도 검증 등)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("잘못된 입력: ${e.message}")

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(
            success = false,
            error = e.message ?: "잘못된 입력입니다.",
            errorCode = "INVALID_INPUT"
        ))
    }

    /**
     * 예상치 못한 모든 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("예상치 못한 예외 발생: ${e.javaClass.simpleName} - ${e.message}", e)
        
        // 에러 메트릭 기록
        encryptionMetricsService.incrementErrorCount(
            errorType = e.javaClass.simpleName,
            operation = "unknown"
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse(
            success = false,
            error = "서버 오류가 발생했습니다. 관리자에게 문의하세요.",
            errorCode = "INTERNAL_SERVER_ERROR"
        ))
    }
}
