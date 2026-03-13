package org.oddb.sdif.data

class InteractionChecker(private val db: DatabaseManager) {

    fun checkInteractions(basketDrugs: List<BasketDrug>): List<InteractionResult> {
        val classKeywords = db.loadClassKeywords()
        val cypRules = db.loadCypRules()
        val fiSource = "Swissmedic FI"

        val interactions = mutableListOf<InteractionResult>()

        for (i in basketDrugs.indices) {
            for (j in (i + 1) until basketDrugs.size) {
                val a = basketDrugs[i]
                val b = basketDrugs[j]

                // Strategy 1: Substance match A->B
                for (subst in b.substances) {
                    val rows = db.findSubstanceInteractions(a.brand, subst)
                    for ((desc, sevScore, sevLabel) in rows) {
                        interactions.add(InteractionResult(
                            drugA = a.brand, drugAAtc = a.atcCode, drugARoute = a.route,
                            drugB = b.brand, drugBAtc = b.atcCode, drugBRoute = b.route,
                            interactionType = "substance", severityScore = sevScore,
                            severityLabel = sevLabel, severityIndicator = sevScore.severityIndicator,
                            keyword = subst, description = desc,
                            explanation = "Wirkstoff \u00ab${subst}\u00bb wird in der Fachinformation von ${a.brand} erw\u00e4hnt",
                            source = fiSource, comboHint = ""
                        ))
                    }
                }

                // Substance match B->A
                for (subst in a.substances) {
                    val rows = db.findSubstanceInteractions(b.brand, subst)
                    for ((desc, sevScore, sevLabel) in rows) {
                        interactions.add(InteractionResult(
                            drugA = b.brand, drugAAtc = b.atcCode, drugARoute = b.route,
                            drugB = a.brand, drugBAtc = a.atcCode, drugBRoute = a.route,
                            interactionType = "substance", severityScore = sevScore,
                            severityLabel = sevLabel, severityIndicator = sevScore.severityIndicator,
                            keyword = subst, description = desc,
                            explanation = "Wirkstoff \u00ab${subst}\u00bb wird in der Fachinformation von ${b.brand} erw\u00e4hnt",
                            source = fiSource, comboHint = ""
                        ))
                    }
                }

                // Strategy 2: Class-level A->B
                for (hit in findClassInteractions(a.interactionsText, b.atcCode, classKeywords)) {
                    val (sevScore, sevLabel) = scoreSeverity(hit.context)
                    val classDesc = DatabaseManager.atcClassDescriptionForCode(b.atcCode)
                    interactions.add(InteractionResult(
                        drugA = a.brand, drugAAtc = a.atcCode, drugARoute = a.route,
                        drugB = b.brand, drugBAtc = b.atcCode, drugBRoute = b.route,
                        interactionType = "class-level", severityScore = sevScore,
                        severityLabel = sevLabel, severityIndicator = sevScore.severityIndicator,
                        keyword = hit.classKeyword, description = hit.context,
                        explanation = "${b.brand} [${b.atcCode}] geh\u00f6rt zur Klasse $classDesc \u2014 Keyword \u00ab${hit.classKeyword}\u00bb gefunden in Fachinformation von ${a.brand}",
                        source = fiSource, comboHint = ""
                    ))
                }
                // Class-level B->A
                for (hit in findClassInteractions(b.interactionsText, a.atcCode, classKeywords)) {
                    val (sevScore, sevLabel) = scoreSeverity(hit.context)
                    val classDesc = DatabaseManager.atcClassDescriptionForCode(a.atcCode)
                    interactions.add(InteractionResult(
                        drugA = b.brand, drugAAtc = b.atcCode, drugARoute = b.route,
                        drugB = a.brand, drugBAtc = a.atcCode, drugBRoute = a.route,
                        interactionType = "class-level", severityScore = sevScore,
                        severityLabel = sevLabel, severityIndicator = sevScore.severityIndicator,
                        keyword = hit.classKeyword, description = hit.context,
                        explanation = "${a.brand} [${a.atcCode}] geh\u00f6rt zur Klasse $classDesc \u2014 Keyword \u00ab${hit.classKeyword}\u00bb gefunden in Fachinformation von ${b.brand}",
                        source = fiSource, comboHint = ""
                    ))
                }

                // Strategy 3: CYP A->B
                for (hit in findCypInteractions(a.interactionsText, b.atcCode, b.substances, cypRules)) {
                    val (sevScore, sevLabel) = scoreSeverity(hit.context)
                    interactions.add(InteractionResult(
                        drugA = a.brand, drugAAtc = a.atcCode, drugARoute = a.route,
                        drugB = b.brand, drugBAtc = b.atcCode, drugBRoute = b.route,
                        interactionType = "CYP", severityScore = sevScore,
                        severityLabel = sevLabel, severityIndicator = sevScore.severityIndicator,
                        keyword = hit.classKeyword, description = hit.context,
                        explanation = "${b.brand} ist ${hit.classKeyword} \u2014 Fachinformation von ${a.brand} erw\u00e4hnt dieses Enzym",
                        source = fiSource, comboHint = ""
                    ))
                }
                // CYP B->A
                for (hit in findCypInteractions(b.interactionsText, a.atcCode, a.substances, cypRules)) {
                    val (sevScore, sevLabel) = scoreSeverity(hit.context)
                    interactions.add(InteractionResult(
                        drugA = b.brand, drugAAtc = b.atcCode, drugARoute = b.route,
                        drugB = a.brand, drugBAtc = a.atcCode, drugBRoute = a.route,
                        interactionType = "CYP", severityScore = sevScore,
                        severityLabel = sevLabel, severityIndicator = sevScore.severityIndicator,
                        keyword = hit.classKeyword, description = hit.context,
                        explanation = "${a.brand} ist ${hit.classKeyword} \u2014 Fachinformation von ${b.brand} erw\u00e4hnt dieses Enzym",
                        source = fiSource, comboHint = ""
                    ))
                }

                // Strategy 4: EPha
                val epha = db.findEphaInteraction(a.atcCode, b.atcCode)
                if (epha != null) {
                    val desc = if (epha.mechanism.isEmpty()) epha.effect
                    else "${epha.effect}\n\nMechanismus: ${epha.mechanism}\n\nMassnahmen: ${epha.measures}"
                    interactions.add(InteractionResult(
                        drugA = a.brand, drugAAtc = a.atcCode, drugARoute = a.route,
                        drugB = b.brand, drugBAtc = b.atcCode, drugBRoute = b.route,
                        interactionType = "epha", severityScore = epha.severityScore,
                        severityLabel = epha.riskLabel, severityIndicator = epha.severityScore.severityIndicator,
                        keyword = epha.riskClass, description = desc,
                        explanation = "EPha Interaktionsdatenbank (ATC ${a.atcCode} \u2194 ${b.atcCode})",
                        source = "EPha", comboHint = ""
                    ))
                }
            }
        }

        // Set combo hints
        val comboHints = basketDrugs.filter { it.comboHint.isNotEmpty() }.associate { it.brand to it.comboHint }
        for (i in interactions.indices) {
            val hint = comboHints[interactions[i].drugA] ?: comboHints[interactions[i].drugB]
            if (hint != null && hint.isNotEmpty()) {
                val drug = if (comboHints.containsKey(interactions[i].drugA)) interactions[i].drugA else interactions[i].drugB
                interactions[i] = interactions[i].copy(comboHint = "$drug: $hint")
            }
        }

        // Sort by severity desc, then route priority
        interactions.sortWith(compareByDescending<InteractionResult> { it.severityScore }
            .thenBy { routePriority(it.drugARoute, it.drugBRoute) })

        // Add FI quality hints for asymmetric severity
        val pairMax = mutableMapOf<String, Int>()
        for (ix in interactions) {
            val key = listOf(ix.drugA, ix.drugB).sorted().joinToString("||")
            pairMax[key] = maxOf(pairMax[key] ?: 0, ix.severityScore)
        }
        for (i in interactions.indices) {
            val key = listOf(interactions[i].drugA, interactions[i].drugB).sorted().joinToString("||")
            val maxSev = pairMax[key] ?: 0
            if (interactions[i].severityScore < maxSev) {
                interactions[i] = interactions[i].copy(
                    fiHint = "Gegenrichtung hat h\u00f6here Einstufung \u2014 diese FI stuft die Interaktion tiefer ein"
                )
            }
        }

        return interactions
    }

    // MARK: - Severity Scoring

    fun scoreSeverity(text: String): Pair<Int, String> {
        val lower = text.lowercase()
        val stripped = stripSectionReferences(lower)

        val contraindicated = listOf(
            "kontraindiziert", "kontraindikation", "darf nicht",
            "nicht angewendet werden", "nicht verabreicht werden",
            "nicht kombiniert werden", "nicht gleichzeitig",
            "ist verboten", "absolut kontraindiziert", "streng kontraindiziert",
            "nicht zusammen", "nicht eingenommen werden", "nicht anwenden"
        )
        for (kw in contraindicated) {
            if (stripped.contains(kw)) return Pair(3, "Kontraindiziert")
        }

        val serious = listOf(
            "erh\u00f6htes risiko", "erh\u00f6hte gefahr", "schwerwiegend", "schwere",
            "lebensbedrohlich", "lebensgef\u00e4hrlich", "gef\u00e4hrlich",
            "stark erh\u00f6ht", "stark verst\u00e4rkt", "toxisch", "toxizit\u00e4t",
            "nephrotoxisch", "hepatotoxisch", "ototoxisch", "neurotoxisch", "kardiotoxisch",
            "t\u00f6dlich", "fatale", "blutungsrisiko", "blutungsgefahr",
            "serotoninsyndrom", "serotonin-syndrom", "qt-verl\u00e4ngerung", "qt-zeit-verl\u00e4ngerung",
            "torsade", "rhabdomyolyse", "nierenversagen", "niereninsuffizienz",
            "nierenfunktionsst\u00f6rung", "leberversagen", "atemdepression", "herzstillstand",
            "arrhythmie", "hyperkali\u00e4mie", "agranulozytos", "stevens-johnson", "anaphyla",
            "lymphoproliferation", "immundepression", "immunsuppression", "panzytopenie",
            "abgeraten", "wird nicht empfohlen"
        )
        for (kw in serious) {
            if (lower.contains(kw)) return Pair(2, "Schwerwiegend")
        }

        val caution = listOf(
            "vorsicht", "\u00fcberwach", "monitor", "kontroll", "engmaschig",
            "dosisanpassung", "dosis reduz", "dosis anpassen", "dosisreduktion",
            "sorgf\u00e4ltig", "regelm\u00e4ssig", "regelm\u00e4\u00dfig", "aufmerksam", "cave", "beobacht",
            "verst\u00e4rkt", "vermindert", "abgeschw\u00e4cht", "erh\u00f6h", "erniedrigt", "beeinflusst",
            "wechselwirkung", "plasmaspiegel", "plasmakonzentration", "serumkonzentration",
            "bioverf\u00fcgbarkeit", "subtherapeutisch", "supratherapeutisch",
            "therapieversagen", "wirkungsverlust", "wirkverlust"
        )
        for (kw in caution) {
            if (lower.contains(kw)) return Pair(1, "Vorsicht")
        }

        return Pair(0, "Keine Einstufung")
    }

    private fun stripSectionReferences(text: String): String {
        var result = text
            .replace("\u00ab", "")
            .replace("\u00bb", "")

        if (result.startsWith("kontraindiziert!")) {
            result = result.removePrefix("kontraindiziert!").trim()
        }

        val patterns = listOf(
            "siehe kontraindikation", "siehe rubrik kontraindikation",
            "siehe abschnitt kontraindikation", "siehe auch kontraindikation",
            "siehe auch rubrik kontraindikation", "siehe kapitel kontraindikation"
        )
        for (pat in patterns) {
            while (true) {
                val idx = result.indexOf(pat)
                if (idx < 0) break
                var start = idx
                if (start > 0 && result[start - 1] == '(') start--
                var end = idx + pat.length
                while (end < result.length) {
                    if (result[end] in ".;)\n") {
                        end++
                        break
                    }
                    end++
                }
                result = result.substring(0, start) + " " + result.substring(end)
            }
        }
        return result
    }

    // MARK: - Class Interaction Detection

    fun findClassInteractions(
        interactionText: String,
        otherAtc: String,
        classKeywords: List<Pair<String, List<String>>>
    ): List<ClassHit> {
        val textLower = interactionText.lowercase()
        val hits = mutableListOf<ClassHit>()

        for ((atcPrefix, keywords) in classKeywords) {
            if (!otherAtc.startsWith(atcPrefix)) continue
            for (keyword in keywords) {
                if (textLower.contains(keyword)) {
                    val context = extractContext(interactionText, keyword)
                    if (context.isNotEmpty()) {
                        hits.add(ClassHit(classKeyword = keyword, context = context))
                        break
                    }
                }
            }
        }
        return hits
    }

    // MARK: - CYP Interaction Detection

    fun findCypInteractions(
        interactionText: String,
        otherAtc: String,
        otherSubstances: List<String>,
        cypRules: List<CypRule>
    ): List<ClassHit> {
        val textLower = interactionText.lowercase()
        val otherSubstLower = otherSubstances.map { it.lowercase() }
        val hits = mutableListOf<ClassHit>()

        for (rule in cypRules) {
            val mentioned = rule.textPatterns.any { textLower.contains(it) }
            if (!mentioned) continue

            val isInhibitor = rule.inhibitorAtc.any { otherAtc.startsWith(it) }
                    || rule.inhibitorSubstances.any { s -> otherSubstLower.contains(s) }
            val isInducer = rule.inducerAtc.any { otherAtc.startsWith(it) }
                    || rule.inducerSubstances.any { s -> otherSubstLower.contains(s) }

            if (isInhibitor || isInducer) {
                val role = if (isInhibitor) "Hemmer" else "Induktor"
                val pattern = rule.textPatterns[0]
                val context = extractContext(interactionText, pattern)
                if (context.isNotEmpty()) {
                    hits.add(ClassHit(classKeyword = "${rule.enzyme}-$role", context = context))
                }
            }
        }
        return hits
    }

    // MARK: - Context Extraction

    fun extractContext(text: String, substance: String): String {
        val lower = text.lowercase()
        var bestSnippet = ""
        var bestSeverity = 0
        var bestIsAnimal = false
        var searchFrom = 0

        while (true) {
            val pos = lower.indexOf(substance, searchFrom)
            if (pos < 0) break

            // Find sentence start
            var start = 0
            val beforePos = lower.substring(0, pos)
            val dotIdx = beforePos.lastIndexOf('.')
            val colonIdx = beforePos.lastIndexOf(':')
            start = maxOf(dotIdx + 1, colonIdx + 1, 0)

            // Find sentence end
            var end = lower.length
            val afterDot = lower.indexOf('.', pos)
            if (afterDot >= 0) end = afterDot + 1

            val snippet = text.substring(start, end).trim()
            val (sev, _) = scoreSeverity(snippet)

            val prefixLower = lower.substring(start, pos)
            val isAnimalModel = prefixLower.contains("tiermodell")
                    || prefixLower.contains("tierstudie")
                    || prefixLower.contains("tierversuch")
            val effectiveSev = if (isAnimalModel) 0 else sev

            val dominated = effectiveSev > bestSeverity
                    || (effectiveSev == bestSeverity && bestIsAnimal && !isAnimalModel)
                    || bestSnippet.isEmpty()

            if (dominated) {
                bestSeverity = effectiveSev
                bestIsAnimal = isAnimalModel
                bestSnippet = if (snippet.length > 500) snippet.take(497) + "..." else snippet
                if (bestSeverity >= 3) break
            }

            searchFrom = pos + substance.length
        }

        return bestSnippet
    }
}
