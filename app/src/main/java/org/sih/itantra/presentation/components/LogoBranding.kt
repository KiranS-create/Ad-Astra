package org.sih.itantra.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Renders the iTantra field radio identity:
 * Stylized geometric triple-mountain peaks logo + "iTantra" branding.
 * Drawn purely with vector canvas paths (0 raster asset overhead).
 */
@Composable
fun LogoBranding(
    modifier: Modifier = Modifier,
    textColor: Color? = null
) {
    val radioColors = LocalRadioColors.current
    val effectiveTextColor = textColor ?: radioColors.textPrimary
    val mountainColor = if (radioColors.isDark) radioColors.sage else radioColors.forest

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.size(width = 30.dp, height = 22.dp)) {
            val w = size.width
            val h = size.height

            // Left peak
            val pathLeft = Path().apply {
                moveTo(w * 0.05f, h * 0.90f)
                lineTo(w * 0.28f, h * 0.28f)
                lineTo(w * 0.50f, h * 0.90f)
                close()
            }
            drawPath(pathLeft, color = mountainColor.copy(alpha = 0.85f))

            // Main center/right primary peak
            val pathCenter = Path().apply {
                moveTo(w * 0.35f, h * 0.90f)
                lineTo(w * 0.65f, h * 0.10f)
                lineTo(w * 0.95f, h * 0.90f)
                close()
            }
            drawPath(pathCenter, color = mountainColor)

            // Small foreground right peak
            val pathRight = Path().apply {
                moveTo(w * 0.70f, h * 0.90f)
                lineTo(w * 0.85f, h * 0.45f)
                lineTo(w * 1.00f, h * 0.90f)
                close()
            }
            drawPath(pathRight, color = mountainColor.copy(alpha = 0.70f))
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "iTantra",
            color = effectiveTextColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )
    }
}
