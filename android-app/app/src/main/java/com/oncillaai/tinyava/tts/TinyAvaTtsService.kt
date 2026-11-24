package com.oncillaai.tinyava.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import com.oncillaai.tinyava.TinyAvaApp
import com.oncillaai.tinyava.engine.PersianTextNormalizer
import com.oncillaai.tinyava.engine.TinyAvaEngine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ln

/**
 * Native Android System TTS Service for TalkBack and all third-party apps.
 * Runs 100% offline using bundled ONNX Runtime models (persian-g2p-tiny + tiny-ava Parisa).
 */
class TinyAvaTtsService : TextToSpeechService() {

    private val stopped = AtomicBoolean(false)
    private lateinit var engine: TinyAvaEngine
    private val persianLocale = Locale("fa", "IR")
    private val parisaVoice by lazy {
        Voice(
            "fa-IR-parisa-local",
            persianLocale,
            Voice.QUALITY_VERY_HIGH,
            Voice.LATENCY_VERY_LOW,
            false,
            emptySet(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        engine = (application as TinyAvaApp).engine
        // Warm up ONNX sessions in background so first TalkBack utterance has minimal latency
        Thread { runCatching { engine.ensureLoaded() } }.start()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val l = lang?.lowercase(Locale.ROOT) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        if (l == "fas" || l == "fa" || l == "per") {
            val c = country?.uppercase(Locale.ROOT) ?: ""
            return if (c == "IRN" || c == "IR") {
                TextToSpeech.LANG_COUNTRY_AVAILABLE
            } else {
                TextToSpeech.LANG_AVAILABLE
            }
        }
        return TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onGetLanguage(): Array<String> = arrayOf("fas", "IRN", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val avail = onIsLanguageAvailable(lang, country, variant)
        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            runCatching { engine.ensureLoaded() }
        }
        return avail
    }

    override fun onGetVoices(): MutableList<Voice> = mutableListOf(parisaVoice)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        return if (onIsLanguageAvailable(lang, country, variant) >= TextToSpeech.LANG_AVAILABLE) {
            parisaVoice.name
        } else {
            null
        }
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        return if (voiceName == parisaVoice.name) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }

    override fun onLoadVoice(voiceName: String?): Int {
        if (voiceName != parisaVoice.name) return TextToSpeech.ERROR
        runCatching { engine.ensureLoaded() }
        return TextToSpeech.SUCCESS
    }

    override fun onStop() {
        stopped.set(true)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        stopped.set(false)

        val text = request.charSequenceText?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            callback.start(TinyAvaEngine.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        val settings = (application as TinyAvaApp).settingsStore.state.value
        // Android speechRate: 100 = 1.0x; multiply by user's preferred speed setting
        val sysRate = (request.speechRate.coerceIn(50, 300) / 100f)
        val effectiveSpeed = (settings.speed * sysRate).coerceIn(0.65f, 2.6f)
        // Android pitch: 100 = 1.0x; convert ratio to semitones and add user pitch offset
        val sysPitchRatio = (request.pitch.coerceIn(50, 200) / 100f)
        val sysPitchSt = (12.0 * ln(sysPitchRatio.toDouble()) / ln(2.0)).toFloat()
        val effectivePitchSt = (settings.pitchSemitones + sysPitchSt).coerceIn(-6f, 6f)

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
