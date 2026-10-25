package com.ivan.jamm

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val tvError = findViewById<TextView>(R.id.tvErrorLog)
        val error = intent.getStringExtra("ERROR") ?: "Неизвестная ошибка"
        tvError.text = error
    }
}
