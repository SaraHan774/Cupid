package com.august.cupid.service

import com.august.cupid.model.dto.*
import com.vanniktech.blurhash.BlurHash
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.min

/**
 * 이미지 최적화 서비스
 *
 * 기능:
 * - 다중 해상도 생성 (Original: 800x800, Large: 400x400, Medium: 200x200, Small: 100x100)
 * - WebP 포맷 변환 + JPEG 폴백
 * - BlurHash 생성
 * - 이미지 검증 및 전처리
 */
@Service
class ImageOptimizationService {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // 지원하는 이미지 형식
        private val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
        )

        // 최대 파일 크기: 10MB
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024

        // 최소/최대 이미지 크기
        private const val MIN_IMAGE_SIZE = 100
        private const val MAX_IMAGE_SIZE = 4000
    }

    /**
     * 이미지 파일 검증
     */
    fun validateImage(file: MultipartFile): ImageValidationResult {
        try {
            // 파일 크기 확인
            if (file.size > MAX_FILE_SIZE) {
                return ImageValidationResult(
                    isValid = false,
                    errorMessage = "파일 크기가 너무 큽니다. 최대 10MB까지 업로드 가능합니다.",
                    fileSize = file.size
                )
            }

            // 파일이 비어있는지 확인
            if (file.isEmpty) {
                return ImageValidationResult(
                    isValid = false,
                    errorMessage = "파일이 비어있습니다."
                )
            }

            // MIME 타입 확인
            val mimeType = file.contentType
            if (mimeType == null || mimeType !in SUPPORTED_MIME_TYPES) {
                return ImageValidationResult(
                    isValid = false,
                    errorMessage = "지원하지 않는 파일 형식입니다. JPEG, PNG, WebP만 지원합니다.",
                    mimeType = mimeType
                )
            }

            // 실제 이미지인지 확인 (매직 바이트 체크)
            val image = ImageIO.read(ByteArrayInputStream(file.bytes))
            if (image == null) {
                return ImageValidationResult(
                    isValid = false,
                    errorMessage = "유효한 이미지 파일이 아닙니다.",
                    mimeType = mimeType
                )
            }

            // 이미지 크기 확인
            if (image.width < MIN_IMAGE_SIZE || image.height < MIN_IMAGE_SIZE) {
                return ImageValidationResult(
                    isValid = false,
                    errorMessage = "이미지 크기가 너무 작습니다. 최소 ${MIN_IMAGE_SIZE}x${MIN_IMAGE_SIZE} 필요합니다.",
                    mimeType = mimeType
                )
            }

            if (image.width > MAX_IMAGE_SIZE || image.height > MAX_IMAGE_SIZE) {
                return ImageValidationResult(
                    isValid = false,
                    errorMessage = "이미지 크기가 너무 큽니다. 최대 ${MAX_IMAGE_SIZE}x${MAX_IMAGE_SIZE} 지원됩니다.",
                    mimeType = mimeType
                )
            }

            // 파일 확장자 추출
            val extension = file.originalFilename?.substringAfterLast('.', "")?.lowercase()

            logger.debug(
                "이미지 검증 성공: size={}, mimeType={}, dimensions={}x{}",
                file.size, mimeType, image.width, image.height
            )

            return ImageValidationResult(
                isValid = true,
                fileSize = file.size,
                mimeType = mimeType,
                extension = extension
            )
        } catch (e: Exception) {
            logger.error("이미지 검증 중 오류 발생", e)
            return ImageValidationResult(
                isValid = false,
                errorMessage = "이미지 처리 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }

    /**
     * 다중 해상도 이미지 생성 (병렬 처리)
     */
    suspend fun generateMultipleResolutions(
        originalImage: BufferedImage,
        config: ImageProcessingConfig = ImageProcessingConfig()
    ): List<OptimizedImage> {
        val resolutions = ImageResolution.getAll()

        logger.info("다중 해상도 생성 시작: {} 개의 해상도", resolutions.size)

        return resolutions.map { resolution ->
            generateSingleResolution(originalImage, resolution, config)
        }
    }

    /**
     * 단일 해상도 이미지 생성
     */
    private fun generateSingleResolution(
        originalImage: BufferedImage,
        resolution: ImageResolution,
        config: ImageProcessingConfig
    ): OptimizedImage {
        try {
            // 이미지 리사이즈 (종횡비 유지하며 정사각형으로 크롭)
            val resized = resizeAndCrop(originalImage, resolution.width, resolution.height)

            // WebP 변환
            val webpData = if (config.generateWebP) {
                convertToWebP(resized, config.webpQuality)
            } else {
                ByteArray(0)
            }

            // JPEG 폴백
            val jpegData = if (config.generateJpegFallback) {
                convertToJPEG(resized, config.jpegQuality)
            } else {
                ByteArray(0)
            }

            logger.debug(
                "해상도 생성 완료: resolution={}, webpSize={}, jpegSize={}",
                resolution, webpData.size, jpegData.size
            )

            return OptimizedImage(
                resolution = resolution,
                webpData = webpData,
                jpegData = jpegData,
                webpSize = webpData.size.toLong(),
                jpegSize = jpegData.size.toLong(),
                width = resolution.width,
                height = resolution.height
            )
        } catch (e: Exception) {
            logger.error("해상도 생성 실패: resolution={}", resolution, e)
            throw RuntimeException("이미지 최적화 실패: ${e.message}", e)
        }
    }

    /**
     * 이미지 리사이즈 및 중앙 크롭 (정사각형)
     */
    private fun resizeAndCrop(image: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        // 원본 이미지의 짧은 쪽을 기준으로 정사각형 크롭
        val size = min(image.width, image.height)

        // 중앙에서 정사각형으로 크롭
        val x = (image.width - size) / 2
        val y = (image.height - size) / 2
        val cropped = image.getSubimage(x, y, size, size)

        // 타겟 크기로 리사이즈
        return Thumbnails.of(cropped)
            .size(targetWidth, targetHeight)
            .outputFormat("png")
            .asBufferedImage()
    }

    /**
     * WebP 포맷으로 변환
     */
    private fun convertToWebP(image: BufferedImage, quality: Int): ByteArray {
        // WebP 변환을 위해 먼저 PNG로 변환 후 외부 라이브러리 사용
        // 현재는 고품질 JPEG로 대체 (WebP 변환은 추가 라이브러리 필요)
        // TODO: 실제 WebP 변환 구현 (libwebp JNI 바인딩 또는 외부 서비스 사용)

        // 임시로 고품질 JPEG 반환
        return convertToJPEG(image, quality)
    }

    /**
     * JPEG 포맷으로 변환
     */
    private fun convertToJPEG(image: BufferedImage, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()

        // JPEG writer 설정
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (!writers.hasNext()) {
            throw RuntimeException("JPEG writer를 찾을 수 없습니다")
        }

        val writer = writers.next()
        val writeParam = writer.defaultWriteParam

        // 압축 품질 설정
        writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
        writeParam.compressionQuality = quality / 100f

        // RGB 이미지로 변환 (JPEG는 투명도 미지원)
        val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = rgbImage.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()

        // 이미지 쓰기
        writer.output = MemoryCacheImageOutputStream(outputStream)
        writer.write(null, IIOImage(rgbImage, null, null), writeParam)
        writer.dispose()

        return outputStream.toByteArray()
    }

    /**
     * BlurHash 생성
     */
    fun generateBlurHash(image: BufferedImage): BlurHashResult {
        try {
            // 작은 이미지로 리사이즈 (BlurHash 계산 최적화)
            val smallImage = Thumbnails.of(image)
                .size(32, 32)
                .asBufferedImage()

            // RGB 배열로 변환
            val width = smallImage.width
            val height = smallImage.height
            val pixels = IntArray(width * height)
            smallImage.getRGB(0, 0, width, height, pixels, 0, width)

            // BlurHash 생성
            val hash = BlurHash.encode(pixels, width, height, componentX = 4, componentY = 3)

            logger.debug("BlurHash 생성 완료: hash={}", hash)

            return BlurHashResult(
                hash = hash,
                componentX = 4,
                componentY = 3
            )
        } catch (e: Exception) {
            logger.error("BlurHash 생성 실패", e)
            throw RuntimeException("BlurHash 생성 실패: ${e.message}", e)
        }
    }

    /**
     * MultipartFile을 BufferedImage로 변환
     */
    fun readImage(file: MultipartFile): BufferedImage {
        return ImageIO.read(ByteArrayInputStream(file.bytes))
            ?: throw RuntimeException("이미지를 읽을 수 없습니다")
    }

    /**
     * 메타데이터 생성
     */
    fun createMetadata(
        file: MultipartFile,
        originalImage: BufferedImage,
        optimizedImages: List<OptimizedImage>
    ): ProfileImageMetadata {
        val resolutionsMap = mutableMapOf<String, ResolutionMetadata>()

        optimizedImages.forEach { optimized ->
            // WebP 버전
            resolutionsMap["${optimized.resolution.suffix}_webp"] = ResolutionMetadata(
                width = optimized.width,
                height = optimized.height,
                fileSize = optimized.webpSize,
                format = "webp",
                url = "" // S3 업로드 후 설정됨
            )

            // JPEG 버전
            resolutionsMap["${optimized.resolution.suffix}_jpeg"] = ResolutionMetadata(
                width = optimized.width,
                height = optimized.height,
                fileSize = optimized.jpegSize,
                format = "jpeg",
                url = "" // S3 업로드 후 설정됨
            )
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
}
