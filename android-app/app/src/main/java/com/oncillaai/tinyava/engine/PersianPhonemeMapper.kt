package com.oncillaai.tinyava.engine

/**
 * Exact 84-symbol unified Persian + English IPA table for tiny-ava v2 (`tiny-ava-v2.onnx`)
 * and ByT5 byte-tokenizer for `persian-g2p-tiny`.
 */
object PersianPhonemeMapper {

    const val PAD = "<pad>"
    const val BOS = "<bos>"
    const val EOS = "<eos>"
    const val SP = "<sp>"
    const val UNK = "<unk>"

    val SYMBOLS: List<String> = listOf(
        PAD, BOS, EOS, SP, UNK,
        ",", ".", "?", "!", ";", ":", "\"", "'", "(", ")", "—", "…",
        // 29 unified consonants (26 EN + 3 FA-specific q, x, r)
        "p", "b", "t", "d", "k", "ɡ", "f", "v", "θ", "ð", "s", "z", "ʃ", "ʒ", "h",
        "m", "n", "ŋ", "l", "ɹ", "j", "w", "tʃ", "dʒ", "ɾ", "ʔ", "q", "x", "r",
        // 38 unified vowels (33 EN + 5 FA-specific a, e, o, u, ɑ)
        "ə", "ɐ", "ɚ", "ᵻ", "ɪ", "i", "iː", "ɛ", "æ", "ʌ", "ɑː", "ɔ", "ɔː", "ʊ", "uː", "ɜː",
        "eɪ", "aɪ", "ɔɪ", "oʊ", "aʊ", "iə", "oː",
        "ɑːɹ", "ɔːɹ", "oːɹ", "ɪɹ", "ɛɹ", "ʊɹ", "aɪɚ", "aɪə", "əl", "n̩",
        "a", "e", "o", "u", "ɑ",
    )

    val SYMBOL_TO_ID: Map<String, Int> = SYMBOLS.withIndex().associate { (i, s) -> s to i }
    val PUNCTUATION: Set<Char> = setOf(',', '.', '?', '!', ';', ':', '"', '\'', '(', ')', '—', '…')
    val PHONE_SET: Set<String> = SYMBOLS.drop(17).toSet()
    val VOWELS: Set<String> = SYMBOLS.drop(46).toSet()

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

    fun byt5Encode(text: String): LongArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = LongArray(bytes.size + 1)
        for (i in bytes.indices) out[i] = (bytes[i].toInt() and 0xFF) + 3L
        out[bytes.size] = 1L
        return out
    }

    fun byt5Decode(ids: List<Int>): String {
        val raw = ByteArray(ids.size)
        var n = 0
        for (id in ids) {
            if (id in 3..258) raw[n++] = (id - 3).toByte()
        }
        return String(raw, 0, n, Charsets.UTF_8).trim()
    }

    /** Fold Persian punctuation into the shared v2 pause tokens (، -> , , ؟ -> ? , ؛ -> ;). */
    fun foldSharedPunctuation(text: String): String {
        return text
            .replace('،', ',')
            .replace('؟', '?')
            .replace('؛', ';')
            .replace('«', '"')
            .replace('»', '"')
    }

    fun splitPunctChunks(text: String): List<Pair<Boolean, String>> {
        val folded = foldSharedPunctuation(text)
        val out = mutableListOf<Pair<Boolean, String>>()
        var start = 0
        for (i in folded.indices) {
            if (folded[i] !in PUNCTUATION) continue
            val seg = folded.substring(start, i).trim()
            if (seg.isNotEmpty()) out.add(false to seg)
            out.add(true to folded[i].toString())
            start = i + 1
        }
        val tail = folded.substring(start).trim()
        if (tail.isNotEmpty()) out.add(false to tail)
        return out
    }

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
            for (k in phones.lastIndex downTo 0) {
                if (phones[k].first in VOWELS) {
                    phones[k] = phones[k].first to 1
                    break
                }
            }
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
