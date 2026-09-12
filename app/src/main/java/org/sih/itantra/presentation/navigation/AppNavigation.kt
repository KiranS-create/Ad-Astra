package org.sih.itantra.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.sih.itantra.presentation.components.RadioNavTab

/**
 * Type-safe navigation destinations for full-screen feature overlays.
 */
sealed interface ScreenDestination {
    /** Feature 2: Tactical Individual Chat screen with canonical peer ID. */
    data class Chat(val peerId: String) : ScreenDestination {
        var expandedMessageId: String? = null
    }

    /** Feature 3: Tactical Contacts Directory & Mesh Node list. */
    data object Contacts : ScreenDestination

    /** Feature 4: Nearby iTantra Devices live discovery screen. */
    data object NearbyDevices : ScreenDestination

    /** Feature 5: Offline Global Search screen. */
    data object GlobalSearch : ScreenDestination

    /** Feature 9: Message Journey network path visualization. */
    data class MessageJourney(val messageId: String) : ScreenDestination

    /** Feature 10: Offline QR Node / Contact Pairing. */
    data object QrPairing : ScreenDestination

    /** Neural Model Status & Offline Audit screen. */
    data object ModelAudit : ScreenDestination

    /** MANET Topology Live & Simulation screen. */
    data object ManetDemo : ScreenDestination

    /** SIH Mission Demo Dashboard. */
    data object SihDemo : ScreenDestination
}

/**
 * Deterministic, type-safe navigation state manager that maintains:
 * 1. Root bottom navigation tab (Radio -> Chats -> Diagnostics -> Settings).
 * 2. Explicit, LIFO back-stack of full-screen destinations.
 *
 * This guarantees that navigating between sub-screens (e.g., Contacts -> Nearby -> Chat)
 * preserves the originating parent screen and Back pops correctly to that parent.
 */
class NavigationStateManager(
    initialTab: RadioNavTab = RadioNavTab.RADIO
) {
    /**
     * Active root bottom-navigation tab.
     */
    var currentTab: RadioNavTab by mutableStateOf(initialTab)
        private set

    /**
     * Explicit navigation back-stack. The topmost element is the currently visible screen overlay.
     */
    val backStack: SnapshotStateList<ScreenDestination> = mutableStateListOf()

    /**
     * Returns the currently visible overlay destination, or null if showing the root tab.
     */
    val currentDestination: ScreenDestination?
        get() = backStack.lastOrNull()

    /**
     * True if there is at least one overlay on the back-stack to pop.
     */
    val canNavigateBack: Boolean
        get() = backStack.isNotEmpty()

    /**
     * Push a new destination onto the back-stack.
     */
    fun navigateTo(destination: ScreenDestination) {
        backStack.add(destination)
    }

    /**
     * Pop the topmost destination from the back-stack.
     * Returns true if a destination was popped, false if back-stack was already empty.
     */
    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
            return true
        }
        return false
    }

    /**
     * Switch the root bottom navigation tab and clear any overlay destinations,
     * returning the user to the root of the chosen tab.
     */
    fun selectTab(tab: RadioNavTab) {
        currentTab = tab
        backStack.clear()
    }
}
