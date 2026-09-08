package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.discovery.DiscoveryScanningState
import org.sih.itantra.core.discovery.DiscoverySourceStatus
import org.sih.itantra.presentation.theme.ColorSignalBlue
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical status banner displaying active discovery channels, capability readiness,
 * and permission states.
 */
@Composable
fun DiscoveryStatusBanner(
    scanningState: DiscoveryScanningState,
    onEnableBluetooth: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Warning Banner: Bluetooth Disabled
        if (scanningState.bleStatus == DiscoverySourceStatus.DISABLED) {
            PermissionWarningBanner(
                icon = Icons.Default.BluetoothDisabled,
                title = "BLUETOOTH ADAPTER DISABLED",
                description = "Bluetooth must be enabled for local BLE device discovery.",
                actionLabel = "ENABLE BLUETOOTH",
                onAction = onEnableBluetooth,
                containerColor = radioColors.alert.copy(alpha = 0.15f),
                borderColor = radioColors.alert,
                contentColor = radioColors.alert
            )
        }

        // Warning Banner: Permission Required
        if (scanningState.bleStatus == DiscoverySourceStatus.PERMISSION_REQUIRED) {
            PermissionWarningBanner(
                icon = Icons.Default.Security,
                title = "BLUETOOTH SCAN PERMISSION REQUIRED",
                description = "Nearby device discovery requires BLUETOOTH_SCAN permission.",
                actionLabel = "GRANT PERMISSION",
                onAction = onRequestPermission,
                containerColor = radioColors.warning.copy(alpha = 0.15f),
                borderColor = radioColors.warning,
                contentColor = radioColors.warning
            )
        }

        // Capability Status Grid Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISCOVERY CHANNELS",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    // Discovered node count badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (radioColors.isDark) radioColors.sage.copy(alpha = 0.2f) else radioColors.forest.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (scanningState.isScanning) radioColors.sage else radioColors.textTertiary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${scanningState.discoveredCount} DISCOVERED",
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Channel Rows
                DiscoveryChannelRow(
                    label = "BLE DISCOVERY",
                    status = scanningState.bleStatus
                )

                DiscoveryChannelRow(
                    label = "UWB PROXIMITY",
                    status = scanningState.uwbStatus
                )

                DiscoveryChannelRow(
                    label = "MESH TOPOLOGY",
                    status = scanningState.meshStatus
                )
            }
        }
    }
}

@Composable
private fun DiscoveryChannelRow(
    label: String,
    status: DiscoverySourceStatus
) {
    val radioColors = LocalRadioColors.current

    val (statusColor, statusText) = when (status) {
        DiscoverySourceStatus.ACTIVE -> Pair(radioColors.sage, "ACTIVE")
        DiscoverySourceStatus.STANDBY -> Pair(ColorSignalBlue, "STANDBY")
        DiscoverySourceStatus.DISABLED -> Pair(radioColors.alert, "DISABLED")
        DiscoverySourceStatus.PERMISSION_REQUIRED -> Pair(radioColors.warning, "PERMISSION REQ")
        DiscoverySourceStatus.UNAVAILABLE -> Pair(radioColors.textTertiary, "NOT AVAILABLE ON THIS DEVICE")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = radioColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PermissionWarningBanner(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics { contentDescription = "$title: $description" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = description,
                    color = radioColors.textPrimary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(contentColor)
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = if (radioColors.isDark) Color.Black else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
