package org.sih.itantra.core.search

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sih.itantra.core.chat.ChatDeliveryStatus
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.MeshTopologySnapshot

/**
 * Offline Global Search Repository for iTantra.
 *
 * Coordinates the local deterministic SearchIndex, filter criteria, and
 * bounded recent search history. Fully offline; zero cloud sync.
 */
class LocalSearchRepository(
    private val context: Context? = null,
    val searchIndex: SearchIndex = SearchIndex(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences("itantra_search_history_prefs", Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(SearchFilterState())
    val filterState: StateFlow<SearchFilterState> = _filterState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _groupedResults = MutableStateFlow<Map<SearchResultType, List<SearchResult>>>(emptyMap())
    val groupedResults: StateFlow<Map<SearchResultType, List<SearchResult>>> = _groupedResults.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val indexChangeListener = {
        executeSearch()
    }

    init {
        loadRecentSearches()
        searchIndex.addChangeListener(indexChangeListener)
    }

    fun setQuery(query: String) {
        _searchQuery.value = query
        executeSearch()
    }

    fun setFilterType(type: SearchFilterType) {
        _filterState.value = _filterState.value.copy(filterType = type)
        executeSearch()
    }

    fun setLanguageFilter(language: IndicLanguage?) {
        _filterState.value = _filterState.value.copy(selectedLanguage = language)
        executeSearch()
    }

    fun setPriorityFilter(priority: MessagePriority?) {
        _filterState.value = _filterState.value.copy(selectedPriority = priority)
        executeSearch()
    }

    fun setDeliveryStatusFilter(deliveryStatus: ChatDeliveryStatus?) {
        _filterState.value = _filterState.value.copy(selectedDeliveryStatus = deliveryStatus)
        executeSearch()
    }

    fun clearFilters() {
        _filterState.value = SearchFilterState()
        executeSearch()
    }

    fun setContactProvider(provider: SearchContactProvider) {
        searchIndex.setContactProvider(provider)
    }

    fun updateTopology(snapshot: MeshTopologySnapshot) {
        searchIndex.updateTopology(snapshot)
    }

    fun executeSearch() {
        val q = _searchQuery.value
        val state = _filterState.value

        if (q.isBlank()) {
            _searchResults.value = emptyList()
            _groupedResults.value = emptyMap()
            return
        }

        val results = searchIndex.search(q, state)
        _searchResults.value = results

        // Group by result type for structured multi-section presentation
        _groupedResults.value = results.groupBy { it.resultType }
    }

    /**
     * Bounded recent searches management (capped at 10, FIFO eviction, local only).
     */
    fun recordSearchCommit(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || trimmed.length < 2) return

        val current = _recentSearches.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed) // Most recent first

        val bounded = current.take(10)
        _recentSearches.value = bounded
        persistRecentSearches(bounded)
    }

    fun removeRecentSearch(query: String) {
        val current = _recentSearches.value.toMutableList()
        current.remove(query)
        _recentSearches.value = current
        persistRecentSearches(current)
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        persistRecentSearches(emptyList())
    }

    private fun loadRecentSearches() {
        val serialized = prefs?.getString("recent_queries", null)
        if (!serialized.isNullOrBlank()) {
            val items = serialized.split("\u001F").filter { it.isNotBlank() }
            _recentSearches.value = items.take(10)
        } else {
            // Default sample suggestions for offline operators
            _recentSearches.value = listOf("distress", "Node 477124", "Sector 4")
        }
    }

    private fun persistRecentSearches(items: List<String>) {
        try {
            val serialized = items.joinToString("\u001F")
            prefs?.edit()?.putString("recent_queries", serialized)?.apply()
        } catch (_: Throwable) {
            // Safe fallback when Android context / SharedPreferences are absent
        }
    }

    fun release() {
        searchIndex.removeChangeListener(indexChangeListener)
    }
}
