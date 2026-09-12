package org.sih.itantra.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical QR Node & Contact Pairing Screen for iTantra.
 *
 * Integrates:
 * 1. "MY QR" Tab: Offline local identity broadcast card with deterministic QR matrix.
 * 2. "SCAN PEER" Tab: Camera viewfinder reticle + manual code fallback for adding contacts.
 *
 * Provides dedicated hardware/gesture [BackHandler] and top-bar back action calling [onBack].
 */
@Composable
fun QrPairingScreen(
    localNodeId: Int,
    callsign: String = "OPERATOR",
    displayName: String = "Tactical Unit",
    supportedLanguages: List<IndicLanguage> = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH),
    contactRepository: ContactRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val radioColors = LocalRadioColors.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
    ) {
        // Tactical Navigation Tab Bar
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = radioColors.surface,
            contentColor = radioColors.textPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    height = 3.dp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, radioColors.border, RoundedCornerShape(0.dp))
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTabIndex == 0)
                                (if (radioColors.isDark) radioColors.sage else radioColors.forest)
                            else radioColors.textTertiary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MY QR",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedTabIndex == 0) radioColors.textPrimary else radioColors.textTertiary
                        )
                    }
                }
            )

            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTabIndex == 1)
                                (if (radioColors.isDark) radioColors.sage else radioColors.forest)
                            else radioColors.textTertiary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SCAN PEER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedTabIndex == 1) radioColors.textPrimary else radioColors.textTertiary
                        )
                    }
                }
            )
        }

        // Active Tab Screen
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                0 -> MyNodeQrScreen(
                    localNodeId = localNodeId,
                    callsign = callsign,
                    displayName = displayName,
                    supportedLanguages = supportedLanguages,
                    onScanPeerClicked = { selectedTabIndex = 1 },
                    onBack = onBack
                )
                1 -> ScanNodeQrScreen(
                    localNodeId = localNodeId,
                    contactRepository = contactRepository,
                    onContactAdded = { onBack() },
                    onBack = onBack
                )
            }
        }
    }
}
