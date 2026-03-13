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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BasketCheckScreen(
    db: DatabaseManager,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<DrugResult>>(emptyList()) }
    var basket by remember { mutableStateOf<List<BasketDrug>>(emptyList()) }
    var interactions by remember { mutableStateOf<List<InteractionResult>>(emptyList()) }
    var isChecking by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val checker = remember { InteractionChecker(db) }

    fun checkInteractions() {
        if (basket.size < 2) {
            interactions = emptyList()
            return
        }
        isChecking = true
        val drugs = basket.toList()
        scope.launch(Dispatchers.IO) {
            val result = checker.checkInteractions(drugs)
            withContext(Dispatchers.Main) {
                interactions = result
                isChecking = false
            }
        }
    }

    fun addToBasket(drug: DrugResult) {
        if (basket.any { it.brand == drug.brandName }) return
        scope.launch(Dispatchers.IO) {
            val resolved = db.resolveDrug(drug.brandName)
            if (resolved != null) {
                withContext(Dispatchers.Main) {
                    basket = basket + resolved
                    searchText = ""
                    suggestions = emptyList()
                    showSuggestions = false
                    if (basket.size >= 2) checkInteractions()
                }
            }
        }
    }

    fun removeFromBasket(drug: BasketDrug) {
        basket = basket.filter { it.id != drug.id }
        if (basket.size >= 2) checkInteractions() else interactions = emptyList()
    }

    fun debounceSearch(query: String) {
        debounceJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            suggestions = emptyList()
            showSuggestions = false
            return
        }
        debounceJob = scope.launch {
            delay(200)
            val results = withContext(Dispatchers.IO) { db.searchDrugs(trimmed) }
            suggestions = results
            showSuggestions = results.isNotEmpty()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text("Interaktions-Check") },
            navigationIcon = {
                IconButton(onClick = onShowSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            },
            actions = {
                if (basket.isNotEmpty()) {
                    TextButton(onClick = {
                        basket = emptyList()
                        interactions = emptyList()
                    }) {
                        Text("Leeren")
                    }
                }
            }
        )

        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                debounceSearch(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Medikament suchen (Markenname oder Wirkstoff)...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchText = ""
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
            // Suggestions list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(suggestions, key = { it.id }) { drug ->
                    ListItem(
                        headlineContent = { Text(drug.brandName) },
                        supportingContent = { Text("[${drug.atcCode}] ${drug.substances}", fontSize = 12.sp) },
                        modifier = Modifier.clickable { addToBasket(drug) }
                    )
                    HorizontalDivider()
                }
            }
        } else {
            // Basket chips
            if (basket.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Medikamente im Warenkorb:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (drug in basket) {
                            AssistChip(
                                onClick = { removeFromBasket(drug) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(drug.brand, fontSize = 13.sp)
                                        Text(
                                            " [${drug.atcCode}]",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Entfernen",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                                    labelColor = Color(0xFF2196F3)
                                )
                            )
                        }
                    }
                }
            }

            // Results
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isChecking) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Interaktionen werden geprüft...")
                            }
                        }
                    }
                } else if (basket.size >= 2 && interactions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Text("Keine Interaktionen gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(interactions, key = { it.id }) { ix ->
                        InteractionCard(
                            severityScore = ix.severityScore,
                            drugLabel = "${ix.drugA} [${ix.drugAAtc}]${routeBadge(ix.drugARoute)} \u2194 ${ix.drugB} [${ix.drugBAtc}]${routeBadge(ix.drugBRoute)}",
                            severityIndicator = ix.severityIndicator,
                            severityLabel = ix.severityLabel,
                            interactionType = ix.interactionType,
                            explanation = ix.explanation,
                            description = ix.description,
                            source = ix.source,
                            fiHint = ix.fiHint,
                            comboHint = ix.comboHint
                        )
                    }
                }
            }
        }
    }
}
