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

    @Test
    fun testBilingualSpanSplitterAndEmojiCleanup() {
        val raw = "👑 Captain Phoenix 👑 یارو پسره با ۲۰۶ چسبیده بود 😍"
        val spans = com.oncillaai.tinyava.engine.LanguageSpanSplitter.split(raw)
        assertEquals(2, spans.size)
        assertEquals(com.oncillaai.tinyava.engine.LanguageSpanSplitter.Lang.EN, spans[0].lang)
        assertEquals("Captain Phoenix", spans[0].text)
        assertEquals(com.oncillaai.tinyava.engine.LanguageSpanSplitter.Lang.FA, spans[1].lang)
        assertTrue(spans[1].text.contains("۲۰۶"))
    }

    @Test
    fun testSonicTimeStretchHighSpeed() {
        // 1 second of 200 Hz sine wave at 24 kHz
        val sr = 24000
        val wave = FloatArray(sr) { i -> kotlin.math.sin(2.0 * Math.PI * 200.0 * i / sr).toFloat() * 0.5f }
        val fast2x = com.oncillaai.tinyava.engine.SonicTimeStretch.changeSpeed(wave, sr, 2.0f)
        val fast3x = com.oncillaai.tinyava.engine.SonicTimeStretch.changeSpeed(wave, sr, 2.5f)
        assertTrue("2x should shrink near half: ${fast2x.size}", fast2x.size in (sr * 0.40).toInt()..(sr * 0.62).toInt())
        assertTrue("2.5x should shrink further: ${fast3x.size}", fast3x.size < fast2x.size)
        assertTrue(fast2x.all { it.isFinite() })
        assertTrue(fast3x.all { it.isFinite() })
    }
}
