package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_aliases")
data class DeviceAlias(
    @PrimaryKey val macAddress: String, // MAC address or BSSID
    val alias: String,
    val isFavorite: Boolean = false,
    val alertThresholdDbm: Int? = null, // Custom threshold, e.g. -80
    val notes: String = "",
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
