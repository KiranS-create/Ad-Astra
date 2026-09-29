package org.sih.itantra.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.discovery.LocateModeEngine
import org.sih.itantra.core.discovery.LocateProximityState
import org.sih.itantra.core.discovery.LocateRssiTrend
import org.sih.itantra.core.discovery.NearbyDeviceRepository
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.theme.TacticalShapeTokens
import java.util.Locale

/**
 * Tactical BLE RSSI Locate Mode Screen.
 *
 * Implements:
 * - Relative qualitative proximity bands (UNKNOWN, FAR, CLOSER, NEAR, VERY_NEAR).
 * - Real-time Exponential Moving Average (EMA) smoothed signal gauge.
 * - Dynamic trend detection (CLOSER, FARTHER, STABLE).
 * - Target selection dropdown from discovered peers.
 * - Battery-safe lifecycle: active scanning only while screen is open.
 * - Truthful representation: strictly disclaims exact distance in meters.
 */
@Composable
fun LocateModeScreen(
    locateEngine: LocateModeEngine,
    discoveryRepository: NearbyDeviceRepository,
    initialTargetNodeId: Int? = null,
    initialTargetCallsign: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val scrollState = rememberScrollState()

    val sessionState by locateEngine.sessionState.collectAsState()
    val discoveredDevices by discoveryRepository.discoveredDevices.collectAsState()

    var showTargetMenu by remember { mutableStateOf(false) }

    // Battery-Safe Lifecycle: Start scan when entering, stop when leaving
    DisposableEffect(initialTargetNodeId) {
        locateEngine.startLocating(
            targetNodeId = initialTargetNodeId,
            targetCallsign = initialTargetCallsign
        )
        onDispose {
            locateEngine.stopLocating()
        }
    }

    // Pulse animation for radar rings
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, radioColors.border, TacticalShapeTokens.Button)
                    .background(radioColors.surface, TacticalShapeTokens.Button)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = radioColors.textPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "BLE LOCATE MODE",
                            color = radioColors.sage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "RELATIVE PROXIMITY SEARCH",
                            color = radioColors.textTertiary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Battery-safe status tag
                Box(
                    modifier = Modifier
                        .clip(TacticalShapeTokens.Tag)
                        .background(radioColors.sage.copy(alpha = 0.15f))
                        .border(1.dp, radioColors.sage, TacticalShapeTokens.Tag)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (sessionState.isScanning) "ACTIVE SCAN" else "STANDBY",
                        color = radioColors.sage,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 2. Target Selector Dropdown Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TacticalShapeTokens.Card)
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border, TacticalShapeTokens.Card)
                    .clickable { showTargetMenu = true }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = "Target Node",
                            tint = radioColors.sage,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TRACKING TARGET:",
                                color = radioColors.textTertiary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = sessionState.targetCallsign ?: "AUTO-SELECT (STRONGEST PEER)",
                                color = radioColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(TacticalShapeTokens.Tag)
                            .background(radioColors.capsule)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "CHANGE ▾",
                            color = radioColors.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                DropdownMenu(
                    expanded = showTargetMenu,
                    onDismissRequest = { showTargetMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Auto-Select Nearest / Strongest",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        onClick = {
                            locateEngine.startLocating(targetNodeId = null, targetCallsign = "AUTO-SELECT")
                            showTargetMenu = false
                        }
                    )
                    discoveredDevices.filter { !it.isLocalDevice }.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${device.callsign} (${device.formattedNodeId})",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            },
                            onClick = {
                                locateEngine.startLocating(
                                    targetNodeId = device.nodeId,
                                    targetCallsign = device.callsign
                                )
                                showTargetMenu = false
                            }
                        )
                    }
                }
            }

            // 3. Concentric Proximity Radar Visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(TacticalShapeTokens.Card)
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border, TacticalShapeTokens.Card),
                contentAlignment = Alignment.Center
            ) {
                val activeTier = sessionState.proximityState.tier
                val sageColor = radioColors.sage
                val warningColor = radioColors.warning
                val alertColor = radioColors.alert

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = minOf(size.width, size.height) * 0.44f

                    // Ring 1 (FAR - Outer)
                    val r1 = maxRadius
                    val active1 = activeTier >= 1
                    drawCircle(
                        color = if (active1) warningColor.copy(alpha = 0.25f) else Color.DarkGray.copy(alpha = 0.15f),
                        radius = r1,
                        center = center,
                        style = Stroke(width = if (activeTier == 1) 3f else 1.5f)
                    )

                    // Ring 2 (CLOSER - Mid-outer)
                    val r2 = maxRadius * 0.75f
                    val active2 = activeTier >= 2
                    drawCircle(
                        color = if (active2) sageColor.copy(alpha = 0.35f) else Color.DarkGray.copy(alpha = 0.15f),
                        radius = r2,
                        center = center,
                        style = Stroke(width = if (activeTier == 2) 3f else 1.5f)
                    )

                    // Ring 3 (NEAR - Mid-inner)
                    val r3 = maxRadius * 0.50f
                    val active3 = activeTier >= 3
                    drawCircle(
                        color = if (active3) sageColor.copy(alpha = 0.55f) else Color.DarkGray.copy(alpha = 0.15f),
                        radius = r3,
                        center = center,
                        style = Stroke(width = if (activeTier == 3) 3.5f else 1.5f)
                    )

                    // Ring 4 (VERY_NEAR - Center core)
                    val r4 = maxRadius * 0.25f
                    val active4 = activeTier >= 4
                    drawCircle(
                        color = if (active4) sageColor.copy(alpha = pulseAlpha) else Color.DarkGray.copy(alpha = 0.15f),
                        radius = r4,
                        center = center,
                        style = Stroke(width = if (activeTier == 4) 4f else 1.5f)
                    )

                    // Center Reticle
                    drawCircle(
                        color = if (active4) sageColor else Color.Gray,
                        radius = 6f,
                        center = center
                    )
                }

                // Center readout overlay
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = sessionState.proximityState.label,
                        color = when (sessionState.proximityState) {
                            LocateProximityState.VERY_NEAR -> radioColors.sage
                            LocateProximityState.NEAR      -> radioColors.sage
                            LocateProximityState.CLOSER    -> radioColors.sage
                            LocateProximityState.FAR       -> radioColors.warning
                            LocateProximityState.UNKNOWN   -> radioColors.textTertiary
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val rssiText = sessionState.smoothedRssi?.let {
                        String.format(Locale.US, "%.1f dBm", it)
                    } ?: "NO RF SIGNAL"

                    Text(
                        text = rssiText,
                        color = radioColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Trend direction badge
                    val trend = sessionState.trend
                    val trendColor = when (trend) {
                        LocateRssiTrend.CLOSER  -> radioColors.sage
                        LocateRssiTrend.FARTHER -> radioColors.warning
                        LocateRssiTrend.STABLE  -> radioColors.textSecondary
                        LocateRssiTrend.UNKNOWN -> radioColors.textTertiary
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(trendColor.copy(alpha = 0.15f))
                            .border(1.dp, trendColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${trend.symbol} ${trend.label}",
                            color = trendColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 4. Telemetry Breakdown Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TacticalShapeTokens.Card)
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border, TacticalShapeTokens.Card)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SIGNAL TELEMETRY (EMA FILTERED)",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("RAW LAST RSSI", color = radioColors.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = sessionState.rawRssi?.let { "$it dBm" } ?: "UNKNOWN",
                            color = radioColors.textPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SMOOTHED (α=0.35)", color = radioColors.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = sessionState.smoothedRssi?.let { String.format(Locale.US, "%.1f dBm", it) } ?: "UNKNOWN",
                            color = radioColors.sage,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SAMPLES INGESTED", color = radioColors.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "${sessionState.sampleCount}",
                            color = radioColors.textPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SIGNAL AGE", color = radioColors.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        val ageSec = if (sessionState.lastReadingTimestampMs > 0) {
                            ((System.currentTimeMillis() - sessionState.lastReadingTimestampMs) / 1000).coerceAtLeast(0)
                        } else null
                        val ageText = when {
                            ageSec == null -> "AWAITING BEACON"
                            sessionState.isStale -> "STALE (${ageSec}s ago)"
                            else -> "${ageSec}s ago"
                        }
                        Text(
                            text = ageText,
                            color = if (sessionState.isStale) radioColors.warning else radioColors.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 5. Scientific Truthfulness & Safety Disclaimer Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TacticalShapeTokens.Card)
                    .background(radioColors.warning.copy(alpha = 0.08f))
                    .border(1.dp, radioColors.warning.copy(alpha = 0.4f), TacticalShapeTokens.Card)
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Proximity Disclaimer",
                        tint = radioColors.warning,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RELATIVE PROXIMITY ONLY: BLE RSSI represents approximate radio signal intensity, NOT exact metric distance. Signal is subject to body shadowing, physical obstacles, and antenna orientation.",
                        color = radioColors.warning,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // 6. Bottom Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(TacticalShapeTokens.Button)
                        .background(radioColors.surface)
                        .border(1.dp, radioColors.border, TacticalShapeTokens.Button)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EXIT LOCATE",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(TacticalShapeTokens.Button)
                        .background(radioColors.sage.copy(alpha = 0.2f))
                        .border(1.dp, radioColors.sage, TacticalShapeTokens.Button)
                        .clickable {
                            locateEngine.startLocating(
                                targetNodeId = sessionState.targetNodeId,
                                targetCallsign = sessionState.targetCallsign
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RE-CALIBRATE ↻",
                        color = radioColors.sage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
