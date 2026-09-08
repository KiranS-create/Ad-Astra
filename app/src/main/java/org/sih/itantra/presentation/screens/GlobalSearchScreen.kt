package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.search.LocalSearchRepository
import org.sih.itantra.core.search.SearchFilterType
import org.sih.itantra.core.search.SearchResult
import org.sih.itantra.core.search.SearchResultType
import org.sih.itantra.presentation.components.SearchFilterChip
import org.sih.itantra.presentation.components.SearchResultCard
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * iTantra Tactical Offline Global Search Screen.
 *
 * Implements deterministic search across local messages, nodes, callsigns, and contacts.
 * Fully offline; zero cloud services; zero speech model loading.
 */
@Composable
fun GlobalSearchScreen(
    searchRepository: LocalSearchRepository = rememberLocalSearchRepository(),
    onBack: () -> Unit = {},
    onOpenChat: (peerId: String) -> Unit = {},
    onOpenContact: (nodeId: Int) -> Unit = {},
    onInspectMessage: (messageId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val query by searchRepository.searchQuery.collectAsState()
    val filterState by searchRepository.filterState.collectAsState()
    val results by searchRepository.searchResults.collectAsState()
    val groupedResults by searchRepository.groupedResults.collectAsState()
    val recentSearches by searchRepository.recentSearches.collectAsState()

    var selectedActionTarget by remember { mutableStateOf<SearchResult?>(null) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showPriorityPicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Tactical Header Bar
            SearchTopHeader(
                onBack = onBack,
                resultCount = results.size,
                isSearching = query.isNotBlank()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Search Input Field
            SearchInputField(
                query = query,
                onQueryChange = { searchRepository.setQuery(it) },
                onSearchCommit = { searchRepository.recordSearchCommit(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Filter Controls (Types, Languages, Priorities)
            FilterBar(
                activeType = filterState.filterType,
                selectedLanguage = filterState.selectedLanguage,
                selectedPriority = filterState.selectedPriority,
                onSelectType = { searchRepository.setFilterType(it) },
                onOpenLanguagePicker = { showLanguagePicker = true },
                onOpenPriorityPicker = { showPriorityPicker = true },
                onClearFilters = { searchRepository.clearFilters() },
                isFiltering = filterState.isFiltering,
                resultCounts = groupedResults.mapValues { it.value.size }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Content Area: Idle State vs Results vs Empty State
            if (query.isBlank()) {
                // STATE A — SEARCH IDLE
                SearchIdleContent(
                    recentSearches = recentSearches,
                    onSelectRecent = {
                        searchRepository.setQuery(it)
                        searchRepository.recordSearchCommit(it)
                    },
                    onRemoveRecent = { searchRepository.removeRecentSearch(it) },
                    onClearAllRecent = { searchRepository.clearRecentSearches() },
                    onSelectTopic = { topic ->
                        searchRepository.setQuery(topic)
                        searchRepository.recordSearchCommit(topic)
                    }
                )
            } else if (results.isEmpty()) {
                // STATE D — NO LOCAL MATCHES
                SearchNoResultsContent(
                    query = query,
                    onClearQuery = { searchRepository.setQuery("") }
                )
            } else {
                // STATE B & E — SEARCH RESULTS (Grouped by Category)
                SearchResultsList(
                    groupedResults = groupedResults,
                    onCardClick = { result -> selectedActionTarget = result },
                    onOpenChat = onOpenChat,
                    onOpenContact = onOpenContact,
                    onInspectMessage = onInspectMessage
                )
            }
        }

        // STATE C — ACTION MODAL DIALOG
        if (selectedActionTarget != null) {
            SearchResultActionDialog(
                result = selectedActionTarget!!,
                onDismiss = { selectedActionTarget = null },
                onOpenChat = { target ->
                    selectedActionTarget = null
                    onOpenChat(target)
                },
                onOpenContact = { nodeId ->
                    selectedActionTarget = null
                    onOpenContact(nodeId)
                },
                onInspectMessage = { msgId ->
                    selectedActionTarget = null
                    onInspectMessage(msgId)
                }
            )
        }

        // Language Filter Selection Dialog
        if (showLanguagePicker) {
            LanguageFilterDialog(
                selected = filterState.selectedLanguage,
                onSelect = {
                    searchRepository.setLanguageFilter(it)
                    showLanguagePicker = false
                },
                onDismiss = { showLanguagePicker = false }
            )
        }

        // Priority Filter Selection Dialog
        if (showPriorityPicker) {
            PriorityFilterDialog(
                selected = filterState.selectedPriority,
                onSelect = {
                    searchRepository.setPriorityFilter(it)
                    showPriorityPicker = false
                },
                onDismiss = { showPriorityPicker = false }
            )
        }
    }
}

@Composable
private fun rememberLocalSearchRepository(): LocalSearchRepository {
    val context = LocalContext.current
    return remember { LocalSearchRepository(context) }
}

@Composable
private fun SearchTopHeader(
    onBack: () -> Unit,
    resultCount: Int,
    isSearching: Boolean
) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(radioColors.capsule)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = radioColors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "GLOBAL SEARCH",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(radioColors.capsule)
                            .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSearching) "$resultCount MATCHES" else "OFFLINE INDEX",
                            color = if (isSearching) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = "LOCAL ON-DEVICE TACTICAL REPOSITORY",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchCommit: (String) -> Unit
) {
    val radioColors = LocalRadioColors.current

    OutlinedTextField(
        value = query,
        onValueChange = {
            onQueryChange(it)
            if (it.length >= 3) {
                onSearchCommit(it)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Search field: messages, nodes, callsigns" },
        placeholder = {
            Text(
                text = "Search messages, nodes, callsigns...",
                color = radioColors.textTertiary,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Icon",
                tint = radioColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Search",
                        tint = radioColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            unfocusedBorderColor = radioColors.border.copy(alpha = 0.6f),
            focusedContainerColor = radioColors.surface,
            unfocusedContainerColor = radioColors.surface,
            focusedTextColor = radioColors.textPrimary,
            unfocusedTextColor = radioColors.textPrimary
        )
    )
}

@Composable
private fun FilterBar(
    activeType: SearchFilterType,
    selectedLanguage: IndicLanguage?,
    selectedPriority: MessagePriority?,
    onSelectType: (SearchFilterType) -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenPriorityPicker: () -> Unit,
    onClearFilters: () -> Unit,
    isFiltering: Boolean,
    resultCounts: Map<SearchResultType, Int>
) {
    val radioColors = LocalRadioColors.current
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Filter Chips for Types
        SearchFilterType.entries.forEach { type ->
            val count = when (type) {
                SearchFilterType.ALL -> resultCounts.values.sum()
                SearchFilterType.MESSAGES -> resultCounts[SearchResultType.MESSAGE]
                SearchFilterType.NODES -> resultCounts[SearchResultType.NODE]
                SearchFilterType.CONTACTS -> resultCounts[SearchResultType.CONTACT]
                SearchFilterType.CONVERSATIONS -> resultCounts[SearchResultType.CONVERSATION]
            }

            SearchFilterChip(
                label = type.label,
                isSelected = activeType == type,
                onClick = { onSelectType(type) },
                count = if (count != null && count > 0) count else null
            )
        }

        // Language Filter Capsule
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedLanguage != null) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.capsule)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { onOpenLanguagePicker() }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = selectedLanguage?.displayName?.uppercase() ?: "LANGUAGE ▾",
                color = if (selectedLanguage != null) Color.White else radioColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Priority Filter Capsule
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedPriority != null) radioColors.alert else radioColors.capsule)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { onOpenPriorityPicker() }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = selectedPriority?.label ?: "PRIORITY ▾",
                color = if (selectedPriority != null) Color.White else radioColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (isFiltering) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.capsule)
                    .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onClearFilters() }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "RESET",
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun SearchIdleContent(
    recentSearches: List<String>,
    onSelectRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAllRecent: () -> Unit,
    onSelectTopic: (String) -> Unit
) {
    val radioColors = LocalRadioColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT SEARCHES",
                    color = radioColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "CLEAR ALL",
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onClearAllRecent() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recentSearches.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(radioColors.surface)
                            .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { onSelectRecent(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = radioColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item,
                                color = radioColors.textPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove $item",
                            tint = radioColors.textTertiary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemoveRecent(item) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Quick Topic Suggestions
        Text(
            text = "QUICK TACTICAL QUERIES",
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopicPill(
                label = "⚡ DISTRESS",
                color = radioColors.alert,
                onClick = { onSelectTopic("distress") }
            )
            TopicPill(
                label = "📡 RELAYED",
                color = Color(0xFF0288D1),
                onClick = { onSelectTopic("relayed") }
            )
            TopicPill(
                label = "✓ ACK CONFIRMED",
                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                onClick = { onSelectTopic("ack") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy & Offline Assurance Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.capsule.copy(alpha = 0.5f))
                .border(1.dp, radioColors.border.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = radioColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline Tactical Index: Operates purely on records stored on this physical handset. Zero cloud sync.",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TopicPill(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.surface)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SearchNoResultsContent(
    query: String,
    onClearQuery: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = "No Results",
            tint = radioColors.textTertiary,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NO LOCAL MATCHES",
            color = radioColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No stored local records match \"$query\".\nSearch only covers information stored on this device. Nodes outside radio range and not logged locally cannot be discovered through search.",
            color = radioColors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                .clickable { onClearQuery() }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "CLEAR SEARCH",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    groupedResults: Map<SearchResultType, List<SearchResult>>,
    onCardClick: (SearchResult) -> Unit,
    onOpenChat: (peerId: String) -> Unit,
    onOpenContact: (nodeId: Int) -> Unit,
    onInspectMessage: (messageId: String) -> Unit
) {
    val radioColors = LocalRadioColors.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        groupedResults.forEach { (type, items) ->
            item(key = "header_${type.name}") {
                Text(
                    text = "${type.label.uppercase()}S (${items.size})",
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }

            items(items, key = { it.resultId }) { item ->
                SearchResultCard(
                    result = item,
                    onClick = { onCardClick(item) },
                    onOpenChat = onOpenChat,
                    onOpenContact = onOpenContact,
                    onInspectMessage = onInspectMessage
                )
            }
        }
    }
}

/**
 * Result Action Boundary Modal Dialog.
 */
@Composable
private fun SearchResultActionDialog(
    result: SearchResult,
    onDismiss: () -> Unit,
    onOpenChat: (peerId: String) -> Unit,
    onOpenContact: (nodeId: Int) -> Unit,
    onInspectMessage: (messageId: String) -> Unit
) {
    val radioColors = LocalRadioColors.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = result.title,
                color = radioColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!result.subtitle.isNullOrBlank()) {
                    Text(
                        text = result.subtitle,
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (!result.snippet.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(radioColors.capsule)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = result.snippet,
                            color = radioColors.textPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action: Open Chat
                val chatTarget = result.nodeId?.let { "Node #$it" } ?: result.title
                ActionModalRow(
                    label = "OPEN CONVERSATION",
                    icon = Icons.Default.Chat,
                    onClick = { onOpenChat(chatTarget) }
                )

                // Action: View Contact (if node ID available)
                if (result.nodeId != null) {
                    ActionModalRow(
                        label = "VIEW TACTICAL CONTACT",
                        icon = Icons.Default.Person,
                        onClick = { onOpenContact(result.nodeId) }
                    )
                }

                // Action: Inspect Message Details (if message)
                if (result.resultType == SearchResultType.MESSAGE) {
                    val rawId = result.resultId.removePrefix("msg_")
                    ActionModalRow(
                        label = "INSPECT TRANSMISSION PACKET",
                        icon = Icons.Default.Info,
                        onClick = { onInspectMessage(rawId) }
                    )
                }

                // Action: Copy
                if (!result.snippet.isNullOrBlank()) {
                    ActionModalRow(
                        label = "COPY MESSAGE SNIPPET",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(result.snippet))
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CLOSE",
                    color = radioColors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = radioColors.surface
    )
}

@Composable
private fun ActionModalRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.background)
            .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = radioColors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LanguageFilterDialog(
    selected: IndicLanguage?,
    onSelect: (IndicLanguage?) -> Unit,
    onDismiss: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "FILTER BY LANGUAGE",
                color = radioColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // All option
                FilterSelectionRow(
                    label = "ALL LANGUAGES",
                    isSelected = selected == null,
                    onClick = { onSelect(null) }
                )
                IndicLanguage.entries.forEach { lang ->
                    FilterSelectionRow(
                        label = "${lang.displayName} (${lang.nativeName})",
                        isSelected = selected == lang,
                        onClick = { onSelect(lang) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCEL", color = radioColors.textSecondary)
            }
        },
        containerColor = radioColors.surface
    )
}

@Composable
private fun PriorityFilterDialog(
    selected: MessagePriority?,
    onSelect: (MessagePriority?) -> Unit,
    onDismiss: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "FILTER BY PRIORITY",
                color = radioColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterSelectionRow(
                    label = "ALL PRIORITIES",
                    isSelected = selected == null,
                    onClick = { onSelect(null) }
                )
                MessagePriority.entries.forEach { prio ->
                    FilterSelectionRow(
                        label = prio.label,
                        isSelected = selected == prio,
                        onClick = { onSelect(prio) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCEL", color = radioColors.textSecondary)
            }
        },
        containerColor = radioColors.surface
    )
}

@Composable
private fun FilterSelectionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) (if (radioColors.isDark) radioColors.sage.copy(alpha = 0.2f) else radioColors.forest.copy(alpha = 0.1f)) else radioColors.background)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
        if (isSelected) {
            Text(
                text = "✓",
                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
