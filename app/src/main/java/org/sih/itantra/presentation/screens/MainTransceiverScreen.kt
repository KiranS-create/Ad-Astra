package org.sih.itantra.presentation.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.core.protocol.VoiceCommandState
import org.sih.itantra.presentation.components.LanguageSelectorPill
import org.sih.itantra.presentation.components.RadioPttControl
import org.sih.itantra.presentation.components.RadioTranscriptRow
import org.sih.itantra.presentation.components.TopRadioHeader
import org.sih.itantra.presentation.components.WaveformVisualizer
import androidx.compose.material3.MaterialTheme
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.theme.TacticalShapeTokens
import org.sih.itantra.presentation.theme.TacticalType
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTransceiverScreen(
    viewModel: TransceiverViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToModelAudit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val pttState by viewModel.pttState.collectAsState()
    val activeTransport by viewModel.activeTransportType.collectAsState()
    val btState by viewModel.bluetoothTransportState.collectAsState()
    val bondedPeers by viewModel.bondedBluetoothDevices.collectAsState()
    val voiceStatus by viewModel.voiceEngineStatus.collectAsState()
    val isContinuous by viewModel.isContinuousMode.collectAsState()
    val languageState by viewModel.languageState.collectAsState()
    val history by viewModel.messageHistory.collectAsState()
    val lastTranscribed by viewModel.lastTranscribedText.collectAsState()
    val isRelayEnabled by viewModel.isRelayEnabled.collectAsState()
    val voiceCommandState by viewModel.voiceCommandState.collectAsState()
    val voiceCommandStatusLabel by viewModel.voiceCommandStatusLabel.collectAsState()

    var showBtSheet by remember { mutableStateOf(false) }
    val btSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val distressStatus by viewModel.distressStatus.collectAsState()
    var showDistressDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // On permission result (granted or denied), send distress immediately
        viewModel.sendDistress()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Telemetry Header & Status Capsule
        TopRadioHeader(
            activeTransport = activeTransport,
            bluetoothState = btState,
            voiceStatus = voiceStatus,
            isRelayEnabled = isRelayEnabled,
            onOpenSettings = onNavigateToSettings,
            onBluetoothClick = {
                viewModel.setTransport(TransportType.BLUETOOTH)
                viewModel.refreshBondedBluetoothDevices()
                showBtSheet = true
            },
            onWifiClick = {
                viewModel.setTransport(TransportType.WIFI)
            },
            onModelClick = {
                onNavigateToModelAudit()
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Language Selector Pill (Dropdown opening 10 Indic languages + AUTO)
        LanguageSelectorPill(
            currentState = languageState,
            onModeSelected = { viewModel.setLanguageMode(it) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Live Speech / Transmission Ticker
        if (lastTranscribed.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.surfaceHighlight)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LAST TX: ",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = lastTranscribed,
                        color = radioColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 5. Dedicated LIVE RADIO TRAFFIC Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(TacticalShapeTokens.Card)
                .background(radioColors.surface.copy(alpha = 0.5f))
                .border(1.dp, radioColors.border.copy(alpha = 0.35f), TacticalShapeTokens.Card)
                .padding(10.dp)
        ) {
            // Live Radio Traffic Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LIVE RADIO TRAFFIC",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(TacticalShapeTokens.Tag)
                            .background((if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "REAL-TIME",
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            style = TacticalType.badgeLabel
                        )
                    }
                }
                Text(
                    text = if (history.isEmpty()) "CH-1 IDLE" else "${history.size} MSG${if (history.size > 1) "S" else ""}",
                    color = radioColors.textTertiary,
                    style = TacticalType.telemetryCode
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (history.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NO RADIO MESSAGES RECEIVED YET",
                            color = radioColors.textTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hold PTT or send a test packet to transmit",
                            color = radioColors.textTertiary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(history) { record ->
                        RadioTranscriptRow(record = record)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 6. Compact Rectangular PTT Control (Refined from legacy Central Large Circular PTT)
        RadioPttControl(
            pttState = pttState,
            isContinuousMode = isContinuous,
            onPressStart = { viewModel.startPtt() },
            onPressRelease = { viewModel.stopPtt() }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Emergency Distress Status Ticker (if active)
        distressStatus?.let { status ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.surfaceHighlight)
                    .border(1.dp, radioColors.alert.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status,
                        color = radioColors.alert,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "[DISMISS]",
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { viewModel.clearDistressStatus() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 7. Tactical Controls Row: WALKIE PTT -> SEND DISTRESS -> TEST PACKET
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Toggle (PTT vs Continuous Phone Mode)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(TacticalShapeTokens.Button)
                    .background(if (isContinuous) radioColors.surfaceHighlight else radioColors.surface)
                    .border(
                        1.dp,
                        if (isContinuous) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.border.copy(alpha = 0.4f),
                        TacticalShapeTokens.Button
                    )
                    .clickable { viewModel.toggleContinuousMode() }
                    .padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = "Radio Mode",
                    tint = if (isContinuous) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isContinuous) "CONTINUOUS" else "WALKIE PTT",
                    color = radioColors.textPrimary,
                    style = TacticalType.badgeLabel,
                    maxLines = 1
                )
            }

            // Emergency Distress Trigger Action — Located Physically Between Walkie PTT & Test Packet
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1.15f)
                    .height(42.dp)
                    .clip(TacticalShapeTokens.Button)
                    .background(radioColors.alert.copy(alpha = 0.18f))
                    .border(1.5.dp, radioColors.alert, TacticalShapeTokens.Button)
                    .clickable { showDistressDialog = true }
                    .padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Send Distress Emergency",
                    tint = radioColors.alert,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SEND DISTRESS",
                    color = radioColors.alert,
                    style = TacticalType.badgeLabel,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }

            // Quick Neural Loopback Test button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(TacticalShapeTokens.Button)
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border.copy(alpha = 0.4f), TacticalShapeTokens.Button)
                    .clickable { viewModel.testNeuralLoopback() }
                    .padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Test Packet",
                    tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "TEST PACKET",
                    color = radioColors.textPrimary,
                    style = TacticalType.badgeLabel,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 8. Voice Control Status Card (compact, always visible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when (voiceCommandState) {
                        VoiceCommandState.AWAITING_CONFIRMATION -> radioColors.alert.copy(alpha = 0.15f)
                        VoiceCommandState.EXECUTING -> radioColors.surfaceHighlight
                        VoiceCommandState.ERROR -> radioColors.alert.copy(alpha = 0.10f)
                        else -> radioColors.surface
                    }
                )
                .border(
                    1.dp,
                    when (voiceCommandState) {
                        VoiceCommandState.AWAITING_CONFIRMATION -> radioColors.alert
                        VoiceCommandState.EXECUTING -> if (radioColors.isDark) radioColors.sage else radioColors.forest
                        else -> radioColors.border.copy(alpha = 0.4f)
                    },
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (voiceCommandState == VoiceCommandState.AWAITING_CONFIRMATION) {
                Column {
                    Text(
                        text = "🎙 $voiceCommandStatusLabel",
                        color = radioColors.alert,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.confirmVoiceCommand() },
                            colors = ButtonDefaults.buttonColors(containerColor = radioColors.alert),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "CONFIRM",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                        Button(
                            onClick = { viewModel.rejectVoiceCommand() },
                            colors = ButtonDefaults.buttonColors(containerColor = radioColors.surfaceHighlight),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "REJECT",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = radioColors.textSecondary
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎙 $voiceCommandStatusLabel",
                        color = when (voiceCommandState) {
                            VoiceCommandState.EXECUTING -> if (radioColors.isDark) radioColors.sage else radioColors.forest
                            VoiceCommandState.ERROR -> radioColors.alert
                            else -> radioColors.textSecondary
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isContinuous) "HANDS-FREE" else "PTT MODE READY",
                        color = radioColors.textTertiary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Bluetooth Peer Selection Bottom Sheet
    if (showBtSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBtSheet = false },
            sheetState = btSheetState,
            containerColor = radioColors.surface,
            contentColor = radioColors.textPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BLUETOOTH RFCOMM PEERS",
                        color = radioColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.refreshBondedBluetoothDevices() }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Refresh",
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Current State: $btState",
                    color = when (btState) {
                        TransportState.CONNECTED -> radioColors.success
                        TransportState.CONNECTING -> Color(0xFF0288D1)
                        TransportState.LISTENING -> radioColors.textSecondary
                        else -> radioColors.alert
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (bondedPeers.isEmpty()) {
                    Text(
                        text = "No bonded Bluetooth devices found. Pair Phone A and Phone B in Android Settings first.",
                        color = radioColors.alert,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        items(bondedPeers) { peer ->
                            val isConnected = peer.isConnected && btState == TransportState.CONNECTED
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isConnected) radioColors.surfaceHighlight else radioColors.surface)
                                    .border(
                                        1.dp,
                                        if (isConnected) radioColors.success else radioColors.border.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        viewModel.connectBluetooth(peer.address)
                                        showBtSheet = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                if (isConnected) radioColors.success.copy(alpha = 0.15f) else radioColors.capsule,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = if (isConnected) radioColors.success else radioColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = peer.name.ifEmpty { "Unknown" },
                                            color = radioColors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = peer.address,
                                            color = radioColors.textTertiary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Text(
                                    text = if (isConnected) "CONNECTED" else "CONNECT",
                                    color = if (isConnected) radioColors.success else (if (radioColors.isDark) radioColors.sage else radioColors.forest),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Emergency Distress Confirmation Dialog
    if (showDistressDialog) {
        AlertDialog(
            onDismissRequest = { showDistressDialog = false },
            containerColor = radioColors.surface,
            titleContentColor = radioColors.alert,
            textContentColor = radioColors.textPrimary,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = radioColors.alert,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "CONFIRM EMERGENCY DISTRESS",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    text = "This will immediately broadcast a highest-priority emergency distress packet over the MANET mesh network.\n\nCurrent on-device GPS location will be attached if available. If permission is denied or location is unavailable, distress is still transmitted immediately.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDistressDialog = false
                        if (viewModel.hasLocationPermission()) {
                            viewModel.sendDistress()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = radioColors.alert)
                ) {
                    Text(
                        text = "CONFIRM & TRANSMIT",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDistressDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = radioColors.surfaceHighlight)
                ) {
                    Text(
                        text = "CANCEL",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = radioColors.textSecondary
                    )
                }
            }
        )
    }
}
