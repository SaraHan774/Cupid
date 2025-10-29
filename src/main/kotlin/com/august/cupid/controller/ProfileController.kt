package com.august.cupid.controller

import com.august.cupid.model.dto.*
import com.august.cupid.service.ProfileImageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

/**
 * 프로필 관리 컨트롤러
 *
 * 기능:
 * - 프로필 이미지 업로드
 * - 프로필 이미지 조회
 * - 프로필 이미지 삭제
 */
@Tag(name = "Profile", description = "프로필 관리 API - 프로필 이미지 업로드/조회/삭제")
@RestController
@RequestMapping("/api/v1/users")
class ProfileController(
    private val profileImageService: ProfileImageService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * JWT에서 사용자 ID 추출
     */
    private fun getUserIdFromAuthentication(authentication: Authentication): UUID? {
        return try {
            val userIdString = authentication.name
            UUID.fromString(userIdString)
        } catch (e: Exception) {
            logger.error("사용자 ID 추출 실패", e)
            null
        }
    }

    /**
     * 프로필 이미지 업로드
     * POST /api/v1/users/profile-image
     *
     * 기능:
     * - 이미지 검증 (크기, 형식)
     * - 다중 해상도 생성 (Original: 800x800, Large: 400x400, Medium: 200x200, Small: 100x100)
     * - WebP 변환 + JPEG 폴백
     * - BlurHash 생성
     * - S3/로컬 스토리지 업로드
     * - 메타데이터 저장
     * - 기존 이미지 삭제
     *
     * 처리 시간 목표: < 2초
     */
    @Operation(
        summary = "프로필 이미지 업로드",
        description = """
            프로필 이미지를 업로드하고 여러 해상도로 최적화합니다.

            **처리 과정:**
            1. 이미지 검증 (최대 10MB, JPEG/PNG/WebP 지원)
            2. 4가지 해상도 생성 (800x800, 400x400, 200x200, 100x100)
            3. WebP 포맷 변환 + JPEG 폴백
            4. BlurHash 생성 (로딩 placeholder용)
            5. S3/로컬 스토리지 업로드
            6. 메타데이터 저장

            **제약사항:**
            - 최대 파일 크기: 10MB
            - 지원 형식: JPEG, PNG, WebP
            - 최소 크기: 100x100
            - 최대 크기: 4000x4000
        """
    )
    @PostMapping(
        "/profile-image",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun uploadProfileImage(
        authentication: Authentication,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false, defaultValue = "85") jpegQuality: Int,
        @RequestParam(required = false, defaultValue = "80") webpQuality: Int,
        @RequestParam(required = false, defaultValue = "true") generateBlurhash: Boolean
    ): ResponseEntity<ProfileImageUploadResponse> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ProfileImageUploadResponse(
                    success = false,
                    message = "인증 정보를 찾을 수 없습니다"
                )
            )
        }

        logger.info(
            "프로필 이미지 업로드 요청: userId={}, fileName={}, size={}",
            userId, file.originalFilename, file.size
        )

        return try {
            val config = ImageProcessingConfig(
                jpegQuality = jpegQuality,
                webpQuality = webpQuality,
                generateBlurhash = generateBlurhash
            )

            val response = runBlocking {
                profileImageService.uploadProfileImage(userId, file, config)
            }

            if (response.success) {
                logger.info("프로필 이미지 업로드 성공: userId={}", userId)
                ResponseEntity.ok(response)
            } else {
                logger.warn("프로필 이미지 업로드 실패: userId={}, message={}", userId, response.message)
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("프로필 이미지 업로드 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ProfileImageUploadResponse(
                    success = false,
                    message = "서버 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 프로필 이미지 조회
     * GET /api/v1/users/{userId}/profile-image
     *
     * 기능:
     * - 프로필 이미지 URL 조회
     * - BlurHash 조회
     * - 메타데이터 조회
     */
    @Operation(
        summary = "프로필 이미지 조회",
        description = """
            특정 사용자의 프로필 이미지 정보를 조회합니다.

            **반환 정보:**
            - 다양한 해상도 URL (original, large, medium, small)
            - BlurHash (로딩 placeholder용)
            - 메타데이터 (원본 크기, 업로드 시간 등)
        """
    )
    @GetMapping("/{userId}/profile-image")
    fun getProfileImage(
        @PathVariable userId: String
    ): ResponseEntity<ProfileImageDetailsResponse> {
        return try {
            val userIdUuid = UUID.fromString(userId)
            val response = profileImageService.getProfileImageDetails(userIdUuid)

            ResponseEntity.ok(response)

        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 사용자 ID 형식: {}", userId, e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ProfileImageDetailsResponse(
                    userId = UUID.randomUUID(),
                    imageUrls = null,
                    blurhash = null,
                    metadata = null,
                    hasImage = false
                )
            )
        } catch (e: Exception) {
            logger.error("프로필 이미지 조회 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ProfileImageDetailsResponse(
                    userId = UUID.randomUUID(),
                    imageUrls = null,
                    blurhash = null,
                    metadata = null,
                    hasImage = false
                )
            )
        }
    }

    /**
     * 내 프로필 이미지 조회
     * GET /api/v1/users/me/profile-image
     *
     * 기능:
     * - 현재 로그인한 사용자의 프로필 이미지 조회
     */
    @Operation(
        summary = "내 프로필 이미지 조회",
        description = "현재 로그인한 사용자의 프로필 이미지 정보를 조회합니다."
    )
    @GetMapping("/me/profile-image")
    fun getMyProfileImage(
        authentication: Authentication
    ): ResponseEntity<ProfileImageDetailsResponse> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ProfileImageDetailsResponse(
                    userId = UUID.randomUUID(),
                    imageUrls = null,
                    blurhash = null,
                    metadata = null,
                    hasImage = false
                )
            )
        }

        return try {
            val response = profileImageService.getProfileImageDetails(userId)
            ResponseEntity.ok(response)

        } catch (e: Exception) {
            logger.error("내 프로필 이미지 조회 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ProfileImageDetailsResponse(
                    userId = userId,
                    imageUrls = null,
                    blurhash = null,
                    metadata = null,
                    hasImage = false
                )
            )
        }
    }

    /**
     * 프로필 이미지 삭제
     * DELETE /api/v1/users/profile-image
     *
     * 기능:
     * - 프로필 이미지 삭제
     * - S3/로컬 스토리지에서 파일 삭제
     * - DB에서 메타데이터 삭제
     */
    @Operation(
        summary = "프로필 이미지 삭제",
        description = """
            현재 로그인한 사용자의 프로필 이미지를 삭제합니다.

            **처리 과정:**
            1. DB에서 프로필 이미지 정보 삭제
            2. S3/로컬 스토리지에서 모든 해상도 파일 삭제
        """
    )
    @DeleteMapping("/profile-image")
    fun deleteProfileImage(
        authentication: Authentication
    ): ResponseEntity<ProfileImageDeleteResponse> {
        val userId = getUserIdFromAuthentication(authentication)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ProfileImageDeleteResponse(
                    success = false,
                    message = "인증 정보를 찾을 수 없습니다"
                )
            )
        }

        logger.info("프로필 이미지 삭제 요청: userId={}", userId)

        return try {
            val response = runBlocking {
                profileImageService.deleteProfileImage(userId)
            }

            if (response.success) {
                logger.info("프로필 이미지 삭제 성공: userId={}", userId)
                ResponseEntity.ok(response)
            } else {
                logger.warn("프로필 이미지 삭제 실패: userId={}, message={}", userId, response.message)
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("프로필 이미지 삭제 중 오류 발생: userId={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ProfileImageDeleteResponse(
                    success = false,
                    message = "서버 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 프로필 이미지 업로드 제약사항 조회
     * GET /api/v1/users/profile-image/constraints
     *
     * 기능:
     * - 최대 파일 크기
     * - 지원 형식
     * - 최소/최대 이미지 크기
     */
    @Operation(
        summary = "프로필 이미지 업로드 제약사항 조회",
        description = "프로필 이미지 업로드 시 적용되는 제약사항을 조회합니다."
    )
    @GetMapping("/profile-image/constraints")
    fun getUploadConstraints(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "maxFileSize" to (10 * 1024 * 1024), // 10MB
                "maxFileSizeReadable" to "10MB",
                "supportedFormats" to listOf("JPEG", "PNG", "WebP"),
                "supportedMimeTypes" to listOf("image/jpeg", "image/png", "image/webp"),
                "minImageSize" to 100,
                "maxImageSize" to 4000,
                "resolutions" to ImageResolution.getAll().map {
                    mapOf(
                        "name" to it.suffix,
                        "width" to it.width,
                        "height" to it.height,
                        "description" to it.description
                    )
                }
            )
        )
    }
}
