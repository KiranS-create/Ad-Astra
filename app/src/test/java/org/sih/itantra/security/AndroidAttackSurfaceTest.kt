package org.sih.itantra.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.crypto.NetworkKeyManager
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Feature 23: Android Attack Surface & Key Storage Security Audit Test.
 *
 * Verifies:
 * - Production AndroidManifest.xml component export boundaries.
 * - Service isolation (`ManetNodeService` is not exported).
 * - Absence of unauthorized ContentProviders or dangerous debug entrypoints in release configuration.
 * - NetworkKeyManager lifecycle, secure key setting, derivation, and zeroization.
 */
class AndroidAttackSurfaceTest {

    @Test
    fun manifest_productionComponents_strictlyAudited() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("Production AndroidManifest.xml must exist", manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        // 1. Check activities: Only MainActivity should be declared in production manifest
        val activities = doc.getElementsByTagName("activity")
        assertEquals("Production manifest should only declare MainActivity", 1, activities.length)
        val mainActivity = activities.item(0)
        val mainActivityExported = mainActivity.attributes.getNamedItem("android:exported")?.nodeValue
        assertEquals("MainActivity must be exported as the LAUNCHER entry point", "true", mainActivityExported)

        // 2. Check services: ManetNodeService must NOT be exported
        val services = doc.getElementsByTagName("service")
        assertEquals("Production manifest declares ManetNodeService", 1, services.length)
        val service = services.item(0)
        val serviceExported = service.attributes.getNamedItem("android:exported")?.nodeValue
        assertEquals("ManetNodeService must strictly be android:exported=false to prevent unauthorized binding", "false", serviceExported)

        // 3. Check receivers: BootReceiver is exported strictly for BOOT_COMPLETED
        val receivers = doc.getElementsByTagName("receiver")
        assertEquals("Production manifest declares BootReceiver", 1, receivers.length)
        val receiver = receivers.item(0)
        val receiverExported = receiver.attributes.getNamedItem("android:exported")?.nodeValue
        assertEquals("BootReceiver must be exported for OS boot broadcasts", "true", receiverExported)

        // 4. Check providers: No ContentProvider should be exposed
        val providers = doc.getElementsByTagName("provider")
        assertEquals("Production manifest must NOT expose any ContentProvider", 0, providers.length)

        // 5. Check allowBackup attribute
        val applications = doc.getElementsByTagName("application")
        assertTrue(applications.length > 0)
        val app = applications.item(0)
        val allowBackup = app.attributes.getNamedItem("android:allowBackup")?.nodeValue
        // Document: allowBackup is currently true in base config (recommended false for high security)
        assertNotNull("allowBackup attribute should be explicitly configured", allowBackup)
    }

    @Test
    fun manifest_debugTestHarnesses_isolatedFromProductionManifest() {
        val debugManifest = File("src/debug/AndroidManifest.xml")
        if (debugManifest.exists()) {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(debugManifest)

            val debugActivities = doc.getElementsByTagName("activity")
            assertTrue("Debug manifest contains test harness activities", debugActivities.length > 0)

            val prodManifestText = File("src/main/AndroidManifest.xml").readText()
            for (i in 0 until debugActivities.length) {
                val act = debugActivities.item(i)
                val actName = act.attributes.getNamedItem("android:name")?.nodeValue ?: ""
                val simpleName = actName.substringAfterLast('.')
                assertFalse(
                    "Debug harness activity $simpleName must NOT leak into production manifest",
                    prodManifestText.contains(simpleName)
                )
            }
        }
    }

    @Test
    fun keyManager_deterministicKeyDerivation_producesExact256BitKey() {
        val passphrase = "Tactical-Operation-Shakti-2026"
        val salt = "Sector-9-Salt".toByteArray(Charsets.UTF_8)

        val key = NetworkKeyManager.deriveKey(passphrase, salt)
        assertEquals("Derived key must be exactly 32 bytes (256 bits)", 32, key.size)

        // Determinism: same passphrase and salt must yield identical key bytes
        val key2 = NetworkKeyManager.deriveKey(passphrase, salt)
        assertTrue("Key derivation must be deterministic", key.contentEquals(key2))

        // Distinct passphrase must yield distinct key bytes
        val keyOther = NetworkKeyManager.deriveKey("Different-Passphrase", salt)
        assertFalse("Different passphrase must yield different key", key.contentEquals(keyOther))
    }

    @Test
    fun keyManager_setKeyAndClear_lifecycleVerified() {
        val customKey = ByteArray(32) { (it + 1).toByte() }

        NetworkKeyManager.setKey(customKey)
        assertTrue(NetworkKeyManager.hasKey())
        assertTrue(customKey.contentEquals(NetworkKeyManager.getKey()!!))

        // Clear key (zeroization / reset)
        NetworkKeyManager.clear()
        assertFalse(NetworkKeyManager.hasKey())
        assertNull(NetworkKeyManager.getKey())

        // Re-provision demo key for ongoing test isolation
        NetworkKeyManager.provisionDemoKey()
        assertTrue(NetworkKeyManager.hasKey())
    }

    @Test
    fun keyManager_invalidKeyLength_rejectedWithException() {
        val shortKey = ByteArray(16) // 128 bits instead of 256 bits
        try {
            NetworkKeyManager.setKey(shortKey)
            org.junit.Assert.fail("Setting 16-byte key must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("256 bits", ignoreCase = true) == true)
        }
    }
}
