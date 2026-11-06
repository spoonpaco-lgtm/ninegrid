package nl.spoonpaco.ninegrid.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object ImageMode {
    const val OFF = "off"
    const val SMALL = "small"
    const val FULL = "full"
}

data class AppSettings(
    val theme: String = "system",
    val currency: String = "EUR",
    val imageMode: String = ImageMode.SMALL,
    val wifiOnly: Boolean = false,
    val onboardingDone: Boolean = false,
    val backupFolder: String? = null,
    val lastBackup: Long = 0,
    val lastUpdateCheck: Long = 0,
    val lastPriceUpdate: Long = 0,
    /** Grafiek: welke lijnen aan staan (sleutels zoals total, deck:3, set:MH1), waarde of aantal, en de periode. */
    val chartLines: List<String> = listOf("total"),
    val chartMetric: String = "value",
    val chartRange: String = "3M",
    /** Scannen: een blad van 9 ("page") of losse kaarten ("single"). */
    val scanMode: String = "page",
    /** Al een keer gevraagd om een back-upmap. */
    val backupAsked: Boolean = false,
    /** Na elke scan tonen hoe lang de herkenning duurde. */
    val showTiming: Boolean = false,
    /** Trillen als de foto genomen is. */
    val haptics: Boolean = true,
    /** Sluitergeluid (standaard uit). */
    val shutterSound: Boolean = false,
    /** Doorlopend scannen bij 1 kaart: zelf de foto maken als de kaart stilligt. */
    val autoCapture: Boolean = true,
    /** Hoe lang de kaart stil moet liggen: fast (0,3 s), normal (0,5 s) of sure (1 s). */
    val autoSpeed: String = "normal",
    /** Melding bij een Breakout: een kaart die op één dag flink stijgt. */
    val breakoutAlerts: Boolean = true,
    val lastBreakoutDay: String = "",
    /** De versie waarvan "Wat is nieuw" al is getoond. */
    val lastSeenVersion: String = "",
    /** Naam waaronder je deelt (gevraagd bij de eerste keer), en je vaste code als deler. */
    val shareName: String = "",
    val shareId: String = "",
    /** Updates op de achtergrond downloaden: "wifi" (standaard), "any" (ook mobiel) of "off". */
    val updateAuto: String = "wifi",
    /** Laatste zoekopdrachten bij Alle kaarten, nieuwste eerst. */
    val recentSearches: List<String> = emptyList(),
    /** Offline kaartdatabase: off, newset (bij nieuwe set) of manual. */
    val offlineDb: String = OfflineDb.OFF,
    val offlineDbUpdated: Long = 0,
    val offlineDbCount: Int = 0,
    /** Al een keer aangeboden (bij een grote import of een wachttijd van Scryfall). */
    val offlineDbOffered: Boolean = false,
    /** Offline kaartdatabase ook via mobiele data (Nu bijwerken, met bevestiging), en eventueel ook automatisch. */
    val offlineDbMobile: Boolean = false,
    val offlineDbMobileAuto: Boolean = false,
    /** De laatst opgegeven set in de scanner. */
    val lastScanSet: String = "",
    val foilTipShown: Boolean = false,
    /** Datum en tijd op het scherm (alleen weergave). */
    val timeFormat: String = "system",
    val dateFormat: String = "system",
    val weekStart: String = "system",
    val showWeekday: Boolean = false,
    /** Onthouden filters (JSON) en sortering per scherm. */
    val filters: Map<String, String> = emptyMap(),
    val collectionSort: String = "set",
    /** Tabblad Decks: format (per format gegroepeerd), value, name, count of updated. */
    val decksSort: String = "format",
    /** Completionist: compleetheid van een set telt alleen de losse collectie, niet wat in decks zit. */
    val completionist: Boolean = false,
    /** Magic-sfeer: foil-glans, verbannen-animatie en stempels (uit te zetten). */
    val effects: Boolean = true,
    /** Accentkleur: leeg = standaard (amber), anders één of twee manakleuren, bijv. "U" of "UR". */
    val accent: String = "",
    /** Privacymodus: bedragen verborgen (behalve de ruilmatch), en in welke stijl. */
    val privacy: Boolean = false,
    val privacyStyle: String = "compleated",
    /** Boosters: kaarten onder dit bedrag tellen als bulk; laatst betaalde prijs en type. */
    val bulkThreshold: Double = 0.25,
    val lastBoosterPrice: Double = 0.0,
    val lastBoosterType: String = "",
    /** AI-hulp (standaard uit): dienst, model, eigen adres, maximum per dag en het gebruik van vandaag. */
    val aiEnabled: Boolean = false,
    val aiProvider: String = "gemini",
    val aiModel: String = "",
    val aiBaseUrl: String = "",
    val aiDailyLimit: Int = 50,
    val aiCountDay: String = "",
    val aiCount: Int = 0,
) {
    val dateStyle get() = nl.spoonpaco.ninegrid.domain.DateStyle(timeFormat, dateFormat, weekStart, showWeekday)
}

object OfflineDb {
    const val OFF = "off"
    const val NEW_SET = "newset"
    const val MANUAL = "manual"
}

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val currency = stringPreferencesKey("currency")
        val imageMode = stringPreferencesKey("image_mode")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val backupFolder = stringPreferencesKey("backup_folder")
        val lastBackup = longPreferencesKey("last_backup")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val lastPriceUpdate = longPreferencesKey("last_price_update")
        val chartLines = stringPreferencesKey("chart_lines")
        val chartMetric = stringPreferencesKey("chart_metric")
        val chartRange = stringPreferencesKey("chart_range")
        val scanMode = stringPreferencesKey("scan_mode")
        val backupAsked = booleanPreferencesKey("backup_asked")
        val showTiming = booleanPreferencesKey("show_timing")
        val haptics = booleanPreferencesKey("haptics")
        val shutterSound = booleanPreferencesKey("shutter_sound")
        val autoCapture = booleanPreferencesKey("auto_capture")
        val autoSpeed = stringPreferencesKey("auto_speed")
        val breakoutAlerts = booleanPreferencesKey("breakout_alerts")
        val lastBreakoutDay = stringPreferencesKey("last_breakout_day")
        val lastSeenVersion = stringPreferencesKey("last_seen_version")
        val shareName = stringPreferencesKey("share_name")
        val shareId = stringPreferencesKey("share_id")
        val updateAuto = stringPreferencesKey("update_auto")
        val recentSearches = stringPreferencesKey("recent_searches")
        val offlineDb = stringPreferencesKey("offline_db")
        val offlineDbUpdated = longPreferencesKey("offline_db_updated")
        val offlineDbCount = androidx.datastore.preferences.core.intPreferencesKey("offline_db_count")
        val offlineDbOffered = booleanPreferencesKey("offline_db_offered")
        val lastScanSet = stringPreferencesKey("last_scan_set")
        val offlineDbMobile = booleanPreferencesKey("offline_db_mobile")
        val offlineDbMobileAuto = booleanPreferencesKey("offline_db_mobile_auto")
        val foilTipShown = booleanPreferencesKey("foil_tip_shown")
        val timeFormat = stringPreferencesKey("time_format")
        val dateFormat = stringPreferencesKey("date_format")
        val weekStart = stringPreferencesKey("week_start")
        val showWeekday = booleanPreferencesKey("show_weekday")
        val collectionSort = stringPreferencesKey("collection_sort")
        val decksSort = stringPreferencesKey("decks_sort")
        val completionist = booleanPreferencesKey("completionist")
        val effects = booleanPreferencesKey("effects")
        val accent = stringPreferencesKey("accent")
        val privacy = booleanPreferencesKey("privacy")
        val privacyStyle = stringPreferencesKey("privacy_style")
        val bulkThreshold = androidx.datastore.preferences.core.doublePreferencesKey("bulk_threshold")
        val lastBoosterPrice = androidx.datastore.preferences.core.doublePreferencesKey("last_booster_price")
        val lastBoosterType = stringPreferencesKey("last_booster_type")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiModel = stringPreferencesKey("ai_model")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiDailyLimit = androidx.datastore.preferences.core.intPreferencesKey("ai_daily_limit")
        val aiCountDay = stringPreferencesKey("ai_count_day")
        val aiCount = androidx.datastore.preferences.core.intPreferencesKey("ai_count")
        val filterScreens = FILTER_SCREENS
        val filters = filterScreens.associateWith { stringPreferencesKey("filter_$it") }
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        theme = this[K.theme] ?: "system",
        currency = this[K.currency] ?: "EUR",
        imageMode = this[K.imageMode] ?: ImageMode.SMALL,
        wifiOnly = this[K.wifiOnly] ?: false,
        onboardingDone = this[K.onboarding] ?: false,
        backupFolder = this[K.backupFolder],
        lastBackup = this[K.lastBackup] ?: 0,
        lastUpdateCheck = this[K.lastUpdateCheck] ?: 0,
        lastPriceUpdate = this[K.lastPriceUpdate] ?: 0,
        chartLines = this[K.chartLines]?.split(",")?.filter { it.isNotBlank() } ?: listOf("total"),
        chartMetric = this[K.chartMetric] ?: "value",
        chartRange = this[K.chartRange] ?: "3M",
        scanMode = this[K.scanMode] ?: "page",
        backupAsked = this[K.backupAsked] ?: false,
        // Nieuwe installaties: standaard uit. Wie al bezig was (en nooit koos), houdt ze aan zoals voorheen.
        showTiming = this[K.showTiming] ?: (this[K.onboarding] == true),
        haptics = this[K.haptics] ?: true,
        shutterSound = this[K.shutterSound] ?: false,
        autoCapture = this[K.autoCapture] ?: true,
        autoSpeed = this[K.autoSpeed] ?: "normal",
        breakoutAlerts = this[K.breakoutAlerts] ?: true,
        lastBreakoutDay = this[K.lastBreakoutDay] ?: "",
        lastSeenVersion = this[K.lastSeenVersion] ?: "",
        shareName = this[K.shareName] ?: "",
        shareId = this[K.shareId] ?: "",
        updateAuto = this[K.updateAuto] ?: "wifi",
        recentSearches = this[K.recentSearches]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
        offlineDb = this[K.offlineDb] ?: OfflineDb.OFF,
        offlineDbUpdated = this[K.offlineDbUpdated] ?: 0,
        offlineDbCount = this[K.offlineDbCount] ?: 0,
        offlineDbOffered = this[K.offlineDbOffered] ?: false,
        lastScanSet = this[K.lastScanSet] ?: "",
        offlineDbMobile = this[K.offlineDbMobile] ?: false,
        offlineDbMobileAuto = this[K.offlineDbMobileAuto] ?: false,
        foilTipShown = this[K.foilTipShown] ?: false,
        timeFormat = this[K.timeFormat] ?: "system",
        dateFormat = this[K.dateFormat] ?: "system",
        weekStart = this[K.weekStart] ?: "system",
        showWeekday = this[K.showWeekday] ?: false,
        filters = K.filters.mapNotNull { (k, key) -> this[key]?.let { k to it } }.toMap(),
        collectionSort = this[K.collectionSort] ?: "set",
        decksSort = this[K.decksSort] ?: "format",
        completionist = this[K.completionist] ?: false,
        effects = this[K.effects] ?: true,
        accent = this[K.accent] ?: "",
        privacy = this[K.privacy] ?: false,
        privacyStyle = this[K.privacyStyle] ?: "compleated",
        bulkThreshold = this[K.bulkThreshold] ?: 0.25,
        lastBoosterPrice = this[K.lastBoosterPrice] ?: 0.0,
        lastBoosterType = this[K.lastBoosterType] ?: "",
        aiEnabled = this[K.aiEnabled] ?: false,
        aiProvider = this[K.aiProvider] ?: "gemini",
        aiModel = this[K.aiModel] ?: "",
        aiBaseUrl = this[K.aiBaseUrl] ?: "",
        aiDailyLimit = this[K.aiDailyLimit] ?: 50,
        aiCountDay = this[K.aiCountDay] ?: "",
        aiCount = this[K.aiCount] ?: 0,
    )

    suspend fun setTheme(v: String) = context.dataStore.edit { it[K.theme] = v }
    suspend fun setCurrency(v: String) = context.dataStore.edit { it[K.currency] = v }
    suspend fun setImageMode(v: String) = context.dataStore.edit { it[K.imageMode] = v }
    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[K.wifiOnly] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboarding] = v }
    suspend fun setBackupFolder(v: String?) = context.dataStore.edit {
        if (v == null) it.remove(K.backupFolder) else it[K.backupFolder] = v
    }
    suspend fun setLastBackup(v: Long) = context.dataStore.edit { it[K.lastBackup] = v }
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[K.lastUpdateCheck] = v }
    suspend fun setLastPriceUpdate(v: Long) = context.dataStore.edit { it[K.lastPriceUpdate] = v }
    suspend fun setChartLines(v: List<String>) = context.dataStore.edit { it[K.chartLines] = v.joinToString(",") }
    suspend fun setChartMetric(v: String) = context.dataStore.edit { it[K.chartMetric] = v }
    suspend fun setChartRange(v: String) = context.dataStore.edit { it[K.chartRange] = v }
    suspend fun setScanMode(v: String) = context.dataStore.edit { it[K.scanMode] = v }
    suspend fun setBackupAsked(v: Boolean) = context.dataStore.edit { it[K.backupAsked] = v }
    suspend fun setShowTiming(v: Boolean) = context.dataStore.edit { it[K.showTiming] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setShutterSound(v: Boolean) = context.dataStore.edit { it[K.shutterSound] = v }
    suspend fun setAutoCapture(v: Boolean) = context.dataStore.edit { it[K.autoCapture] = v }
    suspend fun setAutoSpeed(v: String) = context.dataStore.edit { it[K.autoSpeed] = v }
    suspend fun setBreakoutAlerts(v: Boolean) = context.dataStore.edit { it[K.breakoutAlerts] = v }
    suspend fun setLastBreakoutDay(v: String) = context.dataStore.edit { it[K.lastBreakoutDay] = v }
    suspend fun setLastSeenVersion(v: String) = context.dataStore.edit { it[K.lastSeenVersion] = v }
    suspend fun addRecentSearch(q: String) = context.dataStore.edit { p ->
        val old = p[K.recentSearches]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        p[K.recentSearches] = (listOf(q) + old.filter { !it.equals(q, true) }).take(6).joinToString("\n")
    }
    suspend fun setUpdateAuto(v: String) = context.dataStore.edit { it[K.updateAuto] = v }
    suspend fun setShareName(v: String) = context.dataStore.edit { it[K.shareName] = v.trim() }
    /** Vaste code als deler: één keer gemaakt, daarna altijd dezelfde. */
    suspend fun shareId(): String {
        current().shareId.takeIf { it.isNotBlank() }?.let { return it }
        val id = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[K.shareId] = id }
        return id
    }
    suspend fun setOfflineDb(v: String) = context.dataStore.edit { it[K.offlineDb] = v }
    suspend fun setOfflineDbDone(at: Long, count: Int) = context.dataStore.edit { it[K.offlineDbUpdated] = at; it[K.offlineDbCount] = count }
    suspend fun setOfflineDbMobile(v: Boolean) = context.dataStore.edit { it[K.offlineDbMobile] = v; if (!v) it[K.offlineDbMobileAuto] = false }
    suspend fun setOfflineDbMobileAuto(v: Boolean) = context.dataStore.edit { it[K.offlineDbMobileAuto] = v }
    suspend fun setLastScanSet(v: String) = context.dataStore.edit { it[K.lastScanSet] = v }
    suspend fun setTimeFormat(v: String) = context.dataStore.edit { it[K.timeFormat] = v }
    suspend fun setDateFormat(v: String) = context.dataStore.edit { it[K.dateFormat] = v }
    suspend fun setWeekStart(v: String) = context.dataStore.edit { it[K.weekStart] = v }
    suspend fun setShowWeekday(v: Boolean) = context.dataStore.edit { it[K.showWeekday] = v }
    suspend fun setFilter(screen: String, json: String) = context.dataStore.edit { p -> K.filters[screen]?.let { p[it] = json } }
    suspend fun setAi(enabled: Boolean? = null, provider: String? = null, model: String? = null, baseUrl: String? = null, limit: Int? = null) = context.dataStore.edit { p ->
        enabled?.let { p[K.aiEnabled] = it }; provider?.let { p[K.aiProvider] = it }; model?.let { p[K.aiModel] = it }
        baseUrl?.let { p[K.aiBaseUrl] = it }; limit?.let { p[K.aiDailyLimit] = it }
    }

    /** Eén AI-verzoek tellen. Geeft false als het maximum van vandaag al bereikt is. */
    suspend fun countAiUse(): Boolean {
        var ok = false
        context.dataStore.edit { p ->
            val today = java.time.LocalDate.now().toString()
            val n = if (p[K.aiCountDay] == today) p[K.aiCount] ?: 0 else 0
            val limit = p[K.aiDailyLimit] ?: 50
            ok = n < limit
            if (ok) { p[K.aiCountDay] = today; p[K.aiCount] = n + 1 }
        }
        return ok
    }

    suspend fun setDecksSort(v: String) = context.dataStore.edit { it[K.decksSort] = v }
    suspend fun setCompletionist(v: Boolean) = context.dataStore.edit { it[K.completionist] = v }
    suspend fun setEffects(v: Boolean) = context.dataStore.edit { it[K.effects] = v }
    suspend fun setAccent(v: String) = context.dataStore.edit { it[K.accent] = v }
    suspend fun setPrivacy(v: Boolean) = context.dataStore.edit { it[K.privacy] = v }
    suspend fun setPrivacyStyle(v: String) = context.dataStore.edit { it[K.privacyStyle] = v }
    suspend fun setBulkThreshold(v: Double) = context.dataStore.edit { it[K.bulkThreshold] = v }
    suspend fun setLastBooster(price: Double, type: String) = context.dataStore.edit { it[K.lastBoosterPrice] = price; it[K.lastBoosterType] = type }
    suspend fun setCollectionSort(v: String) = context.dataStore.edit { it[K.collectionSort] = v }
    suspend fun setFoilTipShown() = context.dataStore.edit { it[K.foilTipShown] = true }
    suspend fun setOfflineDbOffered(v: Boolean) = context.dataStore.edit { it[K.offlineDbOffered] = v }
}
