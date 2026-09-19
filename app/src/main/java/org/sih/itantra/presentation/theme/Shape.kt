package org.sih.itantra.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Standardized Tactical Shapes for iTantra, eliminating the 13 arbitrary competing radii.
val TacticalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object TacticalShapeTokens {
    val None = RoundedCornerShape(0.dp)
    val Tag = RoundedCornerShape(4.dp)
    val Chip = RoundedCornerShape(6.dp)
    val Button = RoundedCornerShape(8.dp)
    val Input = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(12.dp)
    val Bubble = RoundedCornerShape(12.dp)
    val Modal = RoundedCornerShape(16.dp)
    val Pill = CircleShape
}
