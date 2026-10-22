package com.ivan.jamm

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    // Наш сервер на Render
    private val serverUrl = "https://jamm-vxy7.onrender.com"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCreate = findViewById<Button>(R.id.btnCreateRoom)
        val btnJoin = findViewById<Button>(R.id.btnJoinRoom)

        btnCreate.setOnClickListener {
            createRoom()
        }

        btnJoin.setOnClickListener {
            // Пока просто заглушка, экран входа сделаем чуть позже
            Toast.makeText(this, "Вход по коду скоро будет!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createRoom() {
        Toast.makeText(this, "Стучимся на сервер...", Toast.LENGTH_SHORT).show()
        
        val request = Request.Builder()
            .url("$serverUrl/api/create-room")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            val room = json.getJSONObject("room")
                            val code = room.getString("code")
                            Toast.makeText(this@MainActivity, "Комната создана! Код: $code", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Ошибка парсинга", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка сервера: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
