package org.sih.itantra.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.journey.MessageJourneyMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.presentation.screens.MessageJourneyScreen
import org.sih.itantra.presentation.theme.ITantraTheme
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Dedicated debug & validation activity for Feature 9: Message Journey Visualization.
 *
 * Allows physical smoke testing on Phone A (Samsung Galaxy A55 5G)
 * without altering shared MainActivity navigation.
 */
class MessageJourneyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ITantraTheme(darkTheme = true) {
                val radioColors = LocalRadioColors.current

                // Realistic fixture scenarios for operator physical smoke testing
                val scenarios = remember {
                    listOf(
                        // 0: Nominal Relayed Message Delivered
                        MessageRecord(
                            id = "msg-8821-042",
                            timestamp = System.currentTimeMillis() - 120_000L,
                            direction = MessageDirection.SENT,
                            language = IndicLanguage.ENGLISH,
                            priority = MessagePriority.NORMAL,
                            text = "Proceed to checkpoint 4",
                            peer = "Node #209070",
                            packetSizeBytes = 256,
                            rawAudioEquivalentBytes = 0L,
                            measuredLatencyMs = 4.2,
                            isRelayed = true,
                            hopCount = 2,
                            deliveryStatus = DeliveryStatus.DELIVERED,
                            transferId = 42.toShort(),
                            deliveryLatencyMs = 42L,
                            qosStatus = "WIFI/QUEUED",
                            isSecure = true,
                            authStatus = "AUTH ✓"
                        ),
                        // 1: DTN Stored Message
                        MessageRecord(
                            id = "dtn-msg-5501",
                            timestamp = System.currentTimeMillis() - 60_000L,
                            direction = MessageDirection.SENT,
                            language = IndicLanguage.HINDI,
                            priority = MessagePriority.IMPORTANT,
                            text = "चौकी 3 के लिए पुनः आपूर्ति अनुरोध",
                            peer = "Node #882104",
                            packetSizeBytes = 312,
                            rawAudioEquivalentBytes = 0L,
                            measuredLatencyMs = 6.8,
                            isRelayed = true,
                            hopCount = 0,
                            deliveryStatus = DeliveryStatus.PENDING,
                            transferId = 101.toShort(),
                            qosStatus = null,
                            isSecure = true,
                            authStatus = "AUTH ✓"
                        ),
                        // 2: Delivery Failed (Timeout)
                        MessageRecord(
                            id = "fail-msg-9901",
                            timestamp = System.currentTimeMillis() - 45_000L,
                            direction = MessageDirection.SENT,
                            language = IndicLanguage.ENGLISH,
                            priority = MessagePriority.NORMAL,
                            text = "Check radio frequency 433 MHz",
                            peer = "Node #330012",
                            packetSizeBytes = 180,
                            rawAudioEquivalentBytes = 0L,
                            measuredLatencyMs = 3.1,
                            isRelayed = false,
                            hopCount = 1,
                            deliveryStatus = DeliveryStatus.TIMEOUT,
                            transferId = 88.toShort(),
                            deliveryLatencyMs = null,
                            isSecure = false,
                            authStatus = "UNVERIFIED"
                        ),
                        // 3: Emergency Distress Broadcast
                        MessageRecord(
                            id = "distress-4412",
                            timestamp = System.currentTimeMillis() - 15_000L,
                            direction = MessageDirection.SENT,
                            language = IndicLanguage.ENGLISH,
                            priority = MessagePriority.DISTRESS,
                            text = "MAYDAY MAYDAY VEHICLE ROLLOVER SECTOR 7",
                            peer = "Emergency Broadcast",
                            packetSizeBytes = 144,
                            rawAudioEquivalentBytes = 0L,
                            measuredLatencyMs = 1.8,
                            isRelayed = false,
                            hopCount = 1,
                            deliveryStatus = DeliveryStatus.DELIVERED,
                            transferId = 999.toShort(),
                            deliveryLatencyMs = 12L,
                            isSecure = true,
                            authStatus = "AUTH ✓"
                        ),
                        // 4: Incomplete / Historical Message (Predating telemetry)
                        MessageRecord(
                            id = "hist-0012-leg",
                            timestamp = 1690000000000L,
                            direction = MessageDirection.SENT,
                            language = IndicLanguage.ENGLISH,
                            priority = MessagePriority.NORMAL,
                            text = "Initial field deployment check",
                            peer = "Node #1002",
                            packetSizeBytes = 128,
                            rawAudioEquivalentBytes = 0L,
                            measuredLatencyMs = 0.0,
                            isRelayed = false,
                            hopCount = 0,
                            deliveryStatus = DeliveryStatus.NONE,
                            transferId = null,
                            deliveryLatencyMs = null
                        )
                    )
                }

                val scenarioLabels = listOf("RELAYED", "DTN STORED", "TIMEOUT", "DISTRESS P1", "HISTORICAL")
                var selectedIndex by remember { mutableIntStateOf(0) }
                val currentRecord = scenarios[selectedIndex]
                val journey = remember(currentRecord.id, currentRecord.deliveryStatus) {
                    MessageJourneyMapper.map(currentRecord)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(radioColors.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Scenario Selector Bar for physical device test control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(radioColors.surface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCENARIO:",
                            color = radioColors.textTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        scenarioLabels.forEachIndexed { idx, label ->
                            val isSelected = selectedIndex == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) {
                                            if (idx == 3) radioColors.alert else radioColors.sage
                                        } else radioColors.capsule
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) {
                                            if (idx == 3) radioColors.alert else radioColors.sage
                                        } else radioColors.border,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { selectedIndex = idx }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) radioColors.background else radioColors.textSecondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Render the production Journey screen
                    MessageJourneyScreen(
                        journey = journey,
                        onBack = { finish() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
