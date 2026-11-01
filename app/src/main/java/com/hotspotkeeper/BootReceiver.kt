package com.hotspotkeeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Cfg(context).enabled) {
            context.startForegroundService(Intent(context, KeeperService::class.java))
        }
    }
}
