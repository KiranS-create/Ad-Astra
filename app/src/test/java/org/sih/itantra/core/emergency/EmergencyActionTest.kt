package org.sih.itantra.core.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype

class EmergencyActionTest {

    @Test
    fun testAllEightTacticalActionsExist() {
        val expectedActions = listOf(
            "MEDICAL", "INJURED", "TRAPPED", "ATTACK",
            "FIRE", "EVACUATION", "NEED_EXTRACTION", "LOCATION"
        )
        val actualActions = EmergencyAction.entries.map { it.actionId }

        assertEquals(8, EmergencyAction.entries.size)
        assertTrue(actualActions.containsAll(expectedActions))
    }

    @Test
    fun testMedicalActionSemanticMapping() {
        val action = EmergencyAction.MEDICAL
        val cmd = action.toSemanticCommand()

        assertEquals(EmergencyCategory.MEDICAL, cmd.category)
        assertEquals(EmergencySubtype.NONE, cmd.subtype)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
        assertEquals(1, cmd.count)
    }

    @Test
    fun testInjuredActionSemanticMapping() {
        val action = EmergencyAction.INJURED
        val cmd = action.toSemanticCommand(count = 2)

        assertEquals(EmergencyCategory.MEDICAL, cmd.category)
        assertEquals(EmergencySubtype.INJURED, cmd.subtype)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
        assertEquals(2, cmd.count)
    }

    @Test
    fun testTrappedActionSemanticMapping() {
        val action = EmergencyAction.TRAPPED
        val cmd = action.toSemanticCommand()

        assertEquals(EmergencyCategory.TRAPPED, cmd.category)
        assertEquals(EmergencySubtype.COLLAPSE, cmd.subtype)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
    }

    @Test
    fun testFireActionSemanticMapping() {
        val action = EmergencyAction.FIRE
        val cmd = action.toSemanticCommand()

        assertEquals(EmergencyCategory.FIRE, cmd.category)
        assertEquals(EmergencySubtype.BUILDING, cmd.subtype)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
    }

    @Test
    fun testExtractionActionSemanticMapping() {
        val action = EmergencyAction.NEED_EXTRACTION
        val cmd = action.toSemanticCommand()

        assertEquals(EmergencyCategory.RESCUE, cmd.category)
        assertEquals(EmergencySubtype.TEAM, cmd.subtype)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
    }

    @Test
    fun testEvacuationActionSemanticMapping() {
        val action = EmergencyAction.EVACUATION
        val cmd = action.toSemanticCommand()

        assertEquals(EmergencyCategory.EVACUATION, cmd.category)
        assertEquals(EmergencySubtype.NONE, cmd.subtype)
        assertEquals(EmergencySeverity.CRITICAL, cmd.severity)
    }

    @Test
    fun testLocationActionSemanticMapping() {
        val action = EmergencyAction.LOCATION
        val cmd = action.toSemanticCommand()

        assertEquals(EmergencyCategory.OTHER, cmd.category)
        assertEquals(EmergencySeverity.ALERT, cmd.severity)
    }

    @Test
    fun testFromIdLookup() {
        assertEquals(EmergencyAction.MEDICAL, EmergencyAction.fromId("medical"))
        assertEquals(EmergencyAction.FIRE, EmergencyAction.fromId("FIRE"))
        assertEquals(EmergencyAction.NEED_EXTRACTION, EmergencyAction.fromId("need_extraction"))
        assertNull(EmergencyAction.fromId("non_existent_action"))
    }

    @Test
    fun testDefaultTextsAreNonBlankAndTactical() {
        EmergencyAction.entries.forEach { action ->
            assertTrue("Default text for ${action.name} should not be blank", action.defaultText.isNotBlank())
            assertTrue("Action label for ${action.name} should not be blank", action.label.isNotBlank())
        }
    }
}
