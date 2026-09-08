package org.sih.itantra.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import org.sih.itantra.core.discovery.BleDiscoverySource
import org.sih.itantra.core.discovery.MeshTopologyDiscoverySource
import org.sih.itantra.core.discovery.NearbyDeviceRepository
import org.sih.itantra.core.discovery.UwbDiscoverySource
import org.sih.itantra.presentation.screens.NearbyDevicesScreen
import org.sih.itantra.presentation.theme.ITantraTheme
import org.sih.itantra.service.ManetNodePreference

/**
 * Isolated test & launch activity for Nearby iTantra Devices (Feature 4).
 * Allows independent physical testing without modifying shared MainActivity or BottomNavBar.
 */
class NearbyDevicesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storedNodeId = ManetNodePreference.lastNodeId(this).let {
            if (it != 0) it else 209070
        }

        setContent {
            ITantraTheme(darkTheme = true) {
                val repository = remember {
                    NearbyDeviceRepository(
                        localNodeId = storedNodeId,
                        bleSource = BleDiscoverySource(applicationContext),
                        uwbSource = UwbDiscoverySource(applicationContext),
                        meshSource = MeshTopologyDiscoverySource(
                            localNodeId = storedNodeId,
                            neighborTableProvider = { emptyList() },
                            routeTableProvider = { emptyList() }
                        )
                    )
                }

                NearbyDevicesScreen(
                    repository = repository,
                    onBack = { finish() },
                    onAddContact = { device ->
                        Toast.makeText(this, "Added contact: Node #${device.nodeId} (${device.callsign})", Toast.LENGTH_SHORT).show()
                    },
                    onOpenChat = { nodeId ->
                        Toast.makeText(this, "Open Chat: Node #$nodeId", Toast.LENGTH_SHORT).show()
                    },
                    onTestConnection = { nodeId ->
                        Toast.makeText(this, "Testing radio connection: Node #$nodeId", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
