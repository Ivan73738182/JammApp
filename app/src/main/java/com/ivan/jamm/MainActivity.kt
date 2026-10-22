package com.ivan.jamm

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCreate = findViewById<Button>(R.id.btnCreateRoom)
        val btnJoin = findViewById<Button>(R.id.btnJoinRoom)

        btnCreate.setOnClickListener {
            Toast.makeText(this, "Создание комнаты (в разработке)", Toast.LENGTH_SHORT).show()
        }

        btnJoin.setOnClickListener {
            Toast.makeText(this, "Вход по коду (в разработке)", Toast.LENGTH_SHORT).show()
        }
    }
}
