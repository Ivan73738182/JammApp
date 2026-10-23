package com.ivan.jamm

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide

class JammPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentTitle = "Jamm Player"
    private var currentArtist = "Неизвестен"
    private var currentCoverUrl: String? = null
    private var musicIsPlaying = false
    private lateinit var mediaSession: MediaSessionCompat

    // Таймер для остановки сервиса
    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        if (!musicIsPlaying) {
            stopSelf() // Останавливаем сервис, если музыка не играет
        }
    }

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
                    musicIsPlaying = true
                    stopHandler.removeCallbacks(stopRunnable) // Отменяем таймер
                    updateNotification()
                }
                override fun onPause() {
                    mediaPlayer?.pause()
                    musicIsPlaying = false
                    startStopTimer() // Запускаем таймер на 5 минут
                    updateNotification()
                }
            })
            isActive = true
        }
    }

    private fun startStopTimer() {
        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.postDelayed(stopRunnable, 5 * 60 * 1000L) // 5 минут
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("URL")
        val title = intent?.getStringExtra("TITLE")
        val artist = intent?.getStringExtra("ARTIST")
        val coverUrl = intent?.getStringExtra("COVER_URL")

        if (url != null && title != null) {
            currentTitle = title
            currentArtist = artist ?: "Неизвестен"
            currentCoverUrl = coverUrl
            playNewTrack(url)
        } else {
            if (mediaPlayer != null) {
                if (musicIsPlaying) {
                    mediaPlayer?.pause()
                    musicIsPlaying = false
                    startStopTimer() // Если поставили на паузу из шторки — запускаем таймер
                } else {
                    mediaPlayer?.start()
                    musicIsPlaying = true
                    stopHandler.removeCallbacks(stopRunnable) // Если включили — отменяем таймер
                }
                updateNotification()
            }
        }

        return START_STICKY
    }

    private fun playNewTrack(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    start()
                    musicIsPlaying = true
                    stopHandler.removeCallbacks(stopRunnable)
                    updateNotification()
                    updateMediaSession()
                }
                setOnCompletionListener {
                    musicIsPlaying = false
                    startStopTimer() // Когда трек закончился — запускаем таймер
                    updateNotification()
                }
                setOnErrorListener { _, _, _ ->
                    musicIsPlaying = false
                    startStopTimer()
                    updateNotification()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            musicIsPlaying = false
            startStopTimer()
            updateNotification()
        }
    }

    private fun updateMediaSession() {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)

        if (!currentCoverUrl.isNullOrEmpty()) {
            try {
                val bitmap: Bitmap = Glide.with(this)
                    .asBitmap()
                    .load(currentCoverUrl)
                    .submit(512, 512)
                    .get()
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            } catch (e: Exception) {
                // Игнорируем ошибку загрузки картинки
            }
        }

        mediaSession.setMetadata(metadataBuilder.build())
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                .setState(
                    if (musicIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()
        )
    }

    private fun updateNotification() {
        updateMediaSession()
        val playPauseAction = if (musicIsPlaying) "⏸ Пауза" else "▶ Играть"
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
        val intent = Intent(this, JammPlayerService::class.java)
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
        stopHandler.removeCallbacks(stopRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
