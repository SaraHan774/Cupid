package com.august.cupid.model.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.*

/**
 * 프로필 이미지 업로드 응답 DTO
 */
data class ProfileImageUploadResponse(
    val success: Boolean,
    val message: String? = null,
    val imageUrls: ImageUrls? = null,
    val blurhash: String? = null,
    val metadata: ProfileImageMetadata? = null
)

/**
 * 다양한 해상도의 이미지 URL
 */
data class ImageUrls(
    val original: String,      // 800x800 - 프로필 상세 페이지
    val large: String,         // 400x400 - 프로필 팝업
    val medium: String,        // 200x200 - 채팅 헤더
    val small: String,         // 100x100 - 채팅 리스트/아바타
    val thumbnail: String      // small과 동일 (호환성 유지)
)

/**
 * 프로필 이미지 메타데이터
 */
data class ProfileImageMetadata(
    val originalFileName: String,
    val originalSize: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val mimeType: String,
    val format: String,
    val uploadedAt: LocalDateTime = LocalDateTime.now(),
    val resolutions: Map<String, ResolutionMetadata>
)

/**
 * 각 해상도별 메타데이터
 */
data class ResolutionMetadata(
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val format: String,        // webp, jpeg
    val url: String
)

/**
 * 프로필 이미지 삭제 응답 DTO
 */
data class ProfileImageDeleteResponse(
    val success: Boolean,
    val message: String
)

/**
 * 이미지 처리 설정
 */
data class ImageProcessingConfig(
    @field:Min(50)
    @field:Max(2000)
    val maxWidth: Int = 800,

    @field:Min(50)
    @field:Max(2000)
    val maxHeight: Int = 800,

    @field:Min(0)
    @field:Max(100)
    val jpegQuality: Int = 85,

    @field:Min(0)
    @field:Max(100)
    val webpQuality: Int = 80,

    val generateWebP: Boolean = true,
    val generateJpegFallback: Boolean = true,
    val generateBlurhash: Boolean = true
)

/**
 * 이미지 해상도 정의
 */
enum class ImageResolution(
    val width: Int,
    val height: Int,
    val suffix: String,
    val description: String
) {
    ORIGINAL(800, 800, "original", "프로필 상세 페이지"),
    LARGE(400, 400, "large", "프로필 팝업"),
    MEDIUM(200, 200, "medium", "채팅 헤더"),
    SMALL(100, 100, "small", "채팅 리스트/아바타");

    companion object {
        fun getAll(): List<ImageResolution> = values().toList()
    }
}

/**
 * 이미지 업로드 검증 결과
 */
data class ImageValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val fileSize: Long = 0,
    val mimeType: String? = null,
    val extension: String? = null
)

/**
 * S3 업로드 결과
 */
data class S3UploadResult(
    val success: Boolean,
    val url: String? = null,
    val key: String? = null,
    val errorMessage: String? = null
)

/**
 * 이미지 최적화 결과
 */
data class OptimizedImage(
    val resolution: ImageResolution,
    val webpData: ByteArray,
    val jpegData: ByteArray,
    val webpSize: Long,
    val jpegSize: Long,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OptimizedImage

        if (resolution != other.resolution) return false
        if (!webpData.contentEquals(other.webpData)) return false
        if (!jpegData.contentEquals(other.jpegData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resolution.hashCode()
        result = 31 * result + webpData.contentHashCode()
        result = 31 * result + jpegData.contentHashCode()
        return result
    }
}

/**
 * BlurHash 생성 결과
 */
data class BlurHashResult(
    val hash: String,
    val componentX: Int = 4,
    val componentY: Int = 3
)

/**
 * 프로필 이미지 상세 조회 응답
 */
data class ProfileImageDetailsResponse(
    val userId: UUID,
    val imageUrls: ImageUrls?,
    val blurhash: String?,
    val metadata: ProfileImageMetadata?,
    val hasImage: Boolean
)
