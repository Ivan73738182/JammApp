package com.ivan.jamm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class PlaylistActivity : AppCompatActivity() {
    private var roomCode: String = ""
    private val serverUrl = "https://jamm-vxy7.onrender.com"
    private val client = OkHttpClient()
    
    private val tracksList = mutableListOf<Track>()
    private lateinit var adapter: TrackAdapter
    private lateinit var btnPlayPause: Button

    data class Track(
        val id: String,
        val title: String,
        val artist: String,
        val url: String,
        val coverUrl: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        val tvCode = findViewById<TextView>(R.id.tvPlaylistCode)
        tvCode.text = "Код комнаты: $roomCode (удерживайте для выхода)"

        tvCode.setOnLongClickListener {
            getSharedPreferences("JammPrefs", MODE_PRIVATE).edit().remove("SAVED_ROOM_CODE").apply()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            true
        }

        val lvTracks = findViewById<ListView>(R.id.lvTracks)
        val btnAdd = findViewById<Button>(R.id.btnAddTrack)
        val etUrl = findViewById<EditText>(R.id.etTrackUrl)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        adapter = TrackAdapter(this, tracksList)
        lvTracks.adapter = adapter

        loadTracks()

        lvTracks.setOnItemClickListener { _, _, position, _ ->
            if (position < tracksList.size) {
                playMusic(position)
            }
        }

        btnAdd.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            val title = url.substringAfterLast("/").take(20)
            addTrackToServer(title, "Неизвестен", "Пользователь", url, "")
            etUrl.text.clear()
        }

        btnPlayPause.setOnClickListener {
            val intent = Intent(this, JammMusicService::class.java)
            startService(intent)
        }
    }

    private inner class TrackAdapter(private val context: Context, private val tracks: List<Track>) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = tracks.size
        override fun getItem(position: Int): Any = tracks[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_track, parent, false)
            val track = tracks[position]

            val ivCover = view.findViewById<ImageView>(R.id.ivCover)
            val tvTitle = view.findViewById<TextView>(R.id.tvTrackTitle)
            val tvArtist = view.findViewById<TextView>(R.id.tvTrackArtist)
            val btnMenu = view.findViewById<TextView>(R.id.tvPlayIcon)

            tvTitle.text = track.title
            tvArtist.text = track.artist

            if (!track.coverUrl.isNullOrEmpty()) {
                Glide.with(context).load(track.coverUrl).into(ivCover)
            } else {
                ivCover.setImageDrawable(null)
                ivCover.setBackgroundColor(0xFF333333.toInt())
            }

            btnMenu.setOnClickListener {
                showTrackOptionsDialog(position)
            }

            return view
        }
    }

    private fun showTrackOptionsDialog(position: Int) {
        val options = arrayOf("Изменить название", "Удалить трек")
        AlertDialog.Builder(this)
            .setTitle(tracksList[position].title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(position)
                    1 -> showDeleteDialog(position)
                }
            }
            .show()
    }

    private fun showRenameDialog(position: Int) {
        val trackId = tracksList[position].id
        val currentTitle = tracksList[position].title
        
        val input = EditText(this)
        input.setText(currentTitle)
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this)
            .setTitle("Новое название")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) renameTrack(trackId, newTitle)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteDialog(position: Int) {
        val trackId = tracksList[position].id
        AlertDialog.Builder(this)
            .setTitle("Удалить трек?")
            .setMessage("Трек будет удален из плейлиста.")
            .setPositiveButton("Удалить") { _, _ -> deleteTrack(trackId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun renameTrack(trackId: String, newTitle: String) {
        val jsonBody = JSONObject().apply { put("title", newTitle) }
        val request = Request.Builder()
            .url("$serverUrl/api/track/$trackId")
            .put(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@PlaylistActivity, "Название обновлено!", Toast.LENGTH_SHORT).show()
                        loadTracks()
                    }
                }
            }
        })
    }

    private fun deleteTrack(trackId: String) {
        val request = Request.Builder().url("$serverUrl/api/track/$trackId").delete().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@PlaylistActivity, "Трек удален", Toast.LENGTH_SHORT).show()
                        loadTracks()
                    }
                }
            }
        })
    }

    private fun playMusic(position: Int) {
        try {
            val urls = ArrayList<String>()
            val titles = ArrayList<String>()
            val artists = ArrayList<String>()
            val covers = ArrayList<String>()
            
            for (t in tracksList) {
                urls.add(t.url)
                titles.add(t.title)
                artists.add(t.artist)
                covers.add(t.coverUrl ?: "")
            }

            val intent = Intent(this, JammMusicService::class.java).apply {
                putStringArrayListExtra("URLS", urls)
                putStringArrayListExtra("TITLES", titles)
                putStringArrayListExtra("ARTISTS", artists)
                putStringArrayListExtra("COVERS", covers)
                putExtra("CURRENT_POSITION", position)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
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
                            for (i in 0 until tracks.length()) {
                                val track = tracks.getJSONObject(i)
                                tracksList.add(Track(
                                    id = track.getString("id"),
                                    title = track.getString("title"),
                                    artist = track.getString("artist"),
                                    url = track.optString("url", ""),
                                    coverUrl = track.optString("cover_url", null)
                                ))
                            }
                            adapter.notifyDataSetChanged()
                        } catch (e: Exception) {}
                    }
                }
            }
        })
    }

    private fun addTrackToServer(title: String, artist: String, addedBy: String, url: String, coverUrl: String) {
        val jsonBody = JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("added_by", addedBy)
            put("url", url)
            put("cover_url", coverUrl)
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
}
