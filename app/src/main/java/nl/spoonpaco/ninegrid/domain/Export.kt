package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.data.db.Section

object Export {
    private fun line(e: EntryWithPrint): String {
        val p = e.print
        val marker = when (e.entry.finish) { Finish.FOIL -> " *F*"; Finish.ETCHED -> " *E*"; else -> "" }
        return if (p == null) "${e.entry.quantity} ?" else "${e.entry.quantity} ${p.name} (${p.setCode}) ${p.collectorNumber}$marker"
    }

    /** Decklijst in het formaat dat Moxfield, Archidekt en NineGrid zelf kunnen lezen. */
    fun deckText(entries: List<EntryWithPrint>, isCommander: Boolean): String {
        val sorted = entries.sortedBy { it.print?.name ?: "" }
        val cmd = sorted.filter { it.entry.section == Section.COMMANDER }
        val main = sorted.filter { it.entry.section == null || it.entry.section == Section.MAIN }
        val side = sorted.filter { it.entry.section == Section.SIDEBOARD }
        return buildString {
            if (cmd.isNotEmpty()) {
                appendLine("Commander")
                cmd.forEach { appendLine(line(it)) }
                appendLine()
                appendLine("Deck")
            } else if (isCommander) appendLine("Deck")
            main.forEach { appendLine(line(it)) }
            if (side.isNotEmpty()) {
                appendLine()
                appendLine("Sideboard")
                side.forEach { appendLine(line(it)) }
            }
        }.trimEnd() + "\n"
    }

    private fun csv(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** De hele collectie als CSV, met de plek van elk exemplaar. */
    fun collectionCsv(entries: List<EntryWithPrint>, deckNames: Map<Long, String>): String = buildString {
        appendLine("Count,Name,Edition,Collector Number,Rarity,Finish,Language,Condition,Proxy,Location,Binder,Page,Slot")
        entries.sortedWith(compareBy({ it.print?.setCode }, { it.print?.collectorNumber })).forEach { e ->
            val p = e.print
            val loc = e.entry.deckId?.let { "Deck: ${deckNames[it] ?: it}" } ?: "Collection"
            appendLine(
                listOf(
                    e.entry.quantity.toString(), p?.name ?: "", p?.setCode ?: "", p?.collectorNumber ?: "", p?.rarity ?: "",
                    e.entry.finish, e.entry.language.ifBlank { "EN" }, e.entry.condition, if (e.entry.proxy) "yes" else "",
                    loc, e.entry.binder ?: "", e.entry.page?.toString() ?: "", e.entry.slot?.toString() ?: "",
                ).joinToString(",") { csv(it) }
            )
        }
    }

    /** Sites waar je een lijst heen kunt zetten. */
    enum class Site(val label: String, val url: String) {
        MOXFIELD("Moxfield", "https://moxfield.com"),
        ARCHIDEKT("Archidekt", "https://archidekt.com"),
        CARDMARKET("Cardmarket", "https://www.cardmarket.com/en/Magic/Wants"),
    }

    /** Max. aantal regels in één Cardmarket-verlanglijst (volgens hun helppagina). */
    const val CARDMARKET_WANTS_MAX = 150

    /**
     * Decklijst voor [site]. Moxfield en Archidekt lezen "aantal Naam (SET) nummer", met *F* voor foil en *E* voor etched.
     * Cardmarket leest "aantal Naam (Setnaam)", zonder nummer en zonder foil of taal.
     */
    fun deckTextFor(site: Site, entries: List<EntryWithPrint>, isCommander: Boolean): String =
        if (site == Site.CARDMARKET) cardmarketList(entries.map { it.print to it.entry.quantity })
        else deckText(entries, isCommander)

    /** "2 Dark Confidant (Modern Masters)": per kaart de set, gelijke regels samengeteld. Zonder printgegevens alleen de naam. */
    fun cardmarketList(cards: List<Pair<nl.spoonpaco.ninegrid.data.db.CardPrint?, Int>>): String =
        cards.filter { it.first != null }.groupBy { (p, _) -> p!!.name to p.setName }
            .map { (k, v) -> Triple(k.first, k.second, v.sumOf { it.second }) }
            .sortedBy { it.first }
            .joinToString("\n") { (name, set, n) -> if (set.isBlank()) "$n $name" else "$n $name ($set)" } + "\n"

    private val moxLanguages = mapOf(
        "EN" to "English", "FR" to "French", "DE" to "German", "IT" to "Italian", "ES" to "Spanish", "PT" to "Portuguese",
        "JA" to "Japanese", "JP" to "Japanese", "KO" to "Korean", "RU" to "Russian", "ZHS" to "Simplified Chinese", "CN" to "Simplified Chinese",
        "ZHT" to "Traditional Chinese", "TW" to "Traditional Chinese",
    )

    /**
     * Collectie voor de CSV-import van Moxfield. Kolomnamen en volgorde zoals Moxfield zelf exporteert.
     * Proxy's blijven als proxy gemarkeerd. Foil: "foil" of "etched", anders leeg.
     */
    fun moxfieldCsv(entries: List<EntryWithPrint>): String = buildString {
        appendLine("Count,Tradelist Count,Name,Edition,Condition,Language,Foil,Tags,Last Modified,Collector Number,Alter,Proxy,Purchase Price")
        entries.filter { it.print != null }.sortedWith(compareBy({ it.print!!.setCode }, { it.print!!.collectorNumber })).forEach { e ->
            val p = e.print!!
            val foil = when (e.entry.finish) { Finish.FOIL -> "foil"; Finish.ETCHED -> "etched"; else -> "" }
            val lang = moxLanguages[e.entry.language.ifBlank { "EN" }.uppercase()] ?: "English"
            appendLine(
                listOf(
                    e.entry.quantity.toString(), "0", p.name, p.setCode.lowercase(), "Near Mint", lang, foil, "", "",
                    p.collectorNumber, "False", if (e.entry.proxy) "True" else "False", "",
                ).joinToString(",") { csv(it) }
            )
        }
    }

    /** Verlanglijst voor Cardmarket: wat je nog mist; bij een gekozen print met setnaam, anders elke versie. Geeft tekst en aantal regels. */
    fun cardmarketWants(wishes: List<nl.spoonpaco.ninegrid.data.db.WishWithPrint>): Pair<String, Int> {
        val lines = wishes.mapNotNull { w ->
            val n = w.item.wanted - w.item.got
            if (n <= 0) return@mapNotNull null
            val setName = w.print?.setName.orEmpty()
            val name = w.print?.name ?: w.item.name
            if (!w.item.anyPrint && setName.isNotBlank()) "$n $name ($setName)" else "$n $name"
        }.sorted()
        return lines.joinToString("\n") + (if (lines.isEmpty()) "" else "\n") to lines.size
    }
}
