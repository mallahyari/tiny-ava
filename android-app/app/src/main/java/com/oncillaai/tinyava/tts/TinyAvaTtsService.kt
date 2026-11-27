package com.oncillaai.tinyava.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import com.oncillaai.tinyava.TinyAvaApp
import com.oncillaai.tinyava.data.AvaPersianVoice
import com.oncillaai.tinyava.engine.LanguageSpanSplitter
import com.oncillaai.tinyava.engine.PersianTextNormalizer
import com.oncillaai.tinyava.engine.TinyAvaEngine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ln

/**
 * Native Android System TTS Service for TalkBack and all apps — dual-language (fa-IR + en-US),
 * 100% offline, with glitch-free high-speed Sonic WSOLA up to 3.5x.
 */
class TinyAvaTtsService : TextToSpeechService() {

    private val stopped = AtomicBoolean(false)
    private lateinit var engine: TinyAvaEngine
    private val persianLocale = Locale.Builder().setLanguage("fa").setRegion("IR").build()
    private val englishLocale = Locale.US

    private val parisaVoice by lazy {
        Voice("fa-IR-parisa-local", persianLocale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_LOW, false, emptySet())
    }
    private val manaVoice by lazy {
        Voice("fa-IR-mana-local", persianLocale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_LOW, false, emptySet())
    }
    private val mayaVoice by lazy {
        Voice("en-US-maya-local", englishLocale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_LOW, false, emptySet())
    }

    override fun onCreate() {
        super.onCreate()
        engine = (application as TinyAvaApp).engine
        Thread { runCatching { engine.ensureLoaded() } }.start()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val l = lang?.lowercase(Locale.ROOT) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        val c = country?.uppercase(Locale.ROOT) ?: ""
        return when (l) {
            "fas", "fa", "per" -> if (c == "IRN" || c == "IR") TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
            "eng", "en" -> if (c == "USA" || c == "US") TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> = arrayOf("fas", "IRN", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val avail = onIsLanguageAvailable(lang, country, variant)
        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            runCatching { engine.ensureLoaded() }
        }
        return avail
    }

    override fun onGetVoices(): MutableList<Voice> = mutableListOf(parisaVoice, manaVoice, mayaVoice)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        val l = lang?.lowercase(Locale.ROOT) ?: return null
        return when (l) {
            "fas", "fa", "per" -> {
                val selected = (application as? TinyAvaApp)?.settingsStore?.state?.value?.persianVoice
                if (selected == AvaPersianVoice.MANA) manaVoice.name else parisaVoice.name
            }
            "eng", "en" -> mayaVoice.name
            else -> null
        }
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        return if (voiceName == parisaVoice.name || voiceName == manaVoice.name || voiceName == mayaVoice.name) {
            TextToSpeech.SUCCESS
        } else {
            TextToSpeech.ERROR
        }
    }

    override fun onLoadVoice(voiceName: String?): Int {
        if (onIsValidVoiceName(voiceName) != TextToSpeech.SUCCESS) return TextToSpeech.ERROR
        runCatching { engine.ensureLoaded() }
        return TextToSpeech.SUCCESS
    }

    override fun onStop() {
        stopped.set(true)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        stopped.set(false)

        val rawChars = request.charSequenceText?.toString().orEmpty()
        val text = if (rawChars == "\u200c") rawChars else rawChars.trim()
        if (text.isEmpty()) {
            callback.start(TinyAvaEngine.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        val settings = (application as TinyAvaApp).settingsStore.state.value
        val sysRate = (request.speechRate.coerceIn(50, 350) / 100f)
        val effectiveSpeed = (settings.speed * sysRate).coerceIn(0.65f, 3.5f)
        val sysPitchRatio = (request.pitch.coerceIn(50, 200) / 100f)
        val sysPitchSt = (12.0 * ln(sysPitchRatio.toDouble()) / ln(2.0)).toFloat()
        val effectivePitchSt = (settings.pitchSemitones + sysPitchSt).coerceIn(-6f, 6f)

        // Honor explicit Android voiceName (e.g. Multilingual TTS / Android voice picker)
        val reqVoice = request.voiceName.orEmpty()
        val persianVoiceOverride: AvaPersianVoice? = when (reqVoice) {
            parisaVoice.name -> AvaPersianVoice.PARISA
            manaVoice.name -> AvaPersianVoice.MANA
            else -> null
        }
        val reqLang = request.language?.lowercase(Locale.ROOT).orEmpty()
        val defaultLang = when {
            reqVoice == mayaVoice.name -> LanguageSpanSplitter.Lang.EN
            reqVoice == parisaVoice.name || reqVoice == manaVoice.name -> LanguageSpanSplitter.Lang.FA
            reqLang == "eng" || reqLang == "en" -> LanguageSpanSplitter.Lang.EN
            else -> LanguageSpanSplitter.Lang.FA
        }

        callback.start(TinyAvaEngine.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
        val maxChunk = callback.maxBufferSize.coerceAtLeast(4096)

        try {
            val sentences = PersianTextNormalizer.splitSentences(text)
            for (sent in sentences) {
                if (stopped.get()) {
                    callback.done()
                    return
                }
                val wave = engine.synthesizeSentence(
                    rawSentence = sent,
                    settings = settings,
                    speedOverride = effectiveSpeed,
                    pitchSemitonesOverride = effectivePitchSt,
                    defaultLang = defaultLang,
                    persianVoiceOverride = persianVoiceOverride,
                    isCancelled = { stopped.get() },
                )
                if (stopped.get()) {
                    callback.done()
                    return
                }
                if (wave.isEmpty()) continue

                val pcm = engine.floatToPcm16Bytes(wave)
                var offset = 0
                while (offset < pcm.size && !stopped.get()) {
                    val len = minOf(maxChunk, pcm.size - offset)
                    val res = callback.audioAvailable(pcm, offset, len)
                    if (res != TextToSpeech.SUCCESS) {
                        callback.done()
                        return
                    }
                    offset += len
                }
            }
            callback.done()
        } catch (_: Throwable) {
            callback.error()
        }
    }
}
