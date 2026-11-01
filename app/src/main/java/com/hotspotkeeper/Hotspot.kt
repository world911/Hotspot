package com.hotspotkeeper

object Hotspot {

    private val BAD = Regex("unknown|error|exception|usage|denied", RegexOption.IGNORE_CASE)
    private val nodeRe = Regex("<node [^>]*>")
    private val attrRe = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun enable(cfg: Cfg, log: (String) -> Unit): Boolean = when (cfg.method) {
        0 -> tapTile(cfg.keyword, log)
        1 -> Tether.silent(cfg, log)
        else -> tapTile(cfg.keyword, log) || Tether.silent(cfg, log)
    }

    fun runCommand(cmd: String, log: (String) -> Unit): Boolean {
        val r = Shell.run(cmd)
        log("Command exit ${r.code}: ${r.out.take(200)}")
        return r.code == 0 && !BAD.containsMatchIn(r.out)
    }

    fun isApUp(): Boolean {
        val out = Shell.run("ip addr").out
        return Regex("""\d+:\s+(ap\d|swlan\d|wlan1)[:@].*[<,]UP[,>]""").containsMatchIn(out)
    }

    fun airplaneState(): String = Shell.run("settings get global airplane_mode_on").out.trim()

    fun setAirplane(on: Boolean, log: (String) -> Unit = {}): Boolean {
        val want = if (on) "1" else "0"
        val word = if (on) "enable" else "disable"
        val r = Shell.run("cmd connectivity airplane-mode $word")
        log("airplane cmd exit ${r.code}: ${r.out.take(160)}")
        Thread.sleep(700)
        if (airplaneState() == want) return true
        val s = Shell.run("settings put global airplane_mode_on $want")
        log("settings put exit ${s.code}: ${s.out.take(160)}")
        val b = Shell.run("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on")
        log("broadcast exit ${b.code}: ${b.out.take(160)}")
        Thread.sleep(700)
        val ok = airplaneState() == want
        log("airplane now: ${airplaneState()} (wanted $want)")
        return ok
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

    private fun collapse() {
        Shell.run("cmd statusbar collapse")
    }

    private fun screenSize(): Pair<Int, Int> {
        val out = Shell.run("wm size").out
        val m = Regex("(\\d+)x(\\d+)").findAll(out).lastOrNull()
        return if (m != null) Pair(m.groupValues[1].toInt(), m.groupValues[2].toInt()) else Pair(1080, 2400)
    }

    private fun dumpNodes(log: (String) -> Unit): List<Map<String, String>> {
        val f = "/data/local/tmp/hk.xml"
        val r = Shell.run("rm -f $f; uiautomator dump $f")
        if (r.out.isNotBlank()) log("uiautomator: ${r.out.take(160)}")
        val xml = Shell.run("cat $f").out
        return nodeRe.findAll(xml)
            .map { m -> attrRe.findAll(m.value).associate { it.groupValues[1] to it.groupValues[2] } }
            .toList()
    }

    private fun findTile(
        nodes: List<Map<String, String>>,
        kws: List<String>
    ): Pair<Map<String, String>?, Boolean> {
        var best: Map<String, String>? = null
        var on = false
        for (a in nodes) {
            val label = (a["content-desc"] ?: "") + " " + (a["text"] ?: "")
            if (kws.none { label.contains(it, ignoreCase = true) } || a["bounds"] == null) continue
            if (a["checked"] == "true" || a["selected"] == "true" ||
                Regex(",\\s*on\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)
            ) on = true
            if (best == null || a["clickable"] == "true") best = a
            if (a["clickable"] == "true") break
        }
        return Pair(best, on)
    }

    private fun tapTileInner(keyword: String, log: (String) -> Unit): Boolean {
        if (isApUp()) {
            log("Hotspot already on")
            return true
        }
        val kws = keyword.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("hotspot") }
        val (w, h) = screenSize()
        val openers = listOf(
            "cmd statusbar expand-settings",
            "input swipe ${w * 85 / 100} 5 ${w * 85 / 100} ${h / 2} 250",
            "input swipe ${w / 2} 5 ${w / 2} ${h * 6 / 10} 250; input swipe ${w / 2} 5 ${w / 2} ${h * 6 / 10} 250"
        )
        for ((i, op) in openers.withIndex()) {
            Shell.run(op)
            Thread.sleep(1500)
            val nodes = dumpNodes(log)
            val (node, on) = findTile(nodes, kws)
            if (node == null) {
                log("Way ${i + 1}: tile not found (${nodes.size} items on screen)")
                collapse()
                Thread.sleep(600)
                continue
            }
            if (on) {
                collapse()
                log("Hotspot tile already on")
                return true
            }
            val b = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").find(node["bounds"]!!)
            if (b == null) {
                collapse()
                log("Could not read tile position")
                return false
            }
            val (x1, y1, x2, y2) = b.destructured
            val cx = (x1.toInt() + x2.toInt()) / 2
            val cy = (y1.toInt() + y2.toInt()) / 2
            val t = Shell.run("input tap $cx $cy")
            if (t.code != 0 || BAD.containsMatchIn(t.out)) {
                collapse()
                log("Tap failed: ${t.out.take(200)}")
                return false
            }
            Thread.sleep(2500)
            collapse()
            log("Tapped hotspot tile (way ${i + 1}) at $cx,$cy. Hotspot interface up: ${isApUp()}")
            return true
        }
        return false
    }

    fun diagnose(log: (String) -> Unit) {
        fun sh(label: String, cmd: String) {
            val r = Shell.run(cmd)
            log("$label: ${r.out.take(200).replace('\n', ' ')} (exit ${r.code})")
        }
        sh("user", "id")
        sh("device", "getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk")
        sh("airplane setting", "settings get global airplane_mode_on")
        sh("airplane cmd", "cmd connectivity airplane-mode")
        sh("screen", "dumpsys power | grep -m1 mWakefulness")
        sh("interfaces", "ip -o link | cut -d: -f2")
        sh("screen size", "wm size")
        log("Hotspot interface up: ${isApUp()}")
        Shell.run("cmd statusbar expand-settings")
        Thread.sleep(1500)
        val nodes = dumpNodes(log)
        val labels = nodes.mapNotNull { a ->
            val s = (a["content-desc"] ?: "").ifBlank { a["text"] ?: "" }
            if (s.isBlank()) null else s
        }.distinct().take(40)
        log("On screen (${nodes.size} items): ${labels.joinToString(" | ")}")
        collapse()
        log("Diagnostics done")
    }
}
