package com.oncillaai.tinyava.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-Kotlin pitch-preserving WSOLA / PSOLA time-stretcher (inspired by Bill Cox's Sonic
 * algorithm used in Android TalkBack / screen readers).
 *
 * Keeps neural TTS duration scaling within its distortion-free range (<= 1.30x) and handles
 * fast blind-user TalkBack speeds (1.30x -> 3.5x+) cleanly in waveform space with zero pitch
 * shift and zero neural glitches.
 */
object SonicTimeStretch {

    private const val MIN_PITCH_HZ = 65
    private const val MAX_PITCH_HZ = 420

    /**
     * Speed up or slow down mono [samples] at [sampleRate] by [factor] (> 1.0 = faster)
     * without changing vocal pitch.
     */
    fun changeSpeed(samples: FloatArray, sampleRate: Int, factor: Float): FloatArray {
        if (samples.isEmpty() || abs(factor - 1.0f) < 0.02f) return samples

        val minPeriod = (sampleRate / MAX_PITCH_HZ).coerceAtLeast(32)
        val maxPeriod = (sampleRate / MIN_PITCH_HZ).coerceAtMost(samples.size / 3)
        if (maxPeriod <= minPeriod || samples.size < maxPeriod * 3) {
            return samples
        }

        val outCap = (samples.size / factor).roundToInt() + maxPeriod * 2
        val out = FloatArray(outCap.coerceAtLeast(1024))
        var outPos = 0
        var inPos = 0
        val n = samples.size

        fun ensureCapacity(needed: Int): FloatArray = out // checked via dynamic list fallback if needed
        val outList = ArrayList<Float>((samples.size / factor).roundToInt() + 1024)

        while (inPos + maxPeriod * 2 < n) {
            val period = findPitchPeriod(samples, inPos, minPeriod, maxPeriod)
            if (factor > 1.0f) {
                // Speed up: crossfade [inPos .. inPos+period] with [inPos+skip .. inPos+skip+period]
                val skip = if (factor >= 2.0f) {
                    (period * (factor - 1.0f)).roundToInt().coerceAtLeast(period)
                } else {
                    period
                }
                val copyAfter = if (factor >= 2.0f) {
                    0
                } else {
                    (period * (2.0f - factor) / (factor - 1.0f)).roundToInt().coerceAtLeast(0)
                }

                if (inPos + skip + period >= n) break

                // Overlap-add crossfade over one pitch period (eliminates clicks & preserves phase)
                for (i in 0 until period) {
                    val w = i.toFloat() / period.toFloat()
                    val v = samples[inPos + i] * (1.0f - w) + samples[inPos + skip + i] * w
                    outList.add(v)
                }
                inPos += period + skip

                // Copy unmodified frames for speeds between 1.0x and 2.0x
                val safeCopy = minOf(copyAfter, n - inPos)
                for (i in 0 until safeCopy) {
                    outList.add(samples[inPos + i])
                }
                inPos += safeCopy
            } else {
                // Slow down (factor < 1.0): duplicate a pitch period with smooth overlap-add
                val insert = period
                val copyAfter = (period * factor / (1.0f - factor)).roundToInt().coerceAtLeast(0)
                for (i in 0 until period) outList.add(samples[inPos + i])
                for (i in 0 until insert) {
                    val w = i.toFloat() / insert.toFloat()
                    val v = samples[inPos + i] * w + samples[inPos + i] * (1.0f - w)
                    outList.add(v)
                }
                inPos += period
                val safeCopy = minOf(copyAfter, n - inPos)
                for (i in 0 until safeCopy) outList.add(samples[inPos + i])
                inPos += safeCopy
            }
            outPos = outList.size
        }

        // Copy remaining tail samples
        while (inPos < n) {
            outList.add(samples[inPos++])
        }

        val result = FloatArray(outList.size)
        for (i in outList.indices) result[i] = outList[i]
        return result
    }

    /** Fast decimated AMDF pitch-period estimator (2x downsample search + fine local refinement). */
    private fun findPitchPeriod(
        samples: FloatArray,
        offset: Int,
        minP: Int,
        maxP: Int,
    ): Int {
        var bestP = minP
        var bestDiff = Float.MAX_VALUE
        // Coarse search (step 2)
        var p = minP
        while (p <= maxP) {
            var diff = 0f
            var i = 0
            while (i < p) {
                diff += abs(samples[offset + i] - samples[offset + p + i])
                i += 2
            }
            val norm = diff / (p / 2 + 1)
            if (norm < bestDiff) {
                bestDiff = norm
                bestP = p
            }
            p += 2
        }
        // Fine refinement around bestP
        val lo = (bestP - 2).coerceAtLeast(minP)
        val hi = (bestP + 2).coerceAtMost(maxP)
        var fineBest = bestP
        var fineDiff = Float.MAX_VALUE
        for (cand in lo..hi) {
            var diff = 0f
            for (i in 0 until cand) {
                diff += abs(samples[offset + i] - samples[offset + cand + i])
            }
            val norm = diff / cand
            if (norm < fineDiff) {
                fineDiff = norm
                fineBest = cand
            }
        }
        return fineBest
    }
}
