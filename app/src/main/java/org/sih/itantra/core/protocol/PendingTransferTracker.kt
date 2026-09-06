package org.sih.itantra.core.protocol

import android.util.Log

enum class DeliveryStatus {
    NONE,
    SENDING,
    PENDING,
    DELIVERED,
    TIMEOUT
}

data class TrackedTransfer(
    val transferId: Short,
    val messageId: String,
    val destinationDeviceId: Int,
    val fragmentCount: Int,
    val payloadBytes: Int,
    val totalWireBytes: Int,
    val sendTimeMs: Long,
    var status: DeliveryStatus = DeliveryStatus.PENDING,
    var deliveryRttMs: Long? = null
)

/**
 * Sender-side tracker for correlated delivery receipts.
 *
 * Enforces:
 * 1. 30-second delivery timeout
 * 2. Duplicate ACK idempotence
 * 3. Bounded capacity
 */
class PendingTransferTracker(
    private val maxCapacity: Int = MAX_CAPACITY,
    private val timeoutMs: Long = Packet.DELIVERY_RECEIPT_TIMEOUT_MS
) {
    companion object {
        private const val TAG = "PendingTransferTracker"
        const val MAX_CAPACITY = 100
    }

    private val transfers = LinkedHashMap<Short, TrackedTransfer>(MAX_CAPACITY, 0.75f, true)
    private val lock = Any()

    /**
     * Registers an outgoing transfer awaiting a delivery receipt.
     */
    fun registerTransfer(
        transferId: Short,
        messageId: String,
        destinationDeviceId: Int,
        fragmentCount: Int,
        payloadBytes: Int,
        totalWireBytes: Int,
        nowMs: Long = System.currentTimeMillis()
    ): TrackedTransfer = synchronized(lock) {
        pruneExpired(nowMs)

        if (transfers.size >= maxCapacity) {
            val oldest = transfers.keys.firstOrNull()
            if (oldest != null) {
                transfers.remove(oldest)
            }
        }

        val transfer = TrackedTransfer(
            transferId = transferId,
            messageId = messageId,
            destinationDeviceId = destinationDeviceId,
            fragmentCount = fragmentCount,
            payloadBytes = payloadBytes,
            totalWireBytes = totalWireBytes,
            sendTimeMs = nowMs,
            status = DeliveryStatus.PENDING
        )
        transfers[transferId] = transfer
        transfer
    }

    /**
     * Acknowledges delivery of a transfer upon receiving a [DeliveryReceipt].
     * Returns the updated [TrackedTransfer] if this was the first delivery confirmation,
     * or `null` if the transfer was unknown or already delivered (idempotent duplicate ACK).
     */
    fun onReceiptReceived(receipt: DeliveryReceipt, nowMs: Long = System.currentTimeMillis()): TrackedTransfer? = synchronized(lock) {
        val transfer = transfers[receipt.transferId] ?: run {
            Log.d(TAG, "Receipt for untracked or previously cleaned transfer 0x${Integer.toHexString(receipt.transferId.toInt() and 0xFFFF)}")
            return null
        }

        if (transfer.status == DeliveryStatus.DELIVERED) {
            Log.d(TAG, "Duplicate receipt for transfer 0x${Integer.toHexString(receipt.transferId.toInt() and 0xFFFF)} safely ignored")
            return null
        }

        val rtt = (nowMs - transfer.sendTimeMs).coerceAtLeast(0L)
        transfer.status = if (receipt.status == DeliveryReceipt.STATUS_DELIVERED) DeliveryStatus.DELIVERED else DeliveryStatus.TIMEOUT
        transfer.deliveryRttMs = rtt

        Log.i(TAG, "Delivery receipt acknowledged for transfer 0x${Integer.toHexString(receipt.transferId.toInt() and 0xFFFF)} " +
                "in ${rtt}ms (status=${transfer.status})")

        return transfer
    }

    /**
     * Scans for transfers exceeding [timeoutMs] and marks them as [DeliveryStatus.TIMEOUT].
     * Returns all newly timed out transfers.
     */
    fun pruneExpired(nowMs: Long = System.currentTimeMillis()): List<TrackedTransfer> = synchronized(lock) {
        val timedOut = mutableListOf<TrackedTransfer>()
        for ((_, transfer) in transfers) {
            if (transfer.status == DeliveryStatus.PENDING && (nowMs - transfer.sendTimeMs) > timeoutMs) {
                transfer.status = DeliveryStatus.TIMEOUT
                timedOut.add(transfer)
                Log.w(TAG, "Transfer 0x${Integer.toHexString(transfer.transferId.toInt() and 0xFFFF)} delivery timeout after ${timeoutMs}ms")
            }
        }
        return timedOut
    }

    fun getTransfer(transferId: Short): TrackedTransfer? = synchronized(lock) {
        transfers[transferId]
    }

    fun clear(): Unit = synchronized(lock) {
        transfers.clear()
    }
}
