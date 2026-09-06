package org.sih.itantra.presentation.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.presentation.components.StatusHeader
import org.sih.itantra.presentation.components.TacticalPttButton
import org.sih.itantra.presentation.components.TranscriptBubble
import org.sih.itantra.presentation.components.WaveformVisualizer
import org.sih.itantra.presentation.theme.DistressRed
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBackground
import org.sih.itantra.presentation.theme.TacticalBorder
import org.sih.itantra.presentation.theme.TacticalSurface
import org.sih.itantra.presentation.theme.TextPrimary
import org.sih.itantra.presentation.theme.TextSecondary
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

@Composable
fun MainTransceiverScreen(
    viewModel: TransceiverViewModel,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToModelStatus: () -> Unit,
    onNavigateToBenchmark: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pttState by viewModel.pttState.collectAsState()
    val activeLang by viewModel.activeLanguage.collectAsState()
    val activeTransport by viewModel.activeTransportType.collectAsState()
    val isContinuous by viewModel.isContinuousMode.collectAsState()
    val history by viewModel.messageHistory.collectAsState()
    val lastTranscribed by viewModel.lastTranscribedText.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalBackground)
            .padding(16.dp)
    ) {
        // Telemetry Header
        StatusHeader(activeTransport = activeTransport)

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation tab bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            NavPill(text = "DIAGNOSTICS", onClick = onNavigateToDiagnostics)
            NavPill(text = "MODELS (10)", onClick = onNavigateToModelStatus)
            NavPill(text = "BENCHMARK", onClick = onNavigateToBenchmark)
            NavPill(text = "HISTORY (${history.size})", onClick = onNavigateToHistory)
            NavPill(text = "TEST NEURAL PTT", onClick = { viewModel.testNeuralLoopback() })
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Language Selector Chip Row
        Text(
            text = "ACTIVE LANGUAGE (10 INDIC)",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            IndicLanguage.entries.forEach { lang ->
                val isSelected = lang == activeLang
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) RadarGreen else TacticalSurface,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) RadarGreen else TacticalBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.setLanguage(lang) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${lang.displayName} (${lang.nativeName})",
                        color = if (isSelected) TacticalBackground else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Transport Selector Chip Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TransportType.entries.forEach { type ->
                val isSelected = type == activeTransport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) SignalBlue else TacticalSurface,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) SignalBlue else TacticalBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.setTransport(type) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (type) {
                            TransportType.WIFI -> "WI-FI"
                            TransportType.BLUETOOTH -> "BT SPP"
                            TransportType.LOOPBACK -> "LOOP"
                            TransportType.EMBEDDED_RADIO -> "SDR"
                        },
                        color = if (isSelected) TacticalBackground else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (activeTransport == TransportType.BLUETOOTH) {
            Spacer(modifier = Modifier.height(6.dp))
            val btState by viewModel.bluetoothTransportState.collectAsState()
            val bondedPeers by viewModel.bondedBluetoothDevices.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BT SPP: $btState",
                        color = when (btState) {
                            org.sih.itantra.core.transport.TransportState.CONNECTED -> RadarGreen
                            org.sih.itantra.core.transport.TransportState.CONNECTING -> SignalBlue
                            org.sih.itantra.core.transport.TransportState.LISTENING -> TextSecondary
                            else -> DistressRed
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clickable { viewModel.refreshBondedBluetoothDevices() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "[REFRESH]",
                            color = SignalBlue,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (bondedPeers.isEmpty()) {
                    Text(
                        text = "NO BONDED PEER (Pair devices in Android Settings)",
                        color = DistressRed,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bondedPeers.forEach { peer ->
                            val isConnected = peer.isConnected && btState == org.sih.itantra.core.transport.TransportState.CONNECTED
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isConnected) RadarGreen.copy(alpha = 0.2f) else TacticalBackground,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isConnected) RadarGreen else TacticalBorder,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { viewModel.connectBluetooth(peer.address) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isConnected) "CONNECTED: ${peer.name}" else "CONNECT: ${peer.name}",
                                    color = if (isConnected) RadarGreen else TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real-Time Oscilloscope Visualizer
        WaveformVisualizer(pttState = pttState)

        Spacer(modifier = Modifier.height(6.dp))

        // Live transcription ticker
        if (lastTranscribed.isNotBlank()) {
            Text(
                text = "TX >> $lastTranscribed",
                color = RadarGreen,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Transcript Log (Incoming & Outgoing Messages)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (history.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                    ) {
                        Text(
                            text = "RADIO CHANNEL QUIET // PRESS TALK OR SEND ALERT",
                            color = TextSecondary.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                items(history) { record ->
                    TranscriptBubble(record = record)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Controls: PTT + Continuous Mode + Emergency Distress
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Mode Toggle (PTT vs Continuous Phone Mode)
            Box(
                modifier = Modifier
                    .background(
                        if (isContinuous) SignalBlue.copy(alpha = 0.2f) else TacticalSurface,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (isContinuous) SignalBlue else TacticalBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { viewModel.toggleContinuousMode() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MODE",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isContinuous) "CONTINUOUS" else "WALKIE PTT",
                        color = if (isContinuous) SignalBlue else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Central Tactical PTT Push Button
            TacticalPttButton(
                pttState = pttState,
                isContinuousMode = isContinuous,
                onPressStart = { viewModel.startPtt() },
                onPressRelease = { viewModel.stopPtt() }
            )

            // Emergency Distress Trigger
            Box(
                modifier = Modifier
                    .background(DistressRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, DistressRed, RoundedCornerShape(8.dp))
                    .clickable { viewModel.sendEmergencyDistress() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Distress Icon",
                        tint = DistressRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DISTRESS",
                        color = DistressRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
