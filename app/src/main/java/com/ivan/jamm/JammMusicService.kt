package com.ivan.jamm

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide

class JammMusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var musicIsPlaying = false
    private lateinit var mediaSession: MediaSessionCompat

    private var urlsList = ArrayList<String>()
    private var titlesList = ArrayList<String>()
    private var artistsList = ArrayList<String>()
    private var coversList = ArrayList<String>()
    private var trackPosition = 0
    private var currentRoomCode: String = "000000"

    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        if (!musicIsPlaying) stopSelf()
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (mediaPlayer != null && musicIsPlaying) {
                sendProgressUpdate()
            }
            progressHandler.postDelayed(this, 1000)
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
                    stopHandler.removeCallbacks(stopRunnable)
                    sendPlayerUpdate()
                    updateNotification()
                }
                override fun onPause() {
                    mediaPlayer?.pause()
                    musicIsPlaying = false
                    startStopTimer()
                    sendPlayerUpdate()
                    updateNotification()
                }
                override fun onSkipToNext() {
                    if (trackPosition < urlsList.size - 1) {
                        trackPosition++
                        playTrack(trackPosition)
                    }
                }
                override fun onSkipToPrevious() {
                    if (trackPosition > 0) {
                        trackPosition--
                        playTrack(trackPosition)
                    }
                }
                override fun onSeekTo(pos: Long) {
                    mediaPlayer?.seekTo(pos.toInt())
                    sendProgressUpdate()
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
        val action = intent?.action
        if (action == "PREV") {
            if (trackPosition > 0) {
                trackPosition--
                playTrack(trackPosition)
            }
            return START_STICKY
        }
        if (action == "NEXT") {
            if (trackPosition < urlsList.size - 1) {
                trackPosition++
                playTrack(trackPosition)
            }
            return START_STICKY
        }
        if (action == "SEEK") {
            val seekTo = intent.getIntExtra("SEEK_TO", 0)
            mediaPlayer?.seekTo(seekTo)
            sendProgressUpdate()
            return START_STICKY
        }

        val urls = intent?.getStringArrayListExtra("URLS")
        val titles = intent?.getStringArrayListExtra("TITLES")
        val artists = intent?.getStringArrayListExtra("ARTISTS")
        val covers = intent?.getStringArrayListExtra("COVERS")
        val pos = intent?.getIntExtra("CURRENT_POSITION", 0) ?: 0
        val room = intent?.getStringExtra("ROOM_CODE")

        if (room != null) {
            currentRoomCode = room
        }

        if (urls != null && titles != null) {
            urlsList = urls
            titlesList = titles
            artistsList = artists ?: arrayListOf()
            coversList = covers ?: arrayListOf()
            trackPosition = pos
            playTrack(trackPosition)
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
                sendPlayerUpdate()
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
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(this@JammMusicService, Uri.parse(urlsList[position]))
                setOnPreparedListener {
                    musicIsPlaying = true
                    sendPlayerUpdate()
                    sendProgressUpdate()
                    start()
                    stopHandler.removeCallbacks(stopRunnable)
                    progressHandler.post(progressRunnable)
                    updateNotification()
                    updateMediaSession()
                }
                setOnCompletionListener {
                    if (trackPosition < urlsList.size - 1) {
                        trackPosition++
                        playTrack(trackPosition)
                    } else {
                        musicIsPlaying = false
                        startStopTimer()
                        sendPlayerUpdate()
                        updateNotification()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    musicIsPlaying = false
                    startStopTimer()
                    sendPlayerUpdate()
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

    private fun sendPlayerUpdate() {
        if (trackPosition >= titlesList.size) return
        val title = titlesList[trackPosition]
        val artist = artistsList.getOrNull(trackPosition) ?: "Неизвестен"
        val coverUrl = coversList.getOrNull(trackPosition)

        val playerIntent = Intent("PLAYER_UPDATE")
        playerIntent.putExtra("TITLE", title)
        playerIntent.putExtra("ARTIST", artist)
        playerIntent.putExtra("IS_PLAYING", musicIsPlaying)
        LocalBroadcastManager.getInstance(this).sendBroadcast(playerIntent)

        val fullIntent = Intent("PLAYER_UPDATE_FULL")
        fullIntent.putExtra("TITLE", title)
        fullIntent.putExtra("ARTIST", artist)
        fullIntent.putExtra("COVER_URL", coverUrl)
        fullIntent.putExtra("IS_PLAYING", musicIsPlaying)
        LocalBroadcastManager.getInstance(this).sendBroadcast(fullIntent)
    }

    private fun sendProgressUpdate() {
        if (mediaPlayer == null) return
        val current = mediaPlayer!!.currentPosition
        val duration = mediaPlayer!!.duration
        val intent = Intent("PLAYER_PROGRESS")
        intent.putExtra("CURRENT_TIME", current)
        intent.putExtra("DURATION", duration)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateMediaSession() {
        if (trackPosition >= titlesList.size) return

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titlesList[trackPosition])
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistsList.getOrNull(trackPosition) ?: "Неизвестен")

        val coverUrl = coversList.getOrNull(trackPosition)
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
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (musicIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    if (mediaPlayer != null) mediaPlayer!!.currentPosition.toLong() else PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()
        )
    }

    private fun updateNotification() {
        updateMediaSession()
        
        if (trackPosition >= titlesList.size) return

        val title = titlesList[trackPosition]
        val artist = artistsList.getOrNull(trackPosition) ?: "Неизвестен"

        val prevIntent = PendingIntent.getService(
            this, 2, Intent(this, JammMusicService::class.java).apply { action = "PREV" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 1, Intent(this, JammMusicService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 3, Intent(this, JammMusicService::class.java).apply { action = "NEXT" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, PlaylistActivity::class.java).apply {
                putExtra("ROOM_CODE", currentRoomCode)
                putExtra("USER_NAME", "Ваня")
            },
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
        progressHandler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
