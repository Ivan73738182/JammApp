package com.ivan.jamm

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class PlaylistActivity : AppCompatActivity() {
    private var roomCode: String = ""
    private val serverUrl = "https://jamm-vxy7.onrender.com"
    private val client = OkHttpClient()
    
    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPlaying = false
    private val trackUrls = mutableListOf<String>()
    private val tracksList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var btnPlayPause: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        val tvCode = findViewById<TextView>(R.id.tvPlaylistCode)
        tvCode.text = "Код комнаты: $roomCode"

        val lvTracks = findViewById<ListView>(R.id.lvTracks)
        val btnAdd = findViewById<Button>(R.id.btnAddTrack)
        val etUrl = findViewById<EditText>(R.id.etTrackUrl)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        adapter = ArrayAdapter(this, R.layout.simple_list_item_white, tracksList)
        lvTracks.adapter = adapter

        loadTracks()

        lvTracks.setOnItemClickListener { _, _, position, _ ->
            if (position < trackUrls.size) {
                playMusic(trackUrls[position])
            }
        }

        btnAdd.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            val title = url.substringAfterLast("/").take(20)
            addTrackToServer(title, "Неизвестен", "Пользователь", url)
            etUrl.text.clear()
        }

        btnPlayPause.setOnClickListener {
            if (mediaPlayer != null) {
                if (isMusicPlaying) {
                    mediaPlayer?.pause()
                    btnPlayPause.text = "▶ Играть"
                    isMusicPlaying = false
                } else {
                    mediaPlayer?.start()
                    btnPlayPause.text = "⏸ Пауза"
                    isMusicPlaying = true
                }
            }
        }
    }

    private fun playMusic(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { 
                    start() 
                    isMusicPlaying = true
                    btnPlayPause.text = "⏸ Пауза"
                }
                setOnCompletionListener {
                    isMusicPlaying = false
                    btnPlayPause.text = "▶ Играть"
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@PlaylistActivity, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            Toast.makeText(this, "Загрузка...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTracks() {
        val request = Request.Builder().url("$serverUrl/api/room/$roomCode").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            val tracks = json.getJSONArray("tracks")
                            tracksList.clear()
                            trackUrls.clear()
                            for (i in 0 until tracks.length()) {
                                val track = tracks.getJSONObject(i)
                                tracksList.add("▶ ${track.getString("title")} - ${track.getString("artist")}")
                                trackUrls.add(track.optString("url", ""))
                            }
                            adapter.notifyDataSetChanged()
                        } catch (e: Exception) {}
                    }
                }
            }
        })
    }

    private fun addTrackToServer(title: String, artist: String, addedBy: String, url: String) {
        val jsonBody = JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("added_by", addedBy)
            put("url", url)
        }

        val request = Request.Builder()
            .url("$serverUrl/api/room/$roomCode/add-track")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@PlaylistActivity, "Добавлено!", Toast.LENGTH_SHORT).show()
                        loadTracks()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

