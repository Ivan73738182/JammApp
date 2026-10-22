package com.ivan.jamm

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val serverUrl = "https://jamm-vxy7.onrender.com"
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    
    private val pingRunnable = object : Runnable {
        override fun run() {
            pingServer()
            handler.postDelayed(this, 5 * 60 * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCreate = findViewById<Button>(R.id.btnCreateRoom)
        val btnJoin = findViewById<Button>(R.id.btnJoinRoom)

        btnCreate.setOnClickListener {
            createRoom()
        }

        btnJoin.setOnClickListener {
            // Переходим на экран ввода кода
            val intent = Intent(this, JoinRoomActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(pingRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pingRunnable)
    }

    private fun pingServer() {
        val request = Request.Builder().url(serverUrl).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun createRoom() {
        Toast.makeText(this, "Стучимся на сервер...", Toast.LENGTH_SHORT).show()
        
        val request = Request.Builder()
            .url("$serverUrl/api/create-room")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            val room = json.getJSONObject("room")
                            val code = room.getString("code")
                            
                            val intent = Intent(this@MainActivity, RoomCodeActivity::class.java)
                            intent.putExtra("ROOM_CODE", code)
                            startActivity(intent)
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
