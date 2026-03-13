package org.oddb.sdif.data

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class DrugResult(
    val id: String = UUID.randomUUID().toString(),
    val brandName: String,
    val atcCode: String,
    val substances: String
)

data class BasketDrug(
    val id: String = UUID.randomUUID().toString(),
    val brand: String,
    val atcCode: String,
    val substances: List<String>,
    val interactionsText: String,
    val route: String,
    val comboHint: String
)

data class InteractionResult(
    val id: String = UUID.randomUUID().toString(),
    val drugA: String,
    val drugAAtc: String,
    val drugARoute: String,
    val drugB: String,
    val drugBAtc: String,
    val drugBRoute: String,
    val interactionType: String,
    val severityScore: Int,
    val severityLabel: String,
    val severityIndicator: String,
    val keyword: String,
    val description: String,
    val explanation: String,
    val source: String,
    var comboHint: String,
    var fiHint: String = ""
)

data class SearchResultItem(
    val id: String = UUID.randomUUID().toString(),
    val drugBrand: String,
    val drugRoute: String,
    val interactingSubstance: String,
    val interactingBrand: String,
    val interactingRoute: String,
    val severityScore: Int,
    val severityLabel: String,
    val severityIndicator: String,
    val description: String,
    val source: String
)

data class TermSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val term: String,
    val count: Int
)

data class ClassInteractionRow(
    val id: String = UUID.randomUUID().toString(),
    val atcPrefix: String,
    val description: String,
    val drugsInClass: Int,
    val drugsMentioning: Int,
    val potentialPairs: Int,
    val topKeyword: String
)

data class ClassHit(
    val classKeyword: String,
    val context: String
)

data class CypRule(
    val enzyme: String,
    val textPatterns: List<String>,
    val inhibitorAtc: List<String>,
    val inhibitorSubstances: List<String>,
    val inducerAtc: List<String>,
    val inducerSubstances: List<String>
)

val Int.severityIndicator: String
    get() = when (this) {
        3 -> "###"
        2 -> "##"
        1 -> "#"
        else -> "-"
    }

val Int.severityLabel: String
    get() = when (this) {
        3 -> "Kontraindiziert"
        2 -> "Schwerwiegend"
        1 -> "Vorsicht"
        else -> "Keine Einstufung"
    }

val Int.severityColor: Color
    get() = when (this) {
        3 -> Color.Red
        2 -> Color(0xFFFF9800) // Orange
        1 -> Color(0xFF2196F3) // Blue
        else -> Color.Gray
    }

fun routePriority(routeA: String, routeB: String): Int {
    fun single(r: String): Int = when (r) {
        "", "p.o.", "i.v." -> 0
        "s.c.", "i.m." -> 1
        "inhalativ" -> 2
        "nasal", "rektal" -> 3
        "ophthalm.", "otisch" -> 4
        "topisch" -> 5
        else -> 3
    }
    return maxOf(single(routeA), single(routeB))
}

fun routeBadge(route: String): String =
    if (route.isEmpty()) "" else " ($route)"
