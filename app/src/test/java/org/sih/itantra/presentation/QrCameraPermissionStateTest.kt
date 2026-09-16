package org.sih.itantra.presentation

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
import org.sih.itantra.core.pairing.QrIdentityCodec
import org.sih.itantra.core.pairing.QrPairingValidator
import org.sih.itantra.core.pairing.ValidationResult

/**
 * Focused tests for QR Camera scanner state, permission decision logic,
 * fallback workflows, and payload validation compatibility.
 */
class QrCameraPermissionStateTest {

    private lateinit var repository: ContactRepository
    private val localNodeId = 209070

    @Before
    fun setup() {
        repository = ContactRepository(context = null)
        repository.clearAll()
    }

    /**
     * Models permission transition state logic.
     */
    data class CameraPermissionModel(
        var isGranted: Boolean = false,
        var denialCount: Int = 0
    ) {
        val shouldShowRationale: Boolean get() = !isGranted && denialCount > 0
        val isRepeatedDenial: Boolean get() = !isGranted && denialCount > 1
        val canActivateCamera: Boolean get() = isGranted

        fun onPermissionResult(granted: Boolean) {
            isGranted = granted
            if (!granted) denialCount++
        }
    }

    @Test
    fun permissionState_initialStateRequiresRequest() {
        val model = CameraPermissionModel(isGranted = false)
        assertFalse("Camera must not activate when ungranted", model.canActivateCamera)
        assertFalse("Initial state should not flag rationale before first request", model.shouldShowRationale)
    }

    @Test
    fun permissionState_grantActivatesCameraImmediately() {
        val model = CameraPermissionModel(isGranted = false)
        model.onPermissionResult(granted = true)

        assertTrue("Camera must activate immediately once granted", model.canActivateCamera)
        assertFalse("Rationale not needed when granted", model.shouldShowRationale)
    }

    @Test
    fun permissionState_singleDenialShowsRationale() {
        val model = CameraPermissionModel(isGranted = false)
        model.onPermissionResult(granted = false)

        assertFalse("Camera must remain disabled", model.canActivateCamera)
        assertTrue("Single denial must show rationale explanation", model.shouldShowRationale)
        assertFalse("Single denial is not yet repeated denial", model.isRepeatedDenial)
    }

    @Test
    fun permissionState_repeatedDenialTriggersSettingsOption() {
        val model = CameraPermissionModel(isGranted = false)
        model.onPermissionResult(granted = false)
        model.onPermissionResult(granted = false)

        assertFalse("Camera must remain disabled", model.canActivateCamera)
        assertTrue("Repeated denial must trigger app settings affordance", model.isRepeatedDenial)
    }

    @Test
    fun fallbackWorkflow_validPayloadDecodesWhenCameraDenied() {
        // Simulates manual code entry when camera is unavailable
        val validPayloadStr = "ITANTRA:1|477124|NODE BRAVO|Recon Unit|hi,en"
        val validation = QrPairingValidator.validateScannedQr(
            rawPayload = validPayloadStr,
            localNodeId = localNodeId,
            contactRepository = repository
        )

        assertTrue("Valid payload must succeed via manual input", validation is ValidationResult.Success)
        val success = validation as ValidationResult.Success
        assertEquals(477124, success.payload.nodeId)
        assertEquals("NODE BRAVO", success.payload.callsign)
        assertEquals("Recon Unit", success.payload.displayName)

        // Verify conversion to contact identity strictly enforces UNVERIFIED trust
        val contactIdentity = success.payload.toContactIdentity(authStatus = ContactAuthStatus.UNVERIFIED)
        assertEquals(ContactAuthStatus.UNVERIFIED, contactIdentity.authStatus)
    }

    @Test
    fun fallbackWorkflow_selfNodeRejectedWhenCameraDenied() {
        val selfPayloadStr = "ITANTRA:1|$localNodeId|NODE ALPHA|Self Unit|hi,en"
        val validation = QrPairingValidator.validateScannedQr(
            rawPayload = selfPayloadStr,
            localNodeId = localNodeId,
            contactRepository = repository
        )

        assertTrue("Self node must be rejected in fallback mode", validation is ValidationResult.SelfNode)
        val selfResult = validation as ValidationResult.SelfNode
        assertEquals(localNodeId, selfResult.nodeId)
    }

    @Test
    fun fallbackWorkflow_duplicateContactRejectedWhenCameraDenied() {
        val peerId = 884411
        repository.addContact(
            ContactIdentity(
                nodeId = peerId,
                callsign = "NODE CHARLIE",
                authStatus = ContactAuthStatus.UNVERIFIED
            )
        )

        val duplicateStr = "ITANTRA:1|$peerId|NODE CHARLIE|Patrol Charlie|hi,en"
        val validation = QrPairingValidator.validateScannedQr(
            rawPayload = duplicateStr,
            localNodeId = localNodeId,
            contactRepository = repository
        )

        assertTrue("Duplicate contact must be flagged in fallback mode", validation is ValidationResult.Duplicate)
    }

    @Test
    fun payloadFormat_preservesExactWireFormat() {
        val raw = "ITANTRA:1|332211|ALPHA PATROL|Lead Scout|hi,mr,en"
        val decoded = QrIdentityCodec.decodeOrThrow(raw)

        assertEquals(332211, decoded.nodeId)
        assertEquals("ALPHA PATROL", decoded.callsign)
        assertEquals("Lead Scout", decoded.displayName)
        assertEquals(3, decoded.supportedLanguages.size)
        assertTrue(decoded.supportedLanguages.contains(IndicLanguage.HINDI))
        assertTrue(decoded.supportedLanguages.contains(IndicLanguage.MARATHI))
        assertTrue(decoded.supportedLanguages.contains(IndicLanguage.ENGLISH))

        val reEncoded = QrIdentityCodec.encode(decoded)
        assertEquals(raw, reEncoded)
    }
}
