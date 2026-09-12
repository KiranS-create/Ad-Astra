package org.sih.itantra.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.presentation.screens.MyNodeQrScreen
import org.sih.itantra.presentation.screens.ScanNodeQrScreen
import org.sih.itantra.presentation.theme.ITantraTheme
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.service.ManetNodePreference

/**
 * Isolated Activity for Feature 10: Offline QR Node / Contact Pairing.
 *
 * Allows independent execution, manual testing, and verification of both:
 * 1. My Node QR broadcast screen
 * 2. Peer QR scanning, validation, and UNVERIFIED contact import
 *
 * Registered in debug manifest to preserve parallel branch isolation without
 * modifying shared navigation.
 */
class QrPairingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storedNodeId = ManetNodePreference.lastNodeId(this).let { stored ->
            if (stored > 0) stored
            else {
                val generated = 209070
                ManetNodePreference.setLastNodeId(this, generated)
                generated
            }
        }

        setContent {
            ITantraTheme(darkTheme = true) {
                val contactRepository = remember { ContactRepository(applicationContext) }
                var selectedTabIndex by remember { mutableIntStateOf(0) }
                val radioColors = LocalRadioColors.current

                Column(modifier = Modifier.fillMaxSize().background(radioColors.background)) {
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
                                        tint = if (selectedTabIndex == 0) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textTertiary
                                    )
                                    Text(
                                        text = " MY QR",
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
                                        tint = if (selectedTabIndex == 1) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textTertiary
                                    )
                                    Text(
                                        text = " SCAN PEER",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (selectedTabIndex == 1) radioColors.textPrimary else radioColors.textTertiary
                                    )
                                }
                            }
                        )
                    }

                    // Content View
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTabIndex) {
                            0 -> {
                                MyNodeQrScreen(
                                    localNodeId = storedNodeId,
                                    callsign = "NODE ALPHA",
                                    displayName = "Tactical Unit Alpha",
                                    supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH),
                                    onScanPeerClicked = { selectedTabIndex = 1 },
                                    onBack = { finish() }
                                )
                            }
                            1 -> {
                                ScanNodeQrScreen(
                                    localNodeId = storedNodeId,
                                    contactRepository = contactRepository,
                                    onContactAdded = { addedNodeId ->
                                        Toast.makeText(
                                            this@QrPairingActivity,
                                            "Paired Node #$addedNodeId registered in tactical contacts",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectedTabIndex = 0
                                    },
                                    onBack = { selectedTabIndex = 0 }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
