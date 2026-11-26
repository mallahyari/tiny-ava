package com.oncillaai.tinyava.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.LruCache
import com.oncillaai.tinyava.data.SettingsState
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs

/**
 * Thread-safe bilingual (Persian + English) ONNX Runtime engine:
 * - Persian: persian-g2p-tiny (8.4 MB int8) + tiny-ava Parisa (20.4 MB)
 * - English: lexicon_en.tsv (3.3 MB) + tiny-voca English F2 (20.4 MB, Apache-2.0)
 * - Glitch-free high speed (1.30x -> 3.5x+) via pitch-preserving Sonic WSOLA time-stretching
 */
class TinyAvaEngine private constructor(private val appContext: Context) {

    companion object {
        const val SAMPLE_RATE = 24_000
        private const val PEAK = 0.99f
        private const val MAX_G2P_LEN = 256
        private const val MAX_TOKENS = 512
        // Keep neural duration scaling inside its distortion-free sweet spot;
        // anything faster uses Sonic WSOLA waveform time-stretching (zero glitches!).
        private const val MAX_NEURAL_SPEED = 1.30f
        private const val MIN_NEURAL_SPEED = 0.75f

        @Volatile
        private var instance: TinyAvaEngine? = null

        fun getInstance(context: Context): TinyAvaEngine {
            return instance ?: synchronized(this) {
                instance ?: TinyAvaEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val lock = Any()
    private var ortEnv: OrtEnvironment? = null
    private var g2pEncoder: OrtSession? = null
    private var g2pDecoder: OrtSession? = null
    private var ttsFaSession: OrtSession? = null
    private var ttsEnSession: OrtSession? = null
    private var enPhonemizer: EnglishPhonemizer? = null

    // LRU RAM cache for short TalkBack UI phrases (< 45 chars): instant <5ms playback
    private val phraseCache = LruCache<String, FloatArray>(256)

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun ensureLoaded() {
        if (isLoaded) return
        synchronized(lock) {
            if (isLoaded) return
            val env = OrtEnvironment.getEnvironment()
            ortEnv = env
            val opts = OrtSession.SessionOptions().apply {
                val cores = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                setIntraOpNumThreads(cores)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val modelDir = ensureModelsOnDisk()
            g2pEncoder = env.createSession(File(modelDir, "g2p_encoder.onnx").absolutePath, opts)
            g2pDecoder = env.createSession(File(modelDir, "g2p_decoder.onnx").absolutePath, opts)
            ttsFaSession = env.createSession(File(modelDir, "tiny-ava-parisa.onnx").absolutePath, opts)
            ttsEnSession = env.createSession(File(modelDir, "tiny-voca-en.onnx").absolutePath, opts)
            enPhonemizer = EnglishPhonemizer.loadFromAssets(appContext)
            isLoaded = true
        }
    }

    private fun ensureModelsOnDisk(): File {
        val dir = File(appContext.filesDir, "tiny_ava_models_v3")
        if (!dir.exists()) dir.mkdirs()
        val files = listOf(
            "models/g2p_encoder.onnx" to "g2p_encoder.onnx",
            "models/g2p_decoder.onnx" to "g2p_decoder.onnx",
            "models/tiny-ava-parisa.onnx" to "tiny-ava-parisa.onnx",
            "models/en/tiny-voca-en.onnx" to "tiny-voca-en.onnx",
        )
        for ((assetPath, outName) in files) {
            val out = File(dir, outName)
            if (out.exists() && out.length() > 0L) continue
            val tmp = File(dir, "$outName.tmp")
            appContext.assets.open(assetPath).use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            tmp.renameTo(out)
        }
        return dir
    }

    fun clearPhraseCache() {
        phraseCache.evictAll()
    }

    /** Run persian-g2p-tiny (ByT5 ONNX) greedily on one Persian clause -> Repr. 1 ASCII phonemes. */
    private fun runG2pClause(clause: String, isCancelled: () -> Boolean): String {
        val env = ortEnv!!
        val enc = g2pEncoder!!
        val dec = g2pDecoder!!

        val inIds = PersianPhonemeMapper.byt5Encode(clause)
        val srcLen = inIds.size.toLong()
        val attn = LongArray(inIds.size) { 1L }

        val inTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inIds), longArrayOf(1L, srcLen))
        val attnTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attn), longArrayOf(1L, srcLen))
        val encOut = try {
            enc.run(mapOf("input_ids" to inTensor, "attention_mask" to attnTensor))
        } finally {
            inTensor.close()
        }

        val encHidden = encOut.get("encoder_hidden_states").get() as OnnxTensor
        val decIds = ArrayList<Int>(96).apply { add(0) }

        try {
            for (step in 0 until MAX_G2P_LEN) {
                if (isCancelled()) break
                val dArr = LongArray(decIds.size) { decIds[it].toLong() }
                val dTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(dArr),
                    longArrayOf(1L, dArr.size.toLong()),
                )
                val nextId = try {
                    dec.run(
                        mapOf(
                            "decoder_input_ids" to dTensor,
                            "encoder_hidden_states" to encHidden,
                            "encoder_attention_mask" to attnTensor,
                        )
                    ).use { decOut ->
                        val logitsTensor = decOut.get("logits").get() as OnnxTensor
                        val vocab = logitsTensor.info.shape[2].toInt()
                        val fb = logitsTensor.floatBuffer
                        val offset = (decIds.size - 1) * vocab
                        var bestIdx = 0
                        var bestVal = Float.NEGATIVE_INFINITY
                        for (v in 0 until vocab) {
                            val value = fb.get(offset + v)
                            if (value > bestVal) {
                                bestVal = value
                                bestIdx = v
                            }
                        }
                        bestIdx
                    }
                } finally {
                    dTensor.close()
                }
                if (nextId == 1) break
                decIds.add(nextId)
            }
        } finally {
            encOut.close()
            attnTensor.close()
        }
        return PersianPhonemeMapper.byt5Decode(decIds.subList(1, decIds.size))
    }

    /**
     * Synthesize one sentence (Persian, English, or mixed code-switched!) -> 24 kHz mono FloatArray.
     * Supports glitch-free speeds up to 3.5x via neural speed (<= 1.30x) + Sonic WSOLA time-stretching.
     */
    fun synthesizeSentence(
        rawSentence: String,
        settings: SettingsState,
        speedOverride: Float? = null,
        pitchSemitonesOverride: Float? = null,
        defaultLang: LanguageSpanSplitter.Lang = LanguageSpanSplitter.Lang.FA,
        isCancelled: () -> Boolean = { false },
    ): FloatArray {
        ensureLoaded()
        if (isCancelled()) return FloatArray(0)

        val cleanedRaw = LanguageSpanSplitter.stripEmojisAndNoise(rawSentence)
        if (cleanedRaw.isBlank()) return FloatArray(0)

        val targetSpeed = (speedOverride ?: settings.speed).coerceIn(0.6f, 3.5f)
        val neuralSpeed = targetSpeed.coerceIn(MIN_NEURAL_SPEED, MAX_NEURAL_SPEED)
        val sonicFactor = targetSpeed / neuralSpeed
        val pitchSt = (pitchSemitonesOverride ?: settings.pitchSemitones).coerceIn(-6.0f, 6.0f)
        val pitchRange = settings.pitchRange.coerceIn(0.7f, 1.4f)

        val cacheKey = if (settings.enablePhraseCache && cleanedRaw.length <= 45) {
            "$cleanedRaw|${defaultLang.name}|${settings.enableEnglishVoice}|${"%.2f".format(targetSpeed)}|${"%.1f".format(pitchSt)}"
        } else null

        if (cacheKey != null) {
            phraseCache.get(cacheKey)?.let { return it }
        }

        // Apply user custom pronunciation dictionary FIRST (so explicit overrides like Wi-Fi -> وای‌فای win)
        val dictMap = settings.customDictionary.associate { it.source to it.replacement }
        var withCustom = cleanedRaw
        if (dictMap.isNotEmpty()) {
            for ((src, dst) in dictMap.entries.sortedByDescending { it.key.length }) {
                if (src.isBlank()) continue
                val esc = Regex.escape(src)
                val re = if (src.all { it.isLetterOrDigit() || it == '-' }) {
                    Regex("(?<![\\p{L}\\p{N}])$esc(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                } else {
                    Regex(esc, RegexOption.IGNORE_CASE)
                }
                withCustom = re.replace(withCustom, dst)
            }
        }

        val spans = if (settings.enableEnglishVoice) {
            LanguageSpanSplitter.split(withCustom, defaultLang = defaultLang)
        } else {
            listOf(LanguageSpanSplitter.Span(LanguageSpanSplitter.Lang.FA, withCustom))
        }
        if (spans.isEmpty()) return FloatArray(0)

        val audioParts = mutableListOf<FloatArray>()
        val interSpanGap = FloatArray((SAMPLE_RATE * 0.055f).toInt())

        for ((idx, span) in spans.withIndex()) {
            if (isCancelled()) return FloatArray(0)
            val part = when (span.lang) {
                LanguageSpanSplitter.Lang.FA -> synthPersianSpan(
                    text = span.text,
                    settings = settings,
                    neuralSpeed = neuralSpeed,
                    pitchSt = pitchSt,
                    pitchRange = pitchRange,
                    isCancelled = isCancelled,
                )
                LanguageSpanSplitter.Lang.EN -> synthEnglishSpan(
                    text = span.text,
                    neuralSpeed = neuralSpeed,
                    pitchSt = pitchSt,
                    pitchRange = pitchRange,
                    isCancelled = isCancelled,
                )
            }
            if (isCancelled()) return FloatArray(0)
            if (part.isNotEmpty()) {
                audioParts.add(part)
                if (idx < spans.lastIndex) audioParts.add(interSpanGap)
            }
        }

        if (audioParts.isEmpty()) return FloatArray(0)
        val joined = if (audioParts.size == 1) {
            audioParts[0]
        } else {
            val total = audioParts.sumOf { it.size }
            FloatArray(total).also { dst ->
                var off = 0
                for (p in audioParts) {
                    p.copyInto(dst, off)
                    off += p.size
                }
            }
        }

        val stretched = if (abs(sonicFactor - 1.0f) >= 0.02f) {
            SonicTimeStretch.changeSpeed(joined, SAMPLE_RATE, sonicFactor)
        } else {
            joined
        }
        val clean = limitPeak(stretched)
        if (cacheKey != null && clean.isNotEmpty()) {
            phraseCache.put(cacheKey, clean)
        }
        return clean
    }

    private fun synthPersianSpan(
        text: String,
        settings: SettingsState,
        neuralSpeed: Float,
        pitchSt: Float,
        pitchRange: Float,
        isCancelled: () -> Boolean,
    ): FloatArray {
        val normalized = PersianTextNormalizer.normalize(
            text = text,
            convertNumbers = settings.convertNumbers,
            convertEnglishUi = !settings.enableEnglishVoice && settings.convertEnglishUi,
            customDictionary = emptyMap(), // already applied before span splitting
        )
        if (normalized.isBlank()) return FloatArray(0)
        val withMark = PersianTextNormalizer.ensureTerminalMark(normalized)
        val punctChunks = PersianPhonemeMapper.splitPunctChunks(withMark)

        val assembled = mutableListOf<Pair<Boolean, Any>>()
        synchronized(lock) {
            for ((isPunct, piece) in punctChunks) {
                if (isCancelled()) return FloatArray(0)
                if (isPunct) {
                    assembled.add(true to piece)
                } else {
                    val repr1 = runG2pClause(piece, isCancelled)
                    if (isCancelled()) return FloatArray(0)
                    assembled.add(false to PersianPhonemeMapper.repr1ToWords(repr1))
                }
            }
        }
        val tokenized = PersianPhonemeMapper.buildTokenSequence(assembled) ?: return FloatArray(0)
        if (tokenized.tokenIds.size > MAX_TOKENS || isCancelled()) return FloatArray(0)

        return synchronized(lock) {
            if (isCancelled()) return FloatArray(0)
            runTtsOnnx(ttsFaSession!!, tokenized.tokenIds, tokenized.stressIds, 0L, neuralSpeed, pitchSt * 0.058f, pitchRange)
        }
    }

    private fun synthEnglishSpan(
        text: String,
        neuralSpeed: Float,
        pitchSt: Float,
        pitchRange: Float,
        isCancelled: () -> Boolean,
    ): FloatArray {
        val phon = enPhonemizer ?: return FloatArray(0)
        val tok = phon.phonemize(text) ?: return FloatArray(0)
        if (tok.tokenIds.size > MAX_TOKENS || isCancelled()) return FloatArray(0)
        return synchronized(lock) {
            if (isCancelled()) return FloatArray(0)
            // voice = 0 (F2 female, matching Parisa's female voice seamlessly)
            runTtsOnnx(ttsEnSession!!, tok.tokenIds, tok.stressIds, 0L, neuralSpeed, pitchSt * 0.058f, pitchRange)
        }
    }

    private fun runTtsOnnx(
        session: OrtSession,
        tokenIds: LongArray,
        stressIds: LongArray,
        voiceId: Long,
        speed: Float,
        pitchShift: Float,
        pitchRange: Float,
    ): FloatArray {
        val env = ortEnv!!
        val n = tokenIds.size.toLong()

        val tTokens = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenIds), longArrayOf(1L, n))
        val tStress = OnnxTensor.createTensor(env, LongBuffer.wrap(stressIds), longArrayOf(1L, n))
        val tVoice = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(voiceId)), longArrayOf(1L))
        val tSpeed = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1L))
        val tPitch = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(pitchShift)), longArrayOf(1L))
        val tRange = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(pitchRange)), longArrayOf(1L))

        return try {
            session.run(
                mapOf(
                    "tokens" to tTokens,
                    "stress" to tStress,
                    "voice" to tVoice,
                    "speed" to tSpeed,
                    "pitch_shift" to tPitch,
                    "pitch_range" to tRange,
                )
            ).use { out ->
                val audioTensor = out.get("audio").get() as OnnxTensor
                val fb = audioTensor.floatBuffer
                val result = FloatArray(fb.remaining())
                fb.get(result)
                result
            }
        } finally {
            tTokens.close()
            tStress.close()
            tVoice.close()
            tSpeed.close()
            tPitch.close()
            tRange.close()
        }
    }

    private fun limitPeak(wave: FloatArray): FloatArray {
        var maxAbs = 0f
        for (x in wave) {
            val a = abs(x)
            if (a > maxAbs) maxAbs = a
        }
        if (maxAbs <= PEAK || maxAbs == 0f) return wave
        val scale = PEAK / maxAbs
        return FloatArray(wave.size) { wave[it] * scale }
    }

    fun floatToPcm16Bytes(samples: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            buf.putShort((clamped * 32767f).toInt().toShort())
        }
        return buf.array()
    }

    fun writeWavFile(samples: FloatArray, outFile: File) {
        val pcm = floatToPcm16Bytes(samples)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()
        FileOutputStream(outFile).use {
            it.write(header)
            it.write(pcm)
        }
    }
}
