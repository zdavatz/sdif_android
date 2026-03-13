package org.oddb.sdif.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class DatabaseManager private constructor(private val context: Context) {

    private var db: SQLiteDatabase? = null

    init {
        openDatabase()
    }

    companion object {
        @Volatile
        private var instance: DatabaseManager? = null

        fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager(context.applicationContext).also { instance = it }
            }
        }

        fun getDbFile(context: Context): File =
            File(context.filesDir, "interactions.db")

        fun atcClassDescription(prefix: String): String = when (prefix) {
            "B01A" -> "Antikoagulantien"
            "B01AC" -> "Thrombozytenaggregationshemmer"
            "M01A" -> "NSAR (NSAIDs)"
            "N02B" -> "Analgetika / Antipyretika"
            "N02A" -> "Opioide"
            "C09A" -> "ACE-Hemmer"
            "C09B" -> "ACE-Hemmer (Kombination)"
            "C09C" -> "Sartane (AT1-Antagonisten)"
            "C09D" -> "Sartane (Kombination)"
            "C07" -> "Beta-Blocker"
            "C08" -> "Calciumkanalblocker"
            "C03" -> "Diuretika"
            "C03C" -> "Schleifendiuretika"
            "C03A" -> "Thiazide"
            "C01A" -> "Herzglykoside"
            "C01B" -> "Antiarrhythmika"
            "C10A" -> "Statine"
            "N06AB" -> "SSRIs"
            "N06A" -> "Antidepressiva"
            "A10" -> "Antidiabetika"
            "H02" -> "Corticosteroide"
            "L04" -> "Immunsuppressiva"
            "L01" -> "Antineoplastika"
            "N03" -> "Antiepileptika"
            "N05A" -> "Antipsychotika"
            "N05B" -> "Anxiolytika"
            "N05C" -> "Sedativa / Hypnotika"
            "J01" -> "Antibiotika"
            "J01FA" -> "Makrolide"
            "J01MA" -> "Fluorchinolone"
            "J02A" -> "Antimykotika"
            "J05A" -> "Antivirale"
            "A02BC" -> "PPI (Protonenpumpenhemmer)"
            "A02B" -> "Ulkusmittel"
            "G03A" -> "Hormonale Kontrazeptiva"
            "N07" -> "Nervensystem (andere)"
            "R03" -> "Bronchodilatatoren"
            "M04" -> "Gichtmittel"
            "B03" -> "Eisenpräparate"
            "L02BA" -> "SERMs (Tamoxifen)"
            "L02B" -> "Hormonantagonisten"
            "V03AB" -> "Antidota"
            "M03A" -> "Muskelrelaxantien"
            else -> ""
        }

        fun atcClassDescriptionForCode(atcCode: String): String {
            val prefixes = listOf(
                "B01AC", "B01A", "M01A", "N02B", "N02A", "C09A", "C09B", "C09C", "C09D",
                "C07", "C08", "C03C", "C03A", "C03", "C01A", "C01B", "C10A", "N06AB", "N06A",
                "A10", "H02", "L04", "L01", "N03", "N05A", "N05B", "N05C",
                "J01FA", "J01MA", "J01", "J02A", "J05A", "A02BC", "A02B", "G03A", "N07", "R03",
                "M04", "B03", "L02BA", "L02B", "V03AB", "M03A"
            )
            for (prefix in prefixes) {
                if (atcCode.startsWith(prefix)) {
                    return atcClassDescription(prefix)
                }
            }
            return ""
        }
    }

    private fun openDatabase() {
        val dbFile = getDbFile(context)
        if (dbFile.exists()) {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }
    }

    fun isDatabaseAvailable(): Boolean = db != null

    fun reloadDatabase() {
        db?.close()
        db = null
        openDatabase()
    }

    // MARK: - Drug Search

    fun searchDrugs(query: String): List<DrugResult> {
        val db = db ?: return emptyList()
        if (query.length < 2) return emptyList()
        val pattern = "%$query%"
        val sql = """
            SELECT DISTINCT brand_name, atc_code, active_substances FROM drugs
            WHERE brand_name LIKE ?1 OR active_substances LIKE ?1
            ORDER BY brand_name LIMIT 20
        """.trimIndent()
        val results = mutableListOf<DrugResult>()
        db.rawQuery(sql, arrayOf(pattern)).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    DrugResult(
                        brandName = cursor.getString(0) ?: "",
                        atcCode = cursor.getString(1) ?: "",
                        substances = cursor.getString(2) ?: ""
                    )
                )
            }
        }
        return results
    }

    fun searchDrugByATC(atc: String): DrugResult? {
        val db = db ?: return null
        if (atc.isEmpty()) return null
        val sql = """
            SELECT brand_name, atc_code, active_substances FROM drugs
            WHERE atc_code = ?1 ORDER BY length(interactions_text) DESC LIMIT 1
        """.trimIndent()
        db.rawQuery(sql, arrayOf(atc)).use { cursor ->
            if (cursor.moveToNext()) {
                return DrugResult(
                    brandName = cursor.getString(0) ?: "",
                    atcCode = cursor.getString(1) ?: "",
                    substances = cursor.getString(2) ?: ""
                )
            }
        }
        return null
    }

    // MARK: - Resolve Drug for Basket

    fun resolveDrug(input: String): BasketDrug? {
        val db = db ?: return null
        val pattern = "%$input%"

        // Try brand name first
        val sql1 = """
            SELECT brand_name, active_substances, atc_code, interactions_text, COALESCE(route, ''), COALESCE(combo_hint, '')
            FROM drugs WHERE brand_name LIKE ?1 ORDER BY length(interactions_text) DESC
        """.trimIndent()
        db.rawQuery(sql1, arrayOf(pattern)).use { cursor ->
            if (cursor.moveToNext()) {
                return basketDrugFromCursor(cursor)
            }
        }

        // Try substance name
        val sql2 = """
            SELECT DISTINCT d.brand_name, d.active_substances, d.atc_code, d.interactions_text,
                   COALESCE(d.route, ''), COALESCE(d.combo_hint, '')
            FROM substance_brand_map s JOIN drugs d ON d.brand_name = s.brand_name
            WHERE s.substance LIKE ?1 ORDER BY length(d.interactions_text) DESC LIMIT 1
        """.trimIndent()
        val patternLower = "%${input.lowercase()}%"
        db.rawQuery(sql2, arrayOf(patternLower)).use { cursor ->
            if (cursor.moveToNext()) {
                return basketDrugFromCursor(cursor)
            }
        }

        return null
    }

    private fun basketDrugFromCursor(cursor: android.database.Cursor): BasketDrug {
        val brand = cursor.getString(0) ?: ""
        val substancesStr = cursor.getString(1) ?: ""
        val atcCode = cursor.getString(2) ?: ""
        val interactionsText = cursor.getString(3) ?: ""
        val route = cursor.getString(4) ?: ""
        val comboHint = cursor.getString(5) ?: ""
        val substances = substancesStr.split(",").map { it.trim().lowercase() }
        return BasketDrug(
            brand = brand, atcCode = atcCode, substances = substances,
            interactionsText = interactionsText, route = route, comboHint = comboHint
        )
    }

    // MARK: - Substance Match Interactions

    fun findSubstanceInteractions(drugBrand: String, substance: String): List<Triple<String, Int, String>> {
        val db = db ?: return emptyList()
        val sql = "SELECT description, severity_score, severity_label FROM interactions WHERE drug_brand = ?1 AND interacting_substance = ?2"
        val results = mutableListOf<Triple<String, Int, String>>()
        db.rawQuery(sql, arrayOf(drugBrand, substance)).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    Triple(
                        cursor.getString(0) ?: "",
                        cursor.getInt(1),
                        cursor.getString(2) ?: ""
                    )
                )
            }
        }
        return results
    }

    // MARK: - EPha Interactions

    data class EphaResult(
        val riskClass: String,
        val riskLabel: String,
        val effect: String,
        val mechanism: String,
        val measures: String,
        val severityScore: Int,
        val title: String
    )

    fun findEphaInteraction(atc1: String, atc2: String): EphaResult? {
        val db = db ?: return null
        val sql = """
            SELECT risk_class, risk_label, effect, mechanism, measures, severity_score, title
            FROM epha_interactions WHERE (atc1 = ?1 AND atc2 = ?2) OR (atc1 = ?2 AND atc2 = ?1) LIMIT 1
        """.trimIndent()
        db.rawQuery(sql, arrayOf(atc1, atc2)).use { cursor ->
            if (cursor.moveToNext()) {
                return EphaResult(
                    riskClass = cursor.getString(0) ?: "",
                    riskLabel = cursor.getString(1) ?: "",
                    effect = cursor.getString(2) ?: "",
                    mechanism = cursor.getString(3) ?: "",
                    measures = cursor.getString(4) ?: "",
                    severityScore = cursor.getInt(5),
                    title = cursor.getString(6) ?: ""
                )
            }
        }
        return null
    }

    // MARK: - Class Keywords

    fun loadClassKeywords(): List<Pair<String, List<String>>> {
        val db = db ?: return emptyList()
        val sql = "SELECT atc_prefix, keyword FROM class_keywords ORDER BY atc_prefix"
        val result = mutableListOf<Pair<String, MutableList<String>>>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val prefix = cursor.getString(0) ?: ""
                val keyword = cursor.getString(1) ?: ""
                val last = result.lastOrNull()
                if (last != null && last.first == prefix) {
                    last.second.add(keyword)
                } else {
                    result.add(Pair(prefix, mutableListOf(keyword)))
                }
            }
        }
        return result.map { Pair(it.first, it.second.toList()) }
    }

    // MARK: - CYP Rules

    fun loadCypRules(): List<CypRule> {
        val db = db ?: return emptyList()
        val sql = "SELECT enzyme, text_pattern, role, atc_prefix, substance FROM cyp_rules ORDER BY enzyme"
        val map = mutableMapOf<String, CypRule>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val enzyme = cursor.getString(0) ?: ""
                val textPattern = cursor.getString(1) ?: ""
                val role = cursor.getString(2) ?: ""
                val atcPrefix = if (cursor.isNull(3)) null else cursor.getString(3)
                val substance = if (cursor.isNull(4)) null else cursor.getString(4)

                val rule = map[enzyme] ?: CypRule(enzyme, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

                val patterns = rule.textPatterns.toMutableList()
                if (textPattern !in patterns) patterns.add(textPattern)

                val iAtc = rule.inhibitorAtc.toMutableList()
                val iSubst = rule.inhibitorSubstances.toMutableList()
                val dAtc = rule.inducerAtc.toMutableList()
                val dSubst = rule.inducerSubstances.toMutableList()

                if (role == "inhibitor") {
                    if (atcPrefix != null && atcPrefix !in iAtc) iAtc.add(atcPrefix)
                    if (substance != null && substance !in iSubst) iSubst.add(substance)
                } else if (role == "inducer") {
                    if (atcPrefix != null && atcPrefix !in dAtc) dAtc.add(atcPrefix)
                    if (substance != null && substance !in dSubst) dSubst.add(substance)
                }

                map[enzyme] = CypRule(enzyme, patterns, iAtc, iSubst, dAtc, dSubst)
            }
        }
        return map.values.toList()
    }

    // MARK: - Clinical Search

    fun searchInteractions(term: String, limit: Int, offset: Int): Pair<Int, List<SearchResultItem>> {
        val db = db ?: return Pair(0, emptyList())
        val pattern = "%$term%"

        // Count FI total
        var total = 0
        db.rawQuery("SELECT COUNT(*) FROM interactions WHERE description LIKE ?1", arrayOf(pattern)).use { cursor ->
            if (cursor.moveToNext()) total = cursor.getInt(0)
        }

        // Count EPha total
        var ephaTotal = 0
        db.rawQuery(
            "SELECT COUNT(*) FROM epha_interactions WHERE effect LIKE ?1 OR mechanism LIKE ?1 OR measures LIKE ?1",
            arrayOf(pattern)
        ).use { cursor ->
            if (cursor.moveToNext()) ephaTotal = cursor.getInt(0)
        }

        val fetchLimit = limit + offset

        // Fetch FI results
        val fiSql = """
            SELECT i.drug_brand, i.drug_substance, i.interacting_substance, i.interacting_brands,
                   i.description, i.severity_score, i.severity_label, COALESCE(d.route, '')
            FROM interactions i LEFT JOIN drugs d ON d.brand_name = i.drug_brand
            WHERE i.description LIKE ?1 ORDER BY i.severity_score DESC LIMIT ?2
        """.trimIndent()
        val fiResults = mutableListOf<SearchResultItem>()
        db.rawQuery(fiSql, arrayOf(pattern, fetchLimit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val drugBrand = cursor.getString(0) ?: ""
                val interactingSubstance = cursor.getString(2) ?: ""
                val sevScore = cursor.getInt(5)
                val sevLabel = cursor.getString(6) ?: ""
                val drugRoute = cursor.getString(7) ?: ""
                val (interactingBrand, interactingRoute) = bestBrandForSubstance(interactingSubstance.lowercase())

                fiResults.add(
                    SearchResultItem(
                        drugBrand = drugBrand, drugRoute = drugRoute,
                        interactingSubstance = interactingSubstance,
                        interactingBrand = interactingBrand, interactingRoute = interactingRoute,
                        severityScore = sevScore, severityLabel = sevLabel,
                        severityIndicator = sevScore.severityIndicator,
                        description = cursor.getString(4) ?: "",
                        source = "Swissmedic FI"
                    )
                )
            }
        }

        // Fetch EPha results
        val ephaSql = """
            SELECT title, effect, mechanism, measures, risk_class, risk_label, severity_score
            FROM epha_interactions WHERE effect LIKE ?1 OR mechanism LIKE ?1 OR measures LIKE ?1
            ORDER BY severity_score DESC LIMIT ?2
        """.trimIndent()
        val ephaResults = mutableListOf<SearchResultItem>()
        db.rawQuery(ephaSql, arrayOf(pattern, fetchLimit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: ""
                val effect = cursor.getString(1) ?: ""
                val mechanism = cursor.getString(2) ?: ""
                val measures = cursor.getString(3) ?: ""
                val sevScore = cursor.getInt(6)
                val sevLabel = cursor.getString(5) ?: ""

                val desc = if (mechanism.isEmpty()) effect
                else "$effect\n\nMechanismus: $mechanism\n\nMassnahmen: $measures"

                ephaResults.add(
                    SearchResultItem(
                        drugBrand = title, drugRoute = "",
                        interactingSubstance = "", interactingBrand = "", interactingRoute = "",
                        severityScore = sevScore, severityLabel = sevLabel,
                        severityIndicator = sevScore.severityIndicator,
                        description = desc, source = "EPha"
                    )
                )
            }
        }

        // Merge, sort, paginate
        val allResults = (fiResults + ephaResults).sortedWith(compareByDescending<SearchResultItem> { it.severityScore }
            .thenBy { routePriority(it.drugRoute, it.interactingRoute) })

        val paged = allResults.drop(offset).take(limit)
        return Pair(total + ephaTotal, paged)
    }

    private fun bestBrandForSubstance(substance: String): Pair<String, String> {
        val db = db ?: return Pair("", "")
        val sql = """
            SELECT brand_name, route FROM substance_brand_map WHERE substance = ?1
            ORDER BY CASE WHEN route = '' THEN 0 WHEN route = 'p.o.' THEN 1 WHEN route = 'i.v.' THEN 2
            WHEN route = 's.c.' THEN 3 WHEN route = 'i.m.' THEN 4 ELSE 9 END LIMIT 1
        """.trimIndent()
        db.rawQuery(sql, arrayOf(substance)).use { cursor ->
            if (cursor.moveToNext()) {
                return Pair(cursor.getString(0) ?: "", cursor.getString(1) ?: "")
            }
        }
        return Pair("", "")
    }

    // MARK: - Term Suggestions

    fun suggestTerms(query: String): List<TermSuggestion> {
        val db = db ?: return emptyList()
        if (query.length < 2) return emptyList()
        val q = query.lowercase()
        val pattern = "%$q%"

        val descriptions = mutableListOf<String>()

        // FI descriptions
        db.rawQuery("SELECT description FROM interactions WHERE description LIKE ?1 LIMIT 500", arrayOf(pattern)).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { descriptions.add(it) }
            }
        }

        // EPha descriptions
        db.rawQuery(
            "SELECT effect || ' ' || mechanism || ' ' || measures FROM epha_interactions WHERE effect LIKE ?1 OR mechanism LIKE ?1 OR measures LIKE ?1 LIMIT 500",
            arrayOf(pattern)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { descriptions.add(it) }
            }
        }

        // Extract terms
        val termCounts = mutableMapOf<String, Int>()
        val formCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val wordBoundary = setOf(' ', '\t', '(', ')', ',', '.', ';')

        for (desc in descriptions) {
            val descLower = desc.lowercase()
            var searchFrom = 0
            while (true) {
                val idx = descLower.indexOf(q, searchFrom)
                if (idx < 0) break

                // Expand to word boundaries
                var start = idx
                while (start > 0 && descLower[start - 1] !in wordBoundary) start--
                var end = idx + q.length
                while (end < descLower.length && descLower[end] !in wordBoundary) end++

                val word = desc.substring(start, end).trim()
                if (word.length >= q.length + 1 && word.length <= 40) {
                    val key = word.lowercase()
                    termCounts[key] = (termCounts[key] ?: 0) + 1
                    val forms = formCounts.getOrPut(key) { mutableMapOf() }
                    forms[word] = (forms[word] ?: 0) + 1
                }

                // Bigram: next word
                if (end < desc.length) {
                    var nextStart = end
                    while (nextStart < desc.length && desc[nextStart] in wordBoundary) nextStart++
                    val separator = desc.substring(end, nextStart)
                    if (separator.isNotEmpty() && separator.all { it.isWhitespace() }) {
                        var nextEnd = nextStart
                        while (nextEnd < desc.length && desc[nextEnd] !in wordBoundary) nextEnd++
                        val bigram = desc.substring(start, nextEnd).trim()
                        if (bigram.length > word.length + 1 && bigram.length <= 60) {
                            val biKey = bigram.lowercase()
                            termCounts[biKey] = (termCounts[biKey] ?: 0) + 1
                            val forms = formCounts.getOrPut(biKey) { mutableMapOf() }
                            forms[bigram] = (forms[bigram] ?: 0) + 1
                        }
                    }
                }

                searchFrom = idx + q.length
            }
        }

        return termCounts.filter { it.value >= 2 }
            .map { (key, count) ->
                val display = formCounts[key]?.maxByOrNull { it.value }?.key ?: key
                TermSuggestion(term = display, count = count)
            }
            .sortedByDescending { it.count }
            .take(15)
    }

    // MARK: - ATC Class Overview

    fun loadClassInteractions(): Pair<Int, List<ClassInteractionRow>> {
        val db = db ?: return Pair(0, emptyList())

        data class DrugRow(val atc: String, val substances: String, val text: String)

        val sql = """
            SELECT brand_name, atc_code, active_substances, interactions_text FROM drugs
            WHERE length(interactions_text) > 0 AND atc_code IS NOT NULL AND atc_code != ''
        """.trimIndent()
        val drugs = mutableListOf<DrugRow>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val atc = cursor.getString(1) ?: ""
                val substances = cursor.getString(2) ?: ""
                val text = cursor.getString(3) ?: ""
                if (atc.isNotEmpty() && text.isNotEmpty()) {
                    drugs.add(DrugRow(atc, substances, text))
                }
            }
        }

        val classKeywords = loadClassKeywords()

        val drugsInClass = mutableMapOf<String, Int>()
        for ((prefix, _) in classKeywords) {
            drugsInClass[prefix] = drugs.count { it.atc.startsWith(prefix) }
        }

        var totalPairs = 0
        val classes = mutableListOf<ClassInteractionRow>()

        for ((prefix, keywords) in classKeywords) {
            val nInClass = drugsInClass[prefix] ?: 0
            if (nInClass == 0) continue

            val mentioningSubstances = mutableSetOf<String>()
            var bestKeyword = ""
            var bestCount = 0

            for (kw in keywords) {
                var count = 0
                for (drug in drugs) {
                    if (drug.atc.startsWith(prefix)) continue
                    if (drug.text.contains(kw, ignoreCase = true)) {
                        mentioningSubstances.add(drug.substances)
                        count++
                    }
                }
                if (count > bestCount) {
                    bestCount = count
                    bestKeyword = kw
                }
            }

            val nMentioning = mentioningSubstances.size
            val pairCount = nMentioning * nInClass
            totalPairs += pairCount
            classes.add(
                ClassInteractionRow(
                    atcPrefix = prefix,
                    description = atcClassDescription(prefix),
                    drugsInClass = nInClass,
                    drugsMentioning = nMentioning,
                    potentialPairs = pairCount,
                    topKeyword = bestKeyword
                )
            )
        }

        classes.sortByDescending { it.potentialPairs }
        return Pair(totalPairs, classes)
    }

    // MARK: - Helpers

}
