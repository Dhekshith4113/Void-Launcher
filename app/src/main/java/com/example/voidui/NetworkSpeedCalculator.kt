package com.example.voidui

import android.net.TrafficStats

class NetworkSpeedCalculator {
    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()
    private var lastTimeStamp = System.currentTimeMillis()

    fun getNetworkSpeed(): Pair<Long, Long> {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTimeStamp = System.currentTimeMillis()

        val timeDiff = (currentTimeStamp - lastTimeStamp) / 1000.0 // in seconds
        if (timeDiff == 0.0) return 0L to 0L

        val downloadSpeed = ((currentRxBytes - lastRxBytes) / timeDiff).toLong() // bytes/sec
        val uploadSpeed = ((currentTxBytes - lastTxBytes) / timeDiff).toLong() // bytes/sec

        // Update last values
        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastTimeStamp = currentTimeStamp

        return downloadSpeed to uploadSpeed
    }
}