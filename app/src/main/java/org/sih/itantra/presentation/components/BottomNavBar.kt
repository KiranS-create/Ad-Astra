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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.presentation.theme.LocalRadioColors

enum class RadioNavTab(val label: String, val icon: ImageVector) {
    CHATS("Chats", Icons.Default.ChatBubbleOutline),
    RADIO("Radio", Icons.Default.Radio),
    DIAGNOSTICS("Diagnostics", Icons.Default.Analytics),
    SETTINGS("Settings", Icons.Default.Settings),
    TRANSCRIPT("Transcript", Icons.Default.ChatBubbleOutline)
}

/**
 * 4-Destination Bottom Navigation Bar for iTantra Field Radio.
 * Calm, industrial, high-contrast, lightweight.
 */
@Composable
fun BottomNavBar(
    currentTab: RadioNavTab,
    onTabSelected: (RadioNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val visibleTabs = listOf(
        RadioNavTab.RADIO,
        RadioNavTab.CHATS,
        RadioNavTab.DIAGNOSTICS,
        RadioNavTab.SETTINGS
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(radioColors.surface)
            .border(
                width = 1.dp,
                color = radioColors.border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleTabs.forEach { tab ->
                val isSelected = currentTab == tab
                val itemColor = if (isSelected) {
                    if (radioColors.isDark) radioColors.sage else radioColors.forest
                } else {
                    radioColors.textTertiary
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (isSelected) itemColor.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = itemColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tab.label,
                        color = itemColor,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
