package com.ivan.jamm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder

class PlaylistActivity : AppCompatActivity() {
    private var roomCode: String = ""
    private var userName: String = "Гость"
    private val serverUrl = "https://jamm-vxy7.onrender.com"
    private val client = OkHttpClient()
    
    // Используем клиент из SupabaseConfig
    private val supabase = SupabaseConfig.client

    private val tracksList = mutableListOf<Track>()
    private val filteredList = mutableListOf<Track>()
    private lateinit var adapter: TrackAdapter
    private lateinit var btnPlayPause: ImageButton
    private lateinit var tvNowPlayingTitle: TextView
    private lateinit var tvNowPlayingArtist: TextView
    private lateinit var etSearch: EditText

    private val playerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra("TITLE") ?: "Ничего не играет"
            val artist = intent?.getStringExtra("ARTIST") ?: ""
            val isPlaying = intent?.getBooleanExtra("IS_PLAYING", false) ?: false

            tvNowPlayingTitle.text = title
            tvNowPlayingArtist.text = artist
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

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

        // Анонимный вход в Supabase для доступа к Storage
        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.auth.signInAnonymously()
            } catch (e: Exception) {
                // Игнорируем, если вход не удался (возможно, не включен в настройках)
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            playerReceiver, IntentFilter("PLAYER_UPDATE")
        )

        roomCode = intent.getStringExtra("ROOM_CODE") ?: "000000"
        userName = intent.getStringExtra("USER_NAME") ?: "Гость"
        
        val tvCode = findViewById<TextView>(R.id.tvPlaylistCode)
        tvCode.text = "Код комнаты: $roomCode"

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
        btnPlayPause = findViewById(R.id.btnPlayPause)
        tvNowPlayingTitle = findViewById(R.id.tvNowPlayingTitle)
        tvNowPlayingArtist = findViewById(R.id.tvNowPlayingArtist)
        etSearch = findViewById(R.id.etSearch)
        val bottomPlayer = findViewById<LinearLayout>(R.id.bottomPlayer)

        bottomPlayer.setOnClickListener {
            startActivity(Intent(this, FullPlayerActivity::class.java))
        }

        adapter = TrackAdapter(this, filteredList)
        lvTracks.adapter = adapter

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTracks(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        loadTracks()

        lvTracks.setOnItemClickListener { _, view, position, _ ->
            view.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
            playMusic(position)
        }

        btnAdd.setOnClickListener {
            val options = arrayOf("Вставить ссылку", "Выбрать из памяти телефона")
            AlertDialog.Builder(this)
                .setTitle("Добавить трек")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        showAddByUrlDialog()
                    } else {
                        openFilePicker()
                    }
                }
                .show()
        }

        btnPlayPause.setOnClickListener {
            val intent = Intent(this, JammMusicService::class.java)
            startService(intent)
        }
    }

    private fun showAddByUrlDialog() {
        val input = EditText(this)
        input.hint = "Вставьте ссылку на песню"
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this)
            .setTitle("Добавить по ссылке")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    downloadAndUpload(url)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun downloadAndUpload(url: String) {
        Toast.makeText(this, "Скачиваем и загружаем в облако...", Toast.LENGTH_SHORT).show()
        
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@PlaylistActivity, "Ошибка сети", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val tempFile = File(cacheDir, "temp_track.mp3")
                        try {
                            val fos = FileOutputStream(tempFile)
                            fos.write(body.bytes())
                            fos.close()
                            
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val fileName = "track_${System.currentTimeMillis()}.mp3"
                                    supabase.storage.from("music").upload(fileName, tempFile.readBytes())
                                    val publicUrl = supabase.storage.from("music").publicUrl(fileName)
                                    
                                    runOnUiThread {
                                        Toast.makeText(this@PlaylistActivity, "Загружено в облако!", Toast.LENGTH_SHORT).show()
                                        val parsedTitle = extractTitleFromUrl(url)
                                        addTrackToServer(parsedTitle, "Неизвестен", userName, publicUrl, "")
                                        tempFile.delete()
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread { Toast.makeText(this@PlaylistActivity, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show() }
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this@PlaylistActivity, "Ошибка файла", Toast.LENGTH_SHORT).show() }
                        }
                    }
                } else {
                    runOnUiThread { Toast.makeText(this@PlaylistActivity, "Ссылка битая", Toast.LENGTH_SHORT).show() }
                }
            }
        })
    }

    private fun openFilePicker() {
        val intent = Intent(this, PickMusicActivity::class.java)
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uris = data?.getStringArrayListExtra("SELECTED_URIS")
            if (uris != null) {
                for (uriString in uris) {
                    val uri = Uri.parse(uriString)
                    val fileName = getFileName(uri)
                    val tempFile = File(cacheDir, fileName)
                    
                    try {
                        val inputStream: InputStream? = contentResolver.openInputStream(uri)
                        val outputStream = FileOutputStream(tempFile)
                        inputStream?.copyTo(outputStream)
                        inputStream?.close()
                        outputStream.close()
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val cloudName = "track_${System.currentTimeMillis()}_${fileName}"
                                supabase.storage.from("music").upload(cloudName, tempFile.readBytes())
                                val publicUrl = supabase.storage.from("music").publicUrl(cloudName)
                                
                                runOnUiThread {
                                    addTrackToServer(fileName.substringBeforeLast("."), "Неизвестен", userName, publicUrl, "")
                                    tempFile.delete()
                                }
                            } catch (e: Exception) {
                                runOnUiThread { Toast.makeText(this@PlaylistActivity, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show() }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Ошибка копирования", Toast.LENGTH_SHORT).show()
                    }
                }
                Toast.makeText(this, "Добавлено ${uris.size} треков", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "track.mp3"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    private fun filterTracks(query: String) {
        filteredList.clear()
        if (query.isEmpty()) {
            filteredList.addAll(tracksList)
        } else {
            for (track in tracksList) {
                if (track.title.lowercase().contains(query.lowercase()) || 
                    track.artist.lowercase().contains(query.lowercase())) {
                    filteredList.add(track)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playerReceiver)
    }

    private fun extractTitleFromUrl(url: String): String {
        try {
            val uri = java.net.URI(url)
            val query = uri.query
            if (query != null) {
                for (param in query.split("&")) {
                    val parts = param.split("=")
                    if (parts.size == 2) {
                        val key = parts[0].lowercase()
                        if (key == "title" || key == "name" || key == "filename") {
                            return URLDecoder.decode(parts[1], "UTF-8")
                        }
                    }
                }
            }
            var lastPart = url.substringAfterLast("/")
            lastPart = lastPart.substringBefore("?")
            lastPart = lastPart.substringBeforeLast(".")
            if (lastPart.isEmpty() || lastPart == "index") return "Неизвестный трек"
            return URLDecoder.decode(lastPart, "UTF-8")
        } catch (e: Exception) {
            return "Неизвестный трек"
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
                ivCover.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                ivCover.setImageResource(R.drawable.app_icon)
                ivCover.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ivCover.setBackgroundColor(0xFF1A1A1A.toInt())
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
            .setTitle(filteredList[position].title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(position)
                    1 -> showDeleteDialog(position)
                }
            }
            .show()
    }

    private fun showRenameDialog(position: Int) {
        val trackId = filteredList[position].id
        val currentTitle = filteredList[position].title
        
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
        val trackId = filteredList[position].id
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
            
            for (t in filteredList) {
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
                putExtra("ROOM_CODE", roomCode)
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
                            filteredList.clear()
                            filteredList.addAll(tracksList)
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
