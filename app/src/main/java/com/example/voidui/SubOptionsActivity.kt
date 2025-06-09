package com.example.voidui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SubOptionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this) // Apply theme before setting content
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_options)

//        val backButtonTheme = findViewById<ImageButton>(R.id.backButtonTheme)
//        backButtonTheme.setOnClickListener {
//            finish()
//            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
//        }

        val radioGroup = findViewById<RadioGroup>(R.id.themeRadioGroup)
        val darkMode = findViewById<RadioButton>(R.id.radioDark)
        val lightMode = findViewById<RadioButton>(R.id.radioLight)
        val systemDefault = findViewById<RadioButton>(R.id.radioSystem)

        when (ThemeManager.getSavedThemeMode(this)) {
            AppCompatDelegate.MODE_NIGHT_YES -> darkMode.isChecked = true
            AppCompatDelegate.MODE_NIGHT_NO -> lightMode.isChecked = true
            else -> systemDefault.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }

            ThemeManager.saveThemeMode(this, mode)
            recreate() // Recreate activity to apply new theme
        }
    }
}
