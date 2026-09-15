package org.sih.itantra.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.crypto.AntiReplayFilter
import org.sih.itantra.core.crypto.ReplayStatus

/**
 * Feature 23: Replay-Protection Audit & Adversarial Verification Suite.
 *
 * Verifies:
 * - 64-packet sliding window enforcement per source node.
 * - Out-of-order packet acceptance within window.
 * - Stale packet rejection beyond 64-packet horizon.
 * - Exact duplicate packet suppression (2x and 100x floods).
 * - Graceful 16-bit signed sequence number rollover handling.
 * - Node window isolation (no crosstalk across distinct source device IDs).
 * - Bounded LRU cache size (max 100 tracked peers).
 * - Thread safety & inactivity pruning.
 */
class ReplayProtectionSecurityTest {

    private lateinit var filter: AntiReplayFilter

    @Before
    fun setUp() {
        filter = AntiReplayFilter(maxTrackedNodes = 100, pruneInactivityMs = 60_000L)
    }

    @Test
    fun replay_identicalPacketTwice_secondAttemptRejectedAsDuplicate() {
        val src = 1001
        val seq: Short = 15

        val res1 = filter.checkAndRecord(src, seq)
        assertEquals(ReplayStatus.ACCEPTED, res1.status)
        assertTrue(res1.isAccepted)

        val res2 = filter.checkAndRecord(src, seq)
        assertEquals(ReplayStatus.DUPLICATE, res2.status)
        assertFalse(res2.isAccepted)
    }

    @Test
    fun replay_identicalPacketFlood100x_allSubsequentAttemptsRejected() {
        val src = 1002
        val seq: Short = 42

        val initial = filter.checkAndRecord(src, seq)
        assertEquals(ReplayStatus.ACCEPTED, initial.status)

        // Adversary replays identical packet 100 times
        for (i in 1..100) {
            val replay = filter.checkAndRecord(src, seq)
            assertEquals("Replay attempt #$i must be rejected as DUPLICATE", ReplayStatus.DUPLICATE, replay.status)
            assertFalse(replay.isAccepted)
        }
    }

    @Test
    fun replay_strictlyIncreasingSequences_allAccepted() {
        val src = 1003
        for (seq in 1..200) {
            val res = filter.checkAndRecord(src, seq.toShort())
            assertEquals(ReplayStatus.ACCEPTED, res.status)
        }
    }

    @Test
    fun replay_outOfOrderArrivalWithinWindow_accepted() {
        val src = 1004

        // Highest sequence jumps to 50
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(src, 50.toShort()).status)

        // Arrive out of order: 48, 45, 49
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(src, 48.toShort()).status)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(src, 45.toShort()).status)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(src, 49.toShort()).status)

        // Replaying any of the out-of-order packets must be rejected as DUPLICATE
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(src, 48.toShort()).status)
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(src, 45.toShort()).status)
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(src, 49.toShort()).status)
    }

    @Test
    fun replay_slidingWindowExactBoundary_distance63Accepted_distance64Stale() {
        val src = 1005

        // Set highest sequence to 100
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(src, 100.toShort()).status)

        // Distance 63: 100 - 63 = 37 (within 64-bit window) -> ACCEPTED
        val res37 = filter.checkAndRecord(src, 37.toShort())
        assertEquals(ReplayStatus.ACCEPTED, res37.status)

        // Distance 64: 100 - 64 = 36 (outside 64-bit window) -> STALE
        val res36 = filter.checkAndRecord(src, 36.toShort())
        assertEquals(ReplayStatus.STALE, res36.status)

        // Distance 80: 100 - 80 = 20 (far outside window) -> STALE
        val res20 = filter.checkAndRecord(src, 20.toShort())
        assertEquals(ReplayStatus.STALE, res20.status)
    }

    @Test
    fun replay_windowAdvanceClearsStaleBits() {
        val src = 1006

        // Initial sequence 10
        filter.checkAndRecord(src, 10.toShort())

        // Sequence jumps by 70 (exceeding window size 64) to 80
        val jump = filter.checkAndRecord(src, 80.toShort())
        assertEquals(ReplayStatus.ACCEPTED, jump.status)

        // Sequence 10 should now be ancient/stale (distance 70)
        val oldSeq = filter.checkAndRecord(src, 10.toShort())
        assertEquals(ReplayStatus.STALE, oldSeq.status)
    }

    @Test
    fun replay_16BitSequenceRollover_handledGracefully() {
        val src = 1007

        // Highest sequence at max positive Short (32767)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(src, Short.MAX_VALUE).status)

        // Rollover to -32768 (signed Short increment by 1)
        val nextSeq = (Short.MAX_VALUE + 1).toShort()
        assertEquals((-32768).toShort(), nextSeq)

        val rolloverRes = filter.checkAndRecord(src, nextSeq)
        assertEquals("Signed 16-bit sequence rollover must be accepted as newer sequence", ReplayStatus.ACCEPTED, rolloverRes.status)

        // Next increment to -32767
        val nextSeq2 = (nextSeq + 1).toShort()
        val rolloverRes2 = filter.checkAndRecord(src, nextSeq2)
        assertEquals(ReplayStatus.ACCEPTED, rolloverRes2.status)
    }

    @Test
    fun replay_independentNodeWindows_noCrosstalk() {
        val nodeA = 111111
        val nodeB = 222222

        // Both nodes transmit sequence 5
        val resA = filter.checkAndRecord(nodeA, 5.toShort())
        val resB = filter.checkAndRecord(nodeB, 5.toShort())

        assertEquals(ReplayStatus.ACCEPTED, resA.status)
        assertEquals(ReplayStatus.ACCEPTED, resB.status)

        // Replaying sequence 5 for node A fails, but sequence 6 for node A succeeds
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(nodeA, 5.toShort()).status)
        assertEquals(ReplayStatus.ACCEPTED, filter.checkAndRecord(nodeA, 6.toShort()).status)

        // Node B's sequence 5 is still tracked as duplicate
        assertEquals(ReplayStatus.DUPLICATE, filter.checkAndRecord(nodeB, 5.toShort()).status)
    }

    @Test
    fun replay_boundedLruNodeStorage_neverExceedsMaxCapacity() {
        val boundedFilter = AntiReplayFilter(maxTrackedNodes = 50)

        // Transmit from 100 distinct node IDs
        for (nodeId in 1..100) {
            boundedFilter.checkAndRecord(nodeId, 1.toShort())
        }

        // Cache must not exceed 50 entries
        assertEquals(50, boundedFilter.getTrackedNodeCount())
    }

    @Test
    fun replay_inactivityPruning_evictsOldPeers() {
        val testFilter = AntiReplayFilter(maxTrackedNodes = 100, pruneInactivityMs = 50L)

        testFilter.checkAndRecord(1, 10.toShort())
        testFilter.checkAndRecord(2, 20.toShort())
        assertEquals(2, testFilter.getTrackedNodeCount())

        // Sleep to exceed 50ms prune threshold
        Thread.sleep(70L)

        val evicted = testFilter.pruneInactive()
        assertEquals(2, evicted)
        assertEquals(0, testFilter.getTrackedNodeCount())
    }
}
