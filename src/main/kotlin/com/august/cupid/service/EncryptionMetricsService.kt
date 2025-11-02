package com.august.cupid.service

import io.micrometer.core.instrument.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

/**
 * 암호화 성능 메트릭 서비스
 * 
 * Prometheus 메트릭을 수집하고 노출하는 서비스
 * 
 * 추적하는 메트릭:
 * - Timers: 키 생성, 암호화, 복호화, 세션 초기화 시간
 * - Counters: 작업 카운트, 에러 카운트
 * - Gauges: 활성 세션 수, 사용 가능한 pre-key 수
 * 
 * 메트릭 태그:
 * - operation: 작업 타입 (generate, encrypt, decrypt, initialize, etc.)
 * - error_type: 에러 타입 (validation, security, decryption_failed, etc.)
 */
@Service
class EncryptionMetricsService(
    private val meterRegistry: MeterRegistry
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // ==================== Timers ====================
    
    /**
     * 키 생성 시간 측정 (Timer)
     * 메트릭 이름: encryption.key.generation
     * 태그: operation=generate
     */
    fun recordKeyGenerationTime(seconds: Double, tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("operation", "generate"))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val timer = Timer.builder("encryption.key.generation")
            .description("Time taken to generate Signal Protocol keys")
            .tags(allTags)
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry)
        
        timer.record((seconds * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        logger.debug("Recorded key generation time: ${seconds}s")
    }
    
    /**
     * 암호화 시간 측정 (Timer)
     * 메트릭 이름: encryption.message.encrypt
     * 태그: operation=encrypt
     */
    fun recordEncryptionTime(seconds: Double, tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("operation", "encrypt"))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val timer = Timer.builder("encryption.message.encrypt")
            .description("Time taken to encrypt a message")
            .tags(allTags)
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry)
        
        timer.record((seconds * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        logger.debug("Recorded encryption time: ${seconds}s")
    }
    
    /**
     * 복호화 시간 측정 (Timer)
     * 메트릭 이름: encryption.message.decrypt
     * 태그: operation=decrypt
     */
    fun recordDecryptionTime(seconds: Double, tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("operation", "decrypt"))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val timer = Timer.builder("encryption.message.decrypt")
            .description("Time taken to decrypt a message")
            .tags(allTags)
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry)
        
        timer.record((seconds * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        logger.debug("Recorded decryption time: ${seconds}s")
    }
    
    /**
     * 세션 초기화 시간 측정 (Timer)
     * 메트릭 이름: encryption.session.initialize
     * 태그: operation=initialize
     */
    fun recordSessionInitializationTime(seconds: Double, tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("operation", "initialize"))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val timer = Timer.builder("encryption.session.initialize")
            .description("Time taken to initialize an encrypted session")
            .tags(allTags)
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry)
        
        timer.record((seconds * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        logger.debug("Recorded session initialization time: ${seconds}s")
    }
    
    // ==================== Counters ====================
    
    /**
     * 키 생성 카운터 증가
     * 메트릭 이름: encryption.key.generation.count
     */
    fun incrementKeyGenerationCount(tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("operation", "generate"))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val counter = Counter.builder("encryption.key.generation.count")
            .description("Total number of key generation operations")
            .tags(allTags)
            .register(meterRegistry)
        
        counter.increment()
        logger.debug("Incremented key generation count")
    }
    
    /**
     * 암호화 카운터 증가
     * 메트릭 이름: encryption.message.encrypt.count
     */
    fun incrementEncryptionCount(tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("operation", "encrypt"))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val counter = Counter.builder("encryption.message.encrypt.count")
            .description("Total number of encryption operations")
            .tags(allTags)
            .register(meterRegistry)
        
        counter.increment()
        logger.debug("Incremented encryption count")
    }
    
    /**
     * 복호화 카운터 증가
     * 메트릭 이름: encryption.message.decrypt.count
     */
    fun incrementDecryptionCount(tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("operation", "decrypt"))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val counter = Counter.builder("encryption.message.decrypt.count")
            .description("Total number of decryption operations")
            .tags(allTags)
            .register(meterRegistry)
        
        counter.increment()
        logger.debug("Incremented decryption count")
    }
    
    /**
     * 에러 카운터 증가
     * 메트릭 이름: encryption.errors
     * 태그: error_type, operation
     */
    fun incrementErrorCount(errorType: String, operation: String, tags: Map<String, String> = emptyMap()) {
        val allTags = mutableListOf<Tag>().apply {
            add(Tag.of("error_type", errorType))
            add(Tag.of("operation", operation))
            addAll(tags.map { Tag.of(it.key, it.value) })
        }
        
        val counter = Counter.builder("encryption.errors")
            .description("Total number of encryption errors")
            .tags(allTags)
            .register(meterRegistry)
        
        counter.increment()
        logger.debug("Incremented error count: type=$errorType, operation=$operation")
    }
    
    // ==================== Gauges ====================
    
    /**
     * 활성 세션 수 측정 (Gauge)
     * 메트릭 이름: encryption.sessions.active
     * 
     * 주의: Gauge는 항상 현재 값을 반영해야 하므로,
     * 함수를 호출할 때마다 값을 갱신해야 함
     */
    private val activeSessionsGauge = AtomicLong(0)
    
    init {
        Gauge.builder("encryption.sessions.active") { activeSessionsGauge.get().toDouble() }
            .description("Number of active encrypted sessions")
            .register(meterRegistry)
    }
    
    fun updateActiveSessionsCount(count: Long) {
        activeSessionsGauge.set(count)
        logger.debug("Updated active sessions count: $count")
    }
    
    /**
     * 사용 가능한 pre-key 수 측정 (Gauge)
     * 메트릭 이름: encryption.prekeys.available
     * 
     * 주의: Gauge는 항상 현재 값을 반영해야 하므로,
     * 함수를 호출할 때마다 값을 갱신해야 함
     */
    private val preKeyGauges = mutableMapOf<String, AtomicLong>()
    
    fun updateAvailablePreKeysCount(userId: String, count: Long) {
        val gauge = preKeyGauges.getOrPut(userId) {
            val newGauge = AtomicLong(count)
            Gauge.builder("encryption.prekeys.available") { newGauge.get().toDouble() }
                .description("Number of available one-time pre-keys per user")
                .tag("user_id", userId)
                .register(meterRegistry)
            newGauge
        }
        gauge.set(count)
        logger.debug("Updated available pre-keys count for user $userId: $count")
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * 작업 실행 시간을 측정하고 기록하는 헬퍼 함수
     * 
     * 사용 예:
     * ```
     * metricsService.recordOperation("encrypt") {
     *     // 암호화 작업
     * }
     * ```
     */
    fun <T> recordOperation(
        operation: String,
        tags: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val startTime = System.nanoTime()
        return try {
            val result = block()
            val durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
            
            when (operation) {
                "generate" -> recordKeyGenerationTime(durationSeconds, tags)
                "encrypt" -> recordEncryptionTime(durationSeconds, tags)
                "decrypt" -> recordDecryptionTime(durationSeconds, tags)
                "initialize" -> recordSessionInitializationTime(durationSeconds, tags)
                else -> {
                    // 범용 타이머
                    val allTags = tags.map { Tag.of(it.key, it.value) }
                    Timer.builder("encryption.operation.$operation")
                        .description("Time taken for $operation operation")
                        .tags(allTags)
                        .register(meterRegistry)
                        .record((durationSeconds * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                }
            }
            
            result
        } catch (e: Exception) {
            incrementErrorCount(
                errorType = e.javaClass.simpleName,
                operation = operation,
                tags = tags
            )
            throw e
        }
    }
}

