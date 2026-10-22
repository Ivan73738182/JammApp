package com.ivan.jamm

import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PlaylistActivity : AppCompatActivity() {
    private var roomCode: String = ""
    
    // Временный список для теста
    private val tracksList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        
        val tvCode = findViewById<TextView>(R.id.tvPlaylistCode)
        tvCode.text = "Код комнаты: $roomCode"

        val lvTracks = findViewById<ListView>(R.id.lvTracks)
        val btnAdd = findViewById<Button>(R.id.btnAddTrack)

        // Простой адаптер для текста
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, tracksList)
        lvTracks.adapter = adapter

        btnAdd.setOnClickListener {
            // Пока добавляем тестовый трек прямо в список на экране
            tracksList.add("Тестовый трек ${tracksList.size + 1}")
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Трек добавлен (локально)", Toast.LENGTH_SHORT).show()
        }
    }
}
