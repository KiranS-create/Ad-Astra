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
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Top Telemetry Header for iTantra Field Radio.
 * Displays branding, quick settings, and the capsule row containing
 * Bluetooth, Wi-Fi, Neural AI model readiness, and Link indicators.
 */
@Composable
fun TopRadioHeader(
    activeTransport: TransportType,
    bluetoothState: TransportState,
    isModelReady: Boolean,
    onOpenSettings: () -> Unit,
    onBluetoothClick: () -> Unit,
    onWifiClick: () -> Unit,
    onModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Top row: Brand + Settings Gear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoBranding()

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = radioColors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Capsule Pill Row (matching hardware design mockup)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(radioColors.capsule, RoundedCornerShape(24.dp))
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Bluetooth Status Item
                val isBtConnected = bluetoothState == TransportState.CONNECTED
                val isBtActive = activeTransport == TransportType.BLUETOOTH
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onBluetoothClick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (isBtConnected) radioColors.success.copy(alpha = 0.15f)
                                else if (isBtActive) radioColors.alert.copy(alpha = 0.12f)
                                else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBtConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth Status",
                            tint = if (isBtConnected) radioColors.success
                            else if (isBtActive) radioColors.alert
                            else radioColors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Wi-Fi Status Item
                val isWifiActive = activeTransport == TransportType.WIFI
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onWifiClick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (isWifiActive) radioColors.success.copy(alpha = 0.15f) else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi Status",
                            tint = if (isWifiActive) radioColors.success else radioColors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 3. AI / Model Readiness Status Item
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onModelClick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (isModelReady) radioColors.success.copy(alpha = 0.15f)
                                else radioColors.warning.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "AI Model Readiness",
                            tint = if (isModelReady) radioColors.success else radioColors.warning,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 4. Offline / Battery / Field Link indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(radioColors.success.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(radioColors.success, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
