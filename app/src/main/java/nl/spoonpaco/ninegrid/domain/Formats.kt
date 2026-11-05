package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.Section

/** Een kaart in een deck, met de gegevens die de controles nodig hebben. */
data class DeckCard(
    val name: String,
    val quantity: Int,
    val section: String,
    val typeLine: String,
    val colorIdentity: String,
    val legalities: String,
    val gameChanger: Boolean,
    val proxy: Boolean,
    val oracleText: String = "",
    val cmc: Double = 0.0,
    val manaCost: String = "",
    val producedMana: String = "",
)

enum class IssueLevel { ERROR, WARNING, INFO }

data class DeckIssue(val level: IssueLevel, val code: String, val detail: String = "")

data class DeckCheck(
    val mainCount: Int,
    val sideboardCount: Int,
    val commanderCount: Int,
    val colorIdentity: String,
    val gameChangers: List<String>,
    val bracketHint: String?,
    val issues: List<DeckIssue>,
    /** Waarom de bracket minstens zo hoog is: game changers, landvernietiging, extra beurten, tutors. */
    val bracketReasons: List<BracketReason> = emptyList(),
)

/** Een signaal voor de bracket, met de kaarten waar het om gaat. */
data class BracketReason(val code: String, val cards: List<String>)

object Formats {
    fun isBasicLand(typeLine: String) = typeLine.contains("Basic Land", ignoreCase = true) ||
        typeLine.contains("Basic Snow Land", ignoreCase = true)

    /** Kaarten waarvan je er zoveel mag hebben als je wilt. */
    private val anyNumber = setOf(
        "Relentless Rats", "Shadowborn Apostle", "Rat Colony", "Persistent Petitioners",
        "Dragon's Approach", "Slime Against Humanity", "Hare Apparent", "Templar Knight",
        "Seven Dwarves", "Nazgûl", "Cid, Timeless Artificer",
    )

    private fun limitFree(name: String, typeLine: String) = isBasicLand(typeLine) || name in anyNumber

    private fun lower(t: String) = t.lowercase().replace('\n', ' ')

    /** Massale landvernietiging: Armageddon, Ravages of War, Jokulhaups, Sunder, Winter Orb-achtig. */
    fun massLandDenial(text: String): Boolean {
        val t = lower(text)
        return Regex("""destroy all (?:[a-z, ]*)lands""").containsMatchIn(t) ||
            Regex("""(?:each player|all players) sacrifices? (?:all|half|[a-z]+ ?)?(?:the )?lands""").containsMatchIn(t) ||
            t.contains("return all lands") || t.contains("lands don't untap during their controllers' untap steps") ||
            t.contains("lands don't untap during their controller's untap step")
    }

    fun extraTurn(text: String) = lower(text).let { it.contains("take an extra turn") || it.contains("takes an extra turn") || it.contains("extra turns") }

    /** Tutor: een willekeurige (niet-land)kaart uit je library zoeken. */
    fun tutor(text: String): Boolean = lower(text).split('.').any { zin ->
        zin.contains("search your library for") && !zin.contains("land") && !zin.contains("basic")
    }

    fun legalityOf(legalities: String, format: String): String? =
        legalities.split(',').firstOrNull { it.startsWith("$format=") }?.substringAfter('=')

    /** Stelt een format voor op basis van de lijst, zoals in de parameterlijst afgesproken. */
    fun suggest(lines: List<ParsedLine>, typeLines: Map<String, String> = emptyMap()): String {
        val main = lines.filter { it.section != DeckListParser.SIDEBOARD }
        val total = main.sumOf { it.quantity }
        val hasCommanderHeader = lines.any { it.section == DeckListParser.COMMANDER }
        val singleton = main.groupBy { it.name }.all { (name, ls) ->
            ls.sumOf { it.quantity } == 1 || isBasicLand(typeLines[name] ?: "") ||
                name in setOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes") ||
                name.startsWith("Snow-Covered ")
        }
        return when {
            hasCommanderHeader -> DeckFormat.COMMANDER
            total in 99..101 && singleton -> DeckFormat.COMMANDER
            total == 60 && lines.filter { it.section == DeckListParser.SIDEBOARD }.sumOf { it.quantity } <= 15 -> DeckFormat.MODERN
            else -> DeckFormat.CASUAL
        }
    }

    /** Combo's van twee kaarten uit Commander Spellbook: vroeg in het spel (minstens 4) of laat (minstens 3). */
    data class ComboSignals(val early: List<List<String>> = emptyList(), val late: List<List<String>> = emptyList())

    fun check(format: String, cards: List<DeckCard>, combos: ComboSignals? = null): DeckCheck {
        val real = cards.filter { !it.proxy }
        val commanders = cards.filter { it.section == Section.COMMANDER }
        val main = cards.filter { it.section == Section.MAIN }
        val side = cards.filter { it.section == Section.SIDEBOARD }
        val mainCount = main.sumOf { it.quantity }
        val sideCount = side.sumOf { it.quantity }
        val cmdCount = commanders.sumOf { it.quantity }
        val issues = mutableListOf<DeckIssue>()
        val order = "WUBRG"
        val identity = cards.flatMap { it.colorIdentity.toList() }.distinct().sortedBy { order.indexOf(it) }.joinToString("")
        val gcs = cards.filter { it.gameChanger }.map { it.name }.distinct()
        var bracket: String? = null
        val reasons = mutableListOf<BracketReason>()

        val byName = cards.filter { it.section != Section.SIDEBOARD || format != DeckFormat.COMMANDER }
            .groupBy { it.name }.mapValues { (_, v) -> v.sumOf { it.quantity } to v.first().typeLine }

        when (format) {
            DeckFormat.COMMANDER -> {
                val total = cmdCount + mainCount
                if (cmdCount == 0) issues += DeckIssue(IssueLevel.ERROR, "no_commander")
                if (total != 100) issues += DeckIssue(IssueLevel.ERROR, "commander_size", total.toString())
                byName.filter { (n, v) -> v.first > 1 && !limitFree(n, v.second) }.keys.forEach {
                    issues += DeckIssue(IssueLevel.ERROR, "singleton", it)
                }
                val cmdIdentity = commanders.flatMap { it.colorIdentity.toList() }.toSet()
                if (commanders.isNotEmpty()) {
                    main.filter { c -> c.colorIdentity.any { it !in cmdIdentity } }.forEach {
                        issues += DeckIssue(IssueLevel.ERROR, "color_identity", it.name)
                    }
                }
                // Officiële brackets kijken ook naar landvernietiging, extra beurten en tutors. Combo's laten we weg:
                // die zijn niet betrouwbaar te herkennen. Daarom altijd "minstens".
                val mld = cards.filter { massLandDenial(it.oracleText) }.map { it.name }.distinct()
                val turns = cards.filter { extraTurn(it.oracleText) }.map { it.name }.distinct()
                val tutors = cards.filter { tutor(it.oracleText) }.map { it.name }.distinct()
                if (gcs.isNotEmpty()) reasons += BracketReason("game_changers", gcs)
                if (mld.isNotEmpty()) reasons += BracketReason("mass_land_denial", mld)
                if (turns.isNotEmpty()) reasons += BracketReason("extra_turns", turns)
                if (tutors.isNotEmpty()) reasons += BracketReason("tutors", tutors)
                combos?.early?.takeIf { it.isNotEmpty() }?.let { reasons += BracketReason("combos_early", it.map { c -> c.joinToString(" + ") }) }
                combos?.late?.takeIf { it.isNotEmpty() }?.let { reasons += BracketReason("combos_late", it.map { c -> c.joinToString(" + ") }) }
                val min = listOf(
                    when { combos?.early?.isNotEmpty() == true -> 4; combos?.late?.isNotEmpty() == true -> 3; else -> 1 },
                    when { gcs.size > 3 -> 4; gcs.isNotEmpty() -> 3; else -> 1 },
                    if (mld.isNotEmpty()) 4 else 1,
                    when { turns.size >= 2 -> 3; turns.size == 1 -> 2; else -> 1 },
                    when { tutors.size >= 4 -> 3; tutors.isNotEmpty() -> 2; else -> 1 },
                ).max()
                bracket = min.toString()
                if (gcs.size > 3) issues += DeckIssue(IssueLevel.INFO, "game_changers_many", gcs.size.toString())
            }
            DeckFormat.CASUAL -> Unit
            else -> {
                if (mainCount < 60) issues += DeckIssue(IssueLevel.ERROR, "main_size", mainCount.toString())
                if (sideCount > 15) issues += DeckIssue(IssueLevel.ERROR, "side_size", sideCount.toString())
                val allByName = cards.groupBy { it.name }.mapValues { (_, v) -> v.sumOf { it.quantity } to v.first().typeLine }
                allByName.filter { (n, v) -> v.first > 4 && !limitFree(n, v.second) }.keys.forEach {
                    issues += DeckIssue(IssueLevel.ERROR, "max_four", it)
                }
            }
        }

        if (format != DeckFormat.CASUAL) {
            real.filter { it.legalities.isNotBlank() }.forEach { c ->
                val l = legalityOf(c.legalities, format)
                if (l != null && l != "legal" && l != "restricted") {
                    issues += DeckIssue(IssueLevel.ERROR, if (l == "banned") "banned" else "not_legal", c.name)
                }
            }
        }
        return DeckCheck(mainCount, sideCount, cmdCount, identity, gcs, bracket, issues.distinct(), reasons)
    }

    fun toDeckCards(entries: List<Pair<Entry, CardPrint?>>): List<DeckCard> = entries.map { (e, p) ->
        DeckCard(
            name = p?.name ?: "?",
            quantity = e.quantity,
            section = e.section ?: Section.MAIN,
            typeLine = p?.typeLine ?: "",
            colorIdentity = p?.colorIdentity ?: "",
            producedMana = p?.producedMana ?: "",
            legalities = p?.legalities ?: "",
            gameChanger = p?.gameChanger ?: false,
            proxy = e.proxy,
            oracleText = p?.oracleText ?: "",
            cmc = p?.cmc ?: 0.0,
            manaCost = p?.manaCost ?: "",
        )
    }
}
