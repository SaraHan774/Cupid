package com.august.cupid.exception

import com.august.cupid.model.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

/**
 * 글로벌 예외 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 일관된 형식으로 처리
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 요청 데이터 검증 실패 (Jakarta Validation @Valid 실패)
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Map<String, String?>>> {
        logger.warn("입력 검증 실패: ${e.bindingResult.fieldErrors}")
        
        val errors = e.bindingResult.fieldErrors.associate { error: FieldError ->
            error.field to error.defaultMessage
        }
        
        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                error = "입력 데이터가 올바르지 않습니다",
                data = errors
            )
        )
    }

    /**
     * 인증 실패 (AuthenticationException)
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("인증 실패: ${e.message}")
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiResponse(
                success = false,
                error = "인증이 필요합니다"
            )
        )
    }

    /**
     * 권한 없음 (AccessDeniedException)
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("권한 없음: ${e.message}")
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiResponse(
                success = false,
                error = "접근 권한이 없습니다"
            )
        )
    }

    /**
     * 잘못된 경로 변수 타입 (예: UUID 파싱 실패)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("타입 불일치: ${e.message}")
        
        val errorMessage = when (e.requiredType) {
            UUID::class.java -> "유효하지 않은 UUID 형식입니다"
            else -> "잘못된 요청 파라미터입니다"
        }
        
        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                error = errorMessage
            )
        )
    }

    /**
     * IllegalArgumentException (비즈니스 로직 오류)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("잘못된 인수: ${e.message}")
        
        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                error = e.message ?: "잘못된 요청입니다"
            )
        )
    }

    /**
     * IllegalStateException (상태 오류)
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("잘못된 상태: ${e.message}")
        
        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                error = e.message ?: "잘못된 상태입니다"
            )
        )
    }

    /**
     * 일반적인 예외 (모든 나머지 예외)
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("예상치 못한 오류 발생", e)
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse(
                success = false,
                error = "서버 오류가 발생했습니다. 관리자에게 문의하세요"
            )
        )
    }
}

