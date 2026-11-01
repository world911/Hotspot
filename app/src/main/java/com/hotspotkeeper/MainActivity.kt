package com.hotspotkeeper

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var cfg: Cfg
    private var dark = false

    private lateinit var shizukuText: TextView
    private lateinit var lastText: TextView
    private lateinit var keeperSwitch: Switch
    private lateinit var autoSwitch: Switch
    private lateinit var intervalEdit: EditText
    private lateinit var timesEdit: EditText
    private lateinit var airEdit: EditText
    private lateinit var delayEdit: EditText
    private lateinit var kwEdit: EditText
    private lateinit var cmdEdit: EditText
    private lateinit var methodBtn: Button
    private lateinit var logText: TextView

    private var syncing = false
    private val logBuf = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { refresh() }
    }

    // ---------- colors ----------
    private fun col(d: String, l: String) = Color.parseColor(if (dark) d else l)
    private fun cBg() = col("#0F1115", "#F3F4F8")
    private fun cCard() = col("#1A1D24", "#FFFFFF")
    private fun cField() = col("#262A33", "#EEF0F5")
    private fun cText() = col("#E8EAED", "#1B1E24")
    private fun cSub() = col("#9AA0A6", "#6B7280")
    private fun cAccent() = col("#4F8CFF", "#2F6BFF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Cfg(this)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        actionBar?.hide()
        window.statusBarColor = cBg()
        window.navigationBarColor = cBg()
        window.decorView.systemUiVisibility =
            if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        Shizuku.addRequestPermissionResultListener(permListener)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(28))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(cBg())
            addView(root)
        })

        root.addView(label("Hotspot Keeper", 28f, cText(), true))
        shizukuText = label("", 13f, cSub())
        root.addView(shizukuText)

        // Card 1: keep hotspot on
        val c1 = card()
        keeperSwitch = Switch(this)
        c1.addView(switchRow("Keep hotspot on", "Turns it back on after airplane mode", keeperSwitch))
        lastText = label("", 12f, cSub()).apply { setPadding(0, dp(10), 0, 0) }
        c1.addView(lastText)
        root.addView(c1)

        // Card 2: auto airplane toggle
        val c2 = card()
        autoSwitch = Switch(this)
        c2.addView(switchRow("Auto airplane toggle", "Cycles airplane mode on a timer", autoSwitch))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        row.addView(numBox("Every (min)", cfg.intervalMin.toString()) { intervalEdit = it }, lp(0, -2, 1f, 10))
        row.addView(numBox("Times (0=inf)", cfg.times.toString()) { timesEdit = it }, lp(0, -2, 1f, 10))
        row.addView(numBox("Stay on (sec)", cfg.airSec.toString()) { airEdit = it }, lp(0, -2, 1f, 0))
        c2.addView(row)
        root.addView(c2)

        // Card 3: actions
        val c3 = card()
        c3.addView(label("Actions", 16f, cText(), true))
        c3.addView(pill("Test hotspot on", true) { test() })
        c3.addView(pill("Test airplane toggle", false) { testAirplane() })
        c3.addView(pill("Run diagnostics", false) { diagnostics() })
        c3.addView(pill("Grant Shizuku permission", false) { requestShizuku() })
        c3.addView(pill("Allow background running", false) { batteryExempt() })
        root.addView(c3)

        // Card 4: advanced (collapsed)
        val c4 = card()
        val advBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val advHeader = label("Advanced settings (tap to show)", 16f, cText(), true)
        advHeader.setOnClickListener {
            val show = advBody.visibility != View.VISIBLE
            advBody.visibility = if (show) View.VISIBLE else View.GONE
            advHeader.text = if (show) "Advanced settings (tap to hide)" else "Advanced settings (tap to show)"
        }
        c4.addView(advHeader)
        methodBtn = pill(methodName(cfg.method), false) {
            cfg.method = (cfg.method + 1) % 3
            methodBtn.text = methodName(cfg.method)
        }
        advBody.addView(label("Method (tap to change)", 12f, cSub()).apply { setPadding(0, dp(12), 0, 0) })
        advBody.addView(methodBtn)
        advBody.addView(label("Wait after airplane mode off (sec)", 12f, cSub()).apply { setPadding(0, dp(12), 0, dp(4)) })
        delayEdit = inputBox(cfg.delaySec.toString(), true)
        advBody.addView(delayEdit)
        advBody.addView(label("Tile name(s), comma separated", 12f, cSub()).apply { setPadding(0, dp(12), 0, dp(4)) })
        kwEdit = inputBox(cfg.keyword, false)
        advBody.addView(kwEdit)
        advBody.addView(label("Shell command (command methods)", 12f, cSub()).apply { setPadding(0, dp(12), 0, dp(4)) })
        cmdEdit = inputBox(cfg.command, false)
        advBody.addView(cmdEdit)
        advBody.addView(pill("Save settings", true) { saveFields(); toast("Saved") })
        c4.addView(advBody)
        root.addView(c4)

        // Card 5: log
        val c5 = card()
        c5.addView(label("Log", 16f, cText(), true))
        logText = label("Nothing yet. Tap a test button.", 12f, cSub()).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
        }
        c5.addView(logText)
        c5.addView(pill("Copy log", false) { copyLog() })
        root.addView(c5)

        keeperSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) {
                saveFields()
                if (on && !shizukuReady()) {
                    toast("Start Shizuku and grant permission first")
                    refresh()
                } else {
                    setService(on)
                }
            }
        }
        autoSwitch.setOnCheckedChangeListener { _, on ->
            if (!syncing) {
                saveFields()
                if (on && !shizukuReady()) {
                    toast("Start Shizuku and grant permission first")
                    refresh()
                } else {
                    setAuto(on)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    // ---------- logic ----------
    private fun methodName(m: Int) = when (m) {
        0 -> "Tap Quick Settings tile"
        1 -> "Shell command only"
        else -> "Tap tile, then command"
    }

    private fun saveFields() {
        cfg.delaySec = delayEdit.text.toString().toIntOrNull() ?: 5
        cfg.keyword = kwEdit.text.toString().ifBlank { "hotspot" }
        cfg.command = cmdEdit.text.toString()
        cfg.intervalMin = (intervalEdit.text.toString().toIntOrNull() ?: 30).coerceAtLeast(1)
        cfg.times = (timesEdit.text.toString().toIntOrNull() ?: 10).coerceAtLeast(0)
        cfg.airSec = (airEdit.text.toString().toIntOrNull() ?: 5).coerceAtLeast(2)
    }

    private fun shizukuReady(): Boolean =
        try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    private fun refresh() {
        val sz = try {
            when {
                !Shizuku.pingBinder() -> "Shizuku is not running. Open Shizuku and start it."
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Shizuku ready"
                else -> "Shizuku running, permission not granted"
            }
        } catch (_: Exception) { "Shizuku not available" }
        shizukuText.text = sz
        lastText.text = "Last: ${cfg.status}"
        syncing = true
        keeperSwitch.isChecked = cfg.enabled
        autoSwitch.isChecked = cfg.autoOn
        syncing = false
    }

    private fun setService(on: Boolean) {
        cfg.enabled = on
        val i = Intent(this, KeeperService::class.java)
        if (on) startForegroundService(i) else stopService(i)
        refresh()
    }

    private fun setAuto(on: Boolean) {
        if (on) {
            cfg.enabled = true
            startForegroundService(
                Intent(this, KeeperService::class.java).setAction(KeeperService.ACTION_AUTO_START)
            )
        } else if (cfg.autoOn) {
            startForegroundService(
                Intent(this, KeeperService::class.java).setAction(KeeperService.ACTION_AUTO_STOP)
            )
        }
        refresh()
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) { toast("Start Shizuku first"); return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { refresh(); return }
            Shizuku.requestPermission(100)
        } catch (e: Exception) { toast("Shizuku error: ${e.message}") }
    }

    private fun batteryExempt() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun logLine(s: String) {
        runOnUiThread {
            logBuf.append(s).append('\n')
            logText.text = logBuf.toString()
        }
    }

    private fun copyLog() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("log", logBuf.toString()))
        toast("Log copied")
    }

    private fun runBg(title: String, work: () -> Unit) {
        saveFields()
        if (!shizukuReady()) {
            logLine("Shizuku is not ready. Start Shizuku and grant permission.")
            return
        }
        logLine("--- $title ---")
        Thread {
            try {
                work()
            } catch (e: Exception) {
                logLine("Error: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }

    private fun test() = runBg("Test hotspot (keep screen on)") {
        val ok = Hotspot.enable(cfg) { logLine(it) }
        logLine(if (ok) "Result: OK" else "Result: FAILED")
    }

    private fun testAirplane() = runBg("Test airplane toggle") {
        logLine("Airplane ON...")
        val a = Hotspot.setAirplane(true) { logLine(it) }
        Thread.sleep(cfg.airSec * 1000L)
        logLine("Airplane OFF...")
        val b = Hotspot.setAirplane(false) { logLine(it) }
        logLine(if (a && b) "Result: OK" else "Result: FAILED")
    }

    private fun diagnostics() = runBg("Diagnostics") {
        Hotspot.diagnose { logLine(it) }
    }

    // ---------- UI helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f, right: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { rightMargin = dp(right) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(cCard(), 18)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun switchRow(title: String, sub: String, sw: Switch): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val colm = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colm.addView(label(title, 17f, cText(), true))
        colm.addView(label(sub, 12f, cSub()))
        row.addView(colm, LinearLayout.LayoutParams(0, -2, 1f))
        sw.scaleX = 1.2f
        sw.scaleY = 1.2f
        row.addView(sw)
        return row
    }

    private fun inputBox(value: String, number: Boolean) = EditText(this).apply {
        setText(value)
        setSingleLine(true)
        setTextColor(cText())
        setHintTextColor(cSub())
        textSize = 15f
        background = rounded(cField(), 12)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (number) {
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
        }
    }

    private fun numBox(caption: String, value: String, out: (EditText) -> Unit): View {
        val colm = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colm.addView(label(caption, 12f, cSub()))
        val e = inputBox(value, true)
        out(e)
        colm.addView(e, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return colm
    }

    private fun pill(title: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = title
        transformationMethod = null
        textSize = 15f
        stateListAnimator = null
        setTextColor(if (filled) Color.WHITE else cAccent())
        background = rounded(if (filled) cAccent() else cField(), 14)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }
}
