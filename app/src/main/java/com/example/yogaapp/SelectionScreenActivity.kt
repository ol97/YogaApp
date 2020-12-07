package com.example.yogaapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.navigation.NavInflater
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment

class SelectionScreenActivity : AppCompatActivity() {
    private val ANALYZER_MODE_KEY = "challenge_recorder_mode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_selection_screen)

        val buttonChallengeMode = findViewById<Button>(R.id.buttonChallengeMode)
        buttonChallengeMode.setOnClickListener{
            val intent = Intent(this, AnalyzerActivity::class.java)
            intent.putExtra(ANALYZER_MODE_KEY, "challenge")
            startActivity(intent)
        }

        val buttonRecorder = findViewById<Button>(R.id.buttonRecorder)
        buttonRecorder.setOnClickListener{
            val intent = Intent(this, AnalyzerActivity::class.java)
            intent.putExtra(ANALYZER_MODE_KEY, "recorder")
            startActivity(intent)
        }

        val buttonHistory = findViewById<Button>(R.id.buttonHistory)
        buttonHistory.setOnClickListener{
            val intent = Intent(this, ArchiveActivity::class.java)
            startActivity(intent)
        }
    }
}