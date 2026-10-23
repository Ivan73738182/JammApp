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
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide

class JammPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var musicIsPlaying = false
    private lateinit var mediaSession: MediaSessionCompat

    // Списки треков
    private var urlsList = ArrayList<String>()
    private var titlesList = ArrayList<String>()
    private var artistsList = ArrayList<String>()
    private var coversList = ArrayList<String>()
    private var currentPosition = 0 // <--- ТУТ VAR

    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        if (!musicIsPlaying) stopSelf()
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
                    stopHandler.removeCallbacks(stopRunnable)
                    updateNotification()
                }
                override fun onPause() {
                    mediaPlayer?.pause()
                    musicIsPlaying = false
                    startStopTimer()
                    updateNotification()
                }
                override fun onSkipToNext() {
                    if (currentPosition < urlsList.size - 1) {
                        currentPosition++
                        playTrack(currentPosition)
                    }
                }
                override fun onSkipToPrevious() {
                    if (currentPosition > 0) {
                        currentPosition--
                        playTrack(currentPosition)
                    }
                }
            })
            isActive = true
        }
    }

    private fun startStopTimer() {
        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.postDelayed(stopRunnable, 5 * 60 * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val urls = intent?.getStringArrayListExtra("URLS")
        val titles = intent?.getStringArrayListExtra("TITLES")
        val artists = intent?.getStringArrayListExtra("ARTISTS")
        val covers = intent?.getStringArrayListExtra("COVERS")
        val pos = intent?.getIntExtra("CURRENT_POSITION", 0) ?: 0

        if (urls != null && titles != null) {
            urlsList = urls
            titlesList = titles
            artistsList = artists ?: arrayListOf()
            coversList = covers ?: arrayListOf()
            currentPosition = pos
            playTrack(currentPosition)
        } else {
            if (mediaPlayer != null) {
                if (musicIsPlaying) {
                    mediaPlayer?.pause()
                    musicIsPlaying = false
                    startStopTimer()
                } else {
                    mediaPlayer?.start()
                    musicIsPlaying = true
                    stopHandler.removeCallbacks(stopRunnable)
                }
                updateNotification()
            }
        }

        return START_STICKY
    }

    private fun playTrack(position: Int) {
        if (position < 0 || position >= urlsList.size) return
        
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(urlsList[position])
                setOnPreparedListener {
                    start()
                    musicIsPlaying = true
                    stopHandler.removeCallbacks(stopRunnable)
                    updateNotification()
                    updateMediaSession()
                }
                setOnCompletionListener {
                    if (currentPosition < urlsList.size - 1) {
                        currentPosition++
                        playTrack(currentPosition)
                    } else {
                        musicIsPlaying = false
                        startStopTimer()
                        updateNotification()
                    }
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
        if (currentPosition >= titlesList.size) return

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titlesList[currentPosition])
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistsList.getOrNull(currentPosition) ?: "Неизвестен")

        val coverUrl = coversList.getOrNull(currentPosition)
        if (!coverUrl.isNullOrEmpty()) {
            try {
                val bitmap: Bitmap = Glide.with(this)
                    .asBitmap()
                    .load(coverUrl)
                    .submit(512, 512)
                    .get()
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            } catch (e: Exception) {}
        }

        mediaSession.setMetadata(metadataBuilder.build())
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or 
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
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
        
        if (currentPosition >= titlesList.size) return

        val title = titlesList[currentPosition]
        val artist = artistsList.getOrNull(currentPosition) ?: "Неизвестен"

        val prevIntent = PendingIntent.getService(
            this, 2, Intent(this, JammPlayerService::class.java).apply { action = "PREV" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 1, Intent(this, JammPlayerService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 3, Intent(this, JammPlayerService::class.java).apply { action = "NEXT" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, PlaylistActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (musicIsPlaying) "⏸" else "▶"

        val notification = NotificationCompat.Builder(this, "jamm_channel")
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "⏮", prevIntent)
            .addAction(0, playPauseIcon, playPauseIntent)
            .addAction(0, "⏭", nextIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
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
        stopHandler.removeCallbacks(stopRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
