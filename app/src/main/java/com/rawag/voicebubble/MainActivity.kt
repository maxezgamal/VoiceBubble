package com.rawag.voicebubble

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var storageRow: TextView
    private lateinit var overlayRow: TextView
    private lateinit var modelRow: TextView
    private lateinit var progress: ProgressBar
    private lateinit var startBtn: Button
    private var downloading = false

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#075E54")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "🎙️ فقاعة الفويس"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#075E54"))
        })
        root.addView(TextView(this).apply {
            text = "فقاعة بتطفو فوق الواتساب وبتحوّل الفويسات لكلام مكتوب، أوفلاين على موبايلك بالكامل بـ Whisper."
            textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(4), 0, dp(16))
        })

        // 1. storage
        storageRow = stepTitle("١. صلاحية قراءة الفويسات")
        root.addView(storageRow)
        root.addView(button("اديله الصلاحية") { requestStorage() })

        // 2. overlay
        overlayRow = stepTitle("٢. صلاحية الظهور فوق التطبيقات")
        root.addView(overlayRow)
        root.addView(button("اديله الصلاحية") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        // 3. model
        modelRow = stepTitle("٣. موديل Whisper (مرة واحدة بس)")
        root.addView(modelRow)
        for (m in WhisperModel.values()) root.addView(button("تحميل ${m.label}") { download(m) })
        root.addView(button("عندي ملف موديل .bin على الموبايل") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 42)
        })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        root.addView(progress)

        // settings
        root.addView(stepTitle("٤. الإعدادات"))
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val langs = listOf("ar" to "عربي", "auto" to "تلقائي", "en" to "English")
        langs.forEachIndexed { i, (code, label) ->
            group.addView(RadioButton(this).apply {
                id = 100 + i; text = label; isChecked = Prefs.lang(this@MainActivity) == code
                setOnClickListener { Prefs.setLang(this@MainActivity, code) }
            })
        }
        root.addView(group)
        root.addView(CheckBox(this).apply {
            text = "فرّغ أي فويس جديد لوحده أول ما يوصل"
            isChecked = Prefs.autoTranscribe(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setAutoTranscribe(this@MainActivity, v) }
        })

        startBtn = button("") { toggleBubble() }.apply { textSize = 18f }
        root.addView(startBtn, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(20) })

        root.addView(TextView(this).apply {
            text = "ملاحظات:\n• الفويس لازم يكون متحمّل على الموبايل (شغّله مرة لو التحميل التلقائي مقفول).\n" +
                "• اسحب الفقاعة لأي مكان، ودوس عليها تشوف النص.\n" +
                "• البرتقالي = بيفرّغ دلوقتي، والأحمر = عدد الفويسات الجديدة.\n" +
                "• لو بتستخدم شاومي/أوبو/ريلمي: اقفل توفير البطارية للتطبيق عشان الفقاعة متقفش."
            textSize = 13f; setTextColor(Color.GRAY); setPadding(0, dp(18), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun hasStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun refresh() {
        storageRow.text = mark(hasStorage()) + " ١. صلاحية قراءة الفويسات"
        overlayRow.text = mark(hasOverlay()) + " ٢. صلاحية الظهور فوق التطبيقات"
        val mp = Prefs.modelPath(this)
        modelRow.text = mark(mp != null) + " ٣. موديل Whisper" + (mp?.let { "  (" + File(it).name + ")" } ?: "")
        startBtn.text = if (BubbleService.running) "⏹ إيقاف الفقاعة" else "▶️ شغّل الفقاعة"
    }

    private fun mark(ok: Boolean) = if (ok) "✅" else "⬜"

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
    }

    private fun toggleBubble() {
        if (BubbleService.running) { stopBubble(); startBtn.postDelayed({ refresh() }, 300); return }
        when {
            !hasStorage() -> toast("اديله صلاحية الملفات الأول")
            !hasOverlay() -> toast("اديله صلاحية الظهور فوق التطبيقات الأول")
            Prefs.modelPath(this) == null -> toast("حمّل موديل Whisper الأول")
            else -> { startBubble(); startBtn.postDelayed({ refresh() }, 300); moveTaskToBack(true) }
        }
    }

    private fun download(m: WhisperModel) {
        if (downloading) return
        downloading = true
        progress.visibility = View.VISIBLE; progress.progress = 0
        modelRow.text = "⏳ بيحمّل ${m.fileName}..."
        Thread {
            try {
                ModelManager.download(this, m) { p -> runOnUiThread { progress.progress = p } }
                runOnUiThread { toast("الموديل اتحمّل ✅") }
            } catch (e: Exception) {
                runOnUiThread { toast("فشل التحميل: ${e.message}") }
            } finally {
                downloading = false
                runOnUiThread { progress.visibility = View.GONE; refresh() }
            }
        }.start()
    }

    @Deprecated("simple activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 42 || resultCode != RESULT_OK) return
        modelRow.text = "⏳ بينسخ الموديل..."
        Thread {
            try { ModelManager.import(this, uri); runOnUiThread { toast("تم ✅") } }
            catch (e: Exception) { runOnUiThread { toast("فشل: ${e.message}") } }
            runOnUiThread { refresh() }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun stepTitle(t: String) = TextView(this).apply {
        text = t; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.BLACK)
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#128C7E")) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
        gravity = Gravity.CENTER
    }
}
