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
    val showTiming: Boolean = true,
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
    /** Offline kaartdatabase: off, newset (bij nieuwe set) of manual. */
    val offlineDb: String = OfflineDb.OFF,
    val offlineDbUpdated: Long = 0,
    val offlineDbCount: Int = 0,
    /** Al een keer aangeboden (bij een grote import of een wachttijd van Scryfall). */
    val offlineDbOffered: Boolean = false,
)

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
        val offlineDb = stringPreferencesKey("offline_db")
        val offlineDbUpdated = longPreferencesKey("offline_db_updated")
        val offlineDbCount = androidx.datastore.preferences.core.intPreferencesKey("offline_db_count")
        val offlineDbOffered = booleanPreferencesKey("offline_db_offered")
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
        showTiming = this[K.showTiming] ?: true,
        haptics = this[K.haptics] ?: true,
        shutterSound = this[K.shutterSound] ?: false,
        autoCapture = this[K.autoCapture] ?: true,
        autoSpeed = this[K.autoSpeed] ?: "normal",
        breakoutAlerts = this[K.breakoutAlerts] ?: true,
        lastBreakoutDay = this[K.lastBreakoutDay] ?: "",
        offlineDb = this[K.offlineDb] ?: OfflineDb.OFF,
        offlineDbUpdated = this[K.offlineDbUpdated] ?: 0,
        offlineDbCount = this[K.offlineDbCount] ?: 0,
        offlineDbOffered = this[K.offlineDbOffered] ?: false,
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
    suspend fun setOfflineDb(v: String) = context.dataStore.edit { it[K.offlineDb] = v }
    suspend fun setOfflineDbDone(at: Long, count: Int) = context.dataStore.edit { it[K.offlineDbUpdated] = at; it[K.offlineDbCount] = count }
    suspend fun setOfflineDbOffered(v: Boolean) = context.dataStore.edit { it[K.offlineDbOffered] = v }
}
