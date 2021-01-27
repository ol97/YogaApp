package com.example.yogaapp

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

// using predefined Settings Fragment, everything is defined in res.xml.root_preferences
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}