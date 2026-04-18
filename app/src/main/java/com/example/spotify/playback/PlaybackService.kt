package com.example.spotify.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.IBinder
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerNotificationManager
import com.example.spotify.HomeActivity
import com.example.spotify.R

class PlaybackService : Service() {

    private var notificationManager: PlayerNotificationManager? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val player = PlaybackManager.initialize(this)
        notificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setMediaDescriptionAdapter(DescriptionAdapter())
            .setNotificationListener(NotificationStateListener())
            .build()
            .apply {
                setUsePreviousAction(true)
                setUseNextAction(true)
                setUseFastForwardAction(false)
                setUseRewindAction(false)
                setSmallIcon(R.drawable.ic_notification_music)
                setPlayer(player)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager?.setPlayer(PlaybackManager.initialize(this))
        return START_STICKY
    }

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        notificationManager = null
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private inner class DescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return PlaybackManager.uiState.value?.currentTrack?.title
                ?: getString(R.string.app_name)
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent {
            val intent = Intent(this@PlaybackService, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                this@PlaybackService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        override fun getCurrentContentText(player: Player): CharSequence {
            return PlaybackManager.uiState.value?.currentTrack?.artist
                ?: getString(R.string.notification_default_artist)
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap {
            return PlaybackManager.uiState.value?.currentTrack?.albumArt
                ?: loadFallbackLargeIcon()
        }
    }

    private inner class NotificationStateListener : PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing && !isForeground) {
                startForeground(notificationId, notification)
                isForeground = true
            } else if (!ongoing && isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            stopSelf()
        }
    }

    private fun loadFallbackLargeIcon(): Bitmap {
        val drawable = AppCompatResources.getDrawable(this, R.drawable.ic_notification_music)
            ?: AppCompatResources.getDrawable(this, R.mipmap.ic_launcher)
            ?: throw IllegalStateException("No notification fallback icon is available.")

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }

    private companion object {
        const val CHANNEL_ID = "offline_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val FALLBACK_ICON_SIZE_PX = 144
    }
}
