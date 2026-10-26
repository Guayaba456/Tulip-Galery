package com.tulip.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tulip.app.logic.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tulipStore by preferencesDataStore(name = "tulip_settings")

data class Settings(
    val theme: String = "system",                 // system | light | dark
    val primary: Int = 0xFF8EC5F2.toInt(),        // azul cielo
    val accent: Int = 0xFFB9A9E8.toInt(),         // lavanda
    val background: Int = 0xFFFAF3E0.toInt(),     // crema
    val thumbDp: Int = 110,
    val listView: Boolean = false,
    val sort: SortOrder = SortOrder.Default,
    val loopQueue: Boolean = true,
    val bgPlayback: Boolean = true,
    val thumbQuality: String = "medium",          // low | medium | high
    val savedQueue: String = "",
)

object SettingsKeys {
    val theme = stringPreferencesKey("theme")
    val primary = intPreferencesKey("primary")
    val accent = intPreferencesKey("accent")
    val background = intPreferencesKey("background")
    val thumbDp = intPreferencesKey("thumb_dp")
    val listView = booleanPreferencesKey("list_view")
    val sort = stringPreferencesKey("sort")
    val loopQueue = booleanPreferencesKey("loop_queue")
    val bgPlayback = booleanPreferencesKey("bg_playback")
    val thumbQuality = stringPreferencesKey("thumb_quality")
    val savedQueue = stringPreferencesKey("saved_queue")
}

class SettingsRepository(private val context: Context) {
    private val d = Settings()

    val settings: Flow<Settings> = context.tulipStore.data.map { p ->
        Settings(
            theme = p[SettingsKeys.theme] ?: d.theme,
            primary = p[SettingsKeys.primary] ?: d.primary,
            accent = p[SettingsKeys.accent] ?: d.accent,
            background = p[SettingsKeys.background] ?: d.background,
            thumbDp = p[SettingsKeys.thumbDp] ?: d.thumbDp,
            listView = p[SettingsKeys.listView] ?: d.listView,
            sort = SortOrder.decode(p[SettingsKeys.sort]),
            loopQueue = p[SettingsKeys.loopQueue] ?: d.loopQueue,
            bgPlayback = p[SettingsKeys.bgPlayback] ?: d.bgPlayback,
            thumbQuality = p[SettingsKeys.thumbQuality] ?: d.thumbQuality,
            savedQueue = p[SettingsKeys.savedQueue] ?: d.savedQueue,
        )
    }

    suspend fun update(block: (MutablePreferences) -> Unit) {
        context.tulipStore.edit { block(it) }
    }
}
