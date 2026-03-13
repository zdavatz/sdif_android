package org.oddb.sdif.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oddb.sdif.data.ClassInteractionRow
import org.oddb.sdif.data.DatabaseManager
import java.text.NumberFormat
import java.util.Locale

enum class SortColumn { ATC_PREFIX, DESCRIPTION, DRUGS_IN_CLASS, DRUGS_MENTIONING, POTENTIAL_PAIRS, TOP_KEYWORD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ATCClassScreen(
    db: DatabaseManager,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var classes by remember { mutableStateOf<List<ClassInteractionRow>>(emptyList()) }
    var totalPairs by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var sortColumn by remember { mutableStateOf(SortColumn.POTENTIAL_PAIRS) }
    var sortAscending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val sortedClasses = remember(classes, sortColumn, sortAscending) {
        val comparator = when (sortColumn) {
            SortColumn.ATC_PREFIX -> compareBy<ClassInteractionRow> { it.atcPrefix }
            SortColumn.DESCRIPTION -> compareBy { it.description }
            SortColumn.DRUGS_IN_CLASS -> compareBy { it.drugsInClass }
            SortColumn.DRUGS_MENTIONING -> compareBy { it.drugsMentioning }
            SortColumn.POTENTIAL_PAIRS -> compareBy { it.potentialPairs }
            SortColumn.TOP_KEYWORD -> compareBy { it.topKeyword }
        }
        if (sortAscending) classes.sortedWith(comparator)
        else classes.sortedWith(comparator.reversed())
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val (total, classList) = db.loadClassInteractions()
            withContext(Dispatchers.Main) {
                totalPairs = total
                classes = classList
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("ATC-Klassen") },
            navigationIcon = {
                IconButton(onClick = onShowSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            }
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("ATC-Klassen werden analysiert...")
                    Text("Kann einige Sekunden dauern", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val horizontalScrollState = rememberScrollState()
            val numberFormat = remember { NumberFormat.getInstance(Locale("de", "CH")).apply { isGroupingUsed = true } }

            fun formatted(value: Int): String = numberFormat.format(value)

            fun toggleSort(col: SortColumn) {
                if (sortColumn == col) {
                    sortAscending = !sortAscending
                } else {
                    sortColumn = col
                    sortAscending = col == SortColumn.ATC_PREFIX || col == SortColumn.DESCRIPTION || col == SortColumn.TOP_KEYWORD
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        HeaderCell("ATC-Code", 80.dp, SortColumn.ATC_PREFIX, sortColumn, sortAscending) { toggleSort(it) }
                        HeaderCell("Klasse", 180.dp, SortColumn.DESCRIPTION, sortColumn, sortAscending) { toggleSort(it) }
                        HeaderCell("Medikamente", 110.dp, SortColumn.DRUGS_IN_CLASS, sortColumn, sortAscending) { toggleSort(it) }
                        HeaderCell("Erwähnungen", 110.dp, SortColumn.DRUGS_MENTIONING, sortColumn, sortAscending) { toggleSort(it) }
                        HeaderCell("Pot. Paare", 110.dp, SortColumn.POTENTIAL_PAIRS, sortColumn, sortAscending) { toggleSort(it) }
                        HeaderCell("Top-Keyword", 160.dp, SortColumn.TOP_KEYWORD, sortColumn, sortAscending) { toggleSort(it) }
                    }
                    HorizontalDivider()
                }

                items(sortedClasses, key = { it.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(row.atcPrefix, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(80.dp).padding(horizontal = 8.dp))
                        Text(row.description, fontSize = 13.sp, modifier = Modifier.width(180.dp).padding(horizontal = 8.dp))
                        Text(formatted(row.drugsInClass), fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(110.dp).padding(horizontal = 8.dp))
                        Text(formatted(row.drugsMentioning), fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(110.dp).padding(horizontal = 8.dp))
                        Text(formatted(row.potentialPairs), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(110.dp).padding(horizontal = 8.dp))
                        Text(row.topKeyword, fontSize = 13.sp, modifier = Modifier.width(160.dp).padding(horizontal = 8.dp))
                    }
                    HorizontalDivider()
                }

                // Total
                item {
                    Text(
                        "Total: ${formatted(totalPairs)} potenzielle Klassen-Interaktionspaare in ${classes.size} ATC-Klassen",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    title: String,
    width: Dp,
    column: SortColumn,
    currentSort: SortColumn,
    ascending: Boolean,
    onSort: (SortColumn) -> Unit
) {
    TextButton(
        onClick = { onSort(column) },
        modifier = Modifier.width(width),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            if (currentSort == column) {
                Icon(
                    if (ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
