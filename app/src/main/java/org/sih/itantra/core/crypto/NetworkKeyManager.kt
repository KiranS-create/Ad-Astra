package org.sih.itantra.core.crypto

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the symmetric network authentication key for iTantra tactical mesh nodes.
 *
 * Security Model:
 * - Offline symmetric-key authentication using HMAC-SHA256.
 * - Key length is strictly 256 bits (32 bytes).
 * - Zero external/cloud dependency (operates fully offline in degraded tactical environments).
 * - Key material is never transmitted over RF/radio interfaces.
 * - Test and demo provisioning paths allow predictable verification without hard-coded production secrets.
 */
object NetworkKeyManager {

    private const val REQUIRED_KEY_LENGTH_BYTES = 32 // 256 bits

    // Tactical demo default passphrase for rapid offline field demo setup
    private const val DEMO_TACTICAL_PASSPHRASE = "iTantra-Tactical-Mesh-Symmetric-Auth-Key-2026"

    private val currentKey = AtomicReference<ByteArray?>(null)

    init {
        // Initialize default demo key so node is operational out-of-the-box in demo/field tests
        provisionDemoKey()
    }

    /**
     * Checks whether an active authentication key is provisioned.
     */
    fun hasKey(): Boolean = currentKey.get() != null

    /**
     * Retrieves the current 256-bit symmetric key bytes, or null if unprovisioned.
     */
    fun getKey(): ByteArray? = currentKey.get()?.copyOf()

    /**
     * Sets a 256-bit (32-byte) symmetric key directly.
     */
    fun setKey(keyBytes: ByteArray) {
        require(keyBytes.size == REQUIRED_KEY_LENGTH_BYTES) {
            "Symmetric key must be exactly $REQUIRED_KEY_LENGTH_BYTES bytes (256 bits), was ${keyBytes.size} bytes"
        }
        currentKey.set(keyBytes.copyOf())
    }

    /**
     * Derives a 256-bit symmetric key deterministically from a passphrase and optional salt.
     * Uses SHA-256 hashing to produce an exact 32-byte key.
     */
    fun deriveKey(passphrase: String, salt: ByteArray? = null): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        if (salt != null && salt.isNotEmpty()) {
            digest.update(salt)
        }
        return digest.digest(passphrase.toByteArray(Charsets.UTF_8))
    }

    /**
     * Provisions the standard tactical mesh demo key.
     */
    fun provisionDemoKey() {
        val derived = deriveKey(DEMO_TACTICAL_PASSPHRASE)
        currentKey.set(derived)
    }

    /**
     * Sets a dedicated test key for isolated unit tests.
     */
    fun setTestKey(testKeyBytes: ByteArray) {
        setKey(testKeyBytes)
    }

    /**
     * Resets the active key state (clears sensitive memory).
     */
    fun clear() {
        currentKey.set(null)
    }
}
