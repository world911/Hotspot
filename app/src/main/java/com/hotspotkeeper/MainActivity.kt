package com.hotspotkeeper

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var cfg: Cfg
    private lateinit var info: TextView
    private lateinit var delayEdit: EditText
    private lateinit var kwEdit: EditText
    private lateinit var cmdEdit: EditText
    private lateinit var methodSpinner: Spinner
    private lateinit var intervalEdit: EditText
    private lateinit var timesEdit: EditText
    private lateinit var airEdit: EditText

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Cfg(this)
        Shizuku.addRequestPermissionResultListener(permListener)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(text("Hotspot Keeper", 22f))
        info = text("", 14f)
        root.addView(info)

        root.addView(button("1. Grant Shizuku permission") { requestShizuku() })
        root.addView(button("2. Allow background running") { batteryExempt() })

        root.addView(text("Method", 14f))
        methodSpinner = Spinner(this)
        methodSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf(
                "Tap Quick Settings tile (best for Android 11)",
                "Shell command only",
                "Tap tile, then command if that fails"
            )
        )
        methodSpinner.setSelection(cfg.method)
        methodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { cfg.method = pos }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(methodSpinner)

        root.addView(text("Wait after airplane mode off (seconds)", 14f))
        delayEdit = edit(cfg.delaySec.toString()).also { it.inputType = 2 }
        root.addView(delayEdit)

        root.addView(text("Tile name to look for", 14f))
        kwEdit = edit(cfg.keyword)
        root.addView(kwEdit)

        root.addView(text("Shell command (for command methods)", 14f))
        cmdEdit = edit(cfg.command)
        root.addView(cmdEdit)

        root.addView(text("Automatic airplane toggle", 18f))
        root.addView(text("Every how many minutes", 14f))
        intervalEdit = edit(cfg.intervalMin.toString()).also { it.inputType = 2 }
        root.addView(intervalEdit)
        root.addView(text("How many times (0 = until stopped)", 14f))
        timesEdit = edit(cfg.times.toString()).also { it.inputType = 2 }
        root.addView(timesEdit)
        root.addView(text("Airplane mode stays on for (seconds)", 14f))
        airEdit = edit(cfg.airSec.toString()).also { it.inputType = 2 }
        root.addView(airEdit)
        root.addView(button("Start auto toggle") { save(); setAuto(true) })
        root.addView(button("Stop auto toggle") { setAuto(false) })

        root.addView(button("Save settings") { save(); refresh() })
        root.addView(button("Test now (turn hotspot on)") { test() })
        root.addView(button("Start keeper") { save(); setService(true) })
        root.addView(button("Stop keeper") { setService(false) })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun save() {
        cfg.delaySec = delayEdit.text.toString().toIntOrNull() ?: 5
        cfg.keyword = kwEdit.text.toString().ifBlank { "hotspot" }
        cfg.command = cmdEdit.text.toString()
        cfg.intervalMin = (intervalEdit.text.toString().toIntOrNull() ?: 30).coerceAtLeast(1)
        cfg.times = (timesEdit.text.toString().toIntOrNull() ?: 10).coerceAtLeast(0)
        cfg.airSec = (airEdit.text.toString().toIntOrNull() ?: 5).coerceAtLeast(2)
    }

    private fun setAuto(on: Boolean) {
        if (on && !shizukuReady()) { info.text = "Shizuku not ready."; return }
        cfg.enabled = true
        val i = Intent(this, KeeperService::class.java)
            .setAction(if (on) KeeperService.ACTION_AUTO_START else KeeperService.ACTION_AUTO_STOP)
        startForegroundService(i)
        refresh()
    }

    private fun shizukuReady(): Boolean =
        try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    private fun refresh() {
        val sz = try {
            when {
                !Shizuku.pingBinder() -> "Shizuku: not running. Open Shizuku and start it."
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Shizuku: ready"
                else -> "Shizuku: running, permission not granted"
            }
        } catch (_: Exception) { "Shizuku: not available" }
        info.text = "$sz\nKeeper: ${if (cfg.enabled) "ON" else "OFF"}" +
            "\nAuto toggle: ${if (cfg.autoOn) "ON" else "OFF"}\nLast: ${cfg.status}"
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) { info.text = "Start Shizuku first."; return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { refresh(); return }
            Shizuku.requestPermission(100)
        } catch (e: Exception) { info.text = "Shizuku error: ${e.message}" }
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

    private fun test() {
        save()
        if (!shizukuReady()) { info.text = "Shizuku not ready."; return }
        info.text = "Testing... keep the screen on"
        Thread {
            val msgs = StringBuilder()
            val ok = try {
                Hotspot.enable(cfg) { msgs.append(it).append('\n') }
            } catch (e: Exception) { msgs.append("Error: ${e.message}"); false }
            runOnUiThread {
                refresh()
                info.append("\nTest: ${if (ok) "OK" else "FAILED"}\n$msgs")
            }
        }.start()
    }

    private fun setService(on: Boolean) {
        cfg.enabled = on
        val i = Intent(this, KeeperService::class.java)
        if (on) startForegroundService(i) else stopService(i)
        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float) = TextView(this).apply {
        text = s
        textSize = size
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun edit(s: String) = EditText(this).apply { setText(s); setSingleLine(true) }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }
}
