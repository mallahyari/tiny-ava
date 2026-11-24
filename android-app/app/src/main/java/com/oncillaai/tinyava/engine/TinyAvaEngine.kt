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
 * Thread-safe ONNX Runtime engine running persian-g2p-tiny (8.4 MB int8) + tiny-ava Parisa (20.4 MB)
 * 100% offline on Android CPU (ARM64 NEON / XNNPACK).
 */
class TinyAvaEngine private constructor(private val appContext: Context) {

    companion object {
        const val SAMPLE_RATE = 24_000
        private const val PEAK = 0.99f
        private const val MAX_G2P_LEN = 256
        private const val MAX_TOKENS = 512

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
    private var ttsSession: OrtSession? = null

    // LRU RAM cache for short TalkBack UI phrases (< 45 chars): instant <5ms playback on repeated menus
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
            ttsSession = env.createSession(File(modelDir, "tiny-ava-parisa.onnx").absolutePath, opts)
            isLoaded = true
        }
    }

    private fun ensureModelsOnDisk(): File {
        val dir = File(appContext.filesDir, "tiny_ava_models_v1")
        if (!dir.exists()) dir.mkdirs()
        val files = listOf("g2p_encoder.onnx", "g2p_decoder.onnx", "tiny-ava-parisa.onnx")
        for (name in files) {
            val out = File(dir, name)
            if (out.exists() && out.length() > 0L) continue
            val tmp = File(dir, "$name.tmp")
            appContext.assets.open("models/$name").use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            tmp.renameTo(out)
        }
        return dir
    }

    fun clearPhraseCache() {
        phraseCache.evictAll()
    }

    /** Run persian-g2p-tiny (ByT5 ONNX) greedily on one clause -> Repr. 1 ASCII phonemes. */
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
        val decIds = ArrayList<Int>(96).apply { add(0) } // decoder_start_token_id = 0

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
                        val shape = logitsTensor.info.shape
                        val vocab = shape[2].toInt()
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
                if (nextId == 1) break // EOS
                decIds.add(nextId)
            }
        } finally {
            encOut.close()
            attnTensor.close()
        }
        return PersianPhonemeMapper.byt5Decode(decIds.subList(1, decIds.size))
    }

    /** Synthesize one normalized Persian sentence -> 24 kHz mono FloatArray (peak-limited <= 0.99). */
    fun synthesizeSentence(
        rawSentence: String,
        settings: SettingsState,
        speedOverride: Float? = null,
        pitchSemitonesOverride: Float? = null,
        isCancelled: () -> Boolean = { false },
    ): FloatArray {
        ensureLoaded()
        if (isCancelled()) return FloatArray(0)

        val dictMap = settings.customDictionary.associate { it.source to it.replacement }
        val normalized = PersianTextNormalizer.normalize(
            text = rawSentence,
            convertNumbers = settings.convertNumbers,
            convertEnglishUi = settings.convertEnglishUi,
            customDictionary = dictMap,
        )
        if (normalized.isBlank()) return FloatArray(0)

        val speed = (speedOverride ?: settings.speed).coerceIn(0.6f, 2.8f)
        val pitchSt = (pitchSemitonesOverride ?: settings.pitchSemitones).coerceIn(-6.0f, 6.0f)
        val pitchRange = settings.pitchRange.coerceIn(0.7f, 1.4f)

        val cacheKey = if (settings.enablePhraseCache && normalized.length <= 45) {
            "$normalized|${"%.2f".format(speed)}|${"%.1f".format(pitchSt)}|${"%.2f".format(pitchRange)}"
        } else null

        if (cacheKey != null) {
            phraseCache.get(cacheKey)?.let { return it }
        }

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

        val wave = synchronized(lock) {
            if (isCancelled()) return FloatArray(0)
            runTtsOnnx(tokenized.tokenIds, tokenized.stressIds, speed, pitchSt * 0.058f, pitchRange)
        }
        val clean = limitPeak(wave)
        if (cacheKey != null && clean.isNotEmpty()) {
            phraseCache.put(cacheKey, clean)
        }
        return clean
    }

    private fun runTtsOnnx(
        tokenIds: LongArray,
        stressIds: LongArray,
        speed: Float,
        pitchShift: Float,
        pitchRange: Float,
    ): FloatArray {
        val env = ortEnv!!
        val tts = ttsSession!!
        val n = tokenIds.size.toLong()

        val tTokens = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenIds), longArrayOf(1L, n))
        val tStress = OnnxTensor.createTensor(env, LongBuffer.wrap(stressIds), longArrayOf(1L, n))
        val tVoice = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0L)), longArrayOf(1L))
        val tSpeed = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1L))
        val tPitch = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(pitchShift)), longArrayOf(1L))
        val tRange = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(pitchRange)), longArrayOf(1L))

        return try {
            tts.run(
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
            val v = (clamped * 32767f).toInt().toShort()
            buf.putShort(v)
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
            putShort(1) // PCM
            putShort(1) // mono
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
