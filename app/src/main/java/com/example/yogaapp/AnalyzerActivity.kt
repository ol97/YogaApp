package com.example.yogaapp

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController

// Activity that hosts fragments for either "Recording Mode" or "Challenge Mode".
// I had some problems making the navigation work the way I wanted.
// Currently the name of the mode selected in
// the main menu is passed to this activity and based on that the correct navigation graph is selected and used.
// Ideally there should be only one navigation graph.
// The alternative is to use FragmentManager and switch between fragments manually.

class AnalyzerActivity : AppCompatActivity() {
    private val ANALYZER_MODE_KEY = "challenge_recorder_mode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()
        setContentView(R.layout.activity_analyzer)

        // select correct nav graph
        if (intent.getStringExtra(ANALYZER_MODE_KEY) == "challenge") {
            findNavController(R.id.analyzer_activity_fragment).setGraph(R.navigation.analyzer_nav_challenge)
        } else {
            findNavController(R.id.analyzer_activity_fragment).setGraph(R.navigation.analyzer_nav_recorder)
        }

    }

}


