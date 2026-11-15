package com.rawag.voicebubble

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** Shared look & feel for the main screen and the floating panel. */
object Ui {
    val GREEN = Color.parseColor("#00A884")
    val GREEN_DARK = Color.parseColor("#008069")
    val TEAL = Color.parseColor("#0B3D35")
    val BG = Color.parseColor("#F2F5F4")
    val CARD = Color.WHITE
    val TEXT = Color.parseColor("#111B21")
    val MUTED = Color.parseColor("#667781")
    val LINE = Color.parseColor("#E6ECEA")
    val ORANGE = Color.parseColor("#F59E0B")
    val RED = Color.parseColor("#EF4444")
    val SOFT_GREEN = Color.parseColor("#E7F8F2")

    fun dp(c: Context, v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt()

    fun rounded(c: Context, color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(c, radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(c, 1), strokeColor)
    }

    fun ripple(c: Context, color: Int, radiusDp: Int, strokeColor: Int? = null) =
        RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22000000")), rounded(c, color, radiusDp, strokeColor), null)

    fun text(c: Context, s: CharSequence, size: Float, color: Int = TEXT, bold: Boolean = false) = TextView(c).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    /** pill shaped button; filled = primary style */
    fun pill(c: Context, label: String, filled: Boolean = false, onClick: () -> Unit) = TextView(c).apply {
        text = label; textSize = 13.5f; gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) Color.WHITE else GREEN_DARK)
        background = if (filled) ripple(c, GREEN, 50) else ripple(c, SOFT_GREEN, 50)
        setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7))
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun card(c: Context, padDp: Int = 16) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c, CARD, 20)
        elevation = dp(c, 1).toFloat()
        setPadding(dp(c, padDp), dp(c, padDp), dp(c, padDp), dp(c, padDp))
    }

    fun lp(c: Context, topDp: Int = 0, w: Int = -1, h: Int = -2) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(c, topDp) }

    fun fmtDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

    fun copy(c: Context, s: String) {
        (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("voice", s))
        Toast.makeText(c, "اتنسخ ✓", Toast.LENGTH_SHORT).show()
    }

    fun share(c: Context, s: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s)
        c.startActivity(Intent.createChooser(send, "مشاركة النص").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** One voice-note card: meta row, transcript (or status), and action pills. */
    fun noteCard(c: Context, n: VoiceNote, compact: Boolean): View {
        val tr = VoiceNotes.transcript(c, n.path)
        val working = Transcriber.pending.contains(n.path)
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(c, if (compact) Color.parseColor("#F7FAF9") else CARD, 16, if (compact) LINE else null)
            if (!compact) elevation = dp(c, 1).toFloat()
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        }

        // meta row: 🎙 0:32 • 03:15 PM • بيزنس
        val meta = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        meta.addView(text(c, "🎙 " + fmtDuration(VoiceNotes.durationSec(n.path)), 12.5f, GREEN_DARK, true).apply {
            background = rounded(c, SOFT_GREEN, 50); setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3))
        })
        val whenStr = DateFormat.format("hh:mm a · d/M", n.time).toString()
        val business = if (n.path.contains("w4b") || n.path.contains("Business")) " · بيزنس" else ""
        meta.addView(text(c, "  $whenStr$business", 12f, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(meta)

        when {
            working -> {
                val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(ProgressBar(c).apply { isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(ORANGE) },
                    LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)))
                row.addView(text(c, "  بيفرّغ دلوقتي...", 14.5f, ORANGE, true))
                card.addView(row, lp(c, 10))
            }
            tr != null -> {
                card.addView(text(c, tr, if (compact) 15f else 16f, TEXT).apply {
                    if (!compact) setTextIsSelectable(true)
                }, lp(c, 10))
                val took = VoiceNotes.took(c, n.path)
                val actions = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                actions.addView(pill(c, "نسخ") { copy(c, tr) })
                actions.addView(View(c), LinearLayout.LayoutParams(dp(c, 8), 1))
                actions.addView(pill(c, "مشاركة") { share(c, tr) })
                actions.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
                if (took > 0) actions.addView(text(c, "اتفرّغ في ${took}ث", 11f, MUTED))
                card.addView(actions, lp(c, 12))
            }
            else -> {
                card.addView(pill(c, "▶  فرّغ الفويس ده", filled = true) { Transcriber.enqueue(n.path) },
                    lp(c, 10, w = -2))
            }
        }
        return card
    }
}
