package com.ivan.jamm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide

class FullPlayerActivity : AppCompatActivity() {
    private lateinit var ivCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var btnPlayPause: ImageButton

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

        ivCover = findViewById(R.id.ivFullCover)
        tvTitle = findViewById(R.id.tvFullTitle)
        tvArtist = findViewById(R.id.tvFullArtist)
        btnPlayPause = findViewById(R.id.btnPlayPauseFull)
        
        val btnBack = findViewById<Button>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }

        btnPlayPause.setOnClickListener {
            val intent = Intent(this, JammMusicService::class.java)
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playerReceiver)
    }
}
