package com.ivan.jamm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentTitle = "Jamm Player"
    private var currentArtist = "Неизвестен"
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("URL") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("TITLE") ?: "Трек"
        val artist = intent.getStringExtra("ARTIST") ?: "Неизвестен"

        currentTitle = title
        currentArtist = artist

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    start()
                    isPlaying = true
                    updateNotification()
                }
                setOnCompletionListener {
                    isPlaying = false
                    updateNotification()
                }
                prepareAsync()
            }
        } else {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                mediaPlayer?.start()
                isPlaying = true
            }
            updateNotification()
        }

        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val playPauseAction = if (isPlaying) "⏸" else "▶"
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, PlaylistActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "jamm_channel")
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, playPauseAction, null)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jamm_channel",
                "Jamm Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
