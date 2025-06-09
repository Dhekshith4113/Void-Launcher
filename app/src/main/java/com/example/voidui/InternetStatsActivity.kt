package com.example.voidui

import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.voidui.databinding.InternetStatsActivityBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class InternetStatsActivity : AppCompatActivity() {
    private lateinit var binding: InternetStatsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = InternetStatsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchTrack.isChecked = SharedPreferencesManager.isSwitchTrackEnabled(this)

//        binding.backBtn.setOnClickListener {
//            finish()
//            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
//        }

        binding.switchTrack.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setSwitchTrackEnabled(this, isChecked)
            if (isChecked) {
                val intent = Intent(this, SpeedMonitorService::class.java)
                startForegroundService(intent)
                populateUsageTable()
            } else {
                val intent = Intent(this, SpeedMonitorService::class.java)
                stopService(intent)
            }
        }
    }

    private fun populateUsageTable() {
        CoroutineScope(Dispatchers.IO).launch {
            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val rows = mutableListOf<TableRow>()

            for (i in 0..6) {
                cal.time = Date()
                cal.add(Calendar.DATE, -i)
                val date = cal.time

                val mobileData = NetworkUsageHelper(this@InternetStatsActivity).getDailyDataUsage(date, NetworkUsageHelper.NetworkType.MOBILE)
                val wifiData = NetworkUsageHelper(this@InternetStatsActivity).getDailyDataUsage(date, NetworkUsageHelper.NetworkType.WIFI)
                val totalData = mobileData + wifiData

                val row = TableRow(this@InternetStatsActivity).apply {
                    addCell(dateFormat.format(date))
                    addCell(formatBytes(mobileData))
                    addCell(formatBytes(wifiData))
                    addCell(formatBytes(totalData))
                }
                rows.add(row)
            }

            val headerRow = TableRow(this@InternetStatsActivity).apply {
                addCell("Date", isHeader = true)
                addCell("Mobile", isHeader = true)
                addCell("Wi-Fi", isHeader = true)
                addCell("Total", isHeader = true)
            }

            withContext(Dispatchers.Main) {
                binding.tableUsage.apply {
                    removeAllViews()
                    addView(headerRow)
                    rows.forEach { addView(it) }
                }
            }
        }
    }

    private fun TableRow.addCell(text: String, isHeader: Boolean = false) {
        val textView = TextView(this@InternetStatsActivity).apply {
            this.text = text
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(if (isHeader) getColor(R.color.textColorPrimary) else getColor(R.color.backgroundColor))
            setTextColor(if (isHeader) getColor(R.color.backgroundColor) else getColor(R.color.textColorPrimary))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.cell_border)
            if (isHeader) {
                setBackgroundColor(getColor(R.color.textColorPrimary))
            }

            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(textView)
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.2f MB", bytes / mb)
            bytes >= kb -> String.format("%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
