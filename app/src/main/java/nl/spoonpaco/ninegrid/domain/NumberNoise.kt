package nl.spoonpaco.ninegrid.domain

/**
 * 0.9.18-voorstel, in 0.9.17 (Mick 23 nov "2"): een gelezen nummer dat duidelijk ruis is.
 *
 * Testset 23 nov: ongeveer 20 kaarten bleven oranje terwijl de naam goed gelezen was, omdat er een nummer als
 * "1", "10" of "440" gelezen was dat bij een andere kaart (of geen kaart) hoort. Dan is het nummer ruis als:
 * - de gelezen naam (zoals gelezen, niet verbeterd) sterk lijkt op de kaart (80%),
 * - die kaart maar één print in de set heeft (dan hoeft het nummer niets te kiezen),
 * - en de kaart bij het nummer niet bestaat, of een duidelijk andere naam heeft die nergens in de gelezen regels staat.
 * Dat laatste vangt het geval waarin de naam van het buurvak komt en het nummer wél klopt.
 */
object NumberNoise {
    fun isNoise(readName: String, cardName: String, printsInSet: Int, numberCardName: String?, lines: List<String>): Boolean {
        if (printsInSet != 1) return false
        if (NameMatch.score(readName, cardName) < 0.8) return false
        if (numberCardName == null) return true
        if (numberCardName.equals(cardName, true) || NameMatch.score(readName, numberCardName) >= 0.5) return false
        return lines.none { NameMatch.score(it, numberCardName) >= 0.7 }
    }
}
