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
)

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

    fun check(format: String, cards: List<DeckCard>): DeckCheck {
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
                bracket = when {
                    gcs.size > 3 -> "4"
                    gcs.isNotEmpty() -> "3"
                    else -> "1-2"
                }
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
        return DeckCheck(mainCount, sideCount, cmdCount, identity, gcs, bracket, issues.distinct())
    }

    fun toDeckCards(entries: List<Pair<Entry, CardPrint?>>): List<DeckCard> = entries.map { (e, p) ->
        DeckCard(
            name = p?.name ?: "?",
            quantity = e.quantity,
            section = e.section ?: Section.MAIN,
            typeLine = p?.typeLine ?: "",
            colorIdentity = p?.colorIdentity ?: "",
            legalities = p?.legalities ?: "",
            gameChanger = p?.gameChanger ?: false,
            proxy = e.proxy,
        )
    }
}
