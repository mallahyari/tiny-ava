package com.oncillaai.tinyava.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Lightweight 24 kHz mono PCM-16 AudioTrack wrapper with immediate stop support. */
class SimpleAudioPlayer {
    @Volatile
    private var track: AudioTrack? = null

    fun playBlocking(pcm16: ByteArray, isCancelled: () -> Boolean) {
        if (pcm16.isEmpty() || isCancelled()) return
        val minBuf = AudioTrack.getMinBufferSize(
            TinyAvaEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(TinyAvaEngine.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf * 2, 16384))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = t
        try {
            t.play()
            var offset = 0
            val chunk = 4096
            while (offset < pcm16.size && !isCancelled()) {
                val len = minOf(chunk, pcm16.size - offset)
                val written = t.write(pcm16, offset, len)
                if (written <= 0) break
                offset += written
            }
            if (!isCancelled()) {
                t.stop()
            }
        } catch (_: IllegalStateException) {
            // Stopped externally
        } finally {
            runCatching { t.release() }
            if (track === t) track = null
        }
    }

    fun stop() {
        val t = track ?: return
        track = null
        runCatching {
            t.pause()
            t.flush()
            t.stop()
            t.release()
        }
    }
}
