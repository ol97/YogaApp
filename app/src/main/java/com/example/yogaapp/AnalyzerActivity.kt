package com.example.yogaapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController

class AnalyzerActivity : AppCompatActivity() {
    private val ANALYZER_MODE_KEY = "challenge_recorder_mode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()
        setContentView(R.layout.activity_analyzer)

        if (intent.getStringExtra(ANALYZER_MODE_KEY) == "challenge") {
            findNavController(R.id.analyzer_activity_fragment).setGraph(R.navigation.analyzer_nav_challenge)
        } else {
            findNavController(R.id.analyzer_activity_fragment).setGraph(R.navigation.analyzer_nav_recorder)
        }

    }

}


