package com.example.yogaapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class SelectionScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_selection_screen)

        val buttonAnalyzer = findViewById<Button>(R.id.buttonAnalyzer)
        buttonAnalyzer.setOnClickListener{
            val intent = Intent(this, AnalyzerActivity::class.java)
            startActivity(intent)
        }

        val buttonHistory = findViewById<Button>(R.id.buttonHistory)
        buttonHistory.setOnClickListener{}
    }
}