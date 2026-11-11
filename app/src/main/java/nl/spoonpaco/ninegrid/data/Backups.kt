package nl.spoonpaco.ninegrid.data

import android.content.Context

/**
 * De complete back-up: alles uit de database (via [Repository]) plus wat buiten de database staat:
 * de instellingen, en per deck de regels, de open/dichte groepen en de sortering.
 * Bewust niet in de back-up: de AI-sleutel (die blijft alleen op de telefoon), de offline kaartdatabase
 * en de plaatjes (die zijn opnieuw te downloaden).
 */
object Backups {
    private const val RULES = "deck_rules"
    private const val DECK_UI = "deck_ui"
    private const val SEP = "\u001f"

    suspend fun create(context: Context, repo: Repository, settings: SettingsStore): BackupFile = repo.exportBackup().copy(
        settings = settings.exportForBackup(),
        deckRules = readPrefs(context, RULES),
        deckUi = readPrefs(context, DECK_UI),
    )

    suspend fun toJson(context: Context, repo: Repository, settings: SettingsStore): Pair<BackupFile, String> {
        val b = create(context, repo, settings)
        return b to repo.backupToJson(b)
    }

    /** Zet de gekozen delen terug. Deckregels horen bij de decks. */
    suspend fun restore(context: Context, repo: Repository, settings: SettingsStore, b: BackupFile, parts: Set<BackupPart>) {
        repo.restoreBackup(b, parts)
        if (BackupPart.DECKS in parts) {
            b.deckRules?.let { writePrefs(context, RULES, it) }
            b.deckUi?.let { writePrefs(context, DECK_UI, it) }
        }
        if (BackupPart.SETTINGS in parts) b.settings?.let { settings.importFromBackup(it) }
    }

    /** Een instellingenbestand als tekst: "s:tekst" of "set:a\u001fb" voor een verzameling. */
    private fun readPrefs(context: Context, name: String): Map<String, String> =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
            when (v) {
                is String -> k to "s:$v"
                is Set<*> -> k to "set:" + v.joinToString(SEP)
                else -> null
            }
        }.toMap()

    private fun writePrefs(context: Context, name: String, values: Map<String, String>) {
        val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (k, tv) ->
            when {
                tv.startsWith("s:") -> e.putString(k, tv.substring(2))
                tv.startsWith("set:") -> e.putStringSet(k, tv.substring(4).split(SEP).filter { it.isNotEmpty() }.toSet())
            }
        }
        e.apply()
    }
}
