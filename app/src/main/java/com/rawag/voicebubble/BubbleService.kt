package com.rawag.voicebubble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class BubbleService : Service() {

    companion object {
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "bubble"
        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private lateinit var transcriber: Transcriber
    private val main = Handler(Looper.getMainLooper())

    private var bubble: FrameLayout? = null
    private var bubbleBg: GradientDrawable? = null
    private var badge: TextView? = null
    private var panel: View? = null
    private var panelList: LinearLayout? = null

    private val startedAt = System.currentTimeMillis()
    private val seen = HashSet<String>()
    private var unread = 0
    private var lastKey = ""

    private val poll = object : Runnable {
        override fun run() {
            scan()
            main.postDelayed(this, 3000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        transcriber = Transcriber(this)
        transcriber.onChanged = { refreshUi() }
        startAsForeground()
        showBubble()
        // mark what already exists as "seen" so we only auto-transcribe new ones
        VoiceNotes.latest(50).forEach { seen.add(it.path) }
        main.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        bubble?.let { wm.removeView(it) }
        panel?.let { wm.removeView(it) }
        transcriber.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ scanning

    private fun scan() {
        val notes = VoiceNotes.latest(20)
        for (n in notes) {
            if (seen.add(n.path) && n.time >= startedAt - 60_000) {
                unread++
                if (Prefs.autoTranscribe(this)) transcriber.enqueue(n.path)
            }
        }
        refreshUi()
    }

    // ------------------------------------------------------------------ notification

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "فقاعة الفويس", NotificationManager.IMPORTANCE_MIN))
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("فقاعة الفويس شغالة")
            .setContentText("بتفرّغ فويسات الواتس أوفلاين")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "إيقاف", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, n)
        }
    }

    // ------------------------------------------------------------------ bubble

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun overlayParams(w: Int, h: Int, focusable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val size = dp(58)
        val frame = FrameLayout(this)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#25D366"))
            setStroke(dp(2), Color.WHITE)
        }
        bubbleBg = bg
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            background = bg
            elevation = dp(6).toFloat()
        }
        frame.addView(icon, FrameLayout.LayoutParams(size, size))
        val b = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E53935")) }
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        badge = b
        frame.addView(b, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END))

        val lp = overlayParams(size + dp(6), size + dp(6))
        lp.x = Prefs.bubbleX(this); lp.y = Prefs.bubbleY(this)

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) { lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); wm.updateViewLayout(frame, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        // snap to nearest edge
                        val screenW = resources.displayMetrics.widthPixels
                        lp.x = if (lp.x + size / 2 < screenW / 2) 0 else screenW - size - dp(6)
                        wm.updateViewLayout(frame, lp)
                        Prefs.setBubblePos(this, lp.x, lp.y)
                    } else togglePanel()
                    true
                }
                else -> false
            }
        }
        wm.addView(frame, lp)
        bubble = frame
    }

    // ------------------------------------------------------------------ panel

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        unread = 0
        val dm = resources.displayMetrics
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
            elevation = dp(12).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "🎙️ آخر الفويسات"
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#075E54"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "✕"; textSize = 20f; setPadding(dp(12), dp(4), dp(4), dp(4)); setTextColor(Color.GRAY)
            setOnClickListener { closePanel() }
        })
        root.addView(header)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panelList = list
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, (dm.heightPixels * 0.55).toInt()))

        val lp = overlayParams((dm.widthPixels * 0.92).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = dp(70)
        wm.addView(root, lp)
        panel = root
        refreshUi()
    }

    private fun closePanel() {
        panel?.let { wm.removeView(it) }
        panel = null; panelList = null; lastKey = ""
        refreshUi()
    }

    private fun refreshUi() {
        // bubble state
        val busy = transcriber.pending.isNotEmpty()
        bubbleBg?.setColor(Color.parseColor(if (busy) "#FB8C00" else "#25D366"))
        badge?.apply {
            if (unread > 0 && panel == null) { visibility = View.VISIBLE; text = if (unread > 9) "9+" else "$unread" }
            else visibility = View.GONE
        }

        val list = panelList ?: return
        val notes = VoiceNotes.latest(15)
        val key = notes.joinToString("|") { it.path + (VoiceNotes.transcript(this, it.path) != null) + transcriber.pending.contains(it.path) }
        if (key == lastKey && list.childCount > 0) return   // nothing changed: keep scroll position
        lastKey = key
        list.removeAllViews()
        if (notes.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (VoiceNotes.whatsappFolderFound()) "مفيش فويسات لسه. شغّل أي فويس في الواتس عشان يتحمّل."
                else "مش لاقي فولدر فويسات الواتساب. اتأكد إنك اديت التطبيق صلاحية الملفات."
                setPadding(0, dp(16), 0, dp(16)); setTextColor(Color.DKGRAY)
            })
            return
        }
        for (n in notes) list.addView(noteCard(n))
    }

    private fun noteCard(n: VoiceNote): View {
        val text = VoiceNotes.transcript(this, n.path)
        val working = transcriber.pending.contains(n.path)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#F0F7F4")) }
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val whenStr = DateFormat.format("hh:mm a  •  dd/MM", n.time).toString()
        val source = if (n.path.contains("w4b") || n.path.contains("Business")) "  •  بيزنس" else ""
        top.addView(TextView(this).apply { this.text = whenStr + source; textSize = 12f; setTextColor(Color.GRAY) },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (text != null) {
            top.addView(TextView(this).apply {
                this.text = "📋 نسخ"; textSize = 13f; setTextColor(Color.parseColor("#128C7E")); setPadding(dp(10), dp(2), 0, dp(2))
                setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("voice", text))
                    Toast.makeText(this@BubbleService, "اتنسخ", Toast.LENGTH_SHORT).show()
                }
            })
        }
        card.addView(top)
        card.addView(TextView(this).apply {
            this.text = when {
                text != null -> text
                working -> "⏳ جاري التفريغ..."
                else -> "▶️ اضغط هنا عشان أفرّغه"
            }
            textSize = 15f
            setTextColor(if (text != null) Color.BLACK else Color.parseColor("#128C7E"))
            setTextIsSelectable(false)
            setPadding(0, dp(4), 0, 0)
            if (text == null && !working) setOnClickListener { transcriber.enqueue(n.path) }
        })
        return LinearLayout(this).apply {
            setPadding(0, dp(6), 0, 0)
            addView(card, LinearLayout.LayoutParams(-1, -2))
        }
    }
}

fun Context.startBubble() {
    startForegroundService(Intent(this, BubbleService::class.java))
}

fun Context.stopBubble() {
    stopService(Intent(this, BubbleService::class.java))
}
