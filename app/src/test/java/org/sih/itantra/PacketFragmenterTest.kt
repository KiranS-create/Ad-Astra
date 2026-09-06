package org.sih.itantra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.ReassemblyBuffer

class PacketFragmenterTest {

    @Test
    fun testFragmentationAndReassembly() {
        val fragmenter = PacketFragmenter(maxPayloadBytes = 64)
        val reassemblyBuffer = ReassemblyBuffer()
        val largeData = ByteArray(200) { (it % 256).toByte() }

        val transferId: Short = 100
        val sourceId = 12345
        val fragments = fragmenter.fragment(largeData, transferId)
        assertEquals(4, fragments.size) // 64 + 64 + 64 + 8 = 200 bytes

        // Simulate receiving fragments out of order
        assertNull(reassemblyBuffer.addFragment(sourceId, fragments[2]))
        assertNull(reassemblyBuffer.addFragment(sourceId, fragments[0]))
        assertNull(reassemblyBuffer.addFragment(sourceId, fragments[3]))

        val assembled = reassemblyBuffer.addFragment(sourceId, fragments[1])
        assertNotNull(assembled)
        assertArrayEquals(largeData, assembled?.payload)
    }
}
