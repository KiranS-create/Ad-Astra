package org.sih.itantra.presentation.components

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.topology.MeshTopologyDisplayState
import org.sih.itantra.core.topology.TopologyDisplayNode
import org.sih.itantra.presentation.theme.LocalRadioColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tactical Radar Topology Canvas.
 *
 * Renders:
 * 1. Polar radar grid (concentric distance rings and crosshairs).
 * 2. Mesh connection links (solid for DIRECT, dashed for RELAY, glowing when highlighted).
 * 3. Interactive node badges positioned with deterministic polar coordinates.
 * 4. Truthful empty-state overlay if no peers are detected.
 */
@Composable
fun MeshTopologyCanvas(
    state: MeshTopologyDisplayState,
    onSelectNode: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val density = LocalDensity.current

    // Radar scanning animation for empty state or live pulses
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseWave by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (radioColors.isDark) Color(0xFF070F0A) else radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val centerX = widthPx / 2f
            val centerY = heightPx / 2f
            val maxRadius = min(centerX, centerY) * 0.84f

            // 1. Calculate deterministic pixel positions for all nodes
            val nodePositions = remember(state.nodes, state.localNode, widthPx, heightPx) {
                val posMap = mutableMapOf<Int, Offset>()

                state.localNode?.let { local ->
                    posMap[local.nodeId] = Offset(centerX, centerY)
                }

                for (node in state.nodes) {
                    val r = maxRadius * node.normalizedRadius
                    val x = centerX + r * cos(node.angleRad)
                    val y = centerY + r * sin(node.angleRad)
                    posMap[node.nodeId] = Offset(x, y)
                }
                posMap
            }

            // 2. Canvas Background: Radar Rings, Crosshairs, and Links
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSelectNode(null) // Tapping background deselects
                    }
            ) {
                val gridColor = if (radioColors.isDark) Color(0xFF1E3A28) else Color(0xFFC0D8C8)

                // Concentric distance rings
                val ringFractions = floatArrayOf(0.28f, 0.50f, 0.78f, 0.96f)
                ringFractions.forEach { fraction ->
                    drawCircle(
                        color = gridColor.copy(alpha = 0.45f),
                        radius = maxRadius * fraction,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.2f)
                    )
                }

                // Radar Crosshairs
                drawLine(
                    color = gridColor.copy(alpha = 0.35f),
                    start = Offset(centerX - maxRadius * 0.98f, centerY),
                    end = Offset(centerX + maxRadius * 0.98f, centerY),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridColor.copy(alpha = 0.35f),
                    start = Offset(centerX, centerY - maxRadius * 0.98f),
                    end = Offset(centerX, centerY + maxRadius * 0.98f),
                    strokeWidth = 1f
                )

                // Dynamic radar pulse if scanning or active
                if (state.isEmpty) {
                    drawCircle(
                        color = (if (radioColors.isDark) Color(0xFF68D391) else radioColors.forest)
                            .copy(alpha = (1.0f - pulseWave).coerceIn(0f, 0.35f)),
                        radius = maxRadius * pulseWave,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.8f)
                    )
                }

                // Mesh Links
                for (link in state.links) {
                    val p1 = nodePositions[link.sourceNodeId]
                    val p2 = nodePositions[link.destinationNodeId]
                    if (p1 != null && p2 != null) {
                        val color = MeshTopologyLinkStyle.linkColor(link.linkType, link.isHighlighted, radioColors)
                        val strokeW = MeshTopologyLinkStyle.strokeWidth(link.isHighlighted)
                        val effect = MeshTopologyLinkStyle.pathEffect(link.linkType, link.isDashed)

                        drawLine(
                            color = color,
                            start = p1,
                            end = p2,
                            strokeWidth = strokeW,
                            pathEffect = effect
                        )
                    }
                }
            }

            // 3. Render Nodes
            val allNodes = remember(state.localNode, state.nodes) {
                listOfNotNull(state.localNode) + state.nodes
            }

            val badgeWidthDp = 88.dp
            val badgeHeightDp = 48.dp
            val badgeWidthPx = with(density) { badgeWidthDp.toPx() }
            val badgeHeightPx = with(density) { badgeHeightDp.toPx() }

            allNodes.forEach { node ->
                val pos = nodePositions[node.nodeId] ?: Offset(centerX, centerY)
                val isSelected = state.selectedNodeId == node.nodeId

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (pos.x - badgeWidthPx / 2f).roundToInt(),
                                y = (pos.y - badgeHeightPx / 2f).roundToInt()
                            )
                        }
                        .wrapContentSize()
                ) {
                    NodeBadge(
                        node = node,
                        isSelected = isSelected,
                        onClick = { onSelectNode(node.nodeId) },
                        modifier = Modifier.width(badgeWidthDp)
                    )
                }
            }

            // 4. Truthful Empty State Overlay
            if (state.isEmpty) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (radioColors.isDark) Color(0xCC0B140E) else Color(0xCCF7FAFC))
                        .border(1.dp, radioColors.warning.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "· · ·  BEACON SEARCH (RFCOMM / UDP)  · · ·",
                        color = radioColors.warning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "NO PEERS DETECTED",
                        color = radioColors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Nodes appear automatically when in RF range",
                        color = radioColors.textSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeBadge(
    node: TopologyDisplayNode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val accentColor = MeshTopologyNodeStyle.nodeColor(node.state, node.isLocal, radioColors)
    val haloColor = MeshTopologyNodeStyle.nodeHaloColor(node.state, node.isLocal, radioColors)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) haloColor
                else if (radioColors.isDark) Color(0xEE0B140E)
                else Color(0xEEF0EFEA)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else accentColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (node.isLocal) "LOCAL" else "#${node.nodeId}",
                color = radioColors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = node.callsign ?: MeshTopologyNodeStyle.stateBadgeText(node.state, node.isLocal),
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
