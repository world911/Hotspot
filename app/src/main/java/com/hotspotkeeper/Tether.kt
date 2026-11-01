package com.hotspotkeeper

import android.os.Binder
import android.os.IBinder
import java.lang.reflect.Proxy
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Silent hotspot start: shell command first, then Android's tethering service directly. */
object Tether {

    fun silent(cfg: Cfg, log: (String) -> Unit): Boolean {
        if (Hotspot.isApUp()) {
            log("Hotspot already on")
            return true
        }
        if (Hotspot.runCommand(cfg.command, log)) {
            Thread.sleep(3000)
            log(if (Hotspot.isApUp()) "Hotspot is up" else "Command ran; hotspot interface not seen")
            return true
        }
        log("Command failed, trying tethering service")
        return direct(log)
    }

    fun direct(log: (String) -> Unit): Boolean {
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
            val svc = SystemServiceHelper.getSystemService("tethering")
            if (svc == null) {
                log("Direct: no tethering service on this phone")
                return false
            }
            val stub = Class.forName("android.net.ITetheringConnector\$Stub")
            val conn = stub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(svc))
            if (conn == null) {
                log("Direct: could not connect to tethering service")
                return false
            }

            val reqCls = Class.forName("android.net.TetheringRequestParcel")
            val req = reqCls.getDeclaredConstructor().newInstance()
            fun setF(name: String, v: Any) {
                try { reqCls.getField(name).set(req, v) } catch (_: Exception) {}
            }
            setF("tetheringType", 0)
            setF("exemptFromEntitlementCheck", true)
            setF("showProvisioningUi", false)

            val lisCls = Class.forName("android.net.IIntResultListener")
            val binder = Binder()
            val listener = Proxy.newProxyInstance(lisCls.classLoader, arrayOf(lisCls)) { _, m, _ ->
                if (m.name == "asBinder") binder else null
            }

            val m = conn.javaClass.methods.firstOrNull { it.name == "startTethering" }
                ?: run {
                    log("Direct: startTethering not found")
                    return false
                }
            val args: Array<Any?> = when (m.parameterTypes.size) {
                3 -> arrayOf(req, "com.android.shell", listener)
                4 -> arrayOf(req, "com.android.shell", null, listener)
                else -> {
                    log("Direct: unsupported method (${m.parameterTypes.size} args)")
                    return false
                }
            }
            m.invoke(conn, *args)
            Thread.sleep(3000)
            log("Direct: request sent. Hotspot interface up: ${Hotspot.isApUp()}")
            return true
        } catch (e: Exception) {
            val c = e.cause ?: e
            log("Direct failed: ${c.javaClass.simpleName}: ${c.message}")
            return false
        }
    }
}
