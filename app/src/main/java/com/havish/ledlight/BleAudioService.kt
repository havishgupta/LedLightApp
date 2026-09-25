package com.havish.ledlight

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BleAudioService : Service() {

    companion object {
        var controller: LedController? = null
        const val ACTION_TOGGLE_VIBE = "com.havish.ledlight.TOGGLE_VIBE"
    }

    override fun onCreate() {
        super.onCreate()
        if (controller == null) {
            controller = LedController(applicationContext)
        }
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "vibe_channel")
            .setContentTitle("LedLight Music Vibe")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_VIBE) {
            controller?.toggleVibe()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel("vibe_channel", "Music Vibe", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
