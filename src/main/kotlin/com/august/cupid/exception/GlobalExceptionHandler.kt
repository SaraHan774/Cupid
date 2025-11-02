package com.august.cupid.exception

import com.august.cupid.model.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 처리기
 * 
 * 기능:
 * - 암호화 관련 예외를 적절한 HTTP 응답으로 변환
 * - 사용자 친화적인 오류 메시지 제공
 * - 일관된 오류 응답 형식 유지
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 암호화 예외 처리
     */
    @ExceptionHandler(EncryptionException::class)
    fun handleEncryptionException(e: EncryptionException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("암호화 예외 발생: ${e.errorCode} - ${e.message}", e.cause)
        
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
}
