package com.rawag.voicebubble

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

class BubbleService : Service() {

    companion object {
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "bubble"
        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var bubble: FrameLayout? = null
    private var bubbleBg: GradientDrawable? = null
    private var pulse: ObjectAnimator? = null
    private var badge: TextView? = null
    private var panel: View? = null
    private var panelList: LinearLayout? = null
    private var panelSubtitle: TextView? = null

    private val startedAt = System.currentTimeMillis()
    private val seen = HashSet<String>()
    private var unread = 0
    private var lastKey = ""

    private val listener: () -> Unit = { refreshUi() }

    private val poll = object : Runnable {
        override fun run() { scan(); main.postDelayed(this, 3000) }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Transcriber.init(this)
        Transcriber.addListener(listener)
        startAsForeground()
        showBubble()
        VoiceNotes.latest(50).forEach { seen.add(it.path) }   // only auto-transcribe notes that arrive from now on
        main.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        Transcriber.removeListener(listener)
        pulse?.cancel()
        bubble?.let { wm.removeView(it) }
        panel?.let { wm.removeView(it) }
        Transcriber.unload()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ scanning

    private fun scan() {
        for (n in VoiceNotes.latest(20)) {
            if (seen.add(n.path) && n.time >= startedAt - 60_000) {
                unread++
                if (Prefs.autoTranscribe(this)) Transcriber.enqueue(n.path)
            }
        }
        refreshUi()
    }

    // ------------------------------------------------------------------ notification

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "فقاعة الفويس", NotificationManager.IMPORTANCE_MIN))
        val stop = PendingIntent.getService(this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("فقاعة الفويس شغالة")
            .setContentText("بتفرّغ فويسات الواتس أوفلاين")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "إيقاف", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7, n)
    }

    // ------------------------------------------------------------------ bubble

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val size = dp(56)
        val frame = FrameLayout(this)
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.GREEN, Ui.GREEN_DARK)).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(3), Color.WHITE)
        }
        bubbleBg = bg
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            background = bg
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(8).toFloat()
        }
        frame.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val b = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 10.5f; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.RED); setStroke(dp(2), Color.WHITE) }
            visibility = View.GONE
            elevation = dp(10).toFloat()
        }
        badge = b
        frame.addView(b, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END))

        val lp = overlayParams(size + dp(10), size + dp(10))
        lp.x = Prefs.bubbleX(this); lp.y = Prefs.bubbleY(this)

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                    icon.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) { lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); wm.updateViewLayout(frame, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    icon.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator()).setDuration(180).start()
                    if (moved) snapToEdge(frame, lp, size)
                    else if (e.action == MotionEvent.ACTION_UP) togglePanel()
                    true
                }
                else -> false
            }
        }
        wm.addView(frame, lp)
        bubble = frame

        // gentle breathing animation while transcribing
        pulse = ObjectAnimator.ofFloat(icon, "alpha", 1f, 0.55f).apply {
            duration = 650; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        }
    }

    private fun snapToEdge(frame: View, lp: WindowManager.LayoutParams, size: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val target = if (lp.x + size / 2 < screenW / 2) 0 else screenW - size - dp(10)
        ValueAnimator.ofInt(lp.x, target).apply {
            duration = 220
            addUpdateListener { lp.x = it.animatedValue as Int; if (frame.isAttachedToWindow) wm.updateViewLayout(frame, lp) }
            start()
        }
        Prefs.setBubblePos(this, target, lp.y)
    }

    // ------------------------------------------------------------------ panel

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        unread = 0
        val dm = resources.displayMetrics
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = Ui.rounded(this@BubbleService, Color.WHITE, 24)
            elevation = dp(16).toFloat()
            clipToOutline = true
        }

        // header with gradient
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, intArrayOf(Ui.TEAL, Ui.GREEN_DARK)).apply {
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(18), dp(14), dp(12), dp(14))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(Ui.text(this, "آخر الفويسات", 17f, Color.WHITE, true))
        val sub = Ui.text(this, "", 12f, Color.parseColor("#CDEFE6"))
        panelSubtitle = sub
        titles.addView(sub)
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "✕"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = Ui.ripple(this@BubbleService, Color.parseColor("#33FFFFFF"), 50)
            setOnClickListener { closePanel() }
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        root.addView(header)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(12)) }
        panelList = list
        root.addView(ScrollView(this).apply { addView(list); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(-1, (dm.heightPixels * 0.55).toInt()))

        val lp = overlayParams((dm.widthPixels * 0.94).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = dp(64)
        lp.windowAnimations = android.R.style.Animation_Dialog
        wm.addView(root, lp)
        panel = root
        refreshUi()
    }

    private fun closePanel() {
        panel?.let { wm.removeView(it) }
        panel = null; panelList = null; panelSubtitle = null; lastKey = ""
        refreshUi()
    }

    private fun refreshUi() {
        val busy = Transcriber.pending.isNotEmpty()
        bubbleBg?.colors = if (busy) intArrayOf(Ui.ORANGE, Color.parseColor("#D97706")) else intArrayOf(Ui.GREEN, Ui.GREEN_DARK)
        pulse?.let { if (busy && !it.isRunning) it.start() else if (!busy && it.isRunning) { it.cancel(); bubble?.getChildAt(0)?.alpha = 1f } }
        badge?.apply {
            if (unread > 0 && panel == null) { visibility = View.VISIBLE; text = if (unread > 9) "9+" else "$unread" }
            else visibility = View.GONE
        }

        val list = panelList ?: return
        val notes = VoiceNotes.latest(15)
        panelSubtitle?.text = if (busy) "بيفرّغ ${Transcriber.pending.size} فويس..." else "أوفلاين · Whisper Small"
        val key = notes.joinToString("|") { it.path + (VoiceNotes.transcript(this, it.path) != null) + Transcriber.pending.contains(it.path) }
        if (key == lastKey && list.childCount > 0) return   // nothing changed: keep scroll position
        lastKey = key
        list.removeAllViews()
        if (notes.isEmpty()) {
            list.addView(Ui.text(this,
                if (VoiceNotes.whatsappFolderFound()) "🎧\nمفيش فويسات لسه.\nشغّل أي فويس في الواتس عشان يتحمّل."
                else "📂\nمش لاقي فولدر فويسات الواتساب.\nافتح التطبيق واتأكد من صلاحية الملفات.",
                14f, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(28), 0, dp(28)) })
            return
        }
        for (n in notes) list.addView(Ui.noteCard(this, n, compact = true), Ui.lp(this, 10))
    }
}

fun Context.startBubble() { startForegroundService(Intent(this, BubbleService::class.java)) }
fun Context.stopBubble() { stopService(Intent(this, BubbleService::class.java)) }
