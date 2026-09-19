package org.sih.itantra.presentation.components.tactical

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sih.itantra.presentation.theme.ColorSage
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.theme.TacticalShapeTokens
import org.sih.itantra.presentation.theme.TacticalType

/**
 * Standard tactical card with uniform 12.dp radius, subtle 1.dp border, and consistent background.
 */
@Composable
fun TacticalCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = TacticalShapeTokens.Card,
    contentPadding: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val radioColors = LocalRadioColors.current
    val bg = backgroundColor ?: radioColors.surface
    val borderCol = borderColor ?: radioColors.border

    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderCol, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = bg,
        shape = shape
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/**
 * Tactical section header featuring an accent pip, uppercase label, and optional trailing badge/action.
 */
@Composable
fun TacticalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    pipColor: Color = ColorSage,
    trailing: @Composable (() -> Unit)? = null
) {
    val radioColors = LocalRadioColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 13.dp)
                    .clip(TacticalShapeTokens.Tag)
                    .background(pipColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = radioColors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * Tactical status chip with a colored indicator dot, subtle capsule background, and monospace label.
 */
@Composable
fun TacticalStatusChip(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    borderColor: Color? = null
) {
    val radioColors = LocalRadioColors.current
    val bg = backgroundColor ?: radioColors.capsule
    val borderCol = borderColor ?: radioColors.border
    val textCol = textColor ?: radioColors.textPrimary

    Row(
        modifier = modifier
            .clip(TacticalShapeTokens.Pill)
            .background(bg)
            .border(1.dp, borderCol, TacticalShapeTokens.Pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label.uppercase(),
            style = TacticalType.badgeLabel,
            color = textCol,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Tactical badge for representation tags (e.g. [DELTA], [SEMANTIC]), hops, QoS.
 */
@Composable
fun TacticalBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ColorSage,
    backgroundColor: Color? = null
) {
    val radioColors = LocalRadioColors.current
    val bg = backgroundColor ?: color.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(TacticalShapeTokens.Tag)
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.6f), TacticalShapeTokens.Tag)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = TacticalType.badgeLabel,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Tactical metric tile displaying numerical value and an uppercase description descriptor.
 */
@Composable
fun TacticalMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    unit: String? = null
) {
    val radioColors = LocalRadioColors.current
    val valCol = valueColor ?: radioColors.textPrimary

    TacticalCard(
        modifier = modifier,
        contentPadding = 8.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = valCol,
                    fontWeight = FontWeight.Bold
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = TacticalType.telemetryCode,
                        color = radioColors.textSecondary
                    )
                }
            }
            Text(
                text = label.uppercase(),
                style = TacticalType.badgeLabel,
                color = radioColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
