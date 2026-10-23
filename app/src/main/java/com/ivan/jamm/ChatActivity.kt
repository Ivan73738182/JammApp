package com.ivan.jamm

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    
    private val messagesList = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            loadMessages()
            handler.postDelayed(this, 3000)
        }
    }

    data class Message(val sender: String, val text: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        senderName = intent.getStringExtra("SENDER_NAME") ?: "Гость"

        val lvMessages = findViewById<ListView>(R.id.lvMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)

        adapter = MessageAdapter(this, messagesList)
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

    private inner class MessageAdapter(context: Context, private val messages: List<Message>) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = messages.size
        override fun getItem(position: Int): Any = messages[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_message, parent, false)
            val message = messages[position]

            val tvSender = view.findViewById<TextView>(R.id.tvSender)
            val tvMessage = view.findViewById<TextView>(R.id.tvMessage)

            tvSender.text = message.sender
            tvMessage.text = message.text

            val isMe = message.sender == senderName

            // Настраиваем выравнивание и цвет
            val params = tvMessage.layoutParams as LinearLayout.LayoutParams
            
            if (isMe) {
                // Мое сообщение: справа, бирюзовое, текст черный
                params.gravity = Gravity.END
                tvMessage.setBackgroundResource(R.drawable.bg_bubble_me)
                tvMessage.setTextColor(ContextCompat.getColor(this@ChatActivity, android.R.color.black))
                tvSender.gravity = Gravity.END
            } else {
                // Чужое сообщение: слева, фиолетовое, текст белый
                params.gravity = Gravity.START
                tvMessage.setBackgroundResource(R.drawable.bg_bubble_other)
                tvMessage.setTextColor(ContextCompat.getColor(this@ChatActivity, android.R.color.white))
                tvSender.gravity = Gravity.START
            }
            
            tvMessage.layoutParams = params

            return view
        }
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
                            
                            // Проверяем, изменилось ли количество, чтобы не дергать список зря
                            if (messagesList.size != msgs.length()) {
                                messagesList.clear()
                                for (i in 0 until msgs.length()) {
                                    val m = msgs.getJSONObject(i)
                                    val sender = m.optString("sender_name", "Гость")
                                    val text = m.getString("text")
                                    messagesList.add(Message(sender, text))
                                }
                                adapter.notifyDataSetChanged()
                            }
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
