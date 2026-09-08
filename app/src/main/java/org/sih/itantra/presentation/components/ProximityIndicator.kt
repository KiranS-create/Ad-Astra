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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.discovery.ProximityState
import org.sih.itantra.presentation.theme.ColorSignalBlue
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Compact tactical proximity badge for device cards.
 * Combines high-contrast visual dots and clear textual status for accessibility.
 */
@Composable
fun ProximityBadge(
    proximityState: ProximityState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val (badgeColor, dotCount) = when (proximityState) {
        ProximityState.VERY_CLOSE -> Pair(radioColors.sage, 4)
        ProximityState.NEARBY -> Pair(ColorSignalBlue, 3)
        ProximityState.FAR -> Pair(radioColors.warning, 2)
        ProximityState.APPROXIMATE -> Pair(radioColors.textSecondary, 1)
        ProximityState.UNKNOWN -> Pair(radioColors.border, 0)
    }

    val description = "Proximity signal: ${proximityState.label}"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = description }
    ) {
        // Signal strength dots
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 1..4) {
                val isFilled = i <= dotCount
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) badgeColor else radioColors.border.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = proximityState.label,
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Animated tactical radar pulse visualization for scanning state.
 * Emits expanding concentric rings with calm, industrial styling.
 */
@Composable
fun TacticalRadarScanAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    isActive: Boolean = true
) {
    val radioColors = LocalRadioColors.current
    val radarColor = if (radioColors.isDark) radioColors.sage else radioColors.forest

    val transition = rememberInfiniteTransition(label = "RadarSweep")
    val pulse1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )

    val pulse2 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )

    val pulse3 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse3"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = if (isActive) "Tactical radar scanning for nearby iTantra nodes" else "Radar idle" }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = this.center
            val maxRadius = size.toPx() / 2f

            // Static background rings
            drawCircle(
                color = radarColor.copy(alpha = 0.10f),
                radius = maxRadius * 0.4f,
                center = center,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = radarColor.copy(alpha = 0.10f),
                radius = maxRadius * 0.7f,
                center = center,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = radarColor.copy(alpha = 0.15f),
                radius = maxRadius * 0.98f,
                center = center,
                style = Stroke(width = 2f)
            )

            // Dynamic expanding pulse waves
            if (isActive) {
                drawCircle(
                    color = radarColor.copy(alpha = (1.0f - pulse1).coerceIn(0f, 0.6f)),
                    radius = maxRadius * pulse1,
                    center = center,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = radarColor.copy(alpha = (1.0f - pulse2).coerceIn(0f, 0.6f)),
                    radius = maxRadius * pulse2,
                    center = center,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = radarColor.copy(alpha = (1.0f - pulse3).coerceIn(0f, 0.6f)),
                    radius = maxRadius * pulse3,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            // Center tactical node dot
            drawCircle(
                color = radarColor,
                radius = 6.dp.toPx(),
                center = center
            )
        }
    }
}
