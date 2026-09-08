package org.sih.itantra.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.mesh.TopologyNodeState
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.search.LocalSearchRepository
import org.sih.itantra.core.search.SearchContactItem
import org.sih.itantra.presentation.screens.GlobalSearchScreen
import org.sih.itantra.presentation.theme.ITantraTheme

/**
 * Dedicated debug & validation activity for Offline Global Search.
 *
 * Allows physical smoke testing on Phone A (Samsung Galaxy A55 5G)
 * without altering shared MainActivity navigation.
 */
class GlobalSearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seed development fixture data if store is currently empty
        seedFixtureDataIfEmpty()

        setContent {
            ITantraTheme(darkTheme = true) {
                val repository = remember {
                    val repo = LocalSearchRepository(applicationContext)
                    // Register sample contact provider for testing
                    repo.setContactProvider {
                        listOf(
                            SearchContactItem(
                                nodeId = 477124,
                                callsign = "SQUAD BRAVO",
                                displayName = "Ridge Patrol Unit",
                                supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.MARATHI, IndicLanguage.ENGLISH),
                                authStatus = "AUTH ✓",
                                notes = "Assigned to Sector 4 outpost",
                                isOffline = false
                            ),
                            SearchContactItem(
                                nodeId = 981240,
                                callsign = "BASE ALPHA",
                                displayName = "Field HQ",
                                supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.TAMIL, IndicLanguage.ENGLISH),
                                authStatus = "AUTH ✓",
                                notes = "Command and dispatch station",
                                isOffline = false
                            )
                        )
                    }
                    repo.updateTopology(
                        MeshTopologySnapshot(
                            nodes = listOf(
                                TopologyNode(
                                    nodeId = 477124,
                                    displayName = "SQUAD BRAVO",
                                    isLocal = false,
                                    isReachable = true,
                                    lastSeen = "2m ago",
                                    lastSeenMs = System.currentTimeMillis() - 120_000L,
                                    hopCount = 1,
                                    transport = "Wi-Fi Direct",
                                    routeState = "DIRECT",
                                    role = TopologyNodeRole.NEIGHBOR,
                                    state = TopologyNodeState.ONLINE
                                ),
                                TopologyNode(
                                    nodeId = 882194,
                                    displayName = "RELAY ECHO",
                                    isLocal = false,
                                    isReachable = true,
                                    lastSeen = "5m ago",
                                    lastSeenMs = System.currentTimeMillis() - 300_000L,
                                    hopCount = 2,
                                    transport = "Bluetooth Mesh",
                                    routeState = "RELAYED",
                                    role = TopologyNodeRole.RELAY,
                                    state = TopologyNodeState.ONLINE
                                )
                            )
                        )
                    )
                    repo
                }

                GlobalSearchScreen(
                    searchRepository = repository,
                    onBack = { finish() },
                    onOpenChat = { peerId ->
                        Toast.makeText(this, "Action: Open Chat ($peerId)", Toast.LENGTH_SHORT).show()
                    },
                    onOpenContact = { nodeId ->
                        Toast.makeText(this, "Action: Open Contact (Node #$nodeId)", Toast.LENGTH_SHORT).show()
                    },
                    onInspectMessage = { msgId ->
                        Toast.makeText(this, "Action: Inspect Message ($msgId)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
        }
    }

    private fun seedFixtureDataIfEmpty() {
        if (MessageHistoryStore.getRecords().isEmpty()) {
            val now = System.currentTimeMillis()

            MessageHistoryStore.addRecord(
                MessageRecord(
                    id = "msg_seed_1",
                    timestamp = now - 60_000L,
                    direction = MessageDirection.RECEIVED,
                    language = IndicLanguage.HINDI,
                    priority = MessagePriority.DISTRESS,
                    text = "Ridge Trail पर सहायता की आवश्यकता है। (Assistance requested at Ridge Trail.)",
                    peer = "Node #477124",
                    packetSizeBytes = 64,
                    rawAudioEquivalentBytes = 48000,
                    measuredLatencyMs = 42.0,
                    isRelayed = false,
                    hopCount = 1,
                    deliveryStatus = DeliveryStatus.DELIVERED,
                    isSecure = true
                )
            )

            MessageHistoryStore.addRecord(
                MessageRecord(
                    id = "msg_seed_2",
                    timestamp = now - 180_000L,
                    direction = MessageDirection.SENT,
                    language = IndicLanguage.ENGLISH,
                    priority = MessagePriority.NORMAL,
                    text = "Sector 4 reconnaissance patrol complete. All routes clear.",
                    peer = "Node #477124",
                    packetSizeBytes = 48,
                    rawAudioEquivalentBytes = 32000,
                    measuredLatencyMs = 38.0,
                    isRelayed = false,
                    hopCount = 1,
                    deliveryStatus = DeliveryStatus.DELIVERED,
                    isSecure = true
                )
            )

            MessageHistoryStore.addRecord(
                MessageRecord(
                    id = "msg_seed_3",
                    timestamp = now - 360_000L,
                    direction = MessageDirection.RECEIVED,
                    language = IndicLanguage.TAMIL,
                    priority = MessagePriority.ALERT,
                    text = "நாங்கள் பாதுகாப்பாக உள்ளோம். (We are safe.)",
                    peer = "Node #981240",
                    packetSizeBytes = 52,
                    rawAudioEquivalentBytes = 36000,
                    measuredLatencyMs = 65.0,
                    isRelayed = true,
                    hopCount = 2,
                    deliveryStatus = DeliveryStatus.DELIVERED,
                    isSecure = true
                )
            )
        }
    }
}
