package nl.spoonpaco.ninegrid.domain

/**
 * Regels voor wie commander mag zijn en welke twee samen mogen. Gebaseerd op de typeregel en de regeltekst
 * van de kaart (Commander-regels van Wizards), zonder lijst van banned kaarten.
 */
object CommanderRules {
    fun canBeCommander(typeLine: String, oracleText: String): Boolean {
        val front = typeLine.substringBefore("//").lowercase()
        val text = oracleText.lowercase()
        if ("can be your commander" in text) return true
        if (!front.contains("legendary")) return false
        return front.contains("creature") || front.contains("vehicle") || front.contains("spacecraft") || front.contains("background")
    }

    private val partnerWith = Regex("""partner with ([^(\n]+)""", RegexOption.IGNORE_CASE)

    /** Soort partnerschap dat de kaart heeft, of leeg. */
    enum class Pairing { NONE, PARTNER, PARTNER_WITH, FRIENDS_FOREVER, BACKGROUND_WANTED, BACKGROUND, DOCTOR, DOCTORS_COMPANION }

    fun pairings(name: String, typeLine: String, oracleText: String): Set<Pairing> {
        val text = oracleText.lowercase(); val type = typeLine.lowercase()
        val out = mutableSetOf<Pairing>()
        if (partnerWith.containsMatchIn(oracleText)) out += Pairing.PARTNER_WITH
        else if (Regex("""(^|\n)partner\b""").containsMatchIn(text) || "partner (you can have two commanders" in text) out += Pairing.PARTNER
        if ("friends forever" in text) out += Pairing.FRIENDS_FOREVER
        if ("choose a background" in text) out += Pairing.BACKGROUND_WANTED
        if (type.contains("background")) out += Pairing.BACKGROUND
        if ("doctor's companion" in text) out += Pairing.DOCTORS_COMPANION
        if (type.contains("time lord") && type.contains("doctor")) out += Pairing.DOCTOR
        return out
    }

    /** Mogen deze twee kaarten samen commander zijn? */
    fun canPair(aName: String, aType: String, aText: String, bName: String, bType: String, bText: String): Boolean {
        val a = pairings(aName, aType, aText); val b = pairings(bName, bType, bText)
        if (Pairing.PARTNER in a && Pairing.PARTNER in b) return true
        if (Pairing.FRIENDS_FOREVER in a && Pairing.FRIENDS_FOREVER in b) return true
        if ((Pairing.BACKGROUND_WANTED in a && Pairing.BACKGROUND in b) || (Pairing.BACKGROUND in a && Pairing.BACKGROUND_WANTED in b)) return true
        if ((Pairing.DOCTORS_COMPANION in a && Pairing.DOCTOR in b) || (Pairing.DOCTOR in a && Pairing.DOCTORS_COMPANION in b)) return true
        // "Partner with X": alleen met precies die kaart.
        fun named(text: String) = partnerWith.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', ' ')
        val an = named(aText); val bn = named(bText)
        if (an != null && an.equals(bName, true) && bn != null && bn.equals(aName, true)) return true
        return false
    }
}
