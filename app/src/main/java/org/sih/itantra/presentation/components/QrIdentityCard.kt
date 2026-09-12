package org.sih.itantra.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.pairing.QrIdentityCodec
import org.sih.itantra.core.pairing.QrIdentityPayload
import org.sih.itantra.core.pairing.QrMatrixEncoder
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical QR Identity Card for presenting the local node's public identity.
 *
 * Displays node callsign, node ID, supported languages, rendered QR code matrix,
 * and copy-payload affordance.
 */
@Composable
fun QrIdentityCard(
    payload: QrIdentityPayload,
    onCopySuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val clipboardManager = LocalClipboardManager.current

    val rawEncoded = remember(payload) {
        QrIdentityCodec.encode(payload)
    }

    // Generate QR bitmap purely offline with custom tactical coloring
    val qrBitmap: Bitmap? = remember(rawEncoded, radioColors.isDark) {
        try {
            val matrix = QrMatrixEncoder.encode(rawEncoded, QrMatrixEncoder.EccLevel.M)
            val dark = if (radioColors.isDark) radioColors.textPrimary.toArgb() else Color.Black.toArgb()
            val light = (if (radioColors.isDark) Color(0xFF141916) else Color.White).toArgb()
            matrix.toBitmap(modulePixelSize = 8, quietZone = 3, darkColor = dark, lightColor = light)
        } catch (_: Throwable) {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Node Monogram / Callsign
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = payload.callsign.uppercase(),
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = payload.displaySubtitle,
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(radioColors.capsule)
                        .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "NODE #${payload.nodeId}",
                        color = radioColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rendered QR Code Container
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (radioColors.isDark) Color(0xFF141916) else Color.White)
                    .border(
                        2.dp,
                        if (radioColors.isDark) radioColors.sage.copy(alpha = 0.4f) else radioColors.forest.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Tactical QR for Node #${payload.nodeId}",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "QR CODE ERROR",
                        color = radioColors.alert,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Instruction Banner
            Text(
                text = "SCAN TO ADD THIS NODE",
                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Scan with another iTantra handset to exchange tactical contact identity. Contains public identity information only.",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Supported Languages Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                payload.supportedLanguages.forEach { lang ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(radioColors.capsule)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = lang.displayName.uppercase(),
                            color = radioColors.textSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Copy Payload Text Button (Manual Fallback / Testing)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.capsule)
                    .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(rawEncoded))
                        onCopySuccess()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy QR payload text",
                    tint = radioColors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "COPY IDENTITY TEXT",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
