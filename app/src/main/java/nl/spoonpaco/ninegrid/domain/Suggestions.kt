package nl.spoonpaco.ninegrid.domain

import kotlinx.serialization.Serializable
import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.data.db.Section

/** Eigen regels per deck, bijvoorbeeld "alleen vampire creatures" of "hoogstens bracket 3". */
@Serializable
data class DeckRules(
    /** Alleen creatures van dit type (bijv. "Vampire"), of leeg. */
    val tribe: String = "",
    /** Hoogste bracket die het deck mag hebben (1–4), of 0 = geen grens. */
    val maxBracket: Int = 0,
    /** Ook kaarten voorstellen die al in een ander deck zitten (niet alleen losse kaarten). */
    val includeOtherDecks: Boolean = false,
    /** Eén kleur buiten het deck toestaan, als de lands in het deck die kleur al maken (niet bij Commander). */
    val splash: Boolean = false,
)

/** Een kaart die je zou kunnen gebruiken, met de reden. */
data class Candidate(
    val key: Long,
    val name: String,
    val typeLine: String,
    val oracleText: String,
    val manaCost: String,
    val cmc: Double,
    val colorIdentity: String,
    val legalities: String,
    val gameChanger: Boolean,
    /** Waar hij nu ligt (collectie of ander deck), voor op het scherm. */
    val where: String? = null,
    /** Welke mana hij maakt (Scryfall), leeg = onbekend. */
    val producedMana: String = "",
)

enum class Role { RAMP, DRAW, REMOVAL, WIPE, COUNTER, PROTECTION, TOKENS, RECURSION }

data class Suggestion(val card: Candidate, val score: Double, val reasons: List<String>)

data class Cut(val name: String, val entryKey: Long, val score: Double, val reasons: List<String>)

data class SuggestionReport(
    val adds: List<Suggestion>,
    val cuts: List<Cut>,
    val lands: List<Suggestion>,
    /** Hoeveel kaarten het deck per rol heeft tegenover het streefgetal. */
    val roles: Map<Role, Pair<Int, Int>>,
    val tribe: String,
    /** Met welke kleuren gerekend is (WUBRG-volgorde), en welke er als splash mogen. */
    val colors: String = "",
    val splashColors: String = "",
)

/**
 * Suggesties per deck uit je eigen collectie: wat erin past en wat eruit kan, binnen het format, de kleuren van de
 * commander, de bracket en je eigen regels. Puur rekenwerk op de regeltekst, zonder internet.
 * Redenen zijn codes (bijv. "tribe:Vampire", "role:DRAW", "theme:tokens") die het scherm vertaalt.
 */
object Suggestions {
    private val basics = setOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes")
    private val basicType = mapOf("Plains" to 'W', "Island" to 'U', "Swamp" to 'B', "Mountain" to 'R', "Forest" to 'G')
    private const val WUBRG = "WUBRG"
    private val reSymbol = Regex("""\{([wubrg])\}""")

    /**
     * Welke kleuren een land kan opleveren. Eerst wat Scryfall zegt (produced_mana), anders typeregel en tekst.
     * Fetchlands zoeken een landtype ("Island or Swamp") en tellen als die kleuren. "Any color" en lands die
     * "a basic land card" zoeken (Evolving Wilds) passen bij elke kleur: dan alle vijf.
     */
    fun landColors(typeLine: String, oracleText: String, producedMana: String, colorIdentity: String): Set<Char> {
        val t = oracleText.lowercase().replace('\n', ' ')
        if (t.contains("mana of any color") || t.contains("basic land card") || t.contains("mana of any one color")) return WUBRG.toSet()
        val out = mutableSetOf<Char>()
        producedMana.filter { it in WUBRG }.forEach { out += it }
        basicType.forEach { (k, c) -> if (typeLine.contains(k)) out += c }
        // Elke zin waarin gezocht wordt: alle landtypen die erin staan, ook met "en" (Krosan Verge: "a Forest card and a Plains card").
        t.split('.').filter { it.contains("search your library for") }.forEach { z -> basicType.forEach { (k, c) -> if (z.contains(k.lowercase())) out += c } }
        if (producedMana.isEmpty()) {
            t.split('.').filter { it.contains("add ") }.forEach { z -> reSymbol.findAll(z.substringAfter("add ")).forEach { out += it.groupValues[1].uppercase()[0] } }
            if (out.isEmpty() && typeLine.contains("Land")) colorIdentity.filter { it in WUBRG }.forEach { out += it }
        }
        return out
    }

    /**
     * Kleuren van het deck. Met een commander: diens kleurenidentiteit. Anders telt een kleur mee als minstens
     * 10% van de niet-landkaarten (op aantal) die kleur heeft, zodat één losse kaart het deck niet breder maakt.
     * Bij minder dan 10 niet-landkaarten zegt een percentage weinig: dan alle kleuren die erin zitten.
     */
    fun deckColors(deck: List<DeckCard>): Set<Char> {
        val cmd = deck.filter { it.section == Section.COMMANDER }
        if (cmd.isNotEmpty()) return cmd.flatMap { it.colorIdentity.toList() }.filter { it in WUBRG }.toSet()
        val spells = deck.filter { it.section != Section.SIDEBOARD && !it.typeLine.contains("Land") }
        val n = spells.sumOf { it.quantity }
        val all = spells.flatMap { it.colorIdentity.toList() }.filter { it in WUBRG }.toSet()
        if (n < 10) return all
        return all.filter { c -> spells.filter { c in it.colorIdentity }.sumOf { it.quantity } * 10 >= n }.toSet()
    }

    private fun order(c: Set<Char>) = WUBRG.filter { it in c }

    /** Streefgetallen per rol (Commander tegenover 60 kaarten). */
    private fun targets(commander: Boolean) = if (commander) mapOf(
        Role.RAMP to 10, Role.DRAW to 10, Role.REMOVAL to 8, Role.WIPE to 3, Role.COUNTER to 0, Role.PROTECTION to 3, Role.TOKENS to 0, Role.RECURSION to 2,
    ) else mapOf(
        Role.RAMP to 0, Role.DRAW to 4, Role.REMOVAL to 6, Role.WIPE to 0, Role.COUNTER to 0, Role.PROTECTION to 0, Role.TOKENS to 0, Role.RECURSION to 0,
    )

    // Vooraf gecompileerd. Accolades altijd escapen: Android (ICU) weigert een losse '}'.
    private val reRamp = Regex("""add \{[wubrgc]\}|add one mana|add two mana|mana of any color""")
    private val reDraw = Regex("""draw (a|two|three|x|that many) cards?|draws? cards? equal""")
    private val reRemoval = Regex("""(destroy|exile) target (creature|permanent|artifact|enchantment|nonland|planeswalker)|deals? \d+ damage to (target|any target)|target (creature|player) sacrifices""")
    private val reWipe = Regex("""(destroy|exile) all (creatures|nonland|permanents|other creatures)|all creatures get -""")
    private val reProtection = Regex("""hexproof|indestructible|protection from|phase out""")
    private val reTokens = Regex("""create (a|an|one|two|three|x|\w+) .*tokens?""")
    private val reRecursion = Regex("""return (target|up to|all) .* from your graveyard""")

    fun rolesOf(text: String, typeLine: String): Set<Role> {
        val t = text.lowercase().replace('\n', ' ')
        val out = mutableSetOf<Role>()
        val isLand = typeLine.contains("Land")
        if (!isLand && (reRamp.containsMatchIn(t) ||
                (t.contains("search your library for") && t.contains("land")))) out += Role.RAMP
        if (reDraw.containsMatchIn(t)) out += Role.DRAW
        if (reRemoval.containsMatchIn(t)) out += Role.REMOVAL
        if (reWipe.containsMatchIn(t)) out += Role.WIPE
        if (t.contains("counter target spell") || t.contains("counter target noncreature")) out += Role.COUNTER
        if (reProtection.containsMatchIn(t) && t.contains("you control")) out += Role.PROTECTION
        if (reTokens.containsMatchIn(t)) out += Role.TOKENS
        if (reRecursion.containsMatchIn(t)) out += Role.RECURSION
        return out
    }

    /** Thema-woorden die in de regeltekst van het deck vaak voorkomen. */
    private val themes = listOf(
        "token", "+1/+1 counter", "sacrifice", "graveyard", "lifelink", "gain life", "flying", "artifact", "enchantment",
        "instant or sorcery", "landfall", "proliferate", "populate", "discard", "mill", "attacks", "dies", "enters the battlefield",
        "treasure", "equip", "aura", "spell", "copy", "haste", "deathtouch", "trample", "-1/-1", "poison", "energy", "food", "clue",
    )

    private fun themeHits(text: String) = themes.filter { text.lowercase().contains(it) }

    private fun subtypes(typeLine: String): Set<String> =
        typeLine.substringBefore("//").substringAfter("—", "").split(' ').map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun isCreature(typeLine: String) = typeLine.substringBefore("//").contains("Creature")

    /** Welk creaturetype past bij het deck: van de commander, als het meeste creatures dat type hebben. */
    fun guessTribe(deck: List<DeckCard>): String {
        val creatures = deck.filter { isCreature(it.typeLine) }
        if (creatures.size < 8) return ""
        val counts = creatures.flatMap { c -> subtypes(c.typeLine).map { it to c.quantity } }.groupBy { it.first }.mapValues { it.value.sumOf { p -> p.second } }
        val total = creatures.sumOf { it.quantity }
        val commanderTypes = deck.filter { it.section == Section.COMMANDER }.flatMap { subtypes(it.typeLine) }.toSet()
        val best = counts.entries.filter { commanderTypes.isEmpty() || it.key in commanderTypes }.maxByOrNull { it.value } ?: return ""
        return if (best.value * 10 >= total * 6) best.key else ""
    }

    private fun bracketOf(c: Candidate) = when {
        Formats.massLandDenial(c.oracleText) -> 4
        c.gameChanger -> 3
        Formats.extraTurn(c.oracleText) || Formats.tutor(c.oracleText) -> 2
        else -> 1
    }

    fun suggest(
        format: String,
        deck: List<DeckCard>,
        deckEntryKeys: Map<String, Long>,
        pool: List<Candidate>,
        rules: DeckRules,
        max: Int = 15,
    ): SuggestionReport {
        val commander = format == DeckFormat.COMMANDER
        val inDeck = deck.filter { it.section != Section.SIDEBOARD }
        val names = inDeck.map { it.name }.toSet()
        val identity = deckColors(deck)
        // Splash: kleuren die de lands in het deck al maken, maar die (nog) geen deckkleur zijn. Niet bij Commander.
        val splash = if (!rules.splash || commander) emptySet() else
            inDeck.filter { it.typeLine.contains("Land") }.flatMap { landColors(it.typeLine, it.oracleText, it.producedMana, it.colorIdentity) }.toSet() - identity
        fun colorsOk(ci: String): Boolean {
            val extra = ci.filter { it in WUBRG }.toSet() - identity
            return extra.isEmpty() || (extra.size == 1 && extra.first() in splash)
        }
        val tribe = rules.tribe.trim()
        val gcInDeck = inDeck.count { it.gameChanger }

        // Rollen in het deck tegenover het streefgetal.
        val target = targets(commander)
        val have = Role.entries.associateWith { r -> inDeck.filter { r in rolesOf(it.oracleText, it.typeLine) }.sumOf { it.quantity } }
        val need = Role.entries.associateWith { r -> ((target[r] ?: 0) - (have[r] ?: 0)).coerceAtLeast(0) }
        // Thema's van het deck (hoe vaak elk woord voorkomt).
        val deckThemes = inDeck.flatMap { themeHits(it.oracleText) }.groupingBy { it }.eachCount()
        val avgCmc = inDeck.filter { !it.typeLine.contains("Land") }.let { l -> if (l.isEmpty()) 0.0 else l.sumOf { it.cmc * it.quantity } / l.sumOf { it.quantity } }

        fun allowed(c: Candidate): Boolean {
            if (c.name in names && commander) return false
            if (c.name in basics) return false
            val legal = Formats.legalityOf(c.legalities, format)
            if (format != DeckFormat.CASUAL && legal != null && legal != "legal") return false
            if (identity.isNotEmpty() && !colorsOk(c.colorIdentity)) return false
            // Tribal: alleen creatures van het gekozen type (andere kaartsoorten mogen wel).
            if (tribe.isNotEmpty() && isCreature(c.typeLine) && tribe !in subtypes(c.typeLine) && !c.oracleText.contains("all creature types", true)) return false
            if (rules.maxBracket in 1..2 && bracketOf(c) > rules.maxBracket) return false
            if (rules.maxBracket == 3 && (Formats.massLandDenial(c.oracleText) || (c.gameChanger && gcInDeck >= 3))) return false
            return true
        }

        fun score(name: String, typeLine: String, text: String, cmc: Double, isCandidate: Boolean): Pair<Double, List<String>> {
            var s = 0.0
            val why = mutableListOf<String>()
            if (tribe.isNotEmpty()) {
                if (isCreature(typeLine) && tribe in subtypes(typeLine)) { s += 3; why += "tribe:$tribe" }
                if (text.contains(tribe, true)) { s += 2; why += "tribe_text:$tribe" }
            }
            rolesOf(text, typeLine).forEach { r ->
                val n = need[r] ?: 0
                if (isCandidate && n > 0) { s += 2.5 + minOf(n, 4) * 0.25; why += "role:${r.name}" }
                else if (!isCandidate) s += if ((have[r] ?: 0) <= (target[r] ?: 0)) 2.0 else 0.5
            }
            val hits = themeHits(text).filter { (deckThemes[it] ?: 0) >= 3 }
            if (hits.isNotEmpty()) { s += minOf(hits.size, 3) * 1.2; why += hits.take(2).map { "theme:$it" } }
            // Een zwaar deck heeft baat bij goedkopere kaarten.
            if (!typeLine.contains("Land")) {
                if (avgCmc > 3.4 && cmc <= 2) { s += 0.8; if (isCandidate) why += "cheap" }
                if (cmc >= 6) s -= 0.8
            }
            return s to why
        }

        val adds = pool.filter { !it.typeLine.contains("Land") && allowed(it) }
            .distinctBy { it.name }
            .map { c -> val (sc, why) = score(c.name, c.typeLine, c.oracleText, c.cmc, true); Suggestion(c, sc, why) }
            .filter { it.score >= 2.5 && it.reasons.isNotEmpty() }
            .sortedByDescending { it.score }
            .take(max)

        // Eruit: niet-land, niet-commander, de laagste score; tribal-regel overtreden telt zwaar.
        val cuts = inDeck.filter { it.section != Section.COMMANDER && !it.typeLine.contains("Land") }
            .distinctBy { it.name }
            .map { d ->
                var (sc, _) = score(d.name, d.typeLine, d.oracleText, d.cmc, false)
                val why = mutableListOf<String>()
                if (tribe.isNotEmpty() && isCreature(d.typeLine) && tribe !in subtypes(d.typeLine)) { sc -= 5; why += "not_tribe:$tribe" }
                if (rules.maxBracket in 1..3 && d.gameChanger && rules.maxBracket < 3) { sc -= 4; why += "over_bracket" }
                if (rules.maxBracket in 1..3 && Formats.massLandDenial(d.oracleText)) { sc -= 4; why += "over_bracket" }
                if (d.cmc >= 6) why += "expensive"
                if (rolesOf(d.oracleText, d.typeLine).isEmpty() && themeHits(d.oracleText).none { (deckThemes[it] ?: 0) >= 3 }) why += "no_role"
                Cut(d.name, deckEntryKeys[d.name] ?: -1, sc, why)
            }
            .filter { it.reasons.isNotEmpty() }
            .sortedBy { it.score }
            .take(max)

        // Lands: niet-basic lands uit je collectie die twee of meer van je kleuren maken, voor in plaats van een basic land.
        val basicsInDeck = inDeck.filter { Formats.isBasicLand(it.typeLine) }.sumOf { it.quantity }
        val lands = if (basicsInDeck == 0) emptyList() else pool.filter { it.typeLine.contains("Land") && !Formats.isBasicLand(it.typeLine) && allowed(it) }
            .distinctBy { it.name }
            .mapNotNull { c ->
                val makes = landColors(c.typeLine, c.oracleText, c.producedMana, c.colorIdentity)
                val any = makes.size == 5
                val mine = makes.count { it in identity }
                val fits = if (any) identity.size >= 2 else mine >= 2 && makes.all { it in identity || it in splash }
                if (!fits) null else Suggestion(c, if (any) 1.8 else 1.5 + mine * 0.3, listOf("land_fix"))
            }
            .sortedByDescending { it.score }
            .take(8)

        return SuggestionReport(adds, cuts, lands, Role.entries.associateWith { (have[it] ?: 0) to (target[it] ?: 0) }, tribe, order(identity), order(splash))
    }
}
