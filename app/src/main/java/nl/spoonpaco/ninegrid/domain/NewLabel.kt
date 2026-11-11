package nl.spoonpaco.ninegrid.domain

/** Wanneer een kaart het label NIEUW krijgt. Alleen losse kaarten in de collectie; kaarten in een deck niet. */
object NewLabel {
    const val DAY = 86_400_000L
    /** De keuzes voor hoelang het label blijft, in dagen; 0 = uit. */
    val CHOICES = listOf(1, 3, 7, 14, 30, 0)
    const val DEFAULT_DAYS = 7

    /**
     * [addedAt]: wanneer de kaart is toegevoegd. [seenAt]: sinds wanneer alles als gezien telt (knop Alles gezien);
     * alleen wat daarna is toegevoegd is nieuw.
     */
    fun isNew(addedAt: Long, deckId: Long?, now: Long, days: Int, seenAt: Long): Boolean =
        days > 0 && deckId == null && addedAt > seenAt && now - addedAt < days * DAY
}
