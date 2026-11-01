package com.hotspotkeeper

object Hotspot {

    fun enable(cfg: Cfg, log: (String) -> Unit): Boolean = when (cfg.method) {
        0 -> tapTile(cfg.keyword, log)
        1 -> runCommand(cfg.command, log)
        else -> tapTile(cfg.keyword, log) || runCommand(cfg.command, log)
    }

    fun runCommand(cmd: String, log: (String) -> Unit): Boolean {
        val r = Shell.run(cmd)
        log("Command exit ${r.code}: ${r.out.take(100)}")
        val bad = Regex("unknown|error|exception|usage", RegexOption.IGNORE_CASE)
        return r.code == 0 && !bad.containsMatchIn(r.out)
    }

    fun isApUp(): Boolean {
        val out = Shell.run("ip addr").out
        return Regex("""\d+:\s+(ap\d|swlan\d|wlan1)[:@].*[<,]UP[,>]""").containsMatchIn(out)
    }

    fun setAirplane(on: Boolean): Boolean {
        val word = if (on) "enable" else "disable"
        val r = Shell.run("cmd connectivity airplane-mode $word")
        val bad = Regex("unknown|error|exception|usage", RegexOption.IGNORE_CASE)
        if (r.code == 0 && !bad.containsMatchIn(r.out)) return true
        Shell.run("settings put global airplane_mode_on ${if (on) 1 else 0}")
        val b = Shell.run("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on")
        return b.code == 0
    }

    fun tapTile(keyword: String, log: (String) -> Unit): Boolean {
        val asleep = Shell.run("dumpsys power").out.contains("mWakefulness=Asleep")
        if (asleep) {
            Shell.run("input keyevent KEYCODE_WAKEUP")
            Thread.sleep(1000)
            Shell.run("wm dismiss-keyguard")
            Thread.sleep(1000)
        }
        try {
            return tapTileInner(keyword, log)
        } finally {
            if (asleep) Shell.run("input keyevent KEYCODE_SLEEP")
        }
    }

    private fun tapTileInner(keyword: String, log: (String) -> Unit): Boolean {
        if (isApUp()) {
            log("Hotspot already on")
            return true
        }
        Shell.run("cmd statusbar expand-settings")
        Thread.sleep(1500)
        val dump = "/data/local/tmp/hk.xml"
        Shell.run("rm -f $dump; uiautomator dump $dump")
        val xml = Shell.run("cat $dump").out

        val nodeRe = Regex("<node [^>]*>")
        val attrRe = Regex("([\\w-]+)=\"([^\"]*)\"")
        var best: Map<String, String>? = null
        var alreadyOn = false
        for (m in nodeRe.findAll(xml)) {
            val a = attrRe.findAll(m.value).associate { it.groupValues[1] to it.groupValues[2] }
            val label = (a["content-desc"] ?: "") + " " + (a["text"] ?: "")
            if (!label.contains(keyword, ignoreCase = true) || a["bounds"] == null) continue
            if (a["checked"] == "true" || a["selected"] == "true" ||
                Regex(",\\s*on\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)
            ) alreadyOn = true
            if (best == null || a["clickable"] == "true") best = a
            if (a["clickable"] == "true") break
        }

        val node = best
        if (node == null) {
            Shell.run("cmd statusbar collapse")
            log("Tile '$keyword' not found on screen")
            return false
        }
        if (alreadyOn) {
            Shell.run("cmd statusbar collapse")
            log("Hotspot tile already on")
            return true
        }
        val b = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").find(node["bounds"]!!)
        if (b == null) {
            Shell.run("cmd statusbar collapse")
            log("Could not read tile position")
            return false
        }
        val (x1, y1, x2, y2) = b.destructured
        val cx = (x1.toInt() + x2.toInt()) / 2
        val cy = (y1.toInt() + y2.toInt()) / 2
        Shell.run("input tap $cx $cy")
        Thread.sleep(1500)
        Shell.run("cmd statusbar collapse")
        log("Tapped hotspot tile")
        return true
    }
}
