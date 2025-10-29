package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.august.cupid.repository.UserRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.*

/**
 * 프로필 이미지 서비스
 *
 * 기능:
 * - 프로필 이미지 업로드
 * - 다중 해상도 생성
 * - WebP 변환 + JPEG 폴백
 * - BlurHash 생성
 * - S3/로컬 스토리지 업로드
 * - 기존 이미지 삭제
 */
@Service
@Transactional
class ProfileImageService(
    private val userRepository: UserRepository,
    private val imageOptimizationService: ImageOptimizationService,
    private val storageService: StorageService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 프로필 이미지 업로드 (메인 메서드)
     *
     * 처리 흐름:
     * 1. 이미지 검증
     * 2. 이미지 읽기
     * 3. 다중 해상도 생성 (병렬 처리)
     * 4. BlurHash 생성
     * 5. S3/로컬 스토리지 업로드 (병렬 처리)
     * 6. 메타데이터 생성
     * 7. DB 업데이트
     * 8. 기존 이미지 삭제
     */
    suspend fun uploadProfileImage(
        userId: UUID,
        file: MultipartFile,
        config: ImageProcessingConfig = ImageProcessingConfig()
    ): ProfileImageUploadResponse = coroutineScope {
        val startTime = System.currentTimeMillis()

        try {
            logger.info("프로필 이미지 업로드 시작: userId={}, fileName={}", userId, file.originalFilename)

            // 1. 사용자 확인
            val user = userRepository.findById(userId).orElse(null)
                ?: return@coroutineScope ProfileImageUploadResponse(
                    success = false,
                    message = "사용자를 찾을 수 없습니다"
                )

            // 2. 이미지 검증
            val validationResult = imageOptimizationService.validateImage(file)
            if (!validationResult.isValid) {
                logger.warn("이미지 검증 실패: userId={}, error={}", userId, validationResult.errorMessage)
                return@coroutineScope ProfileImageUploadResponse(
                    success = false,
                    message = validationResult.errorMessage
                )
            }

            // 3. 이미지 읽기
            val originalImage = imageOptimizationService.readImage(file)
            logger.debug("이미지 읽기 완료: dimensions={}x{}", originalImage.width, originalImage.height)

            // 4. 병렬 처리: 다중 해상도 생성 & BlurHash 생성
            val optimizedImagesDeferred = async(Dispatchers.Default) {
                imageOptimizationService.generateMultipleResolutions(originalImage, config)
            }

            val blurhashDeferred = async(Dispatchers.Default) {
                if (config.generateBlurhash) {
                    imageOptimizationService.generateBlurHash(originalImage)
                } else {
                    null
                }
            }

            val optimizedImages = optimizedImagesDeferred.await()
            val blurhashResult = blurhashDeferred.await()

            logger.info("이미지 처리 완료: resolutions={}, blurhash={}",
                optimizedImages.size, blurhashResult?.hash)

            // 5. 스토리지 업로드 (병렬 처리)
            val uploadResults = uploadAllResolutions(userId, optimizedImages)

            // 6. URL 맵 생성
            val imageUrls = createImageUrls(uploadResults)

            // 7. 메타데이터 생성
            val metadata = createMetadata(file, originalImage, optimizedImages, uploadResults)

            // 8. DB 업데이트 (기존 이미지 URL 저장)
            val oldImageKeys = extractOldImageKeys(user)

            val updatedUser = user.copy(
                profileImageUrl = imageUrls.original,
                profileThumbnailUrl = imageUrls.small,
                profileImageBlurhash = blurhashResult?.hash,
                profileImageMetadata = convertMetadataToMap(metadata),
                updatedAt = LocalDateTime.now()
            )

            userRepository.save(updatedUser)
            logger.info("사용자 프로필 이미지 업데이트 완료: userId={}", userId)

            // 9. 기존 이미지 삭제 (백그라운드)
            if (oldImageKeys.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    deleteOldImages(oldImageKeys)
                }
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            logger.info("프로필 이미지 업로드 완료: userId={}, elapsed={}ms", userId, elapsedTime)

            ProfileImageUploadResponse(
                success = true,
                message = "프로필 이미지가 성공적으로 업로드되었습니다",
                imageUrls = imageUrls,
                blurhash = blurhashResult?.hash,
                metadata = metadata
            )

        } catch (e: Exception) {
            logger.error("프로필 이미지 업로드 실패: userId={}", userId, e)
            ProfileImageUploadResponse(
                success = false,
                message = "이미지 업로드 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }

    /**
     * 모든 해상도를 스토리지에 업로드 (병렬 처리)
     */
    private suspend fun uploadAllResolutions(
        userId: UUID,
        optimizedImages: List<OptimizedImage>
    ): Map<String, S3UploadResult> = coroutineScope {
        val uploadResults = mutableMapOf<String, S3UploadResult>()

        val jobs = optimizedImages.flatMap { optimized ->
            listOf(
                // WebP 업로드
                async(Dispatchers.IO) {
                    val fileName = storageService.generateUniqueFileName(
                        userId, optimized.resolution.suffix, "webp"
                    )
                    val result = storageService.uploadFile(
                        optimized.webpData,
                        fileName,
                        "image/webp",
                        "profile-images"
                    )
                    "${optimized.resolution.suffix}_webp" to result
                },
                // JPEG 업로드
                async(Dispatchers.IO) {
                    val fileName = storageService.generateUniqueFileName(
                        userId, optimized.resolution.suffix, "jpeg"
                    )
                    val result = storageService.uploadFile(
                        optimized.jpegData,
                        fileName,
                        "image/jpeg",
                        "profile-images"
                    )
                    "${optimized.resolution.suffix}_jpeg" to result
                }
            )
        }

        jobs.forEach { job ->
            val (key, result) = job.await()
            uploadResults[key] = result
        }

        logger.info("스토리지 업로드 완료: count={}", uploadResults.size)
        uploadResults
    }

    /**
     * 업로드 결과에서 ImageUrls 생성
     */
    private fun createImageUrls(uploadResults: Map<String, S3UploadResult>): ImageUrls {
        // WebP 우선, 실패 시 JPEG 사용
        fun getUrl(resolution: String): String {
            return uploadResults["${resolution}_webp"]?.url
                ?: uploadResults["${resolution}_jpeg"]?.url
                ?: ""
        }

        return ImageUrls(
            original = getUrl("original"),
            large = getUrl("large"),
            medium = getUrl("medium"),
            small = getUrl("small"),
            thumbnail = getUrl("small")
        )
    }

    /**
     * 메타데이터 생성
     */
    private fun createMetadata(
        file: MultipartFile,
        originalImage: java.awt.image.BufferedImage,
        optimizedImages: List<OptimizedImage>,
        uploadResults: Map<String, S3UploadResult>
    ): ProfileImageMetadata {
        val resolutionsMap = mutableMapOf<String, ResolutionMetadata>()

        optimizedImages.forEach { optimized ->
            // WebP 버전
            val webpKey = "${optimized.resolution.suffix}_webp"
            uploadResults[webpKey]?.let { result ->
                if (result.success && result.url != null) {
                    resolutionsMap[webpKey] = ResolutionMetadata(
                        width = optimized.width,
                        height = optimized.height,
                        fileSize = optimized.webpSize,
                        format = "webp",
                        url = result.url
                    )
                }
            }

            // JPEG 버전
            val jpegKey = "${optimized.resolution.suffix}_jpeg"
            uploadResults[jpegKey]?.let { result ->
                if (result.success && result.url != null) {
                    resolutionsMap[jpegKey] = ResolutionMetadata(
                        width = optimized.width,
                        height = optimized.height,
                        fileSize = optimized.jpegSize,
                        format = "jpeg",
                        url = result.url
                    )
                }
            }
        }

        return ProfileImageMetadata(
            originalFileName = file.originalFilename ?: "unknown",
            originalSize = file.size,
            originalWidth = originalImage.width,
            originalHeight = originalImage.height,
            mimeType = file.contentType ?: "image/jpeg",
            format = file.originalFilename?.substringAfterLast('.', "jpeg")?.lowercase() ?: "jpeg",
            resolutions = resolutionsMap
        )
    }

    /**
     * 메타데이터를 Map으로 변환 (DB 저장용)
     */
    private fun convertMetadataToMap(metadata: ProfileImageMetadata): Map<String, Any> {
        return mapOf(
            "originalFileName" to metadata.originalFileName,
            "originalSize" to metadata.originalSize,
            "originalWidth" to metadata.originalWidth,
            "originalHeight" to metadata.originalHeight,
            "mimeType" to metadata.mimeType,
            "format" to metadata.format,
            "uploadedAt" to metadata.uploadedAt.toString(),
            "resolutions" to metadata.resolutions.mapValues { (_, v) ->
                mapOf(
                    "width" to v.width,
                    "height" to v.height,
                    "fileSize" to v.fileSize,
                    "format" to v.format,
                    "url" to v.url
                )
            }
        )
    }

    /**
     * 기존 이미지 키 추출
     */
    private fun extractOldImageKeys(user: com.august.cupid.model.entity.User): List<String> {
        val keys = mutableListOf<String>()

        user.profileImageMetadata?.let { metadata ->
            @Suppress("UNCHECKED_CAST")
            val resolutions = metadata["resolutions"] as? Map<String, Any>
            resolutions?.values?.forEach { resolutionData ->
                @Suppress("UNCHECKED_CAST")
                val resMap = resolutionData as? Map<String, Any>
                val url = resMap?.get("url") as? String
                if (url != null) {
                    // URL에서 키 추출 (예: http://localhost:8080/uploads/profile-images/xxx.jpg -> profile-images/xxx.jpg)
                    val key = url.substringAfter("/uploads/", "")
                    if (key.isNotEmpty()) {
                        keys.add(key)
                    }
                }
            }
        }

        return keys
    }

    /**
     * 기존 이미지 삭제
     */
    private fun deleteOldImages(keys: List<String>) {
        try {
            val deletedCount = storageService.deleteFiles(keys)
            logger.info("기존 이미지 삭제 완료: total={}, deleted={}", keys.size, deletedCount)
        } catch (e: Exception) {
            logger.error("기존 이미지 삭제 실패", e)
        }
    }

    /**
     * 프로필 이미지 삭제
     */
    suspend fun deleteProfileImage(userId: UUID): ProfileImageDeleteResponse = coroutineScope {
        try {
            logger.info("프로필 이미지 삭제 시작: userId={}", userId)

            val user = userRepository.findById(userId).orElse(null)
                ?: return@coroutineScope ProfileImageDeleteResponse(
                    success = false,
                    message = "사용자를 찾을 수 없습니다"
                )

            // 기존 이미지 키 추출
            val oldImageKeys = extractOldImageKeys(user)

            // DB 업데이트
            val updatedUser = user.copy(
                profileImageUrl = null,
                profileThumbnailUrl = null,
                profileImageBlurhash = null,
                profileImageMetadata = null,
                updatedAt = LocalDateTime.now()
            )

            userRepository.save(updatedUser)

            // 스토리지에서 삭제
            if (oldImageKeys.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    deleteOldImages(oldImageKeys)
                }
            }

            logger.info("프로필 이미지 삭제 완료: userId={}", userId)

            ProfileImageDeleteResponse(
                success = true,
                message = "프로필 이미지가 성공적으로 삭제되었습니다"
            )

        } catch (e: Exception) {
            logger.error("프로필 이미지 삭제 실패: userId={}", userId, e)
            ProfileImageDeleteResponse(
                success = false,
                message = "이미지 삭제 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }

    /**
     * 프로필 이미지 상세 조회
     */
    @Transactional(readOnly = true)
    fun getProfileImageDetails(userId: UUID): ProfileImageDetailsResponse {
        try {
            val user = userRepository.findById(userId).orElse(null)
                ?: return ProfileImageDetailsResponse(
                    userId = userId,
                    imageUrls = null,
                    blurhash = null,
                    metadata = null,
                    hasImage = false
                )

            if (user.profileImageUrl == null) {
                return ProfileImageDetailsResponse(
                    userId = userId,
                    imageUrls = null,
                    blurhash = null,
                    metadata = null,
                    hasImage = false
                )
            }

            // 메타데이터에서 ImageUrls 재구성
            val imageUrls = user.profileImageMetadata?.let { metadata ->
                @Suppress("UNCHECKED_CAST")
                val resolutions = metadata["resolutions"] as? Map<String, Map<String, Any>>

                fun getUrl(resolution: String): String {
                    return (resolutions?.get("${resolution}_webp")?.get("url") as? String)
                        ?: (resolutions?.get("${resolution}_jpeg")?.get("url") as? String)
                        ?: ""
                }

                ImageUrls(
                    original = getUrl("original"),
                    large = getUrl("large"),
                    medium = getUrl("medium"),
                    small = getUrl("small"),
                    thumbnail = getUrl("small")
                )
            }

            // 메타데이터 파싱 (간단 버전)
            val profileMetadata = user.profileImageMetadata?.let {
                ProfileImageMetadata(
                    originalFileName = it["originalFileName"] as? String ?: "",
                    originalSize = (it["originalSize"] as? Number)?.toLong() ?: 0L,
                    originalWidth = (it["originalWidth"] as? Number)?.toInt() ?: 0,
                    originalHeight = (it["originalHeight"] as? Number)?.toInt() ?: 0,
                    mimeType = it["mimeType"] as? String ?: "",
                    format = it["format"] as? String ?: "",
                    resolutions = emptyMap()
                )
            }

            return ProfileImageDetailsResponse(
                userId = userId,
                imageUrls = imageUrls,
                blurhash = user.profileImageBlurhash,
                metadata = profileMetadata,
                hasImage = true
            )

        } catch (e: Exception) {
            logger.error("프로필 이미지 조회 실패: userId={}", userId, e)
            return ProfileImageDetailsResponse(
                userId = userId,
                imageUrls = null,
                blurhash = null,
                metadata = null,
                hasImage = false
            )
        }
    }
}
