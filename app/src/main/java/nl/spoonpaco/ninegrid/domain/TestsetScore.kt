package nl.spoonpaco.ninegrid.domain

/** Vergelijkt wat de scanner las met de juiste uitslag (uitslag.txt) van een testblad. */
object TestsetScore {
    data class Expect(val empty: Boolean, val name: String?, val set: String?, val number: String?)

    /** Uitslag van één blad: per vak 1..9 de verwachte kaart. Regels: "vak 3: Naam (SET nr)", "vak 4: Naam" of "vak 5: leeg". */
    fun parse(lines: List<String>): Map<Int, Expect> = lines.filter { it.startsWith("vak ") }.mapNotNull { l ->
        val slot = l.substringAfter("vak ").substringBefore(":").trim().toIntOrNull() ?: return@mapNotNull null
        val body = l.substringAfter(":").trim()
        if (body.startsWith("leeg")) return@mapNotNull slot to Expect(true, null, null, null)
        val m = Regex("""^(.*?)\s*\(([A-Za-z0-9]+)\s+([^\s,)?]+)""").find(body)
        slot to (if (m != null) Expect(false, m.groupValues[1], m.groupValues[2].uppercase(), m.groupValues[3])
        else Expect(false, body.substringBefore("(").trim(), null, null))
    }.toMap()

    private fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    /** Gelijkenis 0..1 tussen twee namen (bewerkingsafstand); OCR verleest letters, de app corrigeert de naam later met de database. */
    fun similarity(a: String, b: String): Double {
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val d = Array(x.length + 1) { IntArray(y.length + 1) }
        for (i in 0..x.length) d[i][0] = i
        for (j in 0..y.length) d[0][j] = j
        for (i in 1..x.length) for (j in 1..y.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        return 1.0 - d[x.length][y.length].toDouble() / maxOf(x.length, y.length)
    }

    data class Verdict(val ok: Boolean, val nameOk: Boolean, val setOk: Boolean, val numberOk: Boolean)

    fun judge(e: Expect, r: SlotRead?): Verdict =
        judge(e, r?.name, r?.setCode, r?.collectorNumber, r == null || (r.name == null && r.collectorNumber == null))

    /** Naam, set en nummer zoals gelezen (of na het opzoeken); [blank] = er is niets gelezen of gevonden. */
    fun judge(e: Expect, name: String?, set: String?, number: String?, blank: Boolean): Verdict {
        if (e.empty) return Verdict(blank, blank, blank, blank)
        // Een splitkaart heet "A // B": de eerste helft of de hele naam mag gelijken.
        val nameOk = name != null && e.name != null &&
            (similarity(name, e.name) >= 0.8 || name.split("//").any { similarity(it, e.name) >= 0.8 } || e.name.split("//").any { similarity(name, it) >= 0.8 })
        val setOk = e.set == null || set.equals(e.set, true)
        val numberOk = e.number == null || number?.trimStart('0') == e.number.trimStart('0')
        return Verdict(nameOk && setOk && numberOk, nameOk, setOk, numberOk)
    }

    fun describe(e: Expect) = if (e.empty) "leeg" else "${e.name}${if (e.set != null) " (${e.set} ${e.number})" else ""}"
    fun describe(r: SlotRead?) = if (r == null || (r.name == null && r.collectorNumber == null && r.setCode == null)) "niets" else "${r.name ?: "?"} (${r.setCode ?: "?"} ${r.collectorNumber ?: "?"})"
}
