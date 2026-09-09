package org.sih.itantra.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.presentation.components.RadioNavTab

/**
 * Rigorous unit test suite verifying that:
 * 1. Sub-screen navigation pushes cleanly onto the back-stack.
 * 2. Back navigation strictly returns to the originating screen (never jumping unexpectedly to home).
 * 3. Bottom navigation tab switching resets overlay stacks to root.
 * 4. Multi-level deep back-stacks pop in strict LIFO order.
 */
class NavigationBackStackTest {

    private lateinit var navManager: NavigationStateManager

    @Before
    fun setUp() {
        navManager = NavigationStateManager(RadioNavTab.RADIO)
    }

    @Test
    fun testInitialState() {
        assertEquals(RadioNavTab.RADIO, navManager.currentTab)
        assertTrue(navManager.backStack.isEmpty())
        assertNull(navManager.currentDestination)
        assertFalse(navManager.canNavigateBack)
        assertFalse(navManager.navigateBack())
    }

    // 1. Chats Home -> Contacts -> Back returns to Chats Home
    @Test
    fun testChatsHome_to_Contacts_andBack() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.Contacts)

        assertEquals(ScreenDestination.Contacts, navManager.currentDestination)
        assertTrue(navManager.canNavigateBack)

        val popped = navManager.navigateBack()
        assertTrue(popped)
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)
        assertFalse(navManager.canNavigateBack)
    }

    // 2. Contacts -> Nearby Devices -> Back returns to Contacts
    @Test
    fun testContacts_to_NearbyDevices_andBack() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.Contacts)
        navManager.navigateTo(ScreenDestination.NearbyDevices)

        assertEquals(ScreenDestination.NearbyDevices, navManager.currentDestination)

        navManager.navigateBack()
        assertEquals(ScreenDestination.Contacts, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)
    }

    // 3. Contacts -> Individual Chat -> Back returns to Contacts
    @Test
    fun testContacts_to_IndividualChat_andBack() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.Contacts)
        navManager.navigateTo(ScreenDestination.Chat("Node #209070"))

        assertEquals(ScreenDestination.Chat("Node #209070"), navManager.currentDestination)

        navManager.navigateBack()
        assertEquals(ScreenDestination.Contacts, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)
    }

    // 4. Global Search -> Individual Chat -> Back returns to Global Search
    @Test
    fun testGlobalSearch_to_IndividualChat_andBack() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.GlobalSearch)
        navManager.navigateTo(ScreenDestination.Chat("Broadcast"))

        assertEquals(ScreenDestination.Chat("Broadcast"), navManager.currentDestination)

        navManager.navigateBack()
        assertEquals(ScreenDestination.GlobalSearch, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)
    }

    // 5. Global Search -> Contacts -> Back returns to Global Search
    @Test
    fun testGlobalSearch_to_Contacts_andBack() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.GlobalSearch)
        navManager.navigateTo(ScreenDestination.Contacts)

        assertEquals(ScreenDestination.Contacts, navManager.currentDestination)

        navManager.navigateBack()
        assertEquals(ScreenDestination.GlobalSearch, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)
    }

    // 6. Global Search -> Contacts -> Individual Chat -> Back -> Back returns to Global Search
    @Test
    fun testGlobalSearch_to_Contacts_to_Chat_andBack() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.GlobalSearch)
        navManager.navigateTo(ScreenDestination.Contacts)
        navManager.navigateTo(ScreenDestination.Chat("Node #1002"))

        assertEquals(ScreenDestination.Chat("Node #1002"), navManager.currentDestination)

        navManager.navigateBack()
        assertEquals(ScreenDestination.Contacts, navManager.currentDestination)

        navManager.navigateBack()
        assertEquals(ScreenDestination.GlobalSearch, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)
    }

    // 7. Global Search -> Back returns to previous screen (Chats or Settings)
    @Test
    fun testGlobalSearch_backReturnsToPreviousScreen_ChatsOrSettings() {
        // From Chats
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.GlobalSearch)
        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)

        // From Settings
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.GlobalSearch)
        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.SETTINGS, navManager.currentTab)
    }

    // 8. Settings -> Contacts -> Back returns to Settings
    @Test
    fun testSettings_to_Contacts_andBack() {
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.Contacts)
        assertEquals(ScreenDestination.Contacts, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.SETTINGS, navManager.currentTab)
    }

    // 9. Settings -> Nearby Devices -> Back returns to Settings
    @Test
    fun testSettings_to_NearbyDevices_andBack() {
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.NearbyDevices)
        assertEquals(ScreenDestination.NearbyDevices, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.SETTINGS, navManager.currentTab)
    }

    // 10. Settings -> Global Search -> Back returns to Settings
    @Test
    fun testSettings_to_GlobalSearch_andBack() {
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.GlobalSearch)
        assertEquals(ScreenDestination.GlobalSearch, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.SETTINGS, navManager.currentTab)
    }

    // 11. Settings -> Model Audit -> Back returns to Settings
    @Test
    fun testSettings_to_ModelAudit_andBack() {
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.ModelAudit)
        assertEquals(ScreenDestination.ModelAudit, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.SETTINGS, navManager.currentTab)
    }

    // 12. Settings -> MANET Demo -> Back returns to Settings
    @Test
    fun testSettings_to_ManetDemo_andBack() {
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.ManetDemo)
        assertEquals(ScreenDestination.ManetDemo, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.SETTINGS, navManager.currentTab)
    }

    // 13. Settings -> SIH Demo -> Back returns to Settings
    @Test
    fun testSettings_to_SihDemo_andBack() {
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.SihDemo)
        assertEquals(ScreenDestination.SihDemo, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.SETTINGS, navManager.currentTab)
    }

    // 14. Diagnostics -> Model Audit -> Back returns to Diagnostics
    @Test
    fun testDiagnostics_to_ModelAudit_andBack() {
        navManager.selectTab(RadioNavTab.DIAGNOSTICS)
        navManager.navigateTo(ScreenDestination.ModelAudit)
        assertEquals(ScreenDestination.ModelAudit, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.DIAGNOSTICS, navManager.currentTab)
    }

    // 15. Diagnostics -> MANET Demo -> Back returns to Diagnostics
    @Test
    fun testDiagnostics_to_ManetDemo_andBack() {
        navManager.selectTab(RadioNavTab.DIAGNOSTICS)
        navManager.navigateTo(ScreenDestination.ManetDemo)
        assertEquals(ScreenDestination.ManetDemo, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.DIAGNOSTICS, navManager.currentTab)
    }

    // 16. Diagnostics -> SIH Demo -> Back returns to Diagnostics
    @Test
    fun testDiagnostics_to_SihDemo_andBack() {
        navManager.selectTab(RadioNavTab.DIAGNOSTICS)
        navManager.navigateTo(ScreenDestination.SihDemo)
        assertEquals(ScreenDestination.SihDemo, navManager.currentDestination)

        navManager.navigateBack()
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.DIAGNOSTICS, navManager.currentTab)
    }

    // 17. SIH Demo -> onOpenDiagnostics switches to Diagnostics tab and clears stack
    @Test
    fun testSihDemo_openDiagnostics_switchesTabAndClearsStack() {
        navManager.selectTab(RadioNavTab.SETTINGS)
        navManager.navigateTo(ScreenDestination.SihDemo)
        assertEquals(ScreenDestination.SihDemo, navManager.currentDestination)

        navManager.selectTab(RadioNavTab.DIAGNOSTICS)
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.DIAGNOSTICS, navManager.currentTab)
        assertTrue(navManager.backStack.isEmpty())
    }

    // 18. Bottom navigation tab selection clears overlay back-stack
    @Test
    fun testBottomNavTabSelection_clearsOverlayBackStack() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.Contacts)
        navManager.navigateTo(ScreenDestination.NearbyDevices)
        navManager.navigateTo(ScreenDestination.Chat("Node #99"))

        assertEquals(3, navManager.backStack.size)

        navManager.selectTab(RadioNavTab.RADIO)
        assertTrue(navManager.backStack.isEmpty())
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.RADIO, navManager.currentTab)
    }

    // 19. Multi-level deep back-stack traversal (Chats -> Contacts -> Nearby -> Chat -> Back -> Back -> Back -> Chats)
    @Test
    fun testDeepBackStackTraversal_LIFO() {
        navManager.selectTab(RadioNavTab.CHATS)
        navManager.navigateTo(ScreenDestination.Contacts)
        navManager.navigateTo(ScreenDestination.NearbyDevices)
        navManager.navigateTo(ScreenDestination.Chat("Node #555"))

        assertEquals(ScreenDestination.Chat("Node #555"), navManager.currentDestination)
        assertEquals(3, navManager.backStack.size)

        // Pop Chat -> Nearby
        assertTrue(navManager.navigateBack())
        assertEquals(ScreenDestination.NearbyDevices, navManager.currentDestination)
        assertEquals(2, navManager.backStack.size)

        // Pop Nearby -> Contacts
        assertTrue(navManager.navigateBack())
        assertEquals(ScreenDestination.Contacts, navManager.currentDestination)
        assertEquals(1, navManager.backStack.size)

        // Pop Contacts -> Chats Home
        assertTrue(navManager.navigateBack())
        assertNull(navManager.currentDestination)
        assertEquals(RadioNavTab.CHATS, navManager.currentTab)
        assertFalse(navManager.canNavigateBack)

        // Cannot pop further
        assertFalse(navManager.navigateBack())
    }
}
