package com.oncillaai.tinyava.engine

/**
 * Direct, unambiguous spoken phoneme names (Repr. 1 ASCII format for tiny-ava v2) for single-character
 * Persian keyboard typing and TalkBack character-by-character exploration.
 *
 * Follows the established Persian accessibility conventions (eSpeak NG `fa_list`) so homophonous
 * letters (`س / ص / ث`, `ز / ذ / ض / ظ`, `ت / ط`, `ح / ه`, `ق / غ`), diacritics, ZWNJ (`نیم‌فاصله`),
 * and keyboard punctuation are 100% distinct and spoken instantly without running ByT5 G2P.
 */
object PersianKeyboardPhonemes {

    /**
     * Exact Repr. 1 phoneme strings for single Persian letters, diacritics, and ZWNJ.
     * Note: Always bypasses G2P -> ~10ms first synthesis and <2ms on subsequent LRU-cached taps.
     */
    private val PERSIAN_CHAR_REPR1: Map<Char, String> = mapOf(
        // 32 standard Persian letters + Alef-Madda
        'آ' to "A",
        'ا' to "alef",
        'ب' to "be",
        'پ' to "pe",
        'ت' to "te",
        'ث' to "se-ye se noqte",
        'ج' to "jim",
        'چ' to "Ce",
        'ح' to "he-ye jimi",
        'خ' to "xe",
        'د' to "dAl",
        'ذ' to "zAl",
        'ر' to "re",
        'ز' to "ze",
        'ژ' to "Ze",
        'س' to "sin",
        'ش' to "Sin",
        'ص' to "sAd",
        'ض' to "zAd",
        'ط' to "tA",
        'ظ' to "zA",
        'ع' to "eyn",
        'غ' to "qeyn",
        'ف' to "fe",
        'ق' to "qAf",
        'ک' to "kAf",
        'گ' to "gAf",
        'ل' to "lAm",
        'م' to "mim",
        'ن' to "nun",
        'و' to "vAv",
        'ه' to "he",
        'ی' to "ye",

        // Arabic / Hamza variants on Persian keyboards
        'ك' to "kAf-e ?arabi",
        'ي' to "ye-ye ?arabi",
        'ى' to "ye",
        'ئ' to "ye hamze",
        'ؤ' to "vAv hamze",
        'ۀ' to "he hamze",
        'أ' to "alef hamze",
        'إ' to "alef hamze zir",
        'ٱ' to "alef",
        'ء' to "hamze",
        'ة' to "tA-ye ta?nis",

        // Persian & Arabic digits (instant keyboard number-row speech)
        '۰' to "sefr", '٠' to "sefr",
        '۱' to "yek", '١' to "yek",
        '۲' to "do", '٢' to "do",
        '۳' to "se", '٣' to "se",
        '۴' to "CahAr", '٤' to "CahAr",
        '۵' to "panj", '٥' to "panj",
        '۶' to "SeS", '٦' to "SeS",
        '۷' to "haft", '٧' to "haft",
        '۸' to "haSt", '٨' to "haSt",
        '۹' to "noh", '٩' to "noh",

        // Diacritics, ZWNJ (half-space), and Tatweel
        '\u200c' to "nim fAsele",
        '\u064e' to "fathe",
        '\u0650' to "kasre",
        '\u064f' to "zamme",
        '\u0651' to "taSdid",
        '\u0652' to "sAken",
        '\u064b' to "tanvin-e nasb",
        '\u064c' to "tanvin-e raf?",
        '\u064d' to "tanvin-e jar",
        '\u0670' to "alef-e maqsure",
        '\u0653' to "kolAh-e A",
        '\u0640' to "keSide",
        '﷼' to "riyAl",
    )

    /**
     * Common punctuation & symbol keys when spoken as a single character in Persian mode.
     * In English mode (`defaultLang == EN`), ASCII punctuation is left to English or handled in Persian fallback.
     */
    private val PERSIAN_SYMBOL_REPR1: Map<Char, String> = mapOf(
        '.' to "noqte",
        '،' to "virgul",
        ',' to "virgul",
        '؛' to "noqte virgul",
        ';' to "noqte virgul",
        '؟' to "alAmat-e so?Al",
        '?' to "alAmat-e so?Al",
        '!' to "alAmat-e ta?ajob",
        ':' to "do noqte",
        '…' to "se noqte",
        '«' to "giyume bAz",
        '»' to "giyume baste",
        '"' to "giyume",
        '\'' to "ApAstorof",
        '(' to "parAntez bAz",
        ')' to "parAntez baste",
        '[' to "keruSe bAz",
        ']' to "keruSe baste",
        '-' to "xat-e tire",
        '—' to "xat-e tire",
        '_' to "AnderlAyn",
        '/' to "eslaS",
        '\\' to "bak eslaS",
        '@' to "at sAyn",
        '#' to "haStag",
        '%' to "darsad",
        '٪' to "darsad",
        '+' to "be ?alAve",
        '=' to "mosAvi",
        '*' to "setAre",
        '$' to "dolAr",
        '&' to "and",
    )

    /** ASCII digits spoken in Persian when default language is Persian. */
    private val ASCII_DIGIT_FA_REPR1: Map<Char, String> = mapOf(
        '0' to "sefr",
        '1' to "yek",
        '2' to "do",
        '3' to "se",
        '4' to "CahAr",
        '5' to "panj",
        '6' to "SeS",
        '7' to "haft",
        '8' to "haSt",
        '9' to "noh",
    )

    /** True if [raw] is a single keyboard character (letter, diacritic, ZWNJ, or symbol) we speak directly. */
    fun isSingleKeyboardChar(raw: String): Boolean {
        val ch = extractSingleChar(raw) ?: return false
        return ch in PERSIAN_CHAR_REPR1 || ch in PERSIAN_SYMBOL_REPR1 || ch in ASCII_DIGIT_FA_REPR1
    }

    /**
     * If [raw] represents a single keyboard character that should be spoken using Persian letter/symbol names,
     * returns its exact Repr. 1 phoneme string (bypassing ByT5 G2P). Returns null for multi-char text or
     * Latin letters (which are handled by Maya + `lexicon_en.tsv`).
     */
    fun lookupSingleCharRepr1(
        raw: String,
        defaultLang: LanguageSpanSplitter.Lang = LanguageSpanSplitter.Lang.FA,
    ): String? {
        val ch = extractSingleChar(raw) ?: return null
        // Always handle Persian/Arabic letters, diacritics, Persian digits, and ZWNJ regardless of defaultLang
        PERSIAN_CHAR_REPR1[ch]?.let { return it }
        // Persian-specific punctuation even if defaultLang is EN
        if (ch == '،' || ch == '؛' || ch == '؟' || ch == '٪' || ch == '«' || ch == '»') {
            return PERSIAN_SYMBOL_REPR1[ch]
        }
        // Shared ASCII symbols & digits when Persian is the active language
        if (defaultLang == LanguageSpanSplitter.Lang.FA) {
            ASCII_DIGIT_FA_REPR1[ch]?.let { return it }
            PERSIAN_SYMBOL_REPR1[ch]?.let { return it }
        }
        return null
    }

    private fun extractSingleChar(raw: String): Char? {
        if (raw.isEmpty()) return null
        // Preserve ZWNJ even if raw == "\u200c"
        if (raw == "\u200c") return '\u200c'
        val trimmed = raw.trim()
        if (trimmed.length == 1) return trimmed[0]
        // TalkBack sometimes sends a combining diacritic attached to dotted circle ◌ (U+25CC) or space
        if (trimmed.length == 2 && (trimmed[0] == '\u25cc' || trimmed[0] == 'ـ') && trimmed[1] in PERSIAN_CHAR_REPR1) {
            return trimmed[1]
        }
        return null
    }
}
