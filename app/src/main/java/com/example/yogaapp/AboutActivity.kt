package com.example.yogaapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val textViewModelDescription = findViewById<TextView>(R.id.textViewModelDescription)
        val textViewLicenceAnnotation = findViewById<TextView>(R.id.textViewLicenseAnnotation)
        textViewModelDescription.movementMethod = LinkMovementMethod.getInstance()
        textViewLicenceAnnotation.movementMethod = LinkMovementMethod.getInstance()
    }
}