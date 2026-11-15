package com.rawag.voicebubble

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var startBtn: TextView
    private lateinit var prepCard: LinearLayout
    private lateinit var prepBar: ProgressBar
    private lateinit var prepText: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var storageAction: TextView
    private lateinit var overlayAction: TextView
    private lateinit var historyTitle: TextView
    private lateinit var historyList: LinearLayout
    private val langChips = ArrayList<Pair<String, TextView>>()
    private var lastKey = ""
    private var preparing = false

    private val listener: () -> Unit = { refreshHistory() }
    private val poll = object : Runnable {
        override fun run() { refreshStatus(); refreshHistory(); main.postDelayed(this, 4000) }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Transcriber.init(this)
        window.statusBarColor = Ui.TEAL
        window.navigationBarColor = Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Ui.BG)
        }
        root.addView(buildHero())

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(32))
        }
        body.addView(buildPrepCard(), Ui.lp(this, 16))
        body.addView(buildSetupCard(), Ui.lp(this, 16))
        body.addView(buildSettingsCard(), Ui.lp(this, 16))

        historyTitle = Ui.text(this, "آخر الفويسات", 18f, Ui.TEXT, true)
        body.addView(historyTitle, Ui.lp(this, 26))
        historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(historyList)

        body.addView(Ui.text(this,
            "💡 نصايح\n• الفويس لازم يكون متحمّل على الموبايل (شغّله مرة لو التحميل التلقائي مقفول).\n" +
                "• اسحب الفقاعة لأي مكان، ودوسة عليها تفتح النصوص.\n" +
                "• الفقاعة بتنوّر برتقالي وهي بتفرّغ، والرقم الأحمر = فويسات جديدة.\n" +
                "• شاومي/أوبو/ريلمي: اقفل توفير البطارية للتطبيق عشان الفقاعة متقفش.",
            12.5f, Ui.MUTED).apply { background = Ui.rounded(this@MainActivity, Color.parseColor("#E9EFED"), 16); setPadding(dp(14), dp(12), dp(14), dp(12)) },
            Ui.lp(this, 24))
        root.addView(body)

        setContentView(ScrollView(this).apply { addView(root); isVerticalScrollBarEnabled = false; setBackgroundColor(Ui.BG) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        prepareModel()
    }

    override fun onResume() {
        super.onResume()
        Transcriber.addListener(listener)
        main.post(poll)
    }

    override fun onPause() {
        super.onPause()
        Transcriber.removeListener(listener)
        main.removeCallbacks(poll)
    }

    // ------------------------------------------------------------------ hero

    private fun buildHero(): View {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.TEAL, Ui.GREEN_DARK)).apply {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(32).toFloat(), dp(32).toFloat(), dp(32).toFloat(), dp(32).toFloat())
            }
            setPadding(dp(22), dp(28), dp(22), dp(26))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = FrameLayout(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#26FFFFFF")) } }
        logo.addView(ImageView(this).apply { setImageResource(R.drawable.ic_mic) },
            FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        top.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        titles.addView(Ui.text(this, "فقاعة الفويس", 24f, Color.WHITE, true))
        titles.addView(Ui.text(this, "فويسات الواتساب ← كلام مكتوب", 13.5f, Color.parseColor("#CDEFE6")))
        top.addView(titles)
        hero.addView(top)

        // feature chips
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in listOf("🔒 أوفلاين 100%", "🇪🇬 بيفهم مصري", "⚡ Whisper")) {
            chips.addView(Ui.text(this, t, 12f, Color.WHITE).apply {
                background = Ui.rounded(this@MainActivity, Color.parseColor("#22FFFFFF"), 50)
                setPadding(dp(10), dp(5), dp(10), dp(5))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        hero.addView(chips, Ui.lp(this, 18))

        // status + big button
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusDot = View(this)
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)))
        statusText = Ui.text(this, "", 14f, Color.WHITE, true).apply { setPadding(dp(8), 0, 0, 0) }
        statusRow.addView(statusText)
        hero.addView(statusRow, Ui.lp(this, 22))

        startBtn = TextView(this).apply {
            textSize = 17f; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            elevation = dp(4).toFloat()
            setOnClickListener { toggleBubble() }
        }
        hero.addView(startBtn, Ui.lp(this, 12, h = dp(56)))
        refreshStatus()
        return hero
    }

    // ------------------------------------------------------------------ model preparation

    private fun buildPrepCard(): View {
        prepCard = Ui.card(this).apply { visibility = View.GONE }
        prepCard.addView(Ui.text(this, "⚙️ بنجهّز موديل Whisper", 16f, Ui.TEXT, true))
        prepText = Ui.text(this, "أول مرة بس، بياخد ثواني...", 13f, Ui.MUTED)
        prepCard.addView(prepText, Ui.lp(this, 4))
        prepBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(Ui.GREEN)
        }
        prepCard.addView(prepBar, Ui.lp(this, 10))
        return prepCard
    }

    private fun prepareModel() {
        if (ModelManager.isReady(this)) return
        preparing = true
        prepCard.visibility = View.VISIBLE
        refreshStatus()
        Thread {
            try {
                ModelManager.ensureReady(this) { p -> runOnUiThread { prepBar.progress = p; prepText.text = "أول مرة بس... $p%" } }
                runOnUiThread { prepCard.visibility = View.GONE }
            } catch (e: Exception) {
                runOnUiThread { prepText.text = "⚠️ ${e.message}\nاتأكد إن فيه مساحة فاضية ~200 ميجا وافتح التطبيق تاني." }
            } finally {
                preparing = false
                runOnUiThread { refreshStatus() }
            }
        }.start()
    }

    // ------------------------------------------------------------------ setup checklist

    private fun buildSetupCard(): View {
        setupCard = Ui.card(this)
        setupCard.addView(Ui.text(this, "خطوتين بس قبل ما تبدأ", 16f, Ui.TEXT, true))
        storageAction = Ui.pill(this, "سماح", filled = true) { requestStorage() }
        overlayAction = Ui.pill(this, "سماح", filled = true) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        setupCard.addView(setupRow("📂", "قراءة الفويسات", "عشان يوصل لفولدر فويسات الواتساب", storageAction), Ui.lp(this, 12))
        setupCard.addView(View(this).apply { setBackgroundColor(Ui.LINE) }, Ui.lp(this, 12, h = dp(1)))
        setupCard.addView(setupRow("🫧", "الظهور فوق التطبيقات", "عشان الفقاعة تظهر فوق الواتس", overlayAction), Ui.lp(this, 12))
        return setupCard
    }

    private fun setupRow(emoji: String, title: String, desc: String, action: View): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.text(this, emoji, 20f).apply {
            gravity = Gravity.CENTER; background = Ui.rounded(this@MainActivity, Ui.SOFT_GREEN, 14)
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        t.addView(Ui.text(this, title, 15f, Ui.TEXT, true))
        t.addView(Ui.text(this, desc, 12.5f, Ui.MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(action)
        return row
    }

    // ------------------------------------------------------------------ settings

    private fun buildSettingsCard(): View {
        val card = Ui.card(this)
        card.addView(Ui.text(this, "الإعدادات", 16f, Ui.TEXT, true))
        card.addView(Ui.text(this, "لغة الفويسات", 13f, Ui.MUTED), Ui.lp(this, 12))

        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(this@MainActivity, Color.parseColor("#EEF2F1"), 50)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        for ((code, label) in listOf("ar" to "عربي / مصري", "auto" to "تلقائي", "en" to "English")) {
            val chip = TextView(this).apply {
                text = label; textSize = 13.5f; gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { Prefs.setLang(this@MainActivity, code); refreshLang() }
            }
            langChips.add(code to chip)
            seg.addView(chip, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(seg, Ui.lp(this, 8))
        refreshLang()

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        t.addView(Ui.text(this, "تفريغ تلقائي", 15f, Ui.TEXT, true))
        t.addView(Ui.text(this, "أي فويس جديد يتفرّغ لوحده أول ما يوصل", 12.5f, Ui.MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = Prefs.autoTranscribe(this@MainActivity)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.GREEN, Color.parseColor("#F1F1F1")))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.parseColor("#8FDCC4"), Color.parseColor("#C9D1CF")))
            setOnCheckedChangeListener { _, v -> Prefs.setAutoTranscribe(this@MainActivity, v) }
        })
        card.addView(row, Ui.lp(this, 18))
        return card
    }

    private fun refreshLang() {
        val cur = Prefs.lang(this)
        for ((code, chip) in langChips) {
            val on = code == cur
            chip.background = if (on) Ui.rounded(this, Color.WHITE, 50) else null
            chip.elevation = if (on) dp(1).toFloat() else 0f
            chip.setTextColor(if (on) Ui.GREEN_DARK else Ui.MUTED)
            chip.typeface = android.graphics.Typeface.create("sans-serif-medium", if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    // ------------------------------------------------------------------ state

    private fun hasStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun markDone(v: TextView, done: Boolean) {
        v.text = if (done) "✓ تمام" else "سماح"
        v.setTextColor(if (done) Ui.GREEN_DARK else Color.WHITE)
        v.background = if (done) Ui.rounded(this, Ui.SOFT_GREEN, 50) else Ui.ripple(this, Ui.GREEN, 50)
        v.isEnabled = !done
    }

    private fun refreshStatus() {
        if (!::startBtn.isInitialized) return
        val on = BubbleService.running
        statusDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8")) }
        statusText.text = when {
            preparing -> "بنجهّز الموديل..."
            on -> "الفقاعة شغالة فوق الواتس"
            else -> "الفقاعة متوقفة"
        }
        startBtn.text = if (on) "⏹  إيقاف الفقاعة" else "▶  شغّل الفقاعة"
        startBtn.setTextColor(if (on) Color.WHITE else Ui.TEAL)
        startBtn.background = if (on) Ui.ripple(this, Color.parseColor("#33FFFFFF"), 50, Color.parseColor("#66FFFFFF"))
        else Ui.ripple(this, Color.WHITE, 50)

        if (::storageAction.isInitialized) {
            val s = hasStorage(); val o = hasOverlay()
            markDone(storageAction, s); markDone(overlayAction, o)
            setupCard.visibility = if (s && o) View.GONE else View.VISIBLE
        }
    }

    private fun refreshHistory() {
        if (!::historyList.isInitialized) return
        if (!hasStorage()) {
            historyTitle.text = "آخر الفويسات"
            if (lastKey != "noperm") {
                lastKey = "noperm"
                historyList.removeAllViews()
                historyList.addView(emptyState("📂", "اسمح بقراءة الفويسات عشان تظهر هنا"), Ui.lp(this, 10))
            }
            return
        }
        val notes = VoiceNotes.latest(20)
        val key = notes.joinToString("|") { it.path + (VoiceNotes.transcript(this, it.path) != null) + Transcriber.pending.contains(it.path) }
        if (key == lastKey) return
        lastKey = key
        historyTitle.text = if (notes.isEmpty()) "آخر الفويسات" else "آخر الفويسات (${notes.size})"
        historyList.removeAllViews()
        if (notes.isEmpty()) {
            historyList.addView(emptyState("🎧",
                if (VoiceNotes.whatsappFolderFound()) "مفيش فويسات لسه — شغّل أي فويس في الواتس" else "مش لاقي فولدر فويسات الواتساب على الموبايل"),
                Ui.lp(this, 10))
            return
        }
        for (n in notes) historyList.addView(Ui.noteCard(this, n, compact = false), Ui.lp(this, 10))
    }

    private fun emptyState(emoji: String, msg: String) = Ui.card(this, 24).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(Ui.text(this@MainActivity, emoji, 34f).apply { gravity = Gravity.CENTER })
        addView(Ui.text(this@MainActivity, msg, 14f, Ui.MUTED).apply { gravity = Gravity.CENTER }, Ui.lp(this@MainActivity, 6))
    }

    // ------------------------------------------------------------------ actions

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
    }

    private fun toggleBubble() {
        if (BubbleService.running) { stopBubble(); main.postDelayed({ refreshStatus() }, 300); return }
        when {
            preparing -> toast("استنى ثواني لحد ما الموديل يجهز")
            !hasStorage() -> { toast("اسمح بقراءة الفويسات الأول"); requestStorage() }
            !hasOverlay() -> {
                toast("اسمح بالظهور فوق التطبيقات الأول")
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            else -> { startBubble(); main.postDelayed({ refreshStatus() }, 300) }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
