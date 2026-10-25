package com.ivan.jamm

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PickMusicActivity : AppCompatActivity() {
    private val musicList = mutableListOf<MusicItem>()
    private lateinit var adapter: MusicAdapter
    private lateinit var progressBar: ProgressBar

    data class MusicItem(val title: String, val artist: String, val uri: Uri, var isSelected: Boolean = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_music)

        val lvMusic = findViewById<ListView>(R.id.lvMusic)
        val btnAdd = findViewById<Button>(R.id.btnAddSelected)
        progressBar = findViewById(R.id.progressBar)

        adapter = MusicAdapter(this, musicList)
        lvMusic.adapter = adapter

        // Запрашиваем разрешение
        if (hasAudioPermission()) {
            loadMusic()
        } else {
            requestAudioPermission()
        }

        btnAdd.setOnClickListener {
            val selected = musicList.filter { it.isSelected }
            if (selected.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одну песню", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val resultIntent = Intent()
            val uris = ArrayList<String>()
            for (item in selected) {
                uris.add(item.uri.toString())
            }
            resultIntent.putStringArrayListExtra("SELECTED_URIS", uris)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 101)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusic()
            } else {
                Toast.makeText(this, "Без разрешения мы не найдем музыку", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMusic() {
        progressBar.visibility = View.VISIBLE
        
        Thread {
            val musicListTemp = mutableListOf<MusicItem>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol)
                    val artist = it.getString(artistCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    musicListTemp.add(MusicItem(title, artist, uri))
                }
            }

            runOnUiThread {
                musicList.clear()
                musicList.addAll(musicListTemp)
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
        }.start()
    }

    private inner class MusicAdapter(private val context: android.content.Context, private val items: List<MusicItem>) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_music_checkbox, parent, false)
            val item = items[position]

            val cb = view.findViewById<CheckBox>(R.id.cbSelect)
            val tvTitle = view.findViewById<TextView>(R.id.tvSongTitle)
            val tvArtist = view.findViewById<TextView>(R.id.tvSongArtist)

            tvTitle.text = item.title
            tvArtist.text = item.artist

            cb.setOnCheckedChangeListener(null)
            cb.isChecked = item.isSelected

            cb.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
            }

            view.setOnClickListener {
                cb.isChecked = !cb.isChecked
            }

            return view
        }
    }
}
