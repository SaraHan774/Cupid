package com.august.cupid.service

import com.august.cupid.model.dto.S3UploadResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * 스토리지 서비스
 *
 * 기능:
 * - S3 업로드 (프로덕션)
 * - 로컬 파일 시스템 저장 (개발 환경)
 * - 파일 삭제
 * - URL 생성
 */
@Service
class StorageService(
    @Value("\${storage.type:local}") private val storageType: String,
    @Value("\${storage.local.base-path:uploads}") private val localBasePath: String,
    @Value("\${storage.local.base-url:http://localhost:8080/uploads}") private val localBaseUrl: String,
    @Value("\${storage.s3.bucket-name:}") private val s3BucketName: String,
    @Value("\${storage.s3.region:us-east-1}") private val s3Region: String,
    @Value("\${storage.s3.access-key:}") private val s3AccessKey: String,
    @Value("\${storage.s3.secret-key:}") private val s3SecretKey: String,
    @Value("\${storage.s3.cdn-url:}") private val s3CdnUrl: String
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val s3Client: S3Client? by lazy { initializeS3Client() }

    init {
        logger.info("스토리지 서비스 초기화: type={}", storageType)
        if (storageType == "local") {
            initializeLocalStorage()
        }
    }

    /**
     * S3 클라이언트 초기화
     */
    private fun initializeS3Client(): S3Client? {
        return try {
            if (storageType != "s3" || s3AccessKey.isBlank() || s3SecretKey.isBlank()) {
                logger.info("S3 클라이언트 초기화 건너뜀 (로컬 스토리지 모드)")
                return null
            }

            val credentials = AwsBasicCredentials.create(s3AccessKey, s3SecretKey)
            val client = S3Client.builder()
                .region(Region.of(s3Region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()

            logger.info("S3 클라이언트 초기화 완료: bucket={}, region={}", s3BucketName, s3Region)
            client
        } catch (e: Exception) {
            logger.error("S3 클라이언트 초기화 실패", e)
            null
        }
    }

    /**
     * 로컬 스토리지 초기화
     */
    private fun initializeLocalStorage() {
        try {
            val path = Paths.get(localBasePath)
            if (!Files.exists(path)) {
                Files.createDirectories(path)
                logger.info("로컬 스토리지 디렉토리 생성: {}", path.toAbsolutePath())
            }
        } catch (e: Exception) {
            logger.error("로컬 스토리지 초기화 실패", e)
        }
    }

    /**
     * 파일 업로드 (자동으로 S3 또는 로컬 선택)
     */
    fun uploadFile(
        data: ByteArray,
        fileName: String,
        contentType: String,
        folder: String = "profile-images"
    ): S3UploadResult {
        return when (storageType) {
            "s3" -> uploadToS3(data, fileName, contentType, folder)
            "local" -> uploadToLocal(data, fileName, folder)
            else -> {
                logger.warn("알 수 없는 스토리지 타입: {}. 로컬로 대체합니다.", storageType)
                uploadToLocal(data, fileName, folder)
            }
        }
    }

    /**
     * S3에 파일 업로드
     */
    private fun uploadToS3(
        data: ByteArray,
        fileName: String,
        contentType: String,
        folder: String
    ): S3UploadResult {
        return try {
            val client = s3Client ?: return S3UploadResult(
                success = false,
                errorMessage = "S3 클라이언트가 초기화되지 않았습니다"
            )

            val key = "$folder/$fileName"

            val putObjectRequest = PutObjectRequest.builder()
                .bucket(s3BucketName)
                .key(key)
                .contentType(contentType)
                .build()

            client.putObject(putObjectRequest, RequestBody.fromBytes(data))

            // URL 생성 (CDN URL이 있으면 CDN 사용, 없으면 S3 직접 URL)
            val url = if (s3CdnUrl.isNotBlank()) {
                "$s3CdnUrl/$key"
            } else {
                "https://$s3BucketName.s3.$s3Region.amazonaws.com/$key"
            }

            logger.debug("S3 업로드 완료: key={}, url={}", key, url)

            S3UploadResult(
                success = true,
                url = url,
                key = key
            )
        } catch (e: Exception) {
            logger.error("S3 업로드 실패: fileName={}", fileName, e)
            S3UploadResult(
                success = false,
                errorMessage = "S3 업로드 실패: ${e.message}"
            )
        }
    }

    /**
     * 로컬 파일 시스템에 파일 저장
     */
    private fun uploadToLocal(
        data: ByteArray,
        fileName: String,
        folder: String
    ): S3UploadResult {
        return try {
            val folderPath = Paths.get(localBasePath, folder)
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath)
            }

            val filePath = folderPath.resolve(fileName)
            Files.write(filePath, data)

            val url = "$localBaseUrl/$folder/$fileName"

            logger.debug("로컬 저장 완료: path={}, url={}", filePath, url)

            S3UploadResult(
                success = true,
                url = url,
                key = "$folder/$fileName"
            )
        } catch (e: Exception) {
            logger.error("로컬 저장 실패: fileName={}", fileName, e)
            S3UploadResult(
                success = false,
                errorMessage = "로컬 저장 실패: ${e.message}"
            )
        }
    }

    /**
     * 파일 삭제
     */
    fun deleteFile(key: String): Boolean {
        return when (storageType) {
            "s3" -> deleteFromS3(key)
            "local" -> deleteFromLocal(key)
            else -> false
        }
    }

    /**
     * S3에서 파일 삭제
     */
    private fun deleteFromS3(key: String): Boolean {
        return try {
            val client = s3Client ?: return false

            val deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(s3BucketName)
                .key(key)
                .build()

            client.deleteObject(deleteObjectRequest)

            logger.debug("S3 파일 삭제 완료: key={}", key)
            true
        } catch (e: Exception) {
            logger.error("S3 파일 삭제 실패: key={}", key, e)
            false
        }
    }

    /**
     * 로컬 파일 시스템에서 파일 삭제
     */
    private fun deleteFromLocal(key: String): Boolean {
        return try {
            val filePath = Paths.get(localBasePath, key)
            if (Files.exists(filePath)) {
                Files.delete(filePath)
                logger.debug("로컬 파일 삭제 완료: path={}", filePath)
                true
            } else {
                logger.warn("삭제할 파일이 존재하지 않음: path={}", filePath)
                false
            }
        } catch (e: Exception) {
            logger.error("로컬 파일 삭제 실패: key={}", key, e)
            false
        }
    }

    /**
     * 여러 파일 삭제
     */
    fun deleteFiles(keys: List<String>): Int {
        var deletedCount = 0
        keys.forEach { key ->
            if (deleteFile(key)) {
                deletedCount++
            }
        }
        logger.info("파일 삭제 완료: total={}, deleted={}", keys.size, deletedCount)
        return deletedCount
    }

    /**
     * 고유한 파일명 생성
     */
    fun generateUniqueFileName(userId: UUID, resolution: String, format: String): String {
        val timestamp = System.currentTimeMillis()
        return "${userId}_${resolution}_${timestamp}.${format}"
    }

    /**
     * 스토리지 상태 확인
     */
    fun isHealthy(): Boolean {
        return when (storageType) {
            "s3" -> s3Client != null
            "local" -> Files.exists(Paths.get(localBasePath))
            else -> false
        }
    }
}
