package org.sih.itantra.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.protocol.Packet

class ManetSimulatorTest {

    private lateinit var simulator: ManetSimulator

    @Before
    fun setUp() {
        simulator = ManetSimulator()
    }

    @Test
    fun testInitialTopologyState() {
        val state = simulator.topologyState.value
        assertEquals("Should have 4 nodes", 4, state.nodes.size)
        assertEquals("Live hardware count should be 2", 2, state.liveHardwareCount)
        assertEquals("Simulated node count should be 2", 2, state.simulatedNodeCount)

        val nodeA = state.nodes.first { it.id == ManetSimulator.NODE_A_ID }
        assertTrue("Node A should be marked real", nodeA.isReal)
        assertTrue("Node A should be online initially", nodeA.isOnline)

        val nodeC = state.nodes.first { it.id == ManetSimulator.NODE_C_ID }
        assertTrue("Node C should be marked simulated", !nodeC.isReal)
    }

    @Test
    fun testDiscoveryBuildsNeighborTables() {
        simulator.startDiscovery()
        val state = simulator.topologyState.value

        val nodeA = state.nodes.first { it.id == ManetSimulator.NODE_A_ID }
        // Node A is connected to Node B and Node D -> should have 2 neighbors
        assertEquals("Node A should discover 2 neighbors (B, D)", 2, nodeA.neighborCount)

        val nodeB = state.nodes.first { it.id == ManetSimulator.NODE_B_ID }
        // Node B is connected to A and C -> 2 neighbors
        assertEquals("Node B should discover 2 neighbors (A, C)", 2, nodeB.neighborCount)

        val helloEvents = state.events.filter { it.eventType == SimEventType.HELLO }
        assertTrue("Should have logged HELLO events", helloEvents.isNotEmpty())
    }

    @Test
    fun testRouteDiscoveryAndDataDeliveryAtoC() {
        simulator.startDiscovery()
        simulator.sendPacketAtoC("Test voice transmission")
        val state = simulator.topologyState.value

        // Active route established: A -> B -> C
        assertEquals(
            "Active route should be A -> B -> C",
            listOf(ManetSimulator.NODE_A_ID, ManetSimulator.NODE_B_ID, ManetSimulator.NODE_C_ID),
            state.activeRoute
        )

        // Events should contain RREQ, RREP, DATA_FORWARD, and DATA_DELIVERED
        val rreqEvents = state.events.filter { it.eventType == SimEventType.RREQ }
        val rrepEvents = state.events.filter { it.eventType == SimEventType.RREP }
        val deliveryEvents = state.events.filter { it.eventType == SimEventType.DATA_DELIVERED }

        assertTrue("RREQ should be emitted", rreqEvents.isNotEmpty())
        assertTrue("RREP should be emitted", rrepEvents.isNotEmpty())
        assertTrue("Packet should be delivered at Node C", deliveryEvents.isNotEmpty())

        val deliveredPacket = deliveryEvents.first().packet
        assertNotNull("Delivered event should contain Packet", deliveredPacket)
        assertEquals("Delivered packet at destination should have TTL=1", 1.toByte(), deliveredPacket!!.ttl)
        assertEquals("Source should be Node A", ManetSimulator.NODE_A_ID, deliveredPacket.sourceDeviceId)
        assertEquals("Destination should be Node C", ManetSimulator.NODE_C_ID, deliveredPacket.destinationDeviceId)
    }

    @Test
    fun testNodeFailureTriggersRerrAndInvalidation() {
        simulator.startDiscovery()
        simulator.sendPacketAtoC("Message 1")

        // Now fail Node B
        simulator.failNodeB()
        val stateAfterFail = simulator.topologyState.value
        val nodeB = stateAfterFail.nodes.first { it.id == ManetSimulator.NODE_B_ID }
        assertTrue("Node B should be offline", !nodeB.isOnline)

        // Trigger failure handling
        simulator.triggerFailureAndRediscovery()
        val stateAfterRecovery = simulator.topologyState.value

        val rerrEvents = stateAfterRecovery.events.filter { it.eventType == SimEventType.RERR }
        assertTrue("RERR event should be emitted upon route failure", rerrEvents.isNotEmpty())
    }

    @Test
    fun testRouteRediscoveryViaAlternateNodeD() {
        simulator.startDiscovery()
        simulator.sendPacketAtoC("Message 1")

        // Fail Node B and trigger rediscovery
        simulator.failNodeB()
        simulator.triggerFailureAndRediscovery()

        val state = simulator.topologyState.value
        // Alternate route: A -> D -> C
        assertEquals(
            "Recovered route should be A -> D -> C",
            listOf(ManetSimulator.NODE_A_ID, ManetSimulator.NODE_D_ID, ManetSimulator.NODE_C_ID),
            state.activeRoute
        )

        val deliveryEvents = state.events.filter { it.eventType == SimEventType.DATA_DELIVERED }
        assertTrue("Packet should be delivered via alternate Node D", deliveryEvents.isNotEmpty())
    }
}
