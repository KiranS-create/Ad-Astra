package org.sih.itantra.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactIdentity
import org.sih.itantra.core.contact.ContactRepository

/**
 * Unit test suite for QrPairingValidator.
 * Tests self-node detection, duplicate detection, repository integration, and validation results.
 */
class QrPairingValidatorTest {

    private lateinit var repository: ContactRepository
    private val localNodeId = 209070

    @Before
    fun setup() {
        repository = ContactRepository(context = null)
        repository.clearAll()
    }

    // Test Scenario 4: Self-node scan detection and rejection (with helpful user message)
    @Test
    fun selfNodeScan_isDetectedAndRejected() {
        val selfRaw = "ITANTRA:1|$localNodeId|LOCAL OPERATOR|Self Unit|hi,en"

        val result = QrPairingValidator.validateScannedQr(
            rawPayload = selfRaw,
            localNodeId = localNodeId,
            contactRepository = repository
        )

        assertTrue("Result must be SelfNode rejection", result is ValidationResult.SelfNode)
        val selfResult = result as ValidationResult.SelfNode
        assertEquals(localNodeId, selfResult.nodeId)
        assertTrue(
            "Rejection message must mention self node",
            selfResult.userMessage.contains("This is this device") || selfResult.userMessage.contains("own node")
        )
    }

    // Test Scenario 5: Duplicate contact detection and rejection / warning
    @Test
    fun duplicateContact_isDetectedAndWarned() {
        val peerNodeId = 477124
        val existingIdentity = ContactIdentity(
            nodeId = peerNodeId,
            callsign = "NODE BRAVO",
            displayName = "Recon Patrol",
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.MARATHI),
            authStatus = ContactAuthStatus.UNVERIFIED
        )
        repository.addContact(existingIdentity)

        // Attempt to scan duplicate
        val rawDuplicate = "ITANTRA:1|$peerNodeId|NODE BRAVO|Recon Patrol|hi,mr"
        val result = QrPairingValidator.validateScannedQr(
            rawPayload = rawDuplicate,
            localNodeId = localNodeId,
            contactRepository = repository
        )

        assertTrue("Result must be Duplicate rejection", result is ValidationResult.Duplicate)
        val dupResult = result as ValidationResult.Duplicate
        assertEquals(peerNodeId, dupResult.existingContact.nodeId)
        assertEquals("NODE BRAVO", dupResult.existingContact.callsign)
        assertTrue(
            "User message must indicate contact already exists",
            dupResult.userMessage.contains("already exists") || dupResult.userMessage.contains("already in your tactical contacts")
        )
    }

    // Test Scenario 16: ContactRepository insertion with UNVERIFIED status
    @Test
    fun validScan_insertsIntoContactRepository_withUnverifiedStatus() {
        val peerNodeId = 991122
        val rawPeer = "ITANTRA:1|$peerNodeId|SCOUT ECHO|Forward Scout|hi,en"

        val result = QrPairingValidator.validateScannedQr(
            rawPayload = rawPeer,
            localNodeId = localNodeId,
            contactRepository = repository
        )

        assertTrue("Result must be Success", result is ValidationResult.Success)
        val successResult = result as ValidationResult.Success
        val payload = successResult.payload

        // Convert and insert into repository
        val contactIdentity = payload.toContactIdentity(authStatus = ContactAuthStatus.UNVERIFIED)
        val added = repository.addContact(contactIdentity)
        assertTrue("Contact must be successfully added", added)

        // Verify retrieval from repository
        val retrieved = repository.getContact(peerNodeId)
        assertNotNull("Retrieved contact must not be null", retrieved)
        assertEquals(peerNodeId, retrieved!!.nodeId)
        assertEquals("SCOUT ECHO", retrieved.callsign)
        assertEquals("Forward Scout", retrieved.displayName)
        assertEquals(
            "Stored contact must strictly have UNVERIFIED status",
            ContactAuthStatus.UNVERIFIED,
            retrieved.authStatus
        )

        // Verify subsequent scan is now recognized as Duplicate
        val secondScan = QrPairingValidator.validateScannedQr(
            rawPayload = rawPeer,
            localNodeId = localNodeId,
            contactRepository = repository
        )
        assertTrue("Subsequent scan of same node must be detected as Duplicate", secondScan is ValidationResult.Duplicate)
    }

    // Invalid Payload validation
    @Test
    fun invalidPayload_returnsValidationResultInvalid() {
        val malformedRaw = "NOT_A_VALID_ITANTRA_QR"
        val result = QrPairingValidator.validateScannedQr(
            rawPayload = malformedRaw,
            localNodeId = localNodeId,
            contactRepository = repository
        )

        assertTrue("Result must be Invalid", result is ValidationResult.Invalid)
        val invalidResult = result as ValidationResult.Invalid
        assertTrue("Reason must not be blank", invalidResult.reason.isNotBlank())
    }
}
