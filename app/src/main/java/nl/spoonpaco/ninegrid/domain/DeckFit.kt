package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.DeckFormat

/** Waarom een kaart niet in een deck past (punt 4e): meteen oranje op het controlescherm. */
enum class DeckFitIssue { BANNED, NOT_LEGAL, COLORS }

object DeckFit {
    /**
     * [commanderIdentity]: de kleuren van de commander(s); null = nog geen commander bekend (dan geen kleurcontrole).
     * Een proxy telt niet mee voor legaliteit (zoals bij de deckcontrole), wel voor de kleuren.
     * Zonder bekende legaliteit (kaart uit de offline database) wordt er niets gemeld.
     */
    fun issue(
        format: String, legalities: String, colorIdentity: String,
        commanderIdentity: Set<Char>?, isCommander: Boolean, proxy: Boolean,
    ): DeckFitIssue? {
        if (format != DeckFormat.CASUAL && !proxy && legalities.isNotBlank()) {
            when (Formats.legalityOf(legalities, format)) {
                "banned" -> return DeckFitIssue.BANNED
                "not_legal" -> return DeckFitIssue.NOT_LEGAL
            }
        }
        if (format == DeckFormat.COMMANDER && !isCommander && commanderIdentity != null &&
            colorIdentity.any { it !in commanderIdentity }) return DeckFitIssue.COLORS
        return null
    }
}
