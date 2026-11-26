package com.oncillaai.tinyava.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class AppThemeMode(val labelFa: String) {
    LIGHT("روشن"),
    DARK("تیره"),
    HIGH_CONTRAST("کنتراست بالا"),
}

data class DictEntry(
    val source: String,
    val replacement: String,
    val note: String = "",
)

data class SettingsState(
    val speed: Float = 1.2f,
    val pitchSemitones: Float = 0.0f,
    val pitchRange: Float = 1.05f,
    val convertNumbers: Boolean = true,
    val enableEnglishVoice: Boolean = true,
    val convertEnglishUi: Boolean = true,
    val enablePhraseCache: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val customDictionary: List<DictEntry> = DEFAULT_CUSTOM_DICT,
)

val DEFAULT_CUSTOM_DICT = listOf(
    DictEntry("Wi-Fi", "وای‌فای", "کلمه انگلیسی سیستم"),
    DictEntry("WhatsApp", "واتساپ", "نام برنامه"),
    DictEntry("Telegram", "تلگرام", "نام برنامه"),
    DictEntry("Bluetooth", "بلوتوث", "کلمه انگلیسی سیستم"),
    DictEntry("AI", "اِی‌آی", "مخفف انگلیسی"),
)

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tiny_ava_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun load(): SettingsState {
        val themeName = prefs.getString("theme_mode", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name
        val theme = runCatching { AppThemeMode.valueOf(themeName) }.getOrDefault(AppThemeMode.LIGHT)
        val dictJson = prefs.getString("custom_dict", null)
        val dict = if (dictJson != null) parseDict(dictJson) else DEFAULT_CUSTOM_DICT
        return SettingsState(
            speed = prefs.getFloat("speed", 1.2f).coerceIn(0.7f, 3.5f),
            pitchSemitones = prefs.getFloat("pitch_st", 0.0f).coerceIn(-4.0f, 4.0f),
            pitchRange = prefs.getFloat("pitch_range", 1.05f).coerceIn(0.7f, 1.4f),
            convertNumbers = prefs.getBoolean("convert_numbers", true),
            enableEnglishVoice = prefs.getBoolean("enable_english_voice", true),
            convertEnglishUi = prefs.getBoolean("convert_english_ui", true),
            enablePhraseCache = prefs.getBoolean("enable_phrase_cache", true),
            themeMode = theme,
            customDictionary = dict,
        )
    }

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putFloat("speed", next.speed)
            .putFloat("pitch_st", next.pitchSemitones)
            .putFloat("pitch_range", next.pitchRange)
            .putBoolean("convert_numbers", next.convertNumbers)
            .putBoolean("enable_english_voice", next.enableEnglishVoice)
            .putBoolean("convert_english_ui", next.convertEnglishUi)
            .putBoolean("enable_phrase_cache", next.enablePhraseCache)
            .putString("theme_mode", next.themeMode.name)
            .putString("custom_dict", serializeDict(next.customDictionary))
            .apply()
    }

    fun addDictEntry(source: String, replacement: String, note: String = "دلخواه") {
        val s = source.trim()
        val r = replacement.trim()
        if (s.isEmpty() || r.isEmpty()) return
        update { cur ->
            val filtered = cur.customDictionary.filterNot { it.source.equals(s, ignoreCase = true) }
            cur.copy(customDictionary = listOf(DictEntry(s, r, note.ifBlank { "دلخواه" })) + filtered)
        }
    }

    fun updateDictEntry(oldSource: String, newSource: String, newReplacement: String, note: String) {
        val s = newSource.trim()
        val r = newReplacement.trim()
        if (s.isEmpty() || r.isEmpty()) return
        update { cur ->
            val updated = cur.customDictionary.mapNotNull { entry ->
                when {
                    entry.source == oldSource -> DictEntry(s, r, note.ifBlank { entry.note.ifBlank { "دلخواه" } })
                    entry.source.equals(s, ignoreCase = true) -> null // avoid duplicates if renamed onto existing
                    else -> entry
                }
            }
            cur.copy(customDictionary = updated)
        }
    }

    fun removeDictEntry(source: String) {
        update { cur ->
            cur.copy(customDictionary = cur.customDictionary.filterNot { it.source == source })
        }
    }

    private fun serializeDict(entries: List<DictEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("source", e.source)
                    .put("replacement", e.replacement)
                    .put("note", e.note)
            )
        }
        return arr.toString()
    }

    private fun parseDict(raw: String): List<DictEntry> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    DictEntry(
                        source = obj.getString("source"),
                        replacement = obj.getString("replacement"),
                        note = obj.optString("note", ""),
                    )
                )
            }
        }
    }.getOrDefault(DEFAULT_CUSTOM_DICT)
}
