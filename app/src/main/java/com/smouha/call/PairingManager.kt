package com.smouha.call

import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Short-lived pairing secret.
 *
 * Two pair modes:
 * 1) Phone-offer (legacy): phone shows QR/JSON; PC posts that secret to /v1/pair.
 * 2) PC-offer (reverse): PC shows QR; user taps "Accept PC pair" on phone (2 min window);
 *    PC posts its secret; phone accepts any non-empty secret during the window.
 */
class PairingManager(private val prefs: Prefs) {
    data class PairOffer(
        val secret: String,
        val expiresAt: Long
    )

    private val offer = AtomicReference<PairOffer?>(null)
    private val hostPairUntil = AtomicLong(0L)
    private val pendingPcSecret = AtomicReference<String?>(null)
    private val rng = SecureRandom()

    fun refreshOffer(): PairOffer {
        val secret = buildString {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            repeat(10) { append(alphabet[rng.nextInt(alphabet.length)]) }
        }
        val o = PairOffer(secret, System.currentTimeMillis() + Prefs.PAIR_SECRET_TTL_MS)
        offer.set(o)
        return o
    }

    fun currentOffer(): PairOffer {
        val o = offer.get()
        if (o == null || o.expiresAt < System.currentTimeMillis()) {
            return refreshOffer()
        }
        return o
    }

    /** User confirmed they will pair from PC QR — accept PC secret for [windowMs]. */
    fun enableHostPairWindow(windowMs: Long = 120_000L) {
        hostPairUntil.set(System.currentTimeMillis() + windowMs)
        pendingPcSecret.set(null)
    }

    fun hostPairWindowOpen(): Boolean {
        return System.currentTimeMillis() < hostPairUntil.get()
    }

    fun hostPairRemainingMs(): Long {
        return (hostPairUntil.get() - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Optional: store secret from scanned PC QR so only that secret is accepted. */
    fun acceptPcOffer(secret: String, expMs: Long = 0L): Boolean {
        val s = secret.trim()
        if (s.length < 6) return false
        pendingPcSecret.set(s)
        val until = if (expMs > System.currentTimeMillis()) expMs else System.currentTimeMillis() + 120_000L
        hostPairUntil.set(until)
        return true
    }

    fun validateAndConsume(secret: String): Boolean {
        val s = secret.trim()
        if (s.isEmpty()) return false

        // 1) Exact match against pending scanned PC secret
        val pending = pendingPcSecret.get()
        if (pending != null && pending.equals(s, ignoreCase = false)) {
            pendingPcSecret.set(null)
            hostPairUntil.set(0L)
            refreshOffer()
            return true
        }

        // 2) Host-pair window (user tapped Accept PC pair) — accept PC secret
        if (System.currentTimeMillis() < hostPairUntil.get()) {
            pendingPcSecret.set(null)
            hostPairUntil.set(0L)
            refreshOffer()
            return true
        }

        // 3) Classic phone-offer secret
        val o = offer.get() ?: return false
        if (o.expiresAt < System.currentTimeMillis()) return false
        if (o.secret != s) return false
        refreshOffer()
        return true
    }

    fun issueSessionToken(): String {
        val bytes = ByteArray(24)
        rng.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.sessionToken = token
        return token
    }

    fun buildPayloadJson(ip: String?): String {
        val o = currentOffer()
        return JSONObject()
            .put("v", 1)
            .put("app", "SmouhaCall")
            .put("brand", Prefs.BRAND_TAG)
            .put("deviceId", prefs.deviceId)
            .put("deviceName", prefs.deviceName)
            .put("ip", ip ?: "")
            .put("port", Prefs.PORT)
            .put("secret", o.secret)
            .put("exp", o.expiresAt)
            .toString()
    }
}
