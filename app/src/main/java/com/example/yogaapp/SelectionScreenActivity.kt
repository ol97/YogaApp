package com.example.yogaapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

// activity displaying main menu
// only four buttons
// each of them starts appropriate activity
class SelectionScreenActivity : AppCompatActivity() {
    private val ANALYZER_MODE_KEY = "challenge_recorder_mode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_selection_screen)

        // extra value is passed that indicates whether "Challange Mode" or "Recording Mode" should be started
        val buttonChallengeMode = findViewById<Button>(R.id.buttonChallengeMode)
        buttonChallengeMode.setOnClickListener{
            val intent = Intent(this, AnalyzerActivity::class.java)
            intent.putExtra(ANALYZER_MODE_KEY, "challenge")
            startActivity(intent)
        }

        val buttonRecordingMode = findViewById<Button>(R.id.buttonRecordingMode)
        buttonRecordingMode.setOnClickListener{
            val intent = Intent(this, AnalyzerActivity::class.java)
            intent.putExtra(ANALYZER_MODE_KEY, "recorder")
            startActivity(intent)
        }

        val buttonHistory = findViewById<Button>(R.id.buttonHistory)
        buttonHistory.setOnClickListener{
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        val buttonAbout = findViewById<Button>(R.id.buttonAbout)
        buttonAbout.setOnClickListener{
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }
}