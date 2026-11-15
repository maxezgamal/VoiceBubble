package com.rawag.voicebubble

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** App-wide engine: one background thread, one loaded Whisper model, shared by the screen and the bubble. */
object Transcriber {

    private lateinit var ctx: Context
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var whisper = 0L

    /** paths currently queued or running */
    val pending: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun init(c: Context) { if (!::ctx.isInitialized) ctx = c.applicationContext }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun enqueue(path: String, force: Boolean = false) {
        if (!force && VoiceNotes.transcript(ctx, path) != null) return
        if (!pending.add(path)) return
        notifyChanged()
        exec.execute {
            val started = System.currentTimeMillis()
            val text = try { run(path) } catch (t: Throwable) { "⚠️ حصل خطأ: ${t.message}" }
            VoiceNotes.save(ctx, path, text.trim().ifEmpty { "(مفيش كلام واضح)" }, System.currentTimeMillis() - started)
            pending.remove(path)
            notifyChanged()
        }
    }

    private fun run(path: String): String {
        if (whisper == 0L) {
            val model = ModelManager.ensureReady(ctx)
            whisper = WhisperLib.initContext(model)
            if (whisper == 0L) return "⚠️ مش قادر أفتح موديل Whisper"
        }
        val pcm = AudioDecoder.decodeTo16kMono(path)
        if (pcm.size < 1600) return ""
        val lang = Prefs.lang(ctx)
        val prompt = if (lang == "ar") "ده تسجيل صوتي على الواتساب باللهجة المصرية." else ""
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        return WhisperLib.transcribe(whisper, pcm, lang, threads, prompt)
    }

    /** Free the ~300 MB model from RAM; it reloads on the next transcription. */
    fun unload() = exec.execute { if (whisper != 0L) { WhisperLib.freeContext(whisper); whisper = 0L } }

    private fun notifyChanged() = main.post { listeners.forEach { it() } }
}
