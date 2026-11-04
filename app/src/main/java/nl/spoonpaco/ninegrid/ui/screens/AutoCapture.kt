package nl.spoonpaco.ninegrid.ui.screens

/**
 * Doorlopend scannen bij 1 kaart: beslist wanneer de app zelf een foto maakt.
 * Ligt er een kaart stil (dezelfde gelezen naam gedurende [stableMs]), dan foto. Daarna pas weer bij een
 * andere kaart, of bij dezelfde kaart nadat het kader even leeg was (dan telt hij er één bij).
 */
class AutoCapture(var stableMs: Long, private val onTrigger: () -> Unit) {
    private var candidate: String? = null
    private var since = 0L
    private var lastCaptured: String? = null
    private var emptyStart = 0L
    private var wasEmpty = true
    var lastCardSeen = System.currentTimeMillis()
        private set
    /** Bezig met een foto of met lezen: dan niet nog een keer. */
    var busy = false

    fun reset() { candidate = null; lastCaptured = null; wasEmpty = true; lastCardSeen = System.currentTimeMillis() }

    /** Eén beeld van de camera, met de gelezen naam (of null als er geen kaart in het kader ligt). */
    fun onFrame(name: String?, now: Long = System.currentTimeMillis()) {
        if (name == null) {
            candidate = null
            if (emptyStart == 0L) emptyStart = now
            // Pas als "leeg" tellen na een korte tijd, zodat één onleesbaar beeld niet meteen een nieuwe kaart is.
            if (now - emptyStart > 400) wasEmpty = true
            return
        }
        emptyStart = 0L
        lastCardSeen = now
        if (name != candidate) { candidate = name; since = now; return }
        if (busy || now - since < stableMs) return
        if (name != lastCaptured || wasEmpty) {
            lastCaptured = name
            wasEmpty = false
            onTrigger()
        }
    }

    /** Twee minuten geen kaart gezien: meekijken stoppen om de batterij te sparen. */
    fun idle(now: Long = System.currentTimeMillis()) = now - lastCardSeen > 120_000
}

/** De volumeknoppen als ontspanknop zolang het camerascherm open is. */
object ScanKeys {
    @Volatile var onShutter: (() -> Unit)? = null
}
