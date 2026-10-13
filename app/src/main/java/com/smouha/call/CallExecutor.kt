package com.smouha.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Places a direct call when CALL_PHONE is granted; falls back to dialer UI.
 * Opens WhatsApp chat on the phone (not on the PC).
 *
 * Android 10+ blocks background startActivity from a plain service —
 * we try multiple schemes and always use NEW_TASK from the application context.
 */
object CallExecutor {
    fun normalizeEgyptPhone(raw: String): String {
        var s = raw.trim().replace(" ", "").replace("-", "")
        if (s.startsWith("00")) s = "+" + s.substring(2)
        if (s.startsWith("0") && !s.startsWith("00")) {
            s = "+20" + s.substring(1)
        }
        if (!s.startsWith("+") && s.matches(Regex("^1[0125]\\d{8}$"))) {
            s = "+20$s"
        }
        return s
    }

    /** Digits only for wa.me (country code without +). */
    fun digitsForWhatsApp(raw: String): String {
        val norm = normalizeEgyptPhone(raw)
        return norm.replace("+", "").replace(Regex("[^0-9]"), "")
    }

    fun placeCall(ctx: Context, phoneRaw: String): Pair<Boolean, String> {
        val phone = normalizeEgyptPhone(phoneRaw)
        if (phone.length < 8) return false to "invalid_phone"

        val canCall = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            if (canCall) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                        if (tm != null) {
                            tm.placeCall(Uri.parse("tel:$phone"), null)
                            return true to "called_telecom"
                        }
                    } catch (_: Exception) {
                    }
                }
                val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(i)
                true to "called"
            } else {
                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(i)
                true to "dialer"
            }
        } catch (e: Exception) {
            false to (e.message ?: "call_failed")
        }
    }

    /**
     * Open WhatsApp conversation for [phoneRaw] on this device.
     * Tries native scheme first (more reliable from FGS), then package-scoped wa.me.
     */
    fun openWhatsApp(ctx: Context, phoneRaw: String): Pair<Boolean, String> {
        val digits = digitsForWhatsApp(phoneRaw)
        if (digits.length < 8) return false to "invalid_phone"

        val appCtx = ctx.applicationContext
        val attempts = mutableListOf<Pair<Intent, String>>()

        // 1) Native WhatsApp scheme — best from background / FGS
        attempts += Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } to "whatsapp_scheme"

        // 2) Package-scoped https wa.me
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            attempts += Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            } to "wa.me:$pkg"
        }

        // 3) Generic https (may open browser)
        attempts += Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } to "wa.me_generic"

        var lastErr = "whatsapp_failed"
        for ((intent, tag) in attempts) {
            try {
                if (intent.resolveActivity(appCtx.packageManager) == null && intent.`package` != null) {
                    continue
                }
                appCtx.startActivity(intent)
                return true to tag
            } catch (e: Exception) {
                lastErr = e.message ?: tag
            }
        }
        return false to lastErr
    }
}
