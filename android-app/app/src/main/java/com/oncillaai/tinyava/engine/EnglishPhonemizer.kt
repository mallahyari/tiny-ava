package com.oncillaai.tinyava.engine

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * Lightweight pure-Kotlin English phonemizer backed by tiny-voca's Apache-2.0 lexicon (`lexicon_en.tsv`)
 * + morphological suffix rules + clean letter-to-sound fallback, mapped to `tiny-voca-en.onnx`'s
 * exact 76-symbol vocabulary.
 */
class EnglishPhonemizer private constructor(
    private val vocab: Map<String, Int>,
    private val lexicon: Map<String, String>,
) {
    companion object {
        private val PUNCT_SET = setOf(',', '.', '?', '!', ';', ':', '"', '\'', '(', ')', '—', '…')
        private val LETTER_NAMES = mapOf(
            'a' to "ˈeɪ", 'b' to "b ˈiː", 'c' to "s ˈiː", 'd' to "d ˈiː", 'e' to "ˈiː",
            'f' to "ˈɛ f", 'g' to "dʒ ˈiː", 'h' to "ˈeɪ tʃ", 'i' to "ˈaɪ", 'j' to "dʒ ˈeɪ",
            'k' to "k ˈeɪ", 'l' to "ˈɛ l", 'm' to "ˈɛ m", 'n' to "ˈɛ n", 'o' to "ˈoʊ",
            'p' to "p ˈiː", 'q' to "k j ˈuː", 'r' to "ˈɑːɹ", 's' to "ˈɛ s", 't' to "t ˈiː",
            'u' to "j ˈuː", 'v' to "v ˈiː", 'w' to "d ˈʌ b əl j uː", 'x' to "ˈɛ k s",
            'y' to "w ˈaɪ", 'z' to "z ˈiː",
        )

        fun loadFromAssets(context: Context): EnglishPhonemizer {
            val lex = HashMap<String, String>(130_000)
            context.assets.open("models/en/lexicon_en.tsv").use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    for (line in lines) {
                        val tab = line.indexOf('\t')
                        if (tab > 0) {
                            lex[line.substring(0, tab)] = line.substring(tab + 1)
                        }
                    }
                }
            }
            return EnglishPhonemizer(PersianPhonemeMapper.SYMBOL_TO_ID, lex)
        }

        /** Pure-JVM constructor for unit tests without Android Context. */
        fun fromMaps(vocab: Map<String, Int>, lexicon: Map<String, String>) =
            EnglishPhonemizer(vocab, lexicon)
    }

    data class EnTokens(val tokenIds: LongArray, val stressIds: LongArray)

    private val bosId = vocab.getValue("<bos>").toLong()
    private val eosId = vocab.getValue("<eos>").toLong()
    private val spId = vocab.getValue("<sp>").toLong()
    private val unkId = vocab.getValue("<unk>").toLong()

    fun phonemize(rawText: String): EnTokens? {
        val text = normalizeEnglish(rawText)
        if (text.isBlank()) return null

        val ids = ArrayList<Long>(text.length * 2)
        val stress = ArrayList<Long>(text.length * 2)
        ids.add(bosId); stress.add(0L)

        // Split camelCase words (e.g. "CaptainPhoenix" -> "Captain Phoenix") and tokenize words/punctuation
        val spaced = text
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("[_/@#]+"), " ")

        val tokenRegex = Regex("[A-Za-z]+(?:'[A-Za-z]+)?|\\d+|[.,?!;:—…]")
        var prevWasPhone = false

        for (match in tokenRegex.findAll(spaced)) {
            val tok = match.value
            if (tok.length == 1 && tok[0] in PUNCT_SET) {
                val pid = vocab[tok]?.toLong() ?: continue
                if (ids.last() != pid) {
                    ids.add(pid); stress.add(0L)
                }
                prevWasPhone = false
                continue
            }

            val subWords = if (tok.all { it.isDigit() }) {
                englishIntWords(tok.toLongOrNull() ?: 0L).split(" ")
            } else {
                listOf(tok)
            }

            for (w in subWords) {
                if (w.isBlank()) continue
                val ipaStr = lookupWord(w) ?: continue
                if (prevWasPhone) {
                    ids.add(spId); stress.add(0L)
                }
                for (unit in ipaStr.split(' ')) {
                    if (unit.isEmpty()) continue
                    var lvl = 0L
                    var phone = unit
                    if (phone.startsWith("ˈ")) { lvl = 1L; phone = phone.substring(1) }
                    else if (phone.startsWith("ˌ")) { lvl = 2L; phone = phone.substring(1) }
                    val id = vocab[phone]?.toLong() ?: unkId
                    ids.add(id); stress.add(lvl)
                    prevWasPhone = true
                }
            }
        }

        // Ensure terminal period so tiny-voca-en finishes the last syllable cleanly
        val periodId = vocab["."]?.toLong()
        if (periodId != null && prevWasPhone) {
            ids.add(periodId); stress.add(0L)
        }
        ids.add(eosId); stress.add(0L)

        if (ids.size <= 3) return null
        return EnTokens(ids.toLongArray(), stress.toLongArray())
    }

    private fun lookupWord(word: String): String? {
        val lower = word.lowercase(Locale.ROOT)
        lexicon[lower]?.let { return it }

        // Acronym / all-caps short word (2..4 letters, e.g. AI, USB, PDF, VPN, FT)
        if (word.length in 2..4 && word.all { it in 'A'..'Z' } && word.none { it in "AEIOU" } ||
            word in setOf("AI", "UI", "UX", "ID", "OS", "TV", "PC", "OK", "AD", "FT")
        ) {
            return lower.mapNotNull { LETTER_NAMES[it] }.joinToString(" ")
        }

        // Possessive / plural / tense morphology fallback
        if (lower.endsWith("'s")) {
            lexicon[lower.dropLast(2)]?.let { return "$it z" }
        }
        if (lower.endsWith("ies") && lower.length > 4) {
            lexicon[lower.dropLast(3) + "y"]?.let { return "$it z" }
        }
        if (lower.endsWith("es") && lower.length > 3) {
            lexicon[lower.dropLast(2)]?.let { return "$it ɪ z" }
        }
        if (lower.endsWith("s") && lower.length > 2) {
            lexicon[lower.dropLast(1)]?.let { return "$it z" }
        }
        if (lower.endsWith("ing") && lower.length > 5) {
            val stem = lower.dropLast(3)
            lexicon[stem]?.let { return "$it ɪ ŋ" }
            lexicon[stem + "e"]?.let { return "$it ɪ ŋ" }
        }
        if (lower.endsWith("ed") && lower.length > 4) {
            val stem = lower.dropLast(2)
            lexicon[stem]?.let { return "$it d" }
            lexicon[stem + "e"]?.let { return "$it d" }
        }
        if (lower.endsWith("ly") && lower.length > 4) {
            lexicon[lower.dropLast(2)]?.let { return "$it l i" }
        }

        // Clean rule-based letter-to-sound fallback for unseen names/handles (never drops words!)
        return ruleFallback(lower)
    }

    private fun ruleFallback(word: String): String {
        val out = mutableListOf<String>()
        var i = 0
        while (i < word.length) {
            val rem = word.substring(i)
            when {
                rem.startsWith("tion") -> { out.addAll(listOf("ʃ", "ə", "n")); i += 4 }
                rem.startsWith("ch") -> { out.add("tʃ"); i += 2 }
                rem.startsWith("sh") -> { out.add("ʃ"); i += 2 }
                rem.startsWith("th") -> { out.add("θ"); i += 2 }
                rem.startsWith("ph") -> { out.add("f"); i += 2 }
                rem.startsWith("ng") -> { out.add("ŋ"); i += 2 }
                rem.startsWith("ee") || rem.startsWith("ea") -> { out.add("iː"); i += 2 }
                rem.startsWith("oo") -> { out.add("uː"); i += 2 }
                rem.startsWith("ou") || rem.startsWith("ow") -> { out.add("aʊ"); i += 2 }
                rem.startsWith("ai") || rem.startsWith("ay") -> { out.add("eɪ"); i += 2 }
                rem.startsWith("ck") -> { out.add("k"); i += 2 }
                else -> {
                    val ch = word[i]
                    val mapped = when (ch) {
                        'a' -> "æ"; 'b' -> "b"; 'c' -> "k"; 'd' -> "d"; 'e' -> "ɛ"; 'f' -> "f"
                        'g' -> "ɡ"; 'h' -> "h"; 'i' -> "ɪ"; 'j' -> "dʒ"; 'k' -> "k"; 'l' -> "l"
                        'm' -> "m"; 'n' -> "n"; 'o' -> "ɑː"; 'p' -> "p"; 'q' -> "k"; 'r' -> "ɹ"
                        's' -> "s"; 't' -> "t"; 'u' -> "ʌ"; 'v' -> "v"; 'w' -> "w"; 'x' -> "k s"
                        'y' -> "j"; 'z' -> "z"
                        else -> null
                    }
                    if (mapped != null) out.add(mapped)
                    i++
                }
            }
        }
        return out.joinToString(" ")
    }

    private fun normalizeEnglish(text: String): String {
        return text
            .replace('’', '\'').replace('‘', '\'')
            .replace('“', '"').replace('”', '"')
            .replace("–", "—").replace("...", "…")
            .replace(Regex("(\\d+)\\s*%")) { "${englishIntWords(it.groupValues[1].toLongOrNull() ?: 0L)} percent" }
            .trim()
    }

    private fun englishIntWords(n: Long): String {
        if (n == 0L) return "zero"
        if (n < 0L) return "minus " + englishIntWords(-n)
        if (n >= 1_000_000L) return n.toString().map { englishIntWords((it - '0').toLong()) }.joinToString(" ")
        val ones = arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        fun under1000(x: Int): String {
            val parts = mutableListOf<String>()
            if (x >= 100) parts.add("${ones[x / 100]} hundred")
            val r = x % 100
            if (r in 1..19) parts.add(ones[r])
            else if (r >= 20) {
                parts.add(tens[r / 10])
                if (r % 10 > 0) parts.add(ones[r % 10])
            }
            return parts.joinToString(" ")
        }
        val parts = mutableListOf<String>()
        if (n >= 1000) parts.add("${under1000((n / 1000).toInt())} thousand")
        if (n % 1000 > 0) parts.add(under1000((n % 1000).toInt()))
        return parts.joinToString(" ")
    }
}
