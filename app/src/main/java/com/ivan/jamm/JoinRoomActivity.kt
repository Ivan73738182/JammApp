package com.ivan.jamm

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import java.io.IOException

class JoinRoomActivity : AppCompatActivity() {
    private val serverUrl = "https://jamm-vxy7.onrender.com"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_room)

        val etCode = findViewById<EditText>(R.id.etRoomCode)
        val btnJoin = findViewById<Button>(R.id.btnConfirmJoin)

        btnJoin.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(this, "Код должен состоять из 6 цифр", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkRoomExists(code)
        }
    }

    private fun checkRoomExists(code: String) {
        Toast.makeText(this, "Проверяем комнату...", Toast.LENGTH_SHORT).show()
        val request = Request.Builder().url("$serverUrl/api/room/$code").get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@JoinRoomActivity, "Ошибка сети", Toast.LENGTH_LONG).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        getSharedPreferences("JammPrefs", MODE_PRIVATE)
                            .edit().putString("SAVED_ROOM_CODE", code).apply()

                        val intent = Intent(this@JoinRoomActivity, PlaylistActivity::class.java)
                        intent.putExtra("ROOM_CODE", code)
                        intent.putExtra("USER_NAME", "Ксюша") // <--- Гость — Ксюша
                        startActivity(intent)
                        finish()
                    } else if (response.code == 404) {
                        Toast.makeText(this@JoinRoomActivity, "Комната не найдена", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}
