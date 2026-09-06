package org.sih.itantra.presentation

import org.junit.Assert.*
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.demo.DemoSafetyBoundary
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.protocol.GeoLocation
import java.io.File

/**
 * Targeted UI & Navigation Refinement Tests.
 *
 * Verifies:
 * - SIH Demo Mode removed from Radio screen and placed in Settings.
 * - Radio messages given primary visual prominence (multilingual, directional, prioritized).
 * - 3-Action control row ordered: WALKIE PTT -> SEND DISTRESS -> TEST PACKET.
 * - Emergency distress confirmation guard retained.
 * - Full telemetry, security, and delivery receipt integrity.
 */
class SihUiRefinementTest {

    private fun createRecord(
        id: String = "msg-1",
        timestamp: Long = System.currentTimeMillis(),
        direction: MessageDirection = MessageDirection.SENT,
        language: IndicLanguage = IndicLanguage.HINDI,
        priority: MessagePriority = MessagePriority.NORMAL,
        text: String = "Test transmission",
        peer: String = "Broadcast",
        packetSizeBytes: Int = 42,
        rawAudioEquivalentBytes: Long = 96000L,
        measuredLatencyMs: Double = 12.0,
        isSemantic: Boolean = false,
        semanticSummary: String? = null,
        semanticSavingsBytes: Int? = null,
        location: GeoLocation? = null,
        isSecure: Boolean = false,
        authStatus: String? = null,
        deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
        deliveryLatencyMs: Long? = null,
        fragmentCount: Int? = null,
        qosStatus: String? = null
    ) = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = direction,
        language = language,
        priority = priority,
        text = text,
        peer = peer,
        packetSizeBytes = packetSizeBytes,
        rawAudioEquivalentBytes = rawAudioEquivalentBytes,
        measuredLatencyMs = measuredLatencyMs,
        isSemantic = isSemantic,
        semanticSummary = semanticSummary,
        semanticSavingsBytes = semanticSavingsBytes,
        location = location,
        isSecure = isSecure,
        authStatus = authStatus,
        deliveryStatus = deliveryStatus,
        deliveryLatencyMs = deliveryLatencyMs,
        fragmentCount = fragmentCount,
        qosStatus = qosStatus
    )

    // =========================================================================
    // A. Navigation & Launch Points
    // =========================================================================

    @Test
    fun testRadioHasNoSihDemoLauncher() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        assertTrue("MainTransceiverScreen.kt must exist", radioScreenFile.exists())
        val content = radioScreenFile.readText()

        assertFalse("Radio screen must NOT contain onOpenSihDemo parameter", content.contains("onOpenSihDemo"))
        assertFalse("Radio screen must NOT contain SIH DEMO MODE banner", content.contains("⚡ SIH DEMO MODE"))
        assertFalse("Radio screen must NOT contain LAUNCH ➔ button for demo", content.contains("LAUNCH ➔"))
    }

    @Test
    fun testSettingsContainsSihDemoLauncher() {
        val settingsFile = File("src/main/java/org/sih/itantra/presentation/screens/SettingsScreen.kt")
        assertTrue("SettingsScreen.kt must exist", settingsFile.exists())
        val content = settingsFile.readText()

        assertTrue("SettingsScreen must accept onOpenSihDemo callback", content.contains("onOpenSihDemo: () -> Unit"))
        assertTrue("SettingsScreen must contain DEMO & EVALUATION section", content.contains("DEMO & EVALUATION"))
        assertTrue("SettingsScreen must contain ⚡ SIH TACTICAL DEMO", content.contains("⚡ SIH TACTICAL DEMO"))
        assertTrue("SettingsScreen must contain SIMULATION ONLY badge", content.contains("SIMULATION ONLY"))
        assertTrue("SettingsScreen must contain OPEN SIH DEMO action button", content.contains("OPEN SIH DEMO"))
    }

    @Test
    fun testMainActivityNavigationRoutesSihDemoFromSettings() {
        val mainActivityFile = File("src/main/java/org/sih/itantra/presentation/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", mainActivityFile.exists())
        val content = mainActivityFile.readText()

        // Settings tab must pass onOpenSihDemo
        assertTrue(
            "Settings tab in MainActivity must pass onOpenSihDemo",
            content.contains("RadioNavTab.SETTINGS -> SettingsScreen") &&
            content.contains("onOpenSihDemo = { showSihDemo = true }")
        )

        // Radio tab must NOT pass onOpenSihDemo
        val radioBlock = content.substringAfter("RadioNavTab.RADIO -> MainTransceiverScreen(").substringBefore(")")
        assertFalse(
            "Radio tab in MainActivity must NOT pass onOpenSihDemo",
            radioBlock.contains("onOpenSihDemo")
        )
    }

    @Test
    fun testSihDemoSimulationSafetyPreserved() {
        // Enforce safety boundary logic
        assertFalse("Demo safety must prohibit physical radio in sim", DemoSafetyBoundary.canEmitPhysicalRadio(true))
        assertFalse("Demo safety must prohibit hardware location in sim", DemoSafetyBoundary.canAcquireHardwareLocation(true))
        assertFalse("Demo safety must prohibit emergency intents in sim", DemoSafetyBoundary.canBroadcastEmergencyIntent(true))
    }

    @Test
    fun testDiagnosticsRetainsSihDemoShortcut() {
        val diagFile = File("src/main/java/org/sih/itantra/presentation/screens/DiagnosticsScreen.kt")
        assertTrue("DiagnosticsScreen.kt must exist", diagFile.exists())
        val content = diagFile.readText()

        assertTrue("DiagnosticsScreen should retain onOpenSihDemo", content.contains("onOpenSihDemo"))
        assertTrue("DiagnosticsScreen should retain SIH Demo action", content.contains("OPEN SIH MISSION DEMO DASHBOARD"))
    }

    // =========================================================================
    // B. Radio Messages Visibility & Data Integrity
    // =========================================================================

    @Test
    fun testLiveRadioTrafficSectionInMainTransceiverScreen() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        assertTrue("Radio screen must contain LIVE RADIO TRAFFIC header", content.contains("LIVE RADIO TRAFFIC"))
        assertTrue("Radio screen must contain REAL-TIME indicator", content.contains("REAL-TIME"))
        assertTrue("Radio screen must display full history items, not capped to 2", content.contains("items(history)"))
        assertFalse("Radio screen must not cap messages to history.take(2)", content.contains("history.take(2)"))
    }

    @Test
    fun testMessageHistoryStoreChronology() {
        MessageHistoryStore.clear()
        val record1 = createRecord(
            id = "1",
            text = "First transmission",
            timestamp = 1000L,
            peer = "Node-A",
            direction = MessageDirection.SENT
        )
        val record2 = createRecord(
            id = "2",
            text = "Second transmission",
            timestamp = 2000L,
            peer = "Node-B",
            direction = MessageDirection.RECEIVED
        )

        MessageHistoryStore.addRecord(record1)
        MessageHistoryStore.addRecord(record2)

        val history = MessageHistoryStore.getRecords()
        assertEquals(2, history.size)
        // Most recent first
        assertEquals("Second transmission", history[0].text)
        assertEquals("First transmission", history[1].text)
        MessageHistoryStore.clear()
    }

    @Test
    fun testDirectionalSeparationInMessageRecord() {
        val tx = createRecord(
            direction = MessageDirection.SENT,
            text = "Outgoing tactical update"
        )
        val rx = createRecord(
            direction = MessageDirection.RECEIVED,
            text = "Incoming relay packet"
        )

        assertTrue(tx.direction == MessageDirection.SENT)
        assertTrue(rx.direction == MessageDirection.RECEIVED)
        assertNotEquals(tx.direction, rx.direction)
    }

    @Test
    fun testMultilingualMessageScriptPreservation() {
        val tamilText = "மருத்துவ அவசர உதவி தேவைப்படுகிறது"
        val hindiText = "राहत दल उत्तर दिशा की ओर बढ़ रहा है"
        val malayalamText = "രക്ഷാപ്രവർത്തനം ആരംഭിച്ചു"
        val teluguText = "వైద్య సహాయం అవసరం"
        val kannadaText = "ತುರ್ತು ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕಾಗಿದೆ"

        val records = listOf(
            createRecord(id = "1", text = tamilText, language = IndicLanguage.TAMIL),
            createRecord(id = "2", text = hindiText, language = IndicLanguage.HINDI),
            createRecord(id = "3", text = malayalamText, language = IndicLanguage.MALAYALAM),
            createRecord(id = "4", text = teluguText, language = IndicLanguage.TELUGU),
            createRecord(id = "5", text = kannadaText, language = IndicLanguage.KANNADA)
        )

        assertEquals(tamilText, records[0].text)
        assertEquals(hindiText, records[1].text)
        assertEquals(malayalamText, records[2].text)
        assertEquals(teluguText, records[3].text)
        assertEquals(kannadaText, records[4].text)
    }

    @Test
    fun testEmergencyMessagePriorityIntegrity() {
        val distressRecord = createRecord(
            text = "SOS 3 casualties at checkpoint",
            priority = MessagePriority.DISTRESS,
            location = GeoLocation(12.9716, 77.5946, 4.5f, 1000L, 920.0),
            isSemantic = true,
            semanticSummary = "CASUALTY • 3 • MEDICAL"
        )
        val normalRecord = createRecord(
            text = "Routine patrol status check",
            priority = MessagePriority.NORMAL
        )

        assertEquals(MessagePriority.DISTRESS, distressRecord.priority)
        assertEquals(MessagePriority.NORMAL, normalRecord.priority)
        assertTrue(distressRecord.isSemantic)
        assertNotNull(distressRecord.location)
    }

    @Test
    fun testDeliveryAndSecurityBadgesDataPreserved() {
        val secureDeliveredRecord = createRecord(
            text = "Supply truck arrived safely",
            isSecure = true,
            authStatus = "AUTH ✓",
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveryLatencyMs = 24L,
            fragmentCount = 3,
            qosStatus = "PRIORITY_SENT",
            measuredLatencyMs = 45.0
        )

        assertTrue(secureDeliveredRecord.isSecure)
        assertEquals("AUTH ✓", secureDeliveredRecord.authStatus)
        assertEquals(DeliveryStatus.DELIVERED, secureDeliveredRecord.deliveryStatus)
        assertEquals(24L, secureDeliveredRecord.deliveryLatencyMs)
        assertEquals(3, secureDeliveredRecord.fragmentCount)
        assertEquals("PRIORITY_SENT", secureDeliveredRecord.qosStatus)
        assertEquals(45.0, secureDeliveredRecord.measuredLatencyMs, 0.01)
    }

    // =========================================================================
    // C. Operational Controls & Emergency Layout
    // =========================================================================

    @Test
    fun testThreeControlLayoutOrderInMainTransceiverScreen() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        val rowStart = content.indexOf("Tactical Controls Row: WALKIE PTT -> SEND DISTRESS -> TEST PACKET")
        assertTrue("Tactical controls row header comment must exist", rowStart >= 0)

        val afterRow = content.substring(rowStart)
        val posWalkiePtt = afterRow.indexOf("WALKIE PTT")
        val posSendDistress = afterRow.indexOf("SEND DISTRESS")
        val posTestPacket = afterRow.indexOf("TEST PACKET")

        assertTrue("WALKIE PTT must be present in tactical controls row", posWalkiePtt >= 0)
        assertTrue("SEND DISTRESS must be present in tactical controls row", posSendDistress >= 0)
        assertTrue("TEST PACKET must be present in tactical controls row", posTestPacket >= 0)

        assertTrue(
            "SEND DISTRESS must be physically between WALKIE PTT and TEST PACKET",
            posWalkiePtt < posSendDistress && posSendDistress < posTestPacket
        )
    }

    @Test
    fun testEmergencyDistressConfirmationGuardPreserved() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        assertTrue("Distress button must toggle showDistressDialog", content.contains("showDistressDialog = true"))
        assertTrue("Emergency AlertDialog must be present", content.contains("AlertDialog(") && content.contains("CONFIRM EMERGENCY DISTRESS"))
        assertTrue("Location permission launcher must trigger sendDistress", content.contains("viewModel.sendDistress()"))
    }

    @Test
    fun testPttControlAccessible() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        assertTrue("Radio screen must contain RadioPttControl", content.contains("RadioPttControl("))
        assertTrue("Radio screen must wire startPtt", content.contains("viewModel.startPtt()"))
        assertTrue("Radio screen must wire stopPtt", content.contains("viewModel.stopPtt()"))
    }

    @Test
    fun testVoiceControlStatusCardPreserved() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        assertTrue("Voice control state must be observed", content.contains("voiceCommandState"))
        assertTrue("Voice control status label must be observed", content.contains("voiceCommandStatusLabel"))
        assertTrue("Voice command confirmation buttons must be present", content.contains("viewModel.confirmVoiceCommand()"))
        assertTrue("Voice command rejection button must be present", content.contains("viewModel.rejectVoiceCommand()"))
    }

    // =========================================================================
    // D. Radio Traffic Final — Separator Removal & Enlarged Traffic Area
    // =========================================================================

    @Test
    fun testNoDottedSeparatorWaveformInRadioScreen() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        assertTrue("MainTransceiverScreen.kt must exist", radioScreenFile.exists())
        val content = radioScreenFile.readText()

        // WaveformVisualizer must NOT be rendered in the main radio screen layout
        assertFalse(
            "WaveformVisualizer must be removed from MainTransceiverScreen (it was the dotted separator)",
            content.contains("WaveformVisualizer(")
        )
        // No HorizontalDivider or dashed path effects either
        assertFalse(
            "No HorizontalDivider should be present as a separator between language selector and traffic",
            content.contains("HorizontalDivider(")
        )
    }

    @Test
    fun testLiveRadioTrafficUsesWeightModifier() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        // The LIVE RADIO TRAFFIC column must use weight(1f) so it expands to fill available space
        val trafficSection = content.substringAfter("LIVE RADIO TRAFFIC").substringBefore("Central Large Circular PTT")
        assertTrue(
            "LIVE RADIO TRAFFIC section must use Modifier.weight(1f) to fill remaining space",
            trafficSection.contains("weight(1f)")
        )
    }

    @Test
    fun testEmptyStateShowsNewMessageText() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        // New user-friendly empty state text per PART 9
        assertTrue(
            "Empty state must show 'NO RADIO MESSAGES RECEIVED YET'",
            content.contains("NO RADIO MESSAGES RECEIVED YET")
        )
        assertTrue(
            "Empty state must show PTT instruction",
            content.contains("Hold PTT or send a test packet to transmit")
        )
        // Old cryptic text must be gone
        assertFalse(
            "Old 'CH-1 IDLE // READY FOR TRANSMISSION' text must be removed",
            content.contains("CH-1 IDLE // READY FOR TRANSMISSION")
        )
    }

    @Test
    fun testHistoryLazyColumnStillPresent() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val content = radioScreenFile.readText()

        // LazyColumn with items(history) must remain — no separate data source
        assertTrue("LazyColumn must still render items from history", content.contains("items(history)"))
        assertFalse("history.take() must not be used to cap messages", content.contains("history.take("))
    }

    @Test
    fun testSihDemoAccessibleFromSettingsNotRadio() {
        val radioScreenFile = File("src/main/java/org/sih/itantra/presentation/screens/MainTransceiverScreen.kt")
        val radioContent = radioScreenFile.readText()
        val settingsFile = File("src/main/java/org/sih/itantra/presentation/screens/SettingsScreen.kt")
        val settingsContent = settingsFile.readText()

        // Confirm SIH Demo is NOT on Radio screen
        assertFalse("Radio screen must not reference onOpenSihDemo", radioContent.contains("onOpenSihDemo"))
        assertFalse("Radio screen must not show ⚡ SIH DEMO MODE banner", radioContent.contains("⚡ SIH DEMO MODE"))

        // Confirm SIH Demo IS accessible from Settings
        assertTrue("Settings must have onOpenSihDemo callback", settingsContent.contains("onOpenSihDemo"))
        assertTrue("Settings must contain OPEN SIH DEMO action", settingsContent.contains("OPEN SIH DEMO"))
    }
}
