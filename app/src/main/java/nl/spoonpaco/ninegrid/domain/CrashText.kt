package nl.spoonpaco.ninegrid.domain

/**
 * Tekst van een crashrapport: versie, toestel, de foutmelding met de regels code. Bevat geen kaarten, namen of andere
 * gegevens van de gebruiker. Alleen opmaak; het opslaan en delen staat in data/CrashReport.kt.
 */
object CrashText {
    const val MAX_TRACE = 6000

    fun build(version: String, device: String, android: String, thread: String, whenText: String, trace: String): String = buildString {
        appendLine("NineGrid crashrapport")
        appendLine("versie: $version · toestel: $device · Android $android")
        appendLine("tijd: $whenText · thread: $thread")
        appendLine()
        append(trace.take(MAX_TRACE))
        if (trace.length > MAX_TRACE) append("\n… (ingekort)")
        appendLine()
    }

    /** De stacktrace van een fout met alle oorzaken, als tekst. */
    fun traceOf(t: Throwable): String = buildString {
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 5) {
            if (depth > 0) append("Caused by: ")
            appendLine(cur.toString())
            cur.stackTrace.take(25).forEach { appendLine("  at $it") }
            cur = cur.cause?.takeIf { it !== cur }
            depth++
        }
    }
}
