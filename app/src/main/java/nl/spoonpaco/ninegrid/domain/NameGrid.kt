package nl.spoonpaco.ninegrid.domain

/**
 * Het raster van een blad op de gevonden kaartnamen leggen (0.9.13).
 *
 * Zonder kader bepaalde de buitenste tekst het raster. Ligt het blad klein in beeld, of levert een rij geen tekst
 * op (splitkaarten op hun kant), dan schoof het raster een halve rij of kolom en lazen vakken de naam van het
 * buurvak (testset 18 nov: GRN, DOM, het gemengde blad). Een kaartnaam staat altijd bovenaan links op de kaart.
 * Zijn er bij een bekende set genoeg regels die duidelijk een naam uit die set zijn, dan geven hun posities de
 * rijen, de kolommen en de kaartgrootte, en ligt het raster ook vast waar niets gelezen is.
 */
object NameGrid {
    /** Zo goed moet een regel op een naam lijken om als anker te tellen. */
    const val ANCHOR_MIN = 0.85
    /** Minstens zoveel namen nodig, anders het oude raster. */
    const val MIN_ANCHORS = 3
    /** Rij- en kolomafstand ten opzichte van elkaar (kaart 88 x 63 mm). */
    private const val CARD_RATIO = 1.4f
    /** De bovenkant van de naamregel ligt zoveel van een rij onder de bovenkant van het vak. */
    private const val NAME_TOP = 0.05f
    /** De linkerkant van de naamregel ligt zoveel van een kolom rechts van de linkerkant van het vak. */
    private const val NAME_LEFT = 0.07f

    data class Anchor(val name: String, val left: Float, val top: Float)

    /** De regels die duidelijk een kaartnaam uit [setNames] zijn (geen basislanden: die staan ook in regeltekst). */
    fun anchors(lines: List<OcrLine>, setNames: Collection<String>): List<Anchor> {
        val names = setNames.filterNot(NameMatch::isBasicLand).distinct()
        if (names.isEmpty()) return emptyList()
        return lines.mapNotNull { l ->
            if (l.text.count { it.isLetter() } < 4 || l.text.length > 40) return@mapNotNull null
            // Strenger dan voor het herkennen zelf: een anker moet bijna letterlijk een naam zijn, anders kan een
            // kort stukje regeltekst ("graveyard", "Draw a card") op een naam lijken en het raster verschuiven.
            NameMatch.best(l.text, names)?.takeIf { NameMatch.score(l.text, it) >= ANCHOR_MIN }?.let { Anchor(it, l.left, l.top) }
        }
    }

    /** Groepeert waarden die dichter dan [tol] bij elkaar liggen; geeft het gemiddelde per groep, oplopend. */
    internal fun cluster(values: List<Float>, tol: Float): List<Float> {
        val sorted = values.sorted()
        val groups = mutableListOf(mutableListOf(sorted.first()))
        for (v in sorted.drop(1)) if (v - groups.last().last() > tol) groups += mutableListOf(v) else groups.last() += v
        return groups.map { g -> g.average().toFloat() }
    }

    /** De kleinste afstand tussen opeenvolgende groepen, of null bij één groep. */
    private fun pitchOf(groups: List<Float>): Float? = groups.zipWithNext { a, b -> b - a }.minOrNull()

    /**
     * Het raster (links, boven, rechts, onder) uit de namen, of null als dat niet betrouwbaar kan: te weinig namen,
     * of maten die niet bij een blad van 3 x 3 kaarten passen.
     */
    fun frame(lines: List<OcrLine>, setNames: Collection<String>, width: Int, height: Int): Area? {
        // Een naam kan ook in regeltekst staan ("Sacrifice Compulsion: Draw a card"); de naamregel is dan de bovenste.
        val anchors = anchors(lines, setNames).groupBy { it.name }.map { (_, a) -> a.minByOrNull { it.top }!! }
        if (anchors.size < MIN_ANCHORS) return null
        val rows = cluster(anchors.map { it.top }, height * 0.05f)
        val cols = cluster(anchors.map { it.left }, width * 0.05f)
        if (rows.size > 3 || cols.size > 3) return null
        var py0 = pitchOf(rows)
        var px0 = pitchOf(cols)
        // Alleen rij 1 en 3 (of kolom 1 en 3) met namen: de afstand is dan twee kaarten. Herken dat aan de verhouding.
        if (py0 != null && px0 != null) {
            val r = py0 / px0
            if (r > CARD_RATIO * 1.6f) py0 /= 2f
            if (r < CARD_RATIO / 1.6f) px0 /= 2f
        }
        val py: Float = py0 ?: px0?.let { it * CARD_RATIO } ?: return null
        val px: Float = px0 ?: py / CARD_RATIO
        // Past het niet in de foto als blad van 3 x 3, dan niet.
        if (px * 3f > width * 1.05f || py * 3f > height * 1.05f || px < width * 0.06f || py < height * 0.04f) return null
        // Elke rij krijgt een plek 0..2; de groepen moeten op hele kaartafstanden liggen.
        val rowIdx = rows.map { Math.round((it - rows.first()) / py) }
        val colIdx = cols.map { Math.round((it - cols.first()) / px) }
        if (rowIdx.last() > 2 || colIdx.last() > 2) return null
        // Welke rij en kolom is de eerste groep? Alleen rasters binnen de foto (als dat kan). Daarna het raster waar de
        // meeste tekst in valt, maar een paar losse regels naast het blad (een logo, de rand van de map) mogen niet
        // beslissen: binnen 10% van het maximum wint het raster dat het dichtst bij het midden van de foto ligt.
        fun inside(a: Area) = lines.count { a.contains(it.centerX, it.centerY) }
        fun outside(a: Area) = a.left < -width * 0.03f || a.top < -height * 0.03f || a.right > width * 1.03f || a.bottom > height * 1.03f
        fun offCentre(a: Area) = Math.abs((a.left + a.right) / 2f - width / 2f) + Math.abs((a.top + a.bottom) / 2f - height / 2f)
        val tops = (0..(2 - rowIdx.last())).map { k -> rows.first() - k * py - NAME_TOP * py }
        val lefts = (0..(2 - colIdx.last())).map { k -> cols.first() - k * px - NAME_LEFT * px }
        val all = tops.flatMap { t -> lefts.map { l -> Area(l, t, l + 3 * px, t + 3 * py) } }
        val options = all.filterNot(::outside).ifEmpty { all }
        val most = options.maxOf { inside(it) }
        val chosen = options.filter { inside(it) >= most * 0.9f }.minByOrNull(::offCentre) ?: return null
        // Controle: elke naam hoort bovenaan zijn vak te staan. Klopt dat voor minder dan 3 op de 4, dan liever het oude raster.
        val cellW = chosen.width / 3f; val cellH = chosen.height / 3f
        val fitting = anchors.count { a ->
            val inCell = (a.top - chosen.top) - Math.floor(((a.top - chosen.top) / cellH).toDouble()).toFloat() * cellH
            val inCellX = (a.left - chosen.left) - Math.floor(((a.left - chosen.left) / cellW).toDouble()).toFloat() * cellW
            inCell < cellH * 0.3f && inCellX < cellW * 0.4f
        }
        return chosen.takeIf { fitting * 4 >= anchors.size * 3 }
    }
}
