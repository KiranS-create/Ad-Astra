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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import org.sih.itantra.core.message.MessageTechnicalInspector
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Message Technical Inspector Card.
 *
 * Rendered when the operator taps an individual chat bubble to expand its technical telemetry.
 * Presents structured, operator-friendly technical metadata grouped by domain:
 * [MESSAGE], [DELIVERY], [ROUTE], [DTN], [FRAGMENTATION], [SECURITY & INTEGRITY].
 *
 * Rules:
 * - Read-only inspection; no network calls or state changes.
 * - Unavailable fields display "UNKNOWN" or are omitted.
 * - Emergency messages are clearly highlighted with alert styling.
 */
@Composable
fun MessageTechnicalInspectorCard(
    inspector: MessageTechnicalInspector,
    modifier: Modifier = Modifier,
    onOpenJourney: ((String) -> Unit)? = null
) {
    val radioColors = LocalRadioColors.current

    val borderColor = if (inspector.isEmergency) {
        radioColors.alert.copy(alpha = 0.8f)
    } else {
        radioColors.sage.copy(alpha = 0.4f)
    }

    val headerColor = if (inspector.isEmergency) {
        radioColors.alert
    } else {
        radioColors.sage
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF08120C))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Card Header: Title + Priority Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TECHNICAL PACKET INSPECTOR",
                color = headerColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            if (inspector.isEmergency) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(radioColors.alert.copy(alpha = 0.2f))
                        .border(1.dp, radioColors.alert, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = inspector.priorityContext.label,
                        color = radioColors.alert,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = borderColor.copy(alpha = 0.3f)
        )

        // Sections
        inspector.sections.forEachIndexed { index, section ->
            MessageTechnicalSection(section = section)
            if (index < inspector.sections.lastIndex) {
                Spacer(modifier = Modifier.height(2.dp))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 0.5.dp,
                    color = radioColors.border.copy(alpha = 0.2f)
                )
            }
        }

        // Feature 9 Interaction Hook: View Message Journey
        if (onOpenJourney != null) {
            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = radioColors.border.copy(alpha = 0.3f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(radioColors.capsule)
                    .border(1.dp, radioColors.sage.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { onOpenJourney(inspector.messageId) }
                    .padding(vertical = 7.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VIEW MESSAGE JOURNEY →",
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
