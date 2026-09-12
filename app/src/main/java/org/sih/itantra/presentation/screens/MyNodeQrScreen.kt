package org.sih.itantra.presentation.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.pairing.QrIdentityPayload
import org.sih.itantra.presentation.components.QrIdentityCard
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Screen presenting the local operator's public node identity QR code.
 *
 * Allows peers to visually scan or copy the operator's public identity
 * without needing any radio signal or network connectivity.
 */
@Composable
fun MyNodeQrScreen(
    localNodeId: Int,
    callsign: String = "OPERATOR",
    displayName: String = "Tactical Unit",
    supportedLanguages: List<IndicLanguage> = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH),
    onScanPeerClicked: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val radioColors = LocalRadioColors.current
    val scrollState = rememberScrollState()

    val payload = remember(localNodeId, callsign, displayName, supportedLanguages) {
        QrIdentityPayload(
            nodeId = localNodeId,
            callsign = callsign,
            displayName = displayName,
            supportedLanguages = supportedLanguages
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(radioColors.surface)
                    .border(
                        width = 1.dp,
                        color = radioColors.border,
                        shape = RoundedCornerShape(0.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = radioColors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "MY NODE QR CODE",
                            color = radioColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "OFFLINE IDENTITY BROADCAST",
                            color = radioColors.sage,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        containerColor = radioColors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // QR Identity Card
            QrIdentityCard(
                payload = payload,
                onCopySuccess = {
                    Toast.makeText(context, "Tactical QR Payload copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Security & Protocol Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = radioColors.sage,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ZERO-SECRET GUARANTEE",
                            color = radioColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Contains ONLY public identity metadata: Node ID, Callsign, Name, and Languages.\n" +
                               "• ZERO HMAC keys, tokens, credentials, or private keys are ever encoded.\n" +
                               "• Scanned peers are saved as UNVERIFIED until authenticated via radio mesh.\n" +
                               "• 100% offline — works in high-EMCON, jammed, or underground environments.",
                        color = radioColors.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }

            if (onScanPeerClicked != null) {
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onScanPeerClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SWITCH TO SCANNER",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
