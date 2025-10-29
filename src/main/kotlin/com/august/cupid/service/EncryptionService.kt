package com.august.cupid.service

import com.august.cupid.model.dto.*
import java.util.*

/**
 * Encryption Service Interface
 * Defines the contract for Signal Protocol encryption operations
 *
 * SECURITY PRINCIPLES:
 * - End-to-end encryption: Only sender and recipient can read messages
 * - Forward secrecy: Compromising current keys doesn't compromise past messages
 * - Post-compromise security: Compromising keys now doesn't compromise future messages
 * - Deniability: Messages cannot be cryptographically proven to be from sender
 */
interface EncryptionService {

    /**
     * Generate and store identity key pair for a user
     *
     * @param userId User ID
     * @param password User password (for deriving encryption key)
     * @return KeyRegistrationRequest with all generated keys
     * @throws SecurityException if key generation fails
     */
    fun generateIdentityKeys(userId: UUID, password: String): KeyRegistrationRequest

    /**
     * Register user's public keys with the server
     *
     * @param userId User ID
     * @param request Key registration request containing all public keys
     * @return Success status
     * @throws IllegalArgumentException if keys are invalid
     * @throws SecurityException if registration fails
     */
    fun registerKeys(userId: UUID, request: KeyRegistrationRequest): Boolean

    /**
     * Get pre-key bundle for a user (used by sender to establish session)
     *
     * @param userId Target user ID
     * @param deviceId Target device ID
     * @return Pre-key bundle containing all public keys
     * @throws NoSuchElementException if user keys not found
     */
    fun getPreKeyBundle(userId: UUID, deviceId: Int = 1): PreKeyBundleDto

    /**
     * Initialize a session with a recipient using their pre-key bundle
     *
     * @param senderId Sender user ID
     * @param recipientId Recipient user ID
     * @param preKeyBundle Recipient's pre-key bundle
     * @return Success status
     * @throws SecurityException if session initialization fails
     */
    fun initializeSession(
        senderId: UUID,
        recipientId: UUID,
        preKeyBundle: PreKeyBundleDto
    ): Boolean

    /**
     * Encrypt a message for a recipient
     *
     * @param senderId Sender user ID
     * @param recipientId Recipient user ID
     * @param plaintext Message plaintext
     * @param deviceId Recipient device ID
     * @return Encrypted message
     * @throws IllegalStateException if session doesn't exist
     * @throws SecurityException if encryption fails
     */
    fun encryptMessage(
        senderId: UUID,
        recipientId: UUID,
        plaintext: String,
        deviceId: Int = 1
    ): EncryptedMessageDto

    /**
     * Decrypt a message from a sender
     *
     * @param recipientId Recipient user ID (current user)
     * @param encryptedMessage Encrypted message from sender
     * @param password User password (for decrypting private keys)
     * @return Decrypted plaintext
     * @throws SecurityException if decryption fails
     * @throws IllegalArgumentException if message is invalid
     */
    fun decryptMessage(
        recipientId: UUID,
        encryptedMessage: EncryptedMessageDto,
        password: String
    ): String

    /**
     * Replenish one-time pre-keys for a user
     *
     * @param userId User ID
     * @param request Key replenishment request
     * @return Number of keys added
     */
    fun replenishPreKeys(userId: UUID, request: KeyReplenishmentRequest): Int

    /**
     * Rotate signed pre-key for a user
     * Should be called periodically (weekly/monthly)
     *
     * @param userId User ID
     * @param password User password (for accessing private keys)
     * @return New signed pre-key ID
     * @throws SecurityException if rotation fails
     */
    fun rotateSignedPreKey(userId: UUID, password: String): Int

    /**
     * Get key status for a user
     *
     * @param userId User ID
     * @return Key status information
     */
    fun getKeyStatus(userId: UUID): KeyStatusResponse

    /**
     * Delete all keys and sessions for a user
     * SECURITY: Use with caution, this removes all encryption state
     *
     * @param userId User ID
     */
    fun deleteAllKeys(userId: UUID)

    /**
     * Verify identity key fingerprint
     * Used to detect MITM attacks
     *
     * @param userId User ID checking the fingerprint
     * @param remoteUserId Remote user ID
     * @param fingerprintHash Expected fingerprint hash
     * @return True if fingerprint matches
     */
    fun verifyFingerprint(
        userId: UUID,
        remoteUserId: UUID,
        fingerprintHash: String
    ): Boolean

    /**
     * Mark identity as trusted after verification
     *
     * @param userId User ID doing the verification
     * @param remoteUserId Remote user ID being verified
     * @param deviceId Remote device ID
     * @return Success status
     */
    fun trustIdentity(
        userId: UUID,
        remoteUserId: UUID,
        deviceId: Int = 1
    ): Boolean

    /**
     * Check if session exists between two users
     *
     * @param userId First user ID
     * @param remoteUserId Second user ID
     * @param deviceId Remote device ID
     * @return True if session exists
     */
    fun hasSession(
        userId: UUID,
        remoteUserId: UUID,
        deviceId: Int = 1
    ): Boolean

    /**
     * Delete session between two users
     * Forces re-establishment of session
     *
     * @param userId User ID
     * @param remoteUserId Remote user ID
     * @param deviceId Remote device ID
     */
    fun deleteSession(
        userId: UUID,
        remoteUserId: UUID,
        deviceId: Int = 1
    )
}
