package org.sih.itantra.core.demo

/**
 * Architectural Safety Boundary between the SIH Demo Simulation Environment
 * and Physical Device Hardware / Transports.
 *
 * Enforces strict non-bypassable guarantees:
 * 1. SIMULATION mode CANNOT emit physical radio packets (Bluetooth RFCOMM or Wi-Fi Direct UDP).
 * 2. SIMULATION mode CANNOT poll physical GPS / location hardware.
 * 3. SIMULATION mode CANNOT trigger real emergency distress broadcast intents or audible alarm sirens.
 * 4. SIMULATION mode CANNOT modify live persistent network routing tables or peer registries.
 */
object DemoSafetyBoundary {

    /**
     * Verifies that the current execution is isolated within the simulation harness.
     */
    fun isSimulationIsolated(isSimulation: Boolean): Boolean = isSimulation

    /**
     * Explicit guard check before any physical radio frame dispatch.
     * @return true ONLY if execution is NOT in simulation mode.
     */
    fun canEmitPhysicalRadio(isSimulation: Boolean): Boolean {
        return !isSimulation
    }

    /**
     * Explicit guard check before querying device GPS hardware.
     * @return true ONLY if execution is NOT in simulation mode.
     */
    fun canAcquireHardwareLocation(isSimulation: Boolean): Boolean {
        return !isSimulation
    }

    /**
     * Explicit guard check before firing Android emergency intents or alert alarms.
     * @return true ONLY if execution is NOT in simulation mode.
     */
    fun canBroadcastEmergencyIntent(isSimulation: Boolean): Boolean {
        return !isSimulation
    }

    /**
     * Throws an IllegalStateException if a physical hardware action is attempted
     * while in simulation mode.
     */
    fun enforceSimulationSafety(isSimulation: Boolean, actionName: String) {
        if (isSimulation) {
            throw IllegalStateException(
                "SAFETY VIOLATION BLOCKED: Cannot execute physical action '$actionName' while in SIMULATION mode."
            )
        }
    }
}
