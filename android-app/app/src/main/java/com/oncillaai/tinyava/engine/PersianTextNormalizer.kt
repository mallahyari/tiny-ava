package com.oncillaai.tinyava.engine

import java.text.Normalizer

/**
 * Pure-Kotlin Persian text normalizer tailored for Android TalkBack & tiny-ava:
 * - Safe Arabic -> Persian letter folding while preserving optional short-vowel diacritics (ـَ ـِ ـُ ـّ)
 * - Converts Persian/Arabic/ASCII numbers, clock times (14:30), percentages (85%), and decimals to Persian words
 * - Translates common English Android/TalkBack UI terms and applies the user's custom dictionary
 * - Splits long text cleanly into sentences & comma-bounded clauses
 */
object PersianTextNormalizer {

    private val CHAR_MAP = mapOf(
        '\u064a' to '\u06cc', // ي -> ی
        '\u0649' to '\u06cc', // ى -> ی
        '\u0643' to '\u06a9', // ك -> ک
        '\u0629' to '\u0647', // ة -> ه
        '\u06c0' to '\u0647', // ۀ -> ه
        '\u0623' to '\u0627', // أ -> ا
        '\u0625' to '\u0627', // إ -> ا
        '\u0671' to '\u0627', // ٱ -> ا
        '\u00a0' to ' ',
        ',' to '،',
        '?' to '؟',
        ';' to '؛',
        '–' to '—',
        '―' to '—',
        '"' to '«',
    )

    private val DROP_CHARS = setOf('\u0640', '\u200b', '\u200d', '\ufeff')
    private val NON_KEEP_DIACRITICS = Regex("[\\u064b-\\u064d\\u0652-\\u065f\\u0670]")
    private val MULTI_ZWNJ = Regex("\\u200c+")
    private val SPACE_ZWNJ = Regex("\\s+\\u200c|\\u200c\\s+")
    private val MULTI_SPACE = Regex("\\s+")

    private val TIME_REGEX = Regex("\\b(\\d{1,2}):(\\d{2})\\b")
    private val PERCENT_REGEX = Regex("(\\d+(?:[./٫]\\d+)?)\\s*[%٪]")
    private val DECIMAL_REGEX = Regex("\\b(\\d+)[.٫](\\d+)\\b")
    private val INTEGER_REGEX = Regex("\\b\\d+\\b")

    /** Built-in common Android / TalkBack English UI terms for smooth Persian reading. */
    val BUILTIN_ENGLISH_UI: Map<String, String> = mapOf(
        "wi-fi" to "وای‌فای",
        "wifi" to "وای‌فای",
        "bluetooth" to "بلوتوث",
        "whatsapp" to "واتساپ",
        "telegram" to "تلگرام",
        "instagram" to "اینستاگرام",
        "youtube" to "یوتیوب",
        "chrome" to "کروم",
        "google" to "گوگل",
        "android" to "اندروید",
        "talkback" to "تاک‌بک",
        "settings" to "تنظیمات",
        "battery" to "باتری",
        "camera" to "دوربین",
        "gallery" to "گالری",
        "gmail" to "جیمیل",
        "maps" to "مپ",
        "gps" to "جی‌پی‌اس",
        "vpn" to "وی‌پی‌ان",
        "sms" to "اس‌ام‌اس",
        "sim" to "سیم‌کارت",
        "usb" to "یو‌اس‌بی",
        "pdf" to "پی‌دی‌اف",
        "wav" to "ویو",
        "mp3" to "ام‌پی‌تری",
        "ai" to "اِی‌آی",
        "ok" to "اوکی",
        "cancel" to "لغو",
        "back" to "بازگشت",
        "home" to "خانه",
        "menu" to "منو",
        "search" to "جستجو",
        "share" to "اشتراک‌گذاری",
        "copy" to "کپی",
        "paste" to "چسباندن",
        "play" to "پخش",
        "pause" to "توقف",
        "stop" to "توقف",
        "next" to "بعدی",
        "previous" to "قبلی",
        "volume" to "صدا",
        "mute" to "بی‌صدا",
        "notification" to "اعلان",
        "notifications" to "اعلان‌ها",
        "download" to "دانلود",
        "upload" to "آپلود",
        "online" to "آنلاین",
        "offline" to "آفلاین",
        "button" to "دکمه",
        "switch" to "کلید",
        "checked" to "انتخاب شده",
        "unchecked" to "انتخاب نشده",
        "on" to "روشن",
        "off" to "خاموش",
        "tiny-ava" to "تاینی آوا",
        "oncillaai" to "آنسیلا اِی‌آی",
    )

    private val ONES = arrayOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val TEENS = arrayOf(
        "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"
    )
    private val TENS = arrayOf("", "ده", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val HUNDREDS = arrayOf(
        "", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"
    )

    fun toAsciiDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun intToPersian(n: Long): String {
        if (n == 0L) return "صفر"
        if (n < 0L) return "منفی " + intToPersian(-n)
        if (n >= 1_000_000_000_000L) {
            return n.toString().map { ONES[it - '0'].ifEmpty { "صفر" } }.joinToString(" ")
        }
        val scales = arrayOf("", "هزار", "میلیون", "میلیارد")
        val parts = mutableListOf<String>()
        var rem = n
        var scaleIdx = 0
        while (rem > 0L) {
            val chunk = (rem % 1000L).toInt()
            if (chunk > 0) {
                val words = threeDigitsToPersian(chunk)
                val label = scales[scaleIdx]
                parts.add(
                    when {
                        label.isEmpty() -> words
                        chunk == 1 && scaleIdx == 1 -> "هزار"
                        else -> "$words $label"
                    }
                )
            }
            rem /= 1000L
            scaleIdx++
        }
        return parts.reversed().joinToString(" و ")
    }

    private fun threeDigitsToPersian(num: Int): String {
        val h = num / 100
        val r = num % 100
        val parts = mutableListOf<String>()
        if (h > 0) parts.add(HUNDREDS[h])
        if (r in 10..19) {
            parts.add(TEENS[r - 10])
        } else {
            val t = r / 10
            val o = r % 10
            if (t > 0) parts.add(TENS[t])
            if (o > 0) parts.add(ONES[o])
        }
        return parts.joinToString(" و ")
    }

    fun convertNumbersToPersian(text: String): String {
        var s = toAsciiDigits(text)
        // Clock time: 14:30 -> چهارده و سی دقیقه, 9:00 -> نه
        s = TIME_REGEX.replace(s) { m ->
            val h = m.groupValues[1].toLongOrNull() ?: return@replace m.value
            val min = m.groupValues[2].toLongOrNull() ?: return@replace m.value
            if (h > 24 || min > 59) return@replace m.value
            val hw = intToPersian(h)
            if (min == 0L) hw else "$hw و ${intToPersian(min)} دقیقه"
        }
        // Percentage: 85% or 85٪ -> هشتاد و پنج درصد
        s = PERCENT_REGEX.replace(s) { m ->
            val numStr = m.groupValues[1].replace('٫', '.').replace('/', '.')
            val words = if ('.' in numStr) {
                val p = numStr.split('.')
                "${intToPersian(p[0].toLongOrNull() ?: 0L)} ممیز ${intToPersian(p[1].toLongOrNull() ?: 0L)}"
            } else {
                intToPersian(numStr.toLongOrNull() ?: 0L)
            }
            "$words درصد"
        }
        // Decimals: 3.5 -> سه ممیز پنج
        s = DECIMAL_REGEX.replace(s) { m ->
            val a = m.groupValues[1].toLongOrNull() ?: return@replace m.value
            val b = m.groupValues[2].toLongOrNull() ?: return@replace m.value
            "${intToPersian(a)} ممیز ${intToPersian(b)}"
        }
        // Plain integers
        s = INTEGER_REGEX.replace(s) { m ->
            val v = m.value.toLongOrNull()
            if (v != null && m.value.length <= 12) intToPersian(v)
            else m.value.map { ONES[it - '0'].ifEmpty { "صفر" } }.joinToString(" ")
        }
        return s
    }

    fun normalize(
        text: String,
        convertNumbers: Boolean = true,
        convertEnglishUi: Boolean = true,
        customDictionary: Map<String, String> = emptyMap(),
    ): String {
        if (text.isBlank()) return ""
        var s = Normalizer.normalize(text, Normalizer.Form.NFC)

        // 1. Apply user's custom pronunciation dictionary first (case-insensitive word/phrase match)
        if (customDictionary.isNotEmpty()) {
            for ((src, dst) in customDictionary.entries.sortedByDescending { it.key.length }) {
                if (src.isBlank()) continue
                val escaped = Regex.escape(src)
                val boundary = if (src.all { it.isLetterOrDigit() || it == '-' }) {
                    Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                } else {
                    Regex(escaped, RegexOption.IGNORE_CASE)
                }
                s = boundary.replace(s, dst)
            }
        }

        // 2. Apply built-in English Android/TalkBack UI terms
        if (convertEnglishUi) {
            s = Regex("\\b[A-Za-z][A-Za-z0-9\\-]*\\b").replace(s) { m ->
                BUILTIN_ENGLISH_UI[m.value.lowercase()] ?: m.value
            }
        }

        // 3. Convert numbers, times, percentages, decimals to Persian words
        if (convertNumbers) {
            s = convertNumbersToPersian(s)
        }

        // 4. Safe Arabic -> Persian character folding (preserve َ ِ ُ ّ diacritics)
        val sb = StringBuilder(s.length)
        for (ch in s) {
            if (ch in DROP_CHARS) continue
            sb.append(CHAR_MAP[ch] ?: ch)
        }
        s = sb.toString()
        s = NON_KEEP_DIACRITICS.replace(s, "")
        s = Regex("\\.{3,}").replace(s, "…")
        s = Regex("-{2,}|\\s-\\s").replace(s, "—")
        s = MULTI_ZWNJ.replace(s, "\u200c")
        s = SPACE_ZWNJ.replace(s, " ")
        return MULTI_SPACE.replace(s, " ").trim()
    }

    fun ensureTerminalMark(text: String): String {
        val s = text.trim()
        if (s.isEmpty()) return s
        val last = s.last()
        return if (last in ".!؟?…،,;؛:") s else "$s."
    }

    fun splitSentences(text: String): List<String> {
        val cleaned = text.replace(Regex("[\\s\\ufeff]+"), " ").trim()
        if (cleaned.isEmpty()) return emptyList()
        val raw = cleaned.split(Regex("(?<=[.!؟?…][\"')\\]»]?)\\s+"))
        val out = mutableListOf<String>()
        for (part in raw) {
            val trimmed = part.trim()
            if (trimmed.none { it.isLetterOrDigit() }) continue
            if (trimmed.length <= 180) {
                out.add(trimmed)
            } else {
                val clauses = trimmed.split(Regex("(?<=[،,؛;:—])\\s+")).filter { it.isNotBlank() }
                if (clauses.size > 1) {
                    out.addAll(clauses)
                } else {
                    val words = trimmed.split(" ")
                    val half = (words.size + 1) / 2
                    out.add(words.take(half).joinToString(" "))
                    out.add(words.drop(half).joinToString(" "))
                }
            }
        }
        return out
    }
}
