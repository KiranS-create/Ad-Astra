package org.sih.itantra.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Targeted compatibility test verifying:
 * 1. Correct Foreground Service Type constant (connectedDevice).
 * 2. Manifest compliance: FOREGROUND_SERVICE_CONNECTED_DEVICE declared, dataSync removed.
 * 3. Service constants and state transitions for ManetNodeService.
 */
class ManetServiceCompatibilityTest {

    @Test
    fun verifyForegroundServiceTypeConstant() {
        // android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE is 0x00000010 (16)
        assertEquals(
            "FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE must match Android API constant",
            0x00000010,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    @Test
    fun verifyManifestDeclarations() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml should exist at expected relative path", manifestFile.exists())
        val content = manifestFile.readText()

        // Verify connectedDevice is declared
        assertTrue(
            "Manifest must declare FOREGROUND_SERVICE_CONNECTED_DEVICE permission",
            content.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE")
        )
        assertTrue(
            "Manifest must set foregroundServiceType=\"connectedDevice\"",
            content.contains("android:foregroundServiceType=\"connectedDevice\"")
        )

        // Verify dataSync is removed
        assertFalse(
            "Manifest must NOT declare FOREGROUND_SERVICE_DATA_SYNC (subject to Android 15 6-hour timeout)",
            content.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
        )
        assertFalse(
            "Manifest must NOT set foregroundServiceType=\"dataSync\"",
            content.contains("android:foregroundServiceType=\"dataSync\"")
        )

        // Verify stopWithTask=false so node survives UI dismissal
        assertTrue(
            "Service must declare android:stopWithTask=\"false\"",
            content.contains("android:stopWithTask=\"false\"")
        )

        // Verify required runtime/manifest permissions for connectedDevice are declared
        assertTrue(content.contains("android.permission.BLUETOOTH_CONNECT"))
        assertTrue(content.contains("android.permission.CHANGE_WIFI_MULTICAST_STATE"))
    }

    @Test
    fun verifyServiceConstants() {
        assertEquals("manet_node", ManetNodeService.CHANNEL_ID)
        assertEquals(1001, ManetNodeService.NOTIFICATION_ID)
        assertEquals("org.sih.itantra.ACTION_DISABLE_NODE", ManetNodeService.ACTION_DISABLE_NODE)
        assertEquals("org.sih.itantra.ACTION_OPEN_APP", ManetNodeService.ACTION_OPEN_APP)
    }

    @Test
    fun verifyServiceStateModel() {
        val initial = ManetNodeService.ManetServiceState()
        assertFalse(initial.isRunning)
        assertEquals(0, initial.localNodeId)
        assertEquals(0, initial.neighborCount)
        assertEquals(0, initial.routeCount)
        assertEquals(0L, initial.packetsRelayed)

        val active = initial.copy(
            isRunning = true,
            localNodeId = 123456,
            neighborCount = 3,
            routeCount = 2,
            packetsRelayed = 15L
        )
        assertTrue(active.isRunning)
        assertEquals(123456, active.localNodeId)
        assertEquals(3, active.neighborCount)
        assertEquals(2, active.routeCount)
        assertEquals(15L, active.packetsRelayed)

        val stopped = active.copy(isRunning = false, neighborCount = 0, routeCount = 0)
        assertFalse(stopped.isRunning)
        assertEquals(0, stopped.neighborCount)
    }
}
