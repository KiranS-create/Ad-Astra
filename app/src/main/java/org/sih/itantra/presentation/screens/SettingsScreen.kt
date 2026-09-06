package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.presentation.theme.AppThemeMode
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

@Composable
fun SettingsScreen(
    viewModel: TransceiverViewModel,
    onOpenModelAudit: () -> Unit,
    onOpenManetDemo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val activeTransport by viewModel.activeTransportType.collectAsState()
    val btState by viewModel.bluetoothTransportState.collectAsState()
    val bondedPeers by viewModel.bondedBluetoothDevices.collectAsState()

    var distressHolding by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Screen Title
        Text(
            text = "FIELD RADIO SETTINGS",
            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "Hardware telemetry & operational parameters",
            color = radioColors.textSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Theme Configuration
        SectionHeader(title = "INTERFACE THEME")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOptionCard(
                title = "System",
                icon = Icons.Default.Brightness4,
                isSelected = currentThemeMode == AppThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeOptionCard(
                title = "Sand (Light)",
                icon = Icons.Default.LightMode,
                isSelected = currentThemeMode == AppThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOptionCard(
                title = "Charcoal (Dark)",
                icon = Icons.Default.DarkMode,
                isSelected = currentThemeMode == AppThemeMode.DARK,
                onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Transport Link Selection
        SectionHeader(title = "ACTIVE TRANSPORT LINK")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportOptionCard(
                name = "Wi-Fi Multicast",
                subtitle = "UDP 224.0.0.251",
                icon = Icons.Default.Wifi,
                isSelected = activeTransport == TransportType.WIFI,
                onClick = { viewModel.setTransport(TransportType.WIFI) },
                modifier = Modifier.weight(1f)
            )
            TransportOptionCard(
                name = "Bluetooth SPP",
                subtitle = "RFCOMM Classic",
                icon = Icons.Default.Bluetooth,
                isSelected = activeTransport == TransportType.BLUETOOTH,
                onClick = { viewModel.setTransport(TransportType.BLUETOOTH) },
                modifier = Modifier.weight(1f)
            )
        }

        // Bluetooth Peer Pairing Details if active
        if (activeTransport == TransportType.BLUETOOTH) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BT State: $btState",
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.refreshBondedBluetoothDevices() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
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

                    Spacer(modifier = Modifier.height(8.dp))

                    if (bondedPeers.isEmpty()) {
                        Text(
                            text = "No bonded peer found. Pair devices in Android Bluetooth Settings.",
                            color = radioColors.alert,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Bonded Devices (Tap to connect):",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        bondedPeers.forEach { peer ->
                            val isConnected = peer.isConnected && btState == TransportState.CONNECTED
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isConnected) radioColors.success.copy(alpha = 0.15f) else radioColors.capsule
                                    )
                                    .border(
                                        1.dp,
                                        if (isConnected) radioColors.success else radioColors.border.copy(alpha = 0.4f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.connectBluetooth(peer.address) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = peer.name.ifEmpty { "Unknown Device" },
                                        color = radioColors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = peer.address,
                                        color = radioColors.textTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                if (isConnected) {
                                    Text(
                                        text = "CONNECTED",
                                        color = radioColors.success,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                } else {
                                    Text(
                                        text = "CONNECT",
                                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. AI Engine Audit & Model Status
        SectionHeader(title = "ON-DEVICE NEURAL MODELS")
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onOpenModelAudit() }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "10 Indic Languages Offline Audit",
                        color = radioColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sherpa-ONNX Single-Active Eviction & Prewarm",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "VIEW AUDIT →",
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. MESH RELAY & HOP FORWARDING
        SectionHeader(title = "MESH RELAY & HOP FORWARDING")
        Spacer(modifier = Modifier.height(8.dp))

        val isRelayEnabled by viewModel.isRelayEnabled.collectAsState()
        val diagState by viewModel.diagnosticsState.collectAsState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(
                    1.dp,
                    if (isRelayEnabled) (if (radioColors.isDark) radioColors.sage else radioColors.forest)
                    else radioColors.border.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp)
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Autonomous Mesh Relay",
                                color = radioColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isRelayEnabled) radioColors.success.copy(alpha = 0.15f)
                                        else radioColors.capsule
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isRelayEnabled) "ACTIVE" else "STANDBY",
                                    color = if (isRelayEnabled) radioColors.success else radioColors.textTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Forward received radio packets to out-of-range nodes with TTL decrement and duplicate loop suppression.",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = isRelayEnabled,
                        onCheckedChange = { viewModel.setRelayEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if (radioColors.isDark) radioColors.sage else radioColors.forest
                        )
                    )
                }

                if (isRelayEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = radioColors.border.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Relayed: ${diagState.packetsRelayed}",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Dup Dropped: ${diagState.relayDuplicatesDropped}",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "TTL=0 Dropped: ${diagState.relayTtlExpired}",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. MANET NODE MODE — persistent background relay node
        val isNodeModeEnabled by viewModel.isNodeModeEnabled.collectAsState()
        val nodeNeighborCount by viewModel.nodeNeighborCount.collectAsState()
        val nodeRouteCount by viewModel.nodeRouteCount.collectAsState()

        SectionHeader(title = "MANET NODE MODE")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Operates when screen is off or app is closed",
            color = radioColors.textTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(
                    1.dp,
                    if (isNodeModeEnabled) (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.8f)
                    else radioColors.border.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp)
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Persistent Node",
                                color = radioColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isNodeModeEnabled) radioColors.success.copy(alpha = 0.15f)
                                        else radioColors.capsule
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isNodeModeEnabled) "ONLINE" else "OFFLINE",
                                    color = if (isNodeModeEnabled) radioColors.success else radioColors.textTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Continues neighbor discovery, routing, and packet relay when UI is closed or screen is off.",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = isNodeModeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) viewModel.startNodeMode()
                            else viewModel.stopNodeMode()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if (radioColors.isDark) radioColors.sage else radioColors.forest
                        )
                    )
                }

                if (isNodeModeEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = radioColors.border.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Neighbors: $nodeNeighborCount",
                            color = radioColors.success,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Routes: $nodeRouteCount",
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Service active · Notification visible in status bar",
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = radioColors.border.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (radioColors.isDark) radioColors.surfaceHighlight else radioColors.capsule)
                        .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onOpenManetDemo() }
                        .padding(vertical = 9.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "OPEN TOPOLOGY DEMO & SIMULATOR",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(radioColors.warning.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "SIM",
                            color = radioColors.warning,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. EMERGENCY DISTRESS SYSTEM (matching mockup specs)
        SectionHeader(title = "EMERGENCY & DISTRESS")
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(radioColors.alert.copy(alpha = 0.08f))
                .border(1.5.dp, radioColors.alert.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Distress",
                        tint = radioColors.alert,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TACTICAL DISTRESS BEACON",
                        color = radioColors.alert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Broadcasts a high-priority distress alert packet with guaranteed delivery override. Protected by hold-to-send guard.",
                    color = radioColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Hold to send button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (distressHolding) radioColors.alert else radioColors.alert.copy(alpha = 0.2f))
                        .border(1.dp, radioColors.alert, RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    distressHolding = true
                                    val released = tryAwaitRelease()
                                    distressHolding = false
                                    if (released) {
                                        viewModel.sendEmergencyDistress()
                                    }
                                }
                            )
                        }
                ) {
                    Text(
                        text = if (distressHolding) "TRANSMITTING DISTRESS ALERT..." else "HOLD TO SEND DISTRESS",
                        color = if (distressHolding) Color.White else radioColors.alert,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Compliance & Identity Credentials
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "iTantra — SIH26173 / ISRO Compliant",
                    color = radioColors.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Strictly 100% Offline On-Device Neural Transceiver",
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    val radioColors = LocalRadioColors.current
    Text(
        text = title,
        color = radioColors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ThemeOptionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val accent = if (radioColors.isDark) radioColors.sage else radioColors.forest

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) radioColors.surfaceHighlight else radioColors.surface)
            .border(
                1.5.dp,
                if (isSelected) accent else radioColors.border.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) accent else radioColors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = if (isSelected) radioColors.textPrimary else radioColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TransportOptionCard(
    name: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val accent = if (radioColors.isDark) radioColors.sage else radioColors.forest

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) radioColors.surfaceHighlight else radioColors.surface)
            .border(
                1.5.dp,
                if (isSelected) accent else radioColors.border.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (isSelected) accent.copy(alpha = 0.15f) else radioColors.capsule,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (isSelected) accent else radioColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = name,
                    color = if (isSelected) radioColors.textPrimary else radioColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
