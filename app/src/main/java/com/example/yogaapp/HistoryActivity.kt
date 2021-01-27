package com.example.yogaapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController

// Activity hosting fragments for "History" screen. Both fragments (for list view and details) are
// hosted by the same activity.

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        setupActionBarWithNavController(findNavController(R.id.history_fragment))
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.history_fragment)
        return super.onSupportNavigateUp() || navController.navigateUp()
    }
}