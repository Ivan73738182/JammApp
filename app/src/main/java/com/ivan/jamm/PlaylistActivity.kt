package com.ivan.jamm

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
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

    private val tracksList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        
        val tvCode = findViewById<TextView>(R.id.tvPlaylistCode)
        tvCode.text = "Код комнаты: $roomCode"

        val lvTracks = findViewById<ListView>(R.id.lvTracks)
        val btnAdd = findViewById<Button>(R.id.btnAddTrack)

        // Используем наш новый белый шаблон для текста
        adapter = ArrayAdapter(this, R.layout.simple_list_item_white, tracksList)
        lvTracks.adapter = adapter

        // Загружаем треки при открытии экрана
        loadTracks()

        btnAdd.setOnClickListener {
            // Отправляем тестовый трек на сервер
            addTrackToServer("Тестовый трек ${tracksList.size + 1}", "Исполнитель", "Иван")
        }
    }

    private fun loadTracks() {
        val request = Request.Builder()
            .url("$serverUrl/api/room/$roomCode")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PlaylistActivity, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            val tracks = json.getJSONArray("tracks")
                            tracksList.clear()
                            for (i in 0 until tracks.length()) {
                                val track = tracks.getJSONObject(i)
                                tracksList.add("${track.getString("title")} - ${track.getString("artist")}")
                            }
                            adapter.notifyDataSetChanged()
                        } catch (e: Exception) {
                            Toast.makeText(this@PlaylistActivity, "Ошибка парсинга списка", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun addTrackToServer(title: String, artist: String, addedBy: String) {
        val jsonBody = JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("added_by", addedBy)
        }

        val request = Request.Builder()
            .url("$serverUrl/api/room/$roomCode/add-track")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PlaylistActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@PlaylistActivity, "Трек сохранен на сервере!", Toast.LENGTH_SHORT).show()
                        loadTracks() // Обновляем список после добавления
                    } else {
                        Toast.makeText(this@PlaylistActivity, "Ошибка сервера при добавлении", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
