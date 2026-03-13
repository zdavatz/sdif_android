package org.oddb.sdif.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.oddb.sdif.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalSearchScreen(
    db: DatabaseManager,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<TermSuggestion>>(emptyList()) }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var shownCount by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var skipNextSuggest by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val pageSize = 50

    fun performSearch() {
        val term = searchText.trim()
        if (term.length < 2) return
        showSuggestions = false
        isSearching = true
        shownCount = 0
        scope.launch(Dispatchers.IO) {
            val (total, items) = db.searchInteractions(term, pageSize, 0)
            withContext(Dispatchers.Main) {
                totalCount = total
                results = items
                shownCount = items.size
                isSearching = false
            }
        }
    }

    fun loadMore() {
        val term = searchText.trim()
        if (term.isEmpty()) return
        isLoadingMore = true
        val currentOffset = shownCount
        scope.launch(Dispatchers.IO) {
            val (_, items) = db.searchInteractions(term, pageSize, currentOffset)
            withContext(Dispatchers.Main) {
                results = results + items
                shownCount += items.size
                isLoadingMore = false
            }
        }
    }

    fun debounceSuggest(query: String) {
        debounceJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            suggestions = emptyList()
            showSuggestions = false
            return
        }
        debounceJob = scope.launch {
            delay(200)
            val result = withContext(Dispatchers.IO) { db.suggestTerms(trimmed) }
            suggestions = result
            showSuggestions = result.isNotEmpty()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Klinische Suche") },
            navigationIcon = {
                IconButton(onClick = onShowSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            }
        )

        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                if (skipNextSuggest) {
                    skipNextSuggest = false
                } else {
                    debounceSuggest(it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Klinischer Suchbegriff (z.B. QT-Verlängerung)...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchText = ""
                        results = emptyList()
                        totalCount = 0
                        shownCount = 0
                        suggestions = emptyList()
                        showSuggestions = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Löschen")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(suggestions, key = { it.id }) { suggestion ->
                    ListItem(
                        headlineContent = { Text(suggestion.term) },
                        trailingContent = {
                            Text("${suggestion.count} Treffer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.clickable {
                            skipNextSuggest = true
                            searchText = suggestion.term
                            showSuggestions = false
                            suggestions = emptyList()
                            performSearch()
                        }
                    )
                    HorizontalDivider()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Suche...")
                            }
                        }
                    }
                } else if (searchText.isNotEmpty() && results.isEmpty() && shownCount == 0) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Keine Ergebnisse für \"$searchText\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (totalCount > 0) {
                        item {
                            val countText = if (totalCount > shownCount)
                                "$totalCount Interaktionen gefunden ($shownCount angezeigt)"
                            else "$totalCount Interaktionen gefunden"
                            Text(countText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    items(results, key = { it.id }) { r ->
                        val brandInfo = if (r.interactingBrand.isEmpty()) "" else " (${r.interactingBrand})"
                        InteractionCard(
                            severityScore = r.severityScore,
                            drugLabel = "${r.drugBrand}${routeBadge(r.drugRoute)} \u2194 ${r.interactingSubstance}$brandInfo${routeBadge(r.interactingRoute)}",
                            severityIndicator = r.severityIndicator,
                            severityLabel = r.severityLabel,
                            interactionType = null,
                            explanation = null,
                            description = r.description,
                            source = r.source,
                            fiHint = null,
                            comboHint = null
                        )
                    }

                    if (shownCount in 1 until totalCount) {
                        item {
                            Button(
                                onClick = { loadMore() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoadingMore
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("mehr anzeigen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
