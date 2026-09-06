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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.language.LanguageSelectionMode
import org.sih.itantra.presentation.theme.LocalRadioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorPill(
    currentState: org.sih.itantra.core.language.LanguageSelectionState,
    onModeSelected: (org.sih.itantra.core.language.LanguageSelectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isAutoUnavailable = currentState is org.sih.itantra.core.language.LanguageSelectionState.AutoUnavailable
    val isAuto = currentState is org.sih.itantra.core.language.LanguageSelectionState.Auto

    // Main Pill Button
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(radioColors.capsule)
            .border(
                1.dp,
                if (isAutoUnavailable) radioColors.alert.copy(alpha = 0.7f) else radioColors.border.copy(alpha = 0.6f),
                RoundedCornerShape(20.dp)
            )
            .clickable { showSheet = true }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when {
                    isAutoUnavailable -> Icons.Default.Warning
                    isAuto -> Icons.Default.AutoAwesome
                    else -> Icons.Default.Translate
                },
                contentDescription = "Language",
                tint = if (isAutoUnavailable) radioColors.alert else radioColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            val label = when (currentState) {
                is org.sih.itantra.core.language.LanguageSelectionState.Manual ->
                    "${currentState.language.displayName} (${currentState.language.nativeName})"
                is org.sih.itantra.core.language.LanguageSelectionState.Auto ->
                    "AUTO"
                is org.sih.itantra.core.language.LanguageSelectionState.AutoUnavailable ->
                    "AUTO UNAVAILABLE (Manual Req.)"
                is org.sih.itantra.core.language.LanguageSelectionState.Detecting ->
                    "DETECTING..."
                is org.sih.itantra.core.language.LanguageSelectionState.Detected ->
                    "${currentState.language.displayName} (${(currentState.confidence * 100).toInt()}%)"
                is org.sih.itantra.core.language.LanguageSelectionState.Undetermined ->
                    "UNDETERMINED (Manual Req.)"
            }

            Text(
                text = label,
                color = if (isAutoUnavailable) radioColors.alert else radioColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Open Selector",
                tint = radioColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = radioColors.surface,
            contentColor = radioColors.textPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "SELECT RADIO LANGUAGE",
                    color = radioColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isAutoUnavailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(radioColors.alert.copy(alpha = 0.1f))
                            .border(1.dp, radioColors.alert.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Offline Language Identification (LID) is not installed. Manual language selection is required for transmission.",
                            color = radioColors.alert,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // AUTO Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isAuto) radioColors.surfaceHighlight else radioColors.surface
                        )
                        .clickable {
                            onModeSelected(org.sih.itantra.core.language.LanguageSelectionMode.Auto)
                            showSheet = false
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(radioColors.forest.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "AUTO (Offline Language Identification)",
                                color = radioColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Offline LID unavailable — manual selection required",
                                color = radioColors.textTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isAuto) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = radioColors.success,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = radioColors.border.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                // 10 Indic Languages
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    items(IndicLanguage.entries) { lang ->
                        val isSelected = currentState is org.sih.itantra.core.language.LanguageSelectionState.Manual && currentState.language == lang

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) radioColors.surfaceHighlight else radioColors.surface
                                )
                                .clickable {
                                    onModeSelected(org.sih.itantra.core.language.LanguageSelectionMode.Manual(lang))
                                    showSheet = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(radioColors.capsule, CircleShape)
                                        .border(1.dp, radioColors.border.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.isoCode.uppercase(),
                                        color = radioColors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = lang.displayName,
                                        color = radioColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = lang.nativeName,
                                        color = radioColors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = radioColors.success,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
