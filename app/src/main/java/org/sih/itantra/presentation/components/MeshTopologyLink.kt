package org.sih.itantra.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import org.sih.itantra.core.topology.TopologyLinkType
import org.sih.itantra.presentation.theme.RadioColors

/**
 * Visual styling and path effects for mesh links.
 */
object MeshTopologyLinkStyle {

    fun linkColor(linkType: TopologyLinkType, isHighlighted: Boolean, colors: RadioColors): Color {
        return when {
            isHighlighted -> if (colors.isDark) Color(0xFF68D391) else Color(0xFF276749)
            linkType == TopologyLinkType.DIRECT -> colors.success.copy(alpha = 0.85f)
            linkType == TopologyLinkType.RELAY -> colors.warning.copy(alpha = 0.80f)
            linkType == TopologyLinkType.DTN -> Color(0xFF38B2AC).copy(alpha = 0.75f)
            else -> colors.textTertiary.copy(alpha = 0.50f)
        }
    }

    fun pathEffect(linkType: TopologyLinkType, isDashed: Boolean): PathEffect? {
        return if (isDashed || linkType == TopologyLinkType.RELAY) {
            PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
        } else if (linkType == TopologyLinkType.DTN) {
            PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        } else {
            null
        }
    }

    fun strokeWidth(isHighlighted: Boolean): Float {
        return if (isHighlighted) 6f else 3.5f
    }
}
