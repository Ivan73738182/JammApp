package com.ivan.jamm

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RoomCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_code)

        val tvCode = findViewById<TextView>(R.id.tvRoomCode)
        val btnGo = findViewById<Button>(R.id.btnGoToPlaylist)
        val btnShare = findViewById<Button>(R.id.btnShareCode)

        val roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        tvCode.text = roomCode

        btnGo.setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("ROOM_CODE", roomCode)
            intent.putExtra("USER_NAME", "Ваня") // <--- Создатель — Ваня
            startActivity(intent)
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Привет! Присоединяйся к моему плейлисту Jamm 🎵\nКод комнаты: $roomCode\n\nСкачай приложение Jamm и введи этот код в разделе «Войти по коду»."
                )
            }
            try {
                startActivity(Intent.createChooser(shareIntent, "Поделиться кодом через"))
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть меню", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
