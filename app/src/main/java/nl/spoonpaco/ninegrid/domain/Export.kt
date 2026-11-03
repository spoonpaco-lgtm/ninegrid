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
}
