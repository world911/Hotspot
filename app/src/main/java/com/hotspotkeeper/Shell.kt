package com.hotspotkeeper

import rikka.shizuku.Shizuku

object Shell {
    data class Result(val code: Int, val out: String)

    fun run(cmd: String): Result {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        return Result(code, (out + err).trim())
    }
}
