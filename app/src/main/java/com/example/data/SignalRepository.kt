package com.example.data

import android.content.Context
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import kotlin.math.pow
import kotlin.random.Random

// Represents a unified, polished device item for both Wi-Fi and Bluetooth
data class NetworkDevice(
    val ssidOrName: String,
    val macAddress: String,
    val rssidbm: Int,
    val type: DeviceType,
    val frequencyOrClass: String, // WiFi: e.g. "5.18 GHz", Bluetooth: e.g. "Audio"
    val channelOrRssiTrend: Int,   // WiFi channel, or status index
    val securityOrType: String,   // WiFi: "WPA3", "WPA2-PSK", "Open"; Bluetooth: "LE", "Classic"
    val distanceMeters: Double,   // Calculated estimate
    val alias: String? = null,
    val isFavorite: Boolean = false,
    val alertThresholdDbm: Int? = null,
    val notes: String = "",
    val isRealHardware: Boolean = false,
    val signalHistory: List<Int> = emptyList() // Last 10 points for sparkline
)

enum class DeviceType {
    WIFI, BLUETOOTH
}

class SignalRepository(private val context: Context, private val database: AppDatabase) {

    private val deviceAliasDao = database.deviceAliasDao()
    private val scanHistoryDao = database.scanHistoryDao()

    // Expose DB flows
    val allAliasesFlow: Flow<List<DeviceAlias>> = deviceAliasDao.getAllAliasesFlow()
    val recentHistoryFlow: Flow<List<ScanHistory>> = scanHistoryDao.getRecentHistoryFlow()

    fun getDeviceHistoryFlow(macAddress: String): Flow<List<ScanHistory>> {
        return scanHistoryDao.getDeviceHistoryFlow(macAddress)
    }

    suspend fun saveAlias(alias: DeviceAlias) {
        deviceAliasDao.insertAlias(alias)
    }

    suspend fun removeAlias(macAddress: String) {
        deviceAliasDao.deleteAlias(macAddress)
    }

    suspend fun logScanItem(deviceName: String, macAddress: String, deviceType: DeviceType, signal: Int) {
        scanHistoryDao.insertLog(
            ScanHistory(
                deviceName = deviceName,
                macAddress = macAddress,
                deviceType = deviceType.name,
                signalStrength = signal
            )
        )
    }

    suspend fun clearHistory() {
        scanHistoryDao.clearAllHistory()
    }

    // High fidelity simulated Wi-Fi networks
    private val initialSimulatedWifi = listOf(
        NetworkDevice("AirPulse_Main_5G", "1A:2B:3C:4D:5E:6F", -42, DeviceType.WIFI, "5.24 GHz", 48, "WPA3", 1.2),
        NetworkDevice("CoffeeShop_Guest", "A4:93:3F:E5:82:11", -65, DeviceType.WIFI, "2.44 GHz", 6, "WPA2-PSK", 5.6),
        NetworkDevice("Neighbor_WiFi_Ext", "DE:9F:8A:23:45:C1", -82, DeviceType.WIFI, "2.41 GHz", 1, "WPA2", 24.3),
        NetworkDevice("SmartHome_Bridge", "00:1E:42:C8:BB:55", -55, DeviceType.WIFI, "2.46 GHz", 11, "WPA2", 3.1),
        NetworkDevice("Airport_Ultra_Free", "3C:D0:F8:71:A2:E9", -72, DeviceType.WIFI, "5.18 GHz", 36, "Open", 11.5),
        NetworkDevice("Secure_Corporate_Net", "90:A4:DE:60:44:88", -50, DeviceType.WIFI, "5.78 GHz", 157, "Enterprise-WPA3", 2.2),
        NetworkDevice("Hidden Network", "66:77:88:99:AA:BB", -78, DeviceType.WIFI, "2.43 GHz", 4, "WPA2-PSK", 18.2)
    )

    // High fidelity simulated Bluetooth devices
    private val initialSimulatedBluetooth = listOf(
        NetworkDevice("Bose QuietComfort Ultra", "00:11:22:33:44:55", -45, DeviceType.BLUETOOTH, "Audio / Headset", -1, "Classic", 1.5),
        NetworkDevice("Apple Watch Series 9", "E4:F5:F6:A1:B2:C3", -58, DeviceType.BLUETOOTH, "Smartwatch", -1, "Low Energy", 3.8),
        NetworkDevice("Office Smart TV", "BC:FE:D9:8A:76:54", -75, DeviceType.BLUETOOTH, "Screen / Audio", -1, "Classic", 14.1),
        NetworkDevice("Tile Tracker Keys", "12:34:56:78:90:AB", -85, DeviceType.BLUETOOTH, "Key Tracker", -1, "Low Energy", 30.5),
        NetworkDevice("Pixel Buds Pro", "FF:EE:DD:CC:BB:AA", -62, DeviceType.BLUETOOTH, "Audio / Headset", -1, "Low Energy", 4.9),
        NetworkDevice("Unknown Beacon", "34:A2:4C:E3:61:9F", -90, DeviceType.BLUETOOTH, "Uncategorized", -1, "Low Energy", 45.0)
    )

    // Maintain running list with local history
    private val simulatedWifiFlow = MutableStateFlow(initialSimulatedWifi.map { it.copy(signalHistory = generateInitialHistory(it.rssidbm)) })
    private val simulatedBluetoothFlow = MutableStateFlow(initialSimulatedBluetooth.map { it.copy(signalHistory = generateInitialHistory(it.rssidbm)) })

    private fun generateInitialHistory(base: Int): List<Int> {
        return List(8) { base + Random.nextInt(-4, 4) }
    }

    fun getSimulatedWifiFlow(): StateFlow<List<NetworkDevice>> = simulatedWifiFlow
    fun getSimulatedBluetoothFlow(): StateFlow<List<NetworkDevice>> = simulatedBluetoothFlow

    // Fluctuates signal values to create a highly dynamic and interactive real-time visual monitor
    fun fluctuateSignals() {
        simulatedWifiFlow.value = simulatedWifiFlow.value.map { device ->
            val change = Random.nextInt(-6, 7) // Change by up to 6 dBm
            val newRssi = (device.rssidbm + change).coerceIn(-100, -30)
            val updatedHistory = (device.signalHistory + newRssi).takeLast(10)
            val newDist = calculateDistance(newRssi, if (device.frequencyOrClass.contains("5")) 5200.0 else 2400.0)
            device.copy(
                rssidbm = newRssi,
                signalHistory = updatedHistory,
                distanceMeters = newDist
            )
        }

        simulatedBluetoothFlow.value = simulatedBluetoothFlow.value.map { device ->
            val change = Random.nextInt(-5, 6)
            val newRssi = (device.rssidbm + change).coerceIn(-100, -30)
            val updatedHistory = (device.signalHistory + newRssi).takeLast(10)
            val newDist = calculateDistance(newRssi, 2400.0) // bluetooth runs on 2.4GHz
            device.copy(
                rssidbm = newRssi,
                signalHistory = updatedHistory,
                distanceMeters = newDist
            )
        }
    }

    // Formulas for estimating distance based on RSSI (Free-space path loss formula)
    private fun calculateDistance(rssi: Int, frequencyMhz: Double): Double {
        // Free-Space Path Loss (FSPL) distance estimation formula
        // FSPL(dB) = 20 log10(d) + 20 log10(f) + 32.44
        // where d is in km, f is in MHz
        // In indoor/cluttered environments, we also model path loss exponent (N = 2.5 to 3)
        // Simple indoor path loss: d = 10^((Measured_Power - RSSI) / (10 * N))
        // Measured power at 1 meter is typically -40 to -50 dBm
        val txPower = -42.0 // reference RSSI at 1m
        val pathLossExponent = 3.0 // indoor setting
        val distance = 10.0.pow((txPower - rssi) / (10.0 * pathLossExponent))
        return Math.round(distance * 10.0) / 10.0 // 1 decimal place
    }

    // REAL Android API access layers (fails gracefully if emulator has no permissions or features)
    fun getRealWifiDevices(): List<NetworkDevice> {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()
            if (!wifiManager.isWifiEnabled) return emptyList()

            val results = wifiManager.scanResults ?: return emptyList()
            results.map { scanResult ->
                val freqGhz = scanResult.frequency / 1000.0
                val ssid = if (scanResult.SSID.isNullOrEmpty()) "Hidden Network" else scanResult.SSID
                val distance = calculateDistance(scanResult.level, scanResult.frequency.toDouble())
                NetworkDevice(
                    ssidOrName = ssid,
                    macAddress = scanResult.BSSID ?: "00:00:00:00:00:00",
                    rssidbm = scanResult.level,
                    type = DeviceType.WIFI,
                    frequencyOrClass = String.format("%.2f GHz", freqGhz),
                    channelOrRssiTrend = getChannelFromFrequency(scanResult.frequency),
                    securityOrType = getSecurityString(scanResult.capabilities),
                    distanceMeters = distance,
                    isRealHardware = true,
                    signalHistory = listOf(scanResult.level)
                )
            }.sortedByDescending { it.rssidbm }
        } catch (e: SecurityException) {
            Log.w("AirPulse", "Fine Location permission not granted for real Wi-Fi scanning.")
            emptyList()
        } catch (e: Exception) {
            Log.e("AirPulse", "Failed to retrieve real Wi-Fi devices", e)
            emptyList()
        }
    }

    private fun getChannelFromFrequency(frequency: Int): Int {
        return when {
            frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
            frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
            else -> 0
        }
    }

    private fun getSecurityString(capabilities: String): String {
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("WPA") -> "WPA"
            else -> "Open"
        }
    }

    // Get real Bluetooth bonded devices or simple scans
    fun getRealBluetoothDevices(): List<NetworkDevice> {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
            val adapter = manager.adapter ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()

            val pairedDevices = adapter.bondedDevices ?: return emptyList()
            pairedDevices.map { device ->
                // Paired devices RSSI is not directly scan-level in API unless scanning, we use -55 as default
                val deviceName = if (device.name.isNullOrEmpty()) "Bluetooth Device" else device.name
                val deviceClassString = getBluetoothClassString(device.bluetoothClass?.majorDeviceClass ?: 0)
                NetworkDevice(
                    ssidOrName = deviceName,
                    macAddress = device.address ?: "00:00:00:00:00:00",
                    rssidbm = -55, // default since it's paired
                    type = DeviceType.BLUETOOTH,
                    frequencyOrClass = deviceClassString,
                    channelOrRssiTrend = -1,
                    securityOrType = "Classic (Paired)",
                    distanceMeters = calculateDistance(-55, 2400.0),
                    isRealHardware = true,
                    signalHistory = listOf(-55)
                )
            }
        } catch (e: SecurityException) {
            Log.w("AirPulse", "Bluetooth permission not granted.")
            emptyList()
        } catch (e: Exception) {
            Log.e("AirPulse", "Failed to get real bluetooth devices", e)
            emptyList()
        }
    }

    private fun getBluetoothClassString(majorClass: Int): String {
        return when (majorClass) {
            1024 -> "Audio / Video"
            256 -> "Computer"
            512 -> "Phone"
            768 -> "Networking"
            1280 -> "Peripheral"
            else -> "Uncategorized"
        }
    }
}
