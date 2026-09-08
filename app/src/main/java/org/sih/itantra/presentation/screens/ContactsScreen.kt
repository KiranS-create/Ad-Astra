package org.sih.itantra.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactIdentity
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.core.contact.ContactValidator
import org.sih.itantra.core.contact.TacticalContact
import org.sih.itantra.presentation.components.ContactCard
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Contacts Screen for iTantra.
 *
 * Provides a local directory of communication peers and mesh nodes, separating persistent
 * identity from dynamic network reachability state.
 */
@Composable
fun ContactsScreen(
    contactRepository: ContactRepository,
    onOpenChat: (nodeId: Int) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val allContacts by contactRepository.contacts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguageFilter by remember { mutableStateOf<IndicLanguage?>(null) }
    var expandedContactId by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Instant in-memory search and language filtering
    val filteredContacts = remember(allContacts, searchQuery, selectedLanguageFilter) {
        contactRepository.searchContacts(searchQuery, selectedLanguageFilter)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background),
        containerColor = radioColors.background,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                    .clickable { showAddDialog = true }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Contact",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ADD CONTACT",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = radioColors.textPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "TACTICAL CONTACTS",
                            color = radioColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "PEER DIRECTORY & MESH NODES",
                            color = radioColors.textTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Node Count Capsule
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(radioColors.capsule)
                        .border(1.dp, radioColors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${filteredContacts.size} NODES",
                        color = radioColors.sage,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search callsign, node ID, unit...",
                        color = radioColors.textTertiary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = radioColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = radioColors.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = radioColors.sage,
                    unfocusedBorderColor = radioColors.border,
                    focusedTextColor = radioColors.textPrimary,
                    unfocusedTextColor = radioColors.textPrimary,
                    focusedContainerColor = radioColors.surface,
                    unfocusedContainerColor = radioColors.surface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Language Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LanguageFilterChip(
                    label = "ALL",
                    isSelected = selectedLanguageFilter == null,
                    onClick = { selectedLanguageFilter = null }
                )

                IndicLanguage.entries.forEach { lang ->
                    LanguageFilterChip(
                        label = lang.displayName,
                        isSelected = selectedLanguageFilter == lang,
                        onClick = {
                            selectedLanguageFilter = if (selectedLanguageFilter == lang) null else lang
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Contact List or Empty State
            if (filteredContacts.isEmpty()) {
                EmptyContactsView(
                    isSearchActive = searchQuery.isNotBlank() || selectedLanguageFilter != null,
                    onAddContact = { showAddDialog = true },
                    onClearFilters = {
                        searchQuery = ""
                        selectedLanguageFilter = null
                    }
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredContacts, key = { it.nodeId }) { contact ->
                        ContactCard(
                            contact = contact,
                            isExpanded = expandedContactId == contact.nodeId,
                            onToggleExpand = {
                                expandedContactId = if (expandedContactId == contact.nodeId) null else contact.nodeId
                            },
                            onOpenChat = onOpenChat,
                            onDeleteContact = {
                                contactRepository.deleteContact(contact.nodeId)
                            }
                        )
                    }
                }
            }
        }
    }

    // 5. Add Contact Dialog
    if (showAddDialog) {
        AddContactDialog(
            existingNodeIds = allContacts.map { it.nodeId }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { identity ->
                contactRepository.addContact(identity)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun LanguageFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.capsule)
            .border(
                1.dp,
                if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.border,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else radioColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmptyContactsView(
    isSearchActive: Boolean,
    onAddContact: () -> Unit,
    onClearFilters: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(radioColors.capsule)
                .border(1.dp, radioColors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = radioColors.sage,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearchActive) "NO MATCHING CONTACTS" else "NO TRUSTED CONTACTS",
            color = radioColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSearchActive) {
                "No communication peers match your active query or language filter."
            } else {
                "Tactical contacts represent verified iTantra communication peers on the offline mesh.\nAdd a known node ID to track identity and live route telemetry."
            },
            color = radioColors.textTertiary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                .clickable { if (isSearchActive) onClearFilters() else onAddContact() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSearchActive) "CLEAR FILTERS" else "ADD CONTACT",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Tactical Add Contact Dialog with input validation for Node ID and Callsign.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactDialog(
    existingNodeIds: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (ContactIdentity) -> Unit
) {
    val radioColors = LocalRadioColors.current

    var nodeIdText by remember { mutableStateOf("") }
    var callsignText by remember { mutableStateOf("") }
    var unitNameText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var selectedLanguages by remember { mutableStateOf(setOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ADD TACTICAL CONTACT",
                    color = radioColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Register an offline peer identity to monitor mesh presence.",
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Node ID input
                OutlinedTextField(
                    value = nodeIdText,
                    onValueChange = {
                        nodeIdText = it
                        errorMessage = null
                    },
                    label = { Text("Node ID (e.g. 209070)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = radioColors.sage,
                        unfocusedBorderColor = radioColors.border,
                        focusedTextColor = radioColors.textPrimary,
                        unfocusedTextColor = radioColors.textPrimary
                    )
                )

                // Callsign input
                OutlinedTextField(
                    value = callsignText,
                    onValueChange = {
                        callsignText = it
                        errorMessage = null
                    },
                    label = { Text("Callsign (e.g. SQUAD ALPHA)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = radioColors.sage,
                        unfocusedBorderColor = radioColors.border,
                        focusedTextColor = radioColors.textPrimary,
                        unfocusedTextColor = radioColors.textPrimary
                    )
                )

                // Unit Name / Subtitle input
                OutlinedTextField(
                    value = unitNameText,
                    onValueChange = { unitNameText = it },
                    label = { Text("Unit / Display Name (Optional)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = radioColors.sage,
                        unfocusedBorderColor = radioColors.border,
                        focusedTextColor = radioColors.textPrimary,
                        unfocusedTextColor = radioColors.textPrimary
                    )
                )

                // Supported Languages selection
                Text(
                    text = "SUPPORTED LANGUAGES:",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IndicLanguage.entries.forEach { lang ->
                        val isSelected = selectedLanguages.contains(lang)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) radioColors.sage.copy(alpha = 0.2f) else radioColors.capsule)
                                .border(1.dp, if (isSelected) radioColors.sage else radioColors.border, RoundedCornerShape(4.dp))
                                .clickable {
                                    selectedLanguages = if (isSelected) {
                                        if (selectedLanguages.size > 1) selectedLanguages - lang else selectedLanguages
                                    } else {
                                        selectedLanguages + lang
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = lang.displayName,
                                color = if (isSelected) radioColors.sage else radioColors.textTertiary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Error Message if any
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = radioColors.alert,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                            .clickable {
                                val (isNodeValid, nodeErr) = ContactValidator.validateNodeId(nodeIdText)
                                if (!isNodeValid) {
                                    errorMessage = nodeErr
                                    return@clickable
                                }
                                val parsedNodeId = nodeIdText.trim().removePrefix("#").toInt()
                                if (existingNodeIds.contains(parsedNodeId)) {
                                    errorMessage = "A contact with Node #$parsedNodeId already exists"
                                    return@clickable
                                }
                                val (isCallsignValid, callsignErr) = ContactValidator.validateCallsign(callsignText)
                                if (!isCallsignValid) {
                                    errorMessage = callsignErr
                                    return@clickable
                                }

                                onConfirm(
                                    ContactIdentity(
                                        nodeId = parsedNodeId,
                                        callsign = callsignText.trim().uppercase(),
                                        displayName = unitNameText.trim().ifBlank { null },
                                        supportedLanguages = selectedLanguages.toList(),
                                        notes = notesText.trim().ifBlank { null },
                                        authStatus = ContactAuthStatus.UNVERIFIED
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "SAVE CONTACT",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
