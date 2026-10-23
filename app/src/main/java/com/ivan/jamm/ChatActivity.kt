package com.ivan.jamm

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ChatActivity : AppCompatActivity() {
    private var roomCode: String = ""
    private var senderName: String = "Гость"
    private val serverUrl = "https://jamm-vxy7.onrender.com"
    private val client = OkHttpClient()
    
    private val messagesList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            loadMessages()
            handler.postDelayed(this, 3000) // Каждые 3 секунды
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        senderName = intent.getStringExtra("SENDER_NAME") ?: "Гость"

        val lvMessages = findViewById<ListView>(R.id.lvMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)

        adapter = ArrayAdapter(this, R.layout.simple_list_item_white, messagesList)
        lvMessages.adapter = adapter

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            sendMessage(text)
            etMessage.text.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun loadMessages() {
        val request = Request.Builder().url("$serverUrl/api/room/$roomCode/messages").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            val msgs = json.getJSONArray("messages")
                            messagesList.clear()
                            for (i in 0 until msgs.length()) {
                                val m = msgs.getJSONObject(i)
                                val sender = m.optString("sender_name", "Гость")
                                val text = m.getString("text")
                                messagesList.add("$sender: $text")
                            }
                            adapter.notifyDataSetChanged()
                        } catch (e: Exception) {}
                    }
                }
            }
        })
    }

    private fun sendMessage(text: String) {
        val jsonBody = JSONObject().apply {
            put("sender_name", senderName)
            put("text", text)
        }

        val request = Request.Builder()
            .url("$serverUrl/api/room/$roomCode/messages")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        loadMessages()
                    }
                }
            }
        })
    }
}
