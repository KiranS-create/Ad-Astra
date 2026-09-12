package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.emergency.EmergencyAction
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Grid selector of predefined tactical emergency distress categories.
 */
@Composable
fun EmergencyQuickActionRow(
    selectedAction: EmergencyAction,
    onActionSelected: (EmergencyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = EmergencyAction.entries
    val rows = actions.chunked(2)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowActions.forEach { action ->
                    val isSelected = action == selectedAction
                    EmergencyActionChip(
                        action = action,
                        isSelected = isSelected,
                        onClick = { onActionSelected(action) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyActionChip(
    action: EmergencyAction,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val bg = if (isSelected) {
        radioColors.alert.copy(alpha = 0.22f)
    } else {
        radioColors.surface
    }

    val border = if (isSelected) {
        radioColors.alert
    } else {
        radioColors.border.copy(alpha = 0.7f)
    }

    val textColor = if (isSelected) {
        radioColors.alert
    } else {
        radioColors.textSecondary
    }

    val actionSymbol = when (action) {
        EmergencyAction.MEDICAL -> "🚑"
        EmergencyAction.INJURED -> "🩹"
        EmergencyAction.TRAPPED -> "🏚"
        EmergencyAction.ATTACK -> "⚔"
        EmergencyAction.FIRE -> "🔥"
        EmergencyAction.EVACUATION -> "🏃"
        EmergencyAction.NEED_EXTRACTION -> "🚁"
        EmergencyAction.LOCATION -> "📍"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(if (isSelected) 1.5.dp else 1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = actionSymbol,
                fontSize = 12.sp
            )
            Text(
                text = " ${action.label}",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}
