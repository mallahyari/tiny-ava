package com.oncillaai.tinyava.engine

/**
 * Exact 46-symbol Persian IPA table and ByT5 byte-tokenizer for tiny-ava + persian-g2p-tiny.
 * Matches `tiny-ava-parisa.json`, `src/tiny_voca/text.py`, and `demo/worker.js` 1-to-1.
 */
object PersianPhonemeMapper {

    const val PAD = "<pad>"
    const val BOS = "<bos>"
    const val EOS = "<eos>"
    const val SP = "<sp>"
    const val UNK = "<unk>"

    val SYMBOLS: List<String> = listOf(
        PAD, BOS, EOS, SP, UNK,
        "،", ".", "؟", "!", "؛", ":", "«", "»", "(", ")", "—", "…",
        "p", "b", "t", "d", "k", "ɡ", "q", "ʔ",
        "f", "v", "s", "z", "ʃ", "ʒ", "x", "h",
        "tʃ", "dʒ", "m", "n", "l", "r", "j",
        "a", "e", "o", "i", "u", "ɑ",
    )

    val SYMBOL_TO_ID: Map<String, Int> = SYMBOLS.withIndex().associate { (i, s) -> s to i }
    val PUNCTUATION: Set<Char> = setOf('،', '.', '؟', '!', '؛', ':', '«', '»', '(', ')', '—', '…')
    val PHONE_SET: Set<String> = SYMBOLS.drop(17).toSet()
    val VOWELS: Set<String> = setOf("a", "e", "o", "i", "u", "ɑ")

    val REPR1_TO_IPA: Map<Char, String> = mapOf(
        'a' to "a", 'A' to "ɑ", 'e' to "e", 'o' to "o", 'i' to "i", 'u' to "u",
        'p' to "p", 'b' to "b", 't' to "t", 'd' to "d", 'k' to "k", 'g' to "ɡ", 'q' to "q", '?' to "ʔ",
        'f' to "f", 'v' to "v", 's' to "s", 'z' to "z", 'S' to "ʃ", 'Z' to "ʒ", ';' to "ʒ", 'x' to "x", 'h' to "h",
        'C' to "tʃ", 'j' to "dʒ", 'm' to "m", 'n' to "n", 'l' to "l", 'r' to "r", 'y' to "j",
    )

    data class TokenizedSentence(
        val symbols: List<String>,
        val tokenIds: LongArray,
        val stressIds: LongArray,
    )

    /** Pure-Kotlin ByT5 byte encoder: UTF-8 byte + 3, terminated by EOS (1). */
    fun byt5Encode(text: String): LongArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = LongArray(bytes.size + 1)
        for (i in bytes.indices) {
            out[i] = (bytes[i].toInt() and 0xFF) + 3L
        }
        out[bytes.size] = 1L // </s> EOS
        return out
    }

    /** Pure-Kotlin ByT5 byte decoder: strips special tokens (< 3), decodes UTF-8. */
    fun byt5Decode(ids: List<Int>): String {
        val raw = ByteArray(ids.size)
        var n = 0
        for (id in ids) {
            if (id in 3..258) {
                raw[n++] = (id - 3).toByte()
            }
        }
        return String(raw, 0, n, Charsets.UTF_8).trim()
    }

    /** Split normalized text into (isPunctuation, piece) chunks. */
    fun splitPunctChunks(text: String): List<Pair<Boolean, String>> {
        val out = mutableListOf<Pair<Boolean, String>>()
        var start = 0
        for (i in text.indices) {
            if (text[i] !in PUNCTUATION) continue
            val seg = text.substring(start, i).trim()
            if (seg.isNotEmpty()) out.add(false to seg)
            out.add(true to text[i].toString())
            start = i + 1
        }
        val tail = text.substring(start).trim()
        if (tail.isNotEmpty()) out.add(false to tail)
        return out
    }

    /** Convert Repr. 1 phoneme string into words of (ipaSymbol, stressLevel 0/1/2). */
    fun repr1ToWords(repr1: String): List<List<Pair<String, Int>>> {
        val wordsOut = mutableListOf<List<Pair<String, Int>>>()
        for (rawW in repr1.trim().split(Regex("\\s+"))) {
            if (rawW.isEmpty()) continue
            val (rawStem, ez) = when {
                rawW.endsWith("-ye") && rawW.length > 3 -> rawW.dropLast(3) to "ye"
                rawW.endsWith("-e") && rawW.length > 2 -> rawW.dropLast(2) to "e"
                else -> rawW to ""
            }
            var stem = rawStem.replace("-", "")
            if (stem.length >= 2 && stem[0] == '?' && stem[1] in "aAeoiu") {
                stem = stem.substring(1)
            }
            val phones = mutableListOf<Pair<String, Int>>()
            var i = 0
            while (i < stem.length) {
                if (i + 1 < stem.length && stem[i] == 'z' && stem[i + 1] == 'h') {
                    phones.add("ʒ" to 0)
                    i += 2
                    continue
                }
                val ipa = REPR1_TO_IPA[stem[i]]
                if (ipa != null) phones.add(ipa to 0)
                i++
            }
            // Persian primary word-final lexical stress on last vowel of stem
            for (i in phones.lastIndex downTo 0) {
                if (phones[i].first in VOWELS) {
                    phones[i] = phones[i].first to 1
                    break
                }
            }
            // Explicit ezafe vowel carries stress=2
            if (ez.isNotEmpty()) {
                for (ch in ez) {
                    val ipa = REPR1_TO_IPA[ch]
                    if (ipa != null) phones.add(ipa to if (ipa in VOWELS) 2 else 0)
                }
            }
            if (phones.isNotEmpty()) wordsOut.add(phones)
        }
        return wordsOut
    }

    /** Assemble final <bos> ... <eos> token & stress arrays from (isPunct, value/words). */
    fun buildTokenSequence(chunks: List<Pair<Boolean, Any>>): TokenizedSentence? {
        val syms = mutableListOf(BOS)
        val stress = mutableListOf(0L)
        for ((isPunct, value) in chunks) {
            if (isPunct) {
                val mark = value as String
                if (syms.last() != mark && mark in SYMBOL_TO_ID) {
                    syms.add(mark)
                    stress.add(0L)
                }
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val words = value as List<List<Pair<String, Int>>>
            for (w in words) {
                val prev = syms.last()
                if (prev in PHONE_SET || prev == UNK) {
                    syms.add(SP)
                    stress.add(0L)
                }
                for ((ph, lvl) in w) {
                    if (ph in PHONE_SET) {
                        syms.add(ph)
                        stress.add(lvl.toLong())
                    } else {
                        syms.add(UNK)
                        stress.add(0L)
                    }
                }
            }
        }
        syms.add(EOS)
        stress.add(0L)
        if (syms.none { it in PHONE_SET }) return null
        val ids = LongArray(syms.size) { SYMBOL_TO_ID.getValue(syms[it]).toLong() }
        return TokenizedSentence(syms, ids, stress.toLongArray())
    }
}
