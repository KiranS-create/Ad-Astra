package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.message.TechnicalFieldStyle
import org.sih.itantra.core.message.TechnicalInspectorField
import org.sih.itantra.core.message.TechnicalInspectorSection
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Renders an individual section within the Message Technical Inspector.
 *
 * Each section displays a section title tag and a list of key-value telemetry fields.
 */
@Composable
fun MessageTechnicalSection(
    section: TechnicalInspectorSection,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Section Header Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(radioColors.capsule)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "[${section.title}]",
                    color = radioColors.sage,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(1.dp))

        // Section Fields
        section.fields.forEach { field ->
            TechnicalFieldRow(field = field)
        }
    }
}

@Composable
private fun TechnicalFieldRow(
    field: TechnicalInspectorField
) {
    val radioColors = LocalRadioColors.current
    val highlightBlue = Color(0xFF0288D1)

    val valueColor = when (field.style) {
        TechnicalFieldStyle.SUCCESS   -> radioColors.sage
        TechnicalFieldStyle.HIGHLIGHT -> highlightBlue
        TechnicalFieldStyle.WARNING   -> radioColors.warning
        TechnicalFieldStyle.ALERT     -> radioColors.alert
        TechnicalFieldStyle.MUTED     -> radioColors.textTertiary
        TechnicalFieldStyle.NORMAL    -> radioColors.textPrimary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = field.label,
            color = radioColors.textTertiary,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = field.value,
            color = valueColor,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
