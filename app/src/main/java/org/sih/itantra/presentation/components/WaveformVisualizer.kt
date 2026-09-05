package org.sih.itantra.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sih.itantra.core.session.PttState
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.RadarGreenDim
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBorder

/**
 * Animated audio spectrum / oscilloscope bar visualizer for speech activity.
 */
@Composable
fun WaveformVisualizer(
    pttState: PttState,
    barCount: Int = 24,
    modifier: Modifier = Modifier
) {
    val isActive = pttState == PttState.RECORDING || pttState == PttState.SPEECH_DETECTED || pttState == PttState.PLAYING
    val transition = rememberInfiniteTransition(label = "wave")

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val baseHeights = listOf(0.2f, 0.4f, 0.8f, 0.5f, 0.9f, 0.3f, 0.7f, 1.0f, 0.6f, 0.4f, 0.85f, 0.3f)

        for (i in 0 until barCount) {
            val animProgress by transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 350 + (i * 35) % 400,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val heightFactor = if (isActive) {
                (baseHeights[i % baseHeights.size] * animProgress).coerceIn(0.1f, 1.0f)
            } else {
                0.08f
            }

            val color = when (pttState) {
                PttState.PLAYING -> SignalBlue
                PttState.SPEECH_DETECTED -> RadarGreen
                PttState.RECORDING -> RadarGreenDim
                else -> TacticalBorder
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((48 * heightFactor).dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
