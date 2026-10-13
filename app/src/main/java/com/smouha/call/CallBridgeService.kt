package com.smouha.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CallBridgeService : Service() {
    private var server: BridgeHttpServer? = null
    private lateinit var prefs: Prefs
    private lateinit var pairing: PairingManager

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        pairing = PairingManager(prefs)
        // ensure secret exists for QR while service runs
        pairing.currentOffer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBridge()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startBridge()
        }
        return START_STICKY
    }

    private fun startBridge() {
        if (server != null) {
            startForeground(NOTIF_ID, buildNotification())
            return
        }
        try {
            val s = BridgeHttpServer(applicationContext, prefs, pairing, Prefs.PORT)
            s.start()
            server = s
            BridgeState.running = true
            BridgeState.pairing = pairing
            BridgeState.prefs = prefs
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            BridgeState.running = false
            stopSelf()
        }
    }

    private fun stopBridge() {
        try {
            server?.stop()
        } catch (_: Exception) {
        }
        server = null
        BridgeState.running = false
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ip = NetworkUtils.localIpv4() ?: "—"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, Prefs.PORT) + " · $ip")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopBridge()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "smouha_call_bridge"
        const val NOTIF_ID = 8765
        const val ACTION_STOP = "com.smouha.call.STOP"
        const val ACTION_START = "com.smouha.call.START"
    }
}

/** Shared lightweight state for UI */
object BridgeState {
    @Volatile var running: Boolean = false
    @Volatile var pairing: PairingManager? = null
    @Volatile var prefs: Prefs? = null
}
