package com.rawag.voicebubble

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/** Decodes any audio file Android understands (WhatsApp .opus, .m4a, .mp3 ...) to 16 kHz mono float PCM. */
object AudioDecoder {

    private const val TARGET_RATE = 16000

    fun decodeTo16kMono(path: String): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { track = i; break }
        }
        require(track >= 0) { "No audio track" }
        extractor.selectTrack(track)
        val inFormat = extractor.getTrackFormat(track)
        val mime = inFormat.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inFormat, null, null, 0)
        codec.start()

        var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false

        val mono = FloatList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                outIdx >= 0 -> {
                    val out = codec.getOutputBuffer(outIdx)!!
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    out.order(ByteOrder.nativeOrder())
                    if (isFloat) {
                        val fb = out.asFloatBuffer()
                        while (fb.remaining() >= channels) {
                            var s = 0f
                            repeat(channels) { s += fb.get() }
                            mono.add(s / channels)
                        }
                    } else {
                        val sb = out.asShortBuffer()
                        while (sb.remaining() >= channels) {
                            var s = 0f
                            repeat(channels) { s += sb.get() / 32768f }
                            mono.add(s / channels)
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        codec.stop(); codec.release(); extractor.release()
        return resample(mono.toArray(), sampleRate, TARGET_RATE)
    }

    /** Box-filter + linear interpolation resampler — plenty for speech recognition. */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val ratio = from.toDouble() / to
        // simple low-pass: moving average over the decimation window
        val win = ratio.toInt().coerceAtLeast(1)
        val smoothed = if (win > 1) {
            val o = FloatArray(input.size)
            var acc = 0f
            for (i in input.indices) {
                acc += input[i]
                if (i >= win) acc -= input[i - win]
                o[i] = acc / minOf(i + 1, win)
            }
            o
        } else input
        val outLen = (input.size / ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceAtMost(smoothed.size - 1)
            val i1 = (i0 + 1).coerceAtMost(smoothed.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = smoothed[i0] * (1 - frac) + smoothed[i1] * frac
        }
        return out
    }

    private class FloatList {
        private var data = FloatArray(16000 * 60)
        private var n = 0
        fun add(v: Float) {
            if (n == data.size) data = data.copyOf(data.size * 2)
            data[n++] = v
        }
        fun toArray(): FloatArray = data.copyOf(n)
    }
}
