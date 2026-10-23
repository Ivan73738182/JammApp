package com.ivan.jamm

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentTitle = "Jamm Player"
    private var currentArtist = "Неизвестен"
    private var isPlaying = false
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "JammSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    mediaPlayer?.start()
                    isPlaying = true
                    updateNotification()
                }
                override fun onPause() {
                    mediaPlayer?.pause()
                    isPlaying = false
                    updateNotification()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("URL")
        val title = intent?.getStringExtra("TITLE")
        val artist = intent?.getStringExtra("ARTIST")

        if (url != null && title != null) {
            currentTitle = title
            currentArtist = artist ?: "Неизвестен"
            playNewTrack(url)
        } else {
            // Если URL нет, но сервис уже запущен — обрабатываем паузу/игру
            if (mediaPlayer != null) {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                } else {
                    mediaPlayer?.start()
                    isPlaying = true
                }
                updateNotification()
            }
        }

        return START_STICKY // Сервис будет перезапущен, если система его убьет
    }

    private fun playNewTrack(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    start()
                    isPlaying = true
                    updateNotification()
                    updateMediaSession()
                }
                setOnCompletionListener {
                    isPlaying = false
                    updateNotification()
                }
                setOnErrorListener { _, _, _ ->
                    isPlaying = false
                    updateNotification()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            isPlaying = false
            updateNotification()
        }
    }

    private fun updateMediaSession() {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()
        )
    }

    private fun updateNotification() {
        updateMediaSession()
        val playPauseAction = if (isPlaying) "⏸ Пауза" else "▶ Играть"
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
            .addAction(0, playPauseAction, getPauseIntent())
            .build()

        startForeground(1, notification)
    }

    private fun getPauseIntent(): PendingIntent {
        val intent = Intent(this, MusicService::class.java)
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
