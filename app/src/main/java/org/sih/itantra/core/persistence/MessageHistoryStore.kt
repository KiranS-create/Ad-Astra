package org.sih.itantra.core.persistence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sih.itantra.core.protocol.DeliveryStatus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local communication history store.
 * Operates offline; never synchronizes to cloud storage.
 */
object MessageHistoryStore {

    private val records = CopyOnWriteArrayList<MessageRecord>()
    private val _historyFlow = MutableStateFlow<List<MessageRecord>>(emptyList())
    val historyFlow: StateFlow<List<MessageRecord>> = _historyFlow.asStateFlow()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }

    fun addRecord(record: MessageRecord) {
        records.add(0, record) // Most recent first
        _historyFlow.value = records.toList()
        notifyListeners()
    }

    /**
     * Updates delivery state of an outgoing transfer upon ACK reception or timeout.
     */
    fun updateRecordDelivery(transferId: Short, status: DeliveryStatus, latencyMs: Long? = null) {
        var updated = false
        for (i in records.indices) {
            val r = records[i]
            if (r.transferId == transferId && r.direction == MessageDirection.SENT) {
                records[i] = r.copy(
                    deliveryStatus = status,
                    deliveryLatencyMs = latencyMs ?: r.deliveryLatencyMs
                )
                updated = true
                break
            }
        }
        if (updated) {
            _historyFlow.value = records.toList()
            notifyListeners()
        }
    }

    fun getRecords(): List<MessageRecord> = records.toList()

    fun clear() {
        records.clear()
        _historyFlow.value = emptyList()
        notifyListeners()
    }
}
