package com.ivan.jamm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide

class FullPlayerActivity : AppCompatActivity() {
    private lateinit var ivCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    private var isUserSeeking = false

    // Обновление времени
    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isUserSeeking) return
            val current = intent?.getIntExtra("CURRENT_TIME", 0) ?: 0
            val duration = intent?.getIntExtra("DURATION", 0) ?: 0

            if (duration > 0) {
                seekBar.max = duration
                seekBar.progress = current
                tvCurrentTime.text = formatTime(current)
                tvTotalTime.text = formatTime(duration)
            }
        }
    }

    // Обновление данных трека
    private val playerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra("TITLE") ?: "Неизвестно"
            val artist = intent?.getStringExtra("ARTIST") ?: "Неизвестен"
            val coverUrl = intent?.getStringExtra("COVER_URL")
            val isPlaying = intent?.getBooleanExtra("IS_PLAYING", false) ?: false

            tvTitle.text = title
            tvArtist.text = artist
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

            if (!coverUrl.isNullOrEmpty()) {
                Glide.with(this@FullPlayerActivity).load(coverUrl).into(ivCover)
            } else {
                ivCover.setImageResource(R.drawable.app_icon)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_player)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            playerReceiver, IntentFilter("PLAYER_UPDATE_FULL")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            progressReceiver, IntentFilter("PLAYER_PROGRESS")
        )

        ivCover = findViewById(R.id.ivFullCover)
        tvTitle = findViewById(R.id.tvFullTitle)
        tvArtist = findViewById(R.id.tvFullArtist)
        btnPlayPause = findViewById(R.id.btnPlayPauseFull)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnPrev = findViewById<Button>(R.id.btnPrev)
        val btnNext = findViewById<Button>(R.id.btnNext)

        btnBack.setOnClickListener { finish() }

        btnPlayPause.setOnClickListener {
            val intent = Intent(this, JammMusicService::class.java)
            startService(intent)
        }

        // Кнопки переключения треков
        btnPrev.setOnClickListener {
            val intent = Intent(this, JammMusicService::class.java).apply {
                action = "PREV"
            }
            startService(intent)
        }

        btnNext.setOnClickListener {
            val intent = Intent(this, JammMusicService::class.java).apply {
                action = "NEXT"
            }
            startService(intent)
        }

        // Перемотка
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.let {
                    val intent = Intent(this@FullPlayerActivity, JammMusicService::class.java).apply {
                        action = "SEEK"
                        putExtra("SEEK_TO", it.progress)
                    }
                    startService(intent)
                }
            }
        })
    }

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playerReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver)
    }
}
