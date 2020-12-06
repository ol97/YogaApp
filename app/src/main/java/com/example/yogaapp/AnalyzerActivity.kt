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
        if (intent.getStringExtra(ANALYZER_MODE_KEY) == "challenge")
        {
            var graph = findNavController(R.id.analyzer_activity_fragment).graph
            graph.startDestination = R.id.challengeModeFragment
            findNavController(R.id.analyzer_activity_fragment).graph = graph
        }
        else
        {
            var graph = findNavController(R.id.analyzer_activity_fragment).graph
            graph.startDestination = R.id.recorderFragment
            findNavController(R.id.analyzer_activity_fragment).graph = graph
        }
        setupActionBarWithNavController(findNavController(R.id.analyzer_activity_fragment))
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.analyzer_activity_fragment)
        return super.onSupportNavigateUp() || navController.navigateUp()
    }
}