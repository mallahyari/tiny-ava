package com.oncillaai.tinyava

import com.oncillaai.tinyava.engine.PersianPhonemeMapper
import com.oncillaai.tinyava.engine.PersianTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianFrontendTest {

    @Test
    fun testNumberTimeAndPercentConversion() {
        assertEquals("صفر", PersianTextNormalizer.intToPersian(0))
        assertEquals("هشتاد و پنج", PersianTextNormalizer.intToPersian(85))
        assertEquals("هزار و چهارصد و پنج", PersianTextNormalizer.intToPersian(1405))
        val out = PersianTextNormalizer.convertNumbersToPersian("ساعت ۱۴:۳۰ و باتری ۸۵٪")
        assertEquals("ساعت چهارده و سی دقیقه و باتری هشتاد و پنج درصد", out)
    }

    @Test
    fun testEnglishUiAndCustomDict() {
        val out = PersianTextNormalizer.normalize(
            text = "Wi-Fi روشن است و تست خوب بود",
            convertNumbers = true,
            convertEnglishUi = true,
            customDictionary = mapOf("تست" to "تُست"),
        )
        assertEquals("وای‌فای روشن است و تُست خوب بود", out)
    }

    @Test
    fun testByt5RoundTrip() {
        val text = "بارانِ ریزی روی آسفالتِ خیسِ خیابان می‌بارد"
        val ids = PersianPhonemeMapper.byt5Encode(text)
        assertEquals(1L, ids.last())
        val decoded = PersianPhonemeMapper.byt5Decode(ids.dropLast(1).map { it.toInt() })
        assertEquals(text, decoded)
    }

    @Test
    fun testPhonemeInventoryAndStressEzafe() {
        assertEquals(46, PersianPhonemeMapper.SYMBOLS.size)
        assertEquals(29, PersianPhonemeMapper.PHONE_SET.size)

        val words = PersianPhonemeMapper.repr1ToWords("ketAb-e jadid-e ?az ba?d")
        assertEquals(4, words.size)
        // ketAb-e ends with ezafe e (stress=2) and stem vowel ɑ has primary stress=1
        assertEquals("e" to 2, words[0].last())
        assertTrue(words[0].contains("ɑ" to 1))
        // word-initial ? before vowel dropped (?az -> az), medial ? preserved (ba?d)
        assertEquals(listOf("a" to 1, "z" to 0), words[2])
        assertTrue(words[3].any { it.first == "ʔ" })

        val seq = PersianPhonemeMapper.buildTokenSequence(
            listOf(false to words, true to ".")
        )
        assertNotNull(seq)
        assertEquals("<bos>", seq!!.symbols.first())
        assertEquals("<eos>", seq.symbols.last())
        assertEquals(seq.symbols.size, seq.tokenIds.size)
        assertEquals(seq.symbols.size, seq.stressIds.size)
    }
}
