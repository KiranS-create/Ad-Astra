package org.sih.itantra.core.persistence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local communication history store.
 * Operates offline; never synchronizes to cloud storage.
 */
object MessageHistoryStore {

    private val records = CopyOnWriteArrayList<MessageRecord>()
    private val _historyFlow = MutableStateFlow<List<MessageRecord>>(emptyList())
    val historyFlow: StateFlow<List<MessageRecord>> = _historyFlow.asStateFlow()

    fun addRecord(record: MessageRecord) {
        records.add(0, record) // Most recent first
        _historyFlow.value = records.toList()
    }

    fun clear() {
        records.clear()
        _historyFlow.value = emptyList()
    }
}
