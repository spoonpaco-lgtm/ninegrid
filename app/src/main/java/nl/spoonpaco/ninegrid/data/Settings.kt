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
)

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
}
