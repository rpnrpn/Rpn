package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceName: String,
    val macAddress: String,
    val deviceType: String, // "WIFI" or "BLUETOOTH"
    val signalStrength: Int, // dBm
    val timestamp: Long = System.currentTimeMillis()
)
