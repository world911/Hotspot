package com.hotspotkeeper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class KeeperService : Service() {

    private val running = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            if (on) save("Airplane mode on, waiting") else trigger()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("keeper", "Hotspot Keeper", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, "keeper")
            .setContentTitle("Hotspot Keeper is running")
            .setContentText("Turns hotspot back on after airplane mode")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, n)
        registerReceiver(receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        save("Service started")
    }

    @Volatile private var autoThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AUTO_START -> startAuto()
            ACTION_AUTO_STOP -> stopAuto()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopAuto()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun startAuto() {
        stopAuto()
        val cfg = Cfg(this)
        val total = cfg.times
        val t = Thread {
            try {
                var n = 0
                while (total == 0 || n < total) {
                    Thread.sleep(maxOf(cfg.intervalMin, 1) * 60_000L)
                    n++
                    save("Auto toggle $n" + (if (total > 0) "/$total" else "") + ": airplane on")
                    Hotspot.setAirplane(true)
                    Thread.sleep(maxOf(cfg.airSec, 2) * 1000L)
                    Hotspot.setAirplane(false)
                }
                save("Auto toggle finished")
            } catch (_: InterruptedException) {
                save("Auto toggle stopped")
            } catch (e: Exception) {
                save("Auto toggle error: ${e.message}")
            } finally {
                if (autoThread === Thread.currentThread()) cfg.autoOn = false
            }
        }
        autoThread = t
        cfg.autoOn = true
        save("Auto toggle started")
        t.start()
    }

    private fun stopAuto() {
        val t = autoThread
        autoThread = null
        Cfg(this).autoOn = false
        t?.interrupt()
    }

    companion object {
        const val ACTION_AUTO_START = "com.hotspotkeeper.AUTO_START"
        const val ACTION_AUTO_STOP = "com.hotspotkeeper.AUTO_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun trigger() {
        if (!running.compareAndSet(false, true)) return
        Thread {
            try {
                val cfg = Cfg(this)
                Thread.sleep(cfg.delaySec * 1000L)
                var ok = false
                for (i in 1..3) {
                    ok = Hotspot.enable(cfg) { save(it) }
                    if (ok) break
                    Thread.sleep(3000)
                }
                save(if (ok) "Hotspot restored" else "Failed to restore hotspot")
            } catch (e: Exception) {
                save("Error: ${e.message}")
            } finally {
                running.set(false)
            }
        }.start()
    }

    private fun save(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        Cfg(this).status = "$t  $msg"
    }
}
