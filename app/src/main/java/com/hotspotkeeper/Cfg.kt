package com.hotspotkeeper

import android.content.Context

/** Simple settings storage. */
class Cfg(ctx: Context) {
    private val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var delaySec: Int
        get() = p.getInt("delay", 5)
        set(v) { p.edit().putInt("delay", v).apply() }

    var method: Int
        get() = p.getInt("method", 0)
        set(v) { p.edit().putInt("method", v).apply() }

    var command: String
        get() = p.getString("cmd", DEFAULT_CMD) ?: DEFAULT_CMD
        set(v) { p.edit().putString("cmd", v).apply() }

    var keyword: String
        get() = p.getString("kw", "hotspot") ?: "hotspot"
        set(v) { p.edit().putString("kw", v).apply() }

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) { p.edit().putBoolean("enabled", v).apply() }

    var intervalMin: Int
        get() = p.getInt("interval", 30)
        set(v) { p.edit().putInt("interval", v).apply() }

    var times: Int
        get() = p.getInt("times", 10)
        set(v) { p.edit().putInt("times", v).apply() }

    var airSec: Int
        get() = p.getInt("airsec", 5)
        set(v) { p.edit().putInt("airsec", v).apply() }

    var autoOn: Boolean
        get() = p.getBoolean("autoOn", false)
        set(v) { p.edit().putBoolean("autoOn", v).apply() }

    var status: String
        get() = p.getString("status", "-") ?: "-"
        set(v) { p.edit().putString("status", v).apply() }

    companion object {
        const val DEFAULT_CMD = "cmd wifi start-softap MyHotspot wpa2 MyPassword123"
    }
}
