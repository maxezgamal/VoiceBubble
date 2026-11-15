package com.rawag.voicebubble

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** One background thread, one loaded Whisper model, a queue of files. */
class Transcriber(private val ctx: Context) {

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var whisper = 0L
    private var loadedModel: String? = null

    /** paths currently queued or running */
    val pending: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet())

    var onChanged: (() -> Unit)? = null

    fun enqueue(path: String) {
        if (VoiceNotes.transcript(ctx, path) != null) return
        if (!pending.add(path)) return
        notifyChanged()
        exec.execute {
            val text = try {
                run(path)
            } catch (t: Throwable) {
                "⚠️ حصل خطأ: ${t.message}"
            }
            VoiceNotes.save(ctx, path, text.trim().ifEmpty { "(مفيش كلام واضح)" })
            pending.remove(path)
            notifyChanged()
        }
    }

    private fun run(path: String): String {
        val model = Prefs.modelPath(ctx) ?: return "⚠️ لازم تحمّل موديل Whisper الأول من التطبيق"
        if (whisper == 0L || loadedModel != model) {
            if (whisper != 0L) WhisperLib.freeContext(whisper)
            whisper = WhisperLib.initContext(model)
            loadedModel = model
            if (whisper == 0L) return "⚠️ ملف الموديل بايظ، حمّله تاني"
        }
        val pcm = AudioDecoder.decodeTo16kMono(path)
        if (pcm.size < 1600) return ""
        val lang = Prefs.lang(ctx)
        val prompt = if (lang == "ar") "ده تسجيل صوتي على الواتساب باللهجة المصرية." else ""
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        return WhisperLib.transcribe(whisper, pcm, lang, threads, prompt)
    }

    private fun notifyChanged() = main.post { onChanged?.invoke() }

    fun release() {
        exec.execute { if (whisper != 0L) { WhisperLib.freeContext(whisper); whisper = 0L } }
        exec.shutdown()
    }
}
