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

/** Voices exposed in tiny-ava v2 (`tiny-ava-v2.onnx`). */
enum class AvaPersianVoice(val onnxId: Long, val labelFa: String, val subtitleFa: String) {
    PARISA(0L, "پریسا (زن)", "صدای گرم فارسی"),
    MANA(2L, "مانا (زن)", "صدای طبیعی استودیویی"),
}

data class DictEntry(
    val source: String,
    val replacement: String,
    val note: String = "",
)

data class SettingsState(
    val persianVoice: AvaPersianVoice = AvaPersianVoice.PARISA,
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
        val voiceName = prefs.getString("persian_voice", AvaPersianVoice.PARISA.name) ?: AvaPersianVoice.PARISA.name
        val persianVoice = runCatching { AvaPersianVoice.valueOf(voiceName) }.getOrDefault(AvaPersianVoice.PARISA)
        val dictJson = prefs.getString("custom_dict", null)
        val dict = if (dictJson != null) parseDict(dictJson) else DEFAULT_CUSTOM_DICT
        return SettingsState(
            persianVoice = persianVoice,
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
            .putString("persian_voice", next.persianVoice.name)
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

    /** Export custom dictionary as pretty-printed UTF-8 JSON for backup/sharing. */
    fun exportDictionaryJson(): String = DictionarySerializer.toPrettyJson(_state.value.customDictionary)

    /**
     * Import dictionary entries from JSON array OR plain text (`source=replacement` / `source\treplacement`).
     * Merges imported entries on top of existing ones (updating duplicates case-insensitively).
     * Returns the number of valid entries imported.
     */
    fun importDictionary(raw: String): Int {
        val imported = DictionarySerializer.parseFlexible(raw)
        if (imported.isEmpty()) return 0
        update { cur ->
            val merged = LinkedHashMap<String, DictEntry>()
            for (e in imported) merged[e.source.lowercase()] = e
            for (e in cur.customDictionary) {
                val key = e.source.lowercase()
                if (key !in merged) merged[key] = e
            }
            cur.copy(customDictionary = merged.values.toList())
        }
        return imported.size
    }

    private fun serializeDict(entries: List<DictEntry>): String = DictionarySerializer.toCompactJson(entries)

    private fun parseDict(raw: String): List<DictEntry> =
        DictionarySerializer.parseFlexible(raw).ifEmpty { DEFAULT_CUSTOM_DICT }
}

object DictionarySerializer {
    private fun escapeJson(s: String): String = buildString(s.length + 4) {
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    fun toCompactJson(entries: List<DictEntry>): String = buildString {
        append('[')
        entries.forEachIndexed { idx, e ->
            if (idx > 0) append(',')
            append("{\"source\":\"").append(escapeJson(e.source))
                .append("\",\"replacement\":\"").append(escapeJson(e.replacement))
                .append("\",\"note\":\"").append(escapeJson(e.note))
                .append("\"}")
        }
        append(']')
    }

    fun toPrettyJson(entries: List<DictEntry>): String = buildString {
        append("[\n")
        entries.forEachIndexed { idx, e ->
            append("  {\n")
            append("    \"source\": \"").append(escapeJson(e.source)).append("\",\n")
            append("    \"replacement\": \"").append(escapeJson(e.replacement)).append("\",\n")
            append("    \"note\": \"").append(escapeJson(e.note)).append("\"\n")
            append("  }")
            if (idx < entries.lastIndex) append(',')
            append('\n')
        }
        append(']')
    }

    /** Accepts JSON array of objects, JSON object `{"src":"dst"}`, or line-based `src=dst` / `src\tdst`. */
    fun parseFlexible(raw: String): List<DictEntry> {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[")) {
            return runCatching {
                val arr = JSONArray(trimmed)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val s = obj.optString("source", "").trim()
                        val r = obj.optString("replacement", "").trim()
                        if (s.isNotEmpty() && r.isNotEmpty()) {
                            add(DictEntry(s, r, obj.optString("note", "وارد شده")))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
        if (trimmed.startsWith("{")) {
            return runCatching {
                val obj = JSONObject(trimmed)
                buildList {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next().trim()
                        val v = obj.optString(k, "").trim()
                        if (k.isNotEmpty() && v.isNotEmpty()) add(DictEntry(k, v, "وارد شده"))
                    }
                }
            }.getOrDefault(emptyList())
        }
        // Fallback: line-by-line `source=replacement` or `source\treplacement`
        return buildList {
            for (line in trimmed.lineSequence()) {
                val l = line.trim()
                if (l.isEmpty() || l.startsWith("#")) continue
                val parts = when {
                    '\t' in l -> l.split('\t', limit = 3)
                    '=' in l -> l.split('=', limit = 2)
                    else -> continue
                }
                val s = parts[0].trim()
                val r = parts.getOrNull(1)?.trim().orEmpty()
                val note = parts.getOrNull(2)?.trim().orEmpty().ifEmpty { "وارد شده" }
                if (s.isNotEmpty() && r.isNotEmpty()) add(DictEntry(s, r, note))
            }
        }
    }
}
