package com.oncillaai.tinyava.engine

/**
 * Splits mixed Persian + English TalkBack text (e.g. X/Twitter, WhatsApp, Android UI)
 * into clean monolingual spans and strips non-speech emojis/symbols cleanly.
 */
object LanguageSpanSplitter {

    enum class Lang { FA, EN }
    data class Span(val lang: Lang, val text: String)

    private fun isPersianLetter(ch: Char): Boolean =
        ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF'

    private fun isLatinLetter(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z'

    /** Remove emojis and non-speech graphic symbols while preserving Persian, Latin, digits, diacritics & punctuation. */
    fun stripEmojisAndNoise(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val step = Character.charCount(cp)
            val keep = when {
                cp in 0x0020..0x007E -> true // ASCII
                cp in 0x0600..0x06FF -> true // Arabic / Persian + diacritics
                cp == 0x200C -> true         // ZWNJ
                cp == 0x2014 || cp == 0x2013 || cp == 0x2026 || cp == 0x00AB || cp == 0x00BB -> true // — – … « »
                cp == 0x2018 || cp == 0x2019 || cp == 0x201C || cp == 0x201D -> true // smart quotes
                cp == '\n'.code || cp == '\r'.code || cp == '\t'.code -> true
                else -> false
            }
            if (keep) {
                sb.appendCodePoint(cp)
            } else {
                sb.append(' ')
            }
            i += step
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Split a cleaned utterance into contiguous FA and EN spans.
     * Numbers/punctuation inherit the current (or nearest) language so `"یارو با ۲۰۶"` stays FA
     * and `"version 2.0"` stays EN.
     */
    fun split(rawText: String, defaultLang: Lang = Lang.FA): List<Span> {
        val cleaned = stripEmojisAndNoise(rawText)
        if (cleaned.isBlank()) return emptyList()

        // Fast path: purely Persian (or purely English)
        val hasFa = cleaned.any { isPersianLetter(it) }
        val hasEn = cleaned.any { isLatinLetter(it) }
        if (hasFa && !hasEn) return listOf(Span(Lang.FA, cleaned))
        if (hasEn && !hasFa) return listOf(Span(Lang.EN, cleaned))
        if (!hasFa && !hasEn) return listOf(Span(defaultLang, cleaned))

        // Mixed sentence: group whitespace-separated tokens by script
        val tokens = cleaned.split(" ").filter { it.isNotBlank() }
        val out = mutableListOf<Span>()
        var curLang: Lang? = null
        val buf = StringBuilder()

        fun flush() {
            val s = buf.toString().trim()
            if (s.isNotEmpty() && curLang != null) {
                out.add(Span(curLang!!, s))
            }
            buf.setLength(0)
        }

        for (tok in tokens) {
            val tokFa = tok.any { isPersianLetter(it) }
            val tokEn = tok.any { isLatinLetter(it) }
            val tokLang = when {
                tokFa -> Lang.FA
                tokEn -> Lang.EN
                else -> curLang ?: defaultLang
            }
            if (curLang != null && tokLang != curLang) {
                flush()
            }
            curLang = tokLang
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(tok)
        }
        flush()
        return out
    }
}
