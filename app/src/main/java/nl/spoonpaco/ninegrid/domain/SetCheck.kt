package nl.spoonpaco.ninegrid.domain

/** Een opgegeven of gelezen set is een grens: een kaart uit een andere set is altijd om te controleren. */
object SetCheck {
    /** De gevonden kaart staat niet in de gevraagde set. Zonder gevraagde set is er niets om tegen te vergelijken. */
    fun otherSet(wantedSet: String?, foundSet: String): Boolean = wantedSet != null && !foundSet.equals(wantedSet, true)

    /** Geen nummer gelezen en meerdere prints met die naam in de set: de gekozen print is een gok. */
    fun ambiguous(numberRead: Boolean, printsWithName: Int): Boolean = !numberRead && printsWithName >= 2
}
