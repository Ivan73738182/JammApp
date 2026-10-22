package com.ivan.jamm

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RoomCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_code)

        val tvCode = findViewById<TextView>(R.id.tvRoomCode)
        val btnGo = findViewById<Button>(R.id.btnGoToPlaylist)

        // Получаем код, который передали из MainActivity
        val roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        tvCode.text = roomCode

        btnGo.setOnClickListener {
            // Пока заглушка для следующего экрана
            android.widget.Toast.makeText(this, "Плейлист скоро будет!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
