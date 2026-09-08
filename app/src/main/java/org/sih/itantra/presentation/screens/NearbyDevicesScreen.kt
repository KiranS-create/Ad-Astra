package org.sih.itantra.presentation.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.sih.itantra.core.discovery.NearbyDevice
import org.sih.itantra.core.discovery.NearbyDeviceRepository
import org.sih.itantra.presentation.components.DiscoveryStatusBanner
import org.sih.itantra.presentation.components.NearbyDeviceCard
import org.sih.itantra.presentation.components.TacticalRadarScanAnimation
import org.sih.itantra.presentation.theme.ColorSignalBlue
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Dedicated "Nearby iTantra Devices" screen (Feature 4).
 *
 * Implements states A through E:
 * - STATE A: Active scanning animation and live capability matrix.
 * - STATE B: List of discovered nodes ordered by proximity hierarchy.
 * - STATE C: Detailed node inspection and explicit user confirmation dialog.
 * - STATE D: Informative offline empty state with retry affordance.
 * - STATE E: Dynamic Bluetooth & permission guidance banners.
 */
@Composable
fun NearbyDevicesScreen(
    repository: NearbyDeviceRepository,
    onBack: () -> Unit = {},
    onAddContact: (NearbyDevice) -> Unit = {},
    onOpenChat: (Int) -> Unit = {},
    onTestConnection: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val devices by repository.discoveredDevices.collectAsState()
    val scanningState by repository.scanningState.collectAsState()

    var pendingContactDevice by remember { mutableStateOf<NearbyDevice?>(null) }
    var connectionTestNodeId by remember { mutableStateOf<Int?>(null) }

    // Permission launcher for Android 12+ BLUETOOTH_SCAN
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch { repository.startScanning() }
        } else {
            Toast.makeText(context, "Bluetooth scan permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Bluetooth enable intent launcher
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { repository.startScanning() }
        }
    }

    // Lifecycle: start scanning on entry, clean up on exit
    DisposableEffect(Unit) {
        scope.launch {
            repository.startScanning()
        }
        onDispose {
            scope.launch {
                repository.stopScanning()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Tactical Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(radioColors.surface)
                            .border(1.dp, radioColors.border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = radioColors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "NEARBY iTANTRA DEVICES",
                            color = radioColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "OFFLINE LOCAL DISCOVERY",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                // Rescan Action Icon
                IconButton(
                    onClick = {
                        scope.launch {
                            repository.startScanning()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (scanningState.isScanning) radioColors.sage.copy(alpha = 0.2f) else radioColors.surface)
                        .border(1.dp, if (scanningState.isScanning) radioColors.sage else radioColors.border, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart nearby scan",
                        tint = if (scanningState.isScanning) radioColors.sage else radioColors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Discovery Status & Hardware Capability Banner (STATE A / E)
            DiscoveryStatusBanner(
                scanningState = scanningState,
                onEnableBluetooth = {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBtLauncher.launch(intent)
                },
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.BLUETOOTH_SCAN)
                }
            )

            // 3. Discovered Nodes List OR Scanning/Empty State
            if (devices.isNotEmpty()) {
                // STATE B: Discovered Devices
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(devices, key = { if (it.nodeId > 0) it.nodeId.toString() else it.callsign }) { device ->
                        NearbyDeviceCard(
                            device = device,
                            onAddContact = { pendingContactDevice = it },
                            onOpenChat = { onOpenChat(it) },
                            onTestConnection = {
                                connectionTestNodeId = it
                                onTestConnection(it)
                            }
                        )
                    }
                }
            } else if (scanningState.isScanning) {
                // STATE A: Active Scanning (0 devices discovered yet)
                ActiveScanningStateView()
            } else {
                // STATE D: Offline Empty State
                OfflineEmptyStateView(
                    onRestartScan = {
                        scope.launch { repository.startScanning() }
                    }
                )
            }
        }

        // STEP 8: Security / Trust Confirmation Dialog before Adding Contact
        pendingContactDevice?.let { device ->
            AlertDialog(
                onDismissRequest = { pendingContactDevice = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = radioColors.warning,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "ADD NODE TO CONTACTS?",
                        color = radioColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Node: #${device.nodeId} (${device.callsign})",
                            color = radioColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Proximity: ${device.proximityState.label} (${device.discoverySource.badgeText})",
                            color = ColorSignalBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "NOTICE: Local radio proximity indicates physical vicinity, but does not verify cryptographic peer identity. Are you sure you want to add this node to tactical contacts?",
                            color = radioColors.textSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = pendingContactDevice
                            pendingContactDevice = null
                            target?.let {
                                onAddContact(it)
                                Toast.makeText(context, "Node #${it.nodeId} confirmed and added", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CONFIRM & ADD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingContactDevice = null }) {
                        Text("CANCEL", color = radioColors.textSecondary, fontFamily = FontFamily.Monospace)
                    }
                },
                containerColor = radioColors.surface,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // STEP 10: Connection Test Diagnostics Info Modal
        connectionTestNodeId?.let { nodeId ->
            AlertDialog(
                onDismissRequest = { connectionTestNodeId = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ColorSignalBlue,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "RADIO CONNECTION TEST",
                        color = radioColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Text(
                        text = "Testing connectivity to Node #$nodeId.\n\nConnection test is coordinated with the active MANET radio transceiver stack. Check Diagnostics tab for raw packet telemetry.",
                        color = radioColors.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { connectionTestNodeId = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorSignalBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("DISMISS", fontFamily = FontFamily.Monospace)
                    }
                },
                containerColor = radioColors.surface,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

/**
 * State A: Active radar scanning view when waiting for devices.
 */
@Composable
private fun ActiveScanningStateView() {
    val radioColors = LocalRadioColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TacticalRadarScanAnimation(size = 130.dp, isActive = true)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "SEARCHING FOR iTANTRA NODES...",
            color = radioColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Listening on BLE advertising channels and local Wi-Fi multicast beacons.",
            color = radioColors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * State D: Tactical Offline Empty State.
 */
@Composable
private fun OfflineEmptyStateView(
    onRestartScan: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(radioColors.surface)
                .border(1.dp, radioColors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = radioColors.textTertiary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NO NEARBY iTANTRA NODES",
            color = radioColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            OfflineGuidanceBullet(text = "Ensure nearby devices have iTantra discovery active.")
            OfflineGuidanceBullet(text = "Peer device must be within physical Bluetooth or local Wi-Fi range.")
            OfflineGuidanceBullet(text = "100% offline ad-hoc discovery — no Internet or cloud required.")
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onRestartScan,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = radioColors.textPrimary
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(radioColors.border)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "RESTART SCAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun OfflineGuidanceBullet(text: String) {
    val radioColors = LocalRadioColors.current

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "• ",
            color = radioColors.sage,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            color = radioColors.textSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}
