package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DeviceAlias
import com.example.data.DeviceType
import com.example.data.NetworkDevice
import com.example.data.ScanHistory
import com.example.data.SignalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AppScreen {
    SCANNER, SURROUND_WIFI, WIFI_PORTAL, BT_INTERNET, SETTINGS
}

class SignalViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = SignalRepository(application, database)

    // UI Configuration States
    val currentAppScreen = MutableStateFlow(AppScreen.SCANNER)
    val selectedTab = MutableStateFlow(DeviceType.WIFI)
    val isSimulationMode = MutableStateFlow(true) // Default to true in emulator environments
    val searchQuery = MutableStateFlow("")
    val showFavoritesOnly = MutableStateFlow(false)
    val minRssiThreshold = MutableStateFlow(-95) // Slider filter for weak signals
    val isScanning = MutableStateFlow(false)
    val appThemeSetting = MutableStateFlow("light") // "light", "dark", or "system"

    fun setAppTheme(theme: String) {
        appThemeSetting.value = theme
    }

    // Selected Device for detail analysis
    val selectedDevice = MutableStateFlow<NetworkDevice?>(null)

    // Handshake / Connection Test States
    val isTestingConnection = MutableStateFlow(false)
    val connectionProgress = MutableStateFlow(0f)
    val connectionStatusText = MutableStateFlow("")
    val pingMs = MutableStateFlow<Int?>(null)

    // Periodically fluctuate simulated signals when active
    private var fluctuationJob: Job? = null
    private var scanLogJob: Job? = null

    // Room Database flows
    val deviceAliases = repository.allAliasesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scanHistory = repository.recentHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Combined WiFi Devices Flow
    val wifiDevices: StateFlow<List<NetworkDevice>> = combine(
        isSimulationMode,
        repository.getSimulatedWifiFlow(),
        deviceAliases
    ) { simulation, simulatedList, aliases ->
        val rawList = if (simulation) {
            simulatedList
        } else {
            val real = repository.getRealWifiDevices()
            if (real.isEmpty()) simulatedList else real
        }

        mergeWithAliases(rawList, aliases)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Combined Bluetooth Devices Flow
    val bluetoothDevices: StateFlow<List<NetworkDevice>> = combine(
        isSimulationMode,
        repository.getSimulatedBluetoothFlow(),
        deviceAliases
    ) { simulation, simulatedList, aliases ->
        val rawList = if (simulation) {
            simulatedList
        } else {
            val real = repository.getRealBluetoothDevices()
            if (real.isEmpty()) simulatedList else real
        }

        mergeWithAliases(rawList, aliases)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        startPeriodicFluctuation()
        startPeriodicHistoryLogging()
    }

    private fun mergeWithAliases(rawList: List<NetworkDevice>, aliases: List<DeviceAlias>): List<NetworkDevice> {
        val aliasMap = aliases.associateBy { it.macAddress }
        return rawList.map { device ->
            val matchingAlias = aliasMap[device.macAddress]
            if (matchingAlias != null) {
                device.copy(
                    alias = matchingAlias.alias,
                    isFavorite = matchingAlias.isFavorite,
                    alertThresholdDbm = matchingAlias.alertThresholdDbm,
                    notes = matchingAlias.notes
                )
            } else {
                device
            }
        }.sortedWith(compareByDescending<NetworkDevice> { it.isFavorite }.thenBy { it.ssidOrName.lowercase() }.thenBy { it.macAddress })
    }

    private fun startPeriodicFluctuation() {
        fluctuationJob?.cancel()
        fluctuationJob = viewModelScope.launch {
            while (true) {
                if (isSimulationMode.value) {
                    repository.fluctuateSignals()
                    // Update details view if active to reflect real-time sparkline spikes
                    selectedDevice.value?.let { current ->
                        val currentList = if (current.type == DeviceType.WIFI) wifiDevices.value else bluetoothDevices.value
                        val updated = currentList.find { it.macAddress == current.macAddress }
                        if (updated != null) {
                            selectedDevice.value = updated
                        }
                    }
                }
                delay(3000) // update every 3s
            }
        }
    }

    // Periodically log favorite or strong signals in the history db to populate graph over time
    private fun startPeriodicHistoryLogging() {
        scanLogJob?.cancel()
        scanLogJob = viewModelScope.launch {
            while (true) {
                delay(12000) // Log state snapshots every 12 seconds
                val wifi = wifiDevices.value.take(2)
                val bluetooth = bluetoothDevices.value.take(2)
                
                (wifi + bluetooth).forEach { dev ->
                    val name = dev.alias ?: dev.ssidOrName
                    repository.logScanItem(name, dev.macAddress, dev.type, dev.rssidbm)
                }
            }
        }
    }

    // User actions
    fun toggleSimulationMode() {
        isSimulationMode.value = !isSimulationMode.value
    }

    fun selectTab(tab: DeviceType) {
        selectedTab.value = tab
    }

    fun selectScreen(screen: AppScreen) {
        currentAppScreen.value = screen
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateMinRssi(rssi: Int) {
        minRssiThreshold.value = rssi
    }

    fun toggleFavorites() {
        showFavoritesOnly.value = !showFavoritesOnly.value
    }

    fun selectDevice(device: NetworkDevice?) {
        selectedDevice.value = device
        // Reset connection testing state when opening/closing a device details sheet
        isTestingConnection.value = false
        connectionProgress.value = 0f
        connectionStatusText.value = ""
        pingMs.value = null
    }

    // Manual scan refresh button
    fun triggerManualScan() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            // Perform 1.5s visual scanner sweep
            for (i in 1..4) {
                if (isSimulationMode.value) {
                    repository.fluctuateSignals()
                }
                delay(350)
            }
            // Add a major scan record in history for top items
            val wifi = wifiDevices.value.take(3)
            val bluetooth = bluetoothDevices.value.take(3)
            (wifi + bluetooth).forEach { dev ->
                val name = dev.alias ?: dev.ssidOrName
                repository.logScanItem(name, dev.macAddress, dev.type, dev.rssidbm)
            }
            isScanning.value = false
        }
    }

    // Local DB nickname / alias handlers
    fun saveDeviceAlias(mac: String, name: String, notes: String, isFavorite: Boolean, alertThreshold: Int?) {
        viewModelScope.launch {
            val updatedAlias = DeviceAlias(
                macAddress = mac,
                alias = name.trim(),
                notes = notes.trim(),
                isFavorite = isFavorite,
                alertThresholdDbm = alertThreshold,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            repository.saveAlias(updatedAlias)
            
            // Log it in scan history as configured
            repository.logScanItem(name.ifEmpty { "Configured Device" }, mac, selectedTab.value, -50)

            // Update selected device info state directly to refresh sheet UI immediately
            selectedDevice.value?.let { current ->
                if (current.macAddress == mac) {
                    selectedDevice.value = current.copy(
                        alias = updatedAlias.alias,
                        isFavorite = updatedAlias.isFavorite,
                        alertThresholdDbm = updatedAlias.alertThresholdDbm,
                        notes = updatedAlias.notes
                    )
                }
            }
        }
    }

    fun deleteDeviceAlias(mac: String) {
        viewModelScope.launch {
            repository.removeAlias(mac)
            selectedDevice.value?.let { current ->
                if (current.macAddress == mac) {
                    selectedDevice.value = current.copy(
                        alias = null,
                        isFavorite = false,
                        alertThresholdDbm = null,
                        notes = ""
                    )
                }
            }
        }
    }

    fun clearHistoryLogs() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Real-time Handshake & Connection simulator
    fun startConnectionTest(device: NetworkDevice) {
        if (isTestingConnection.value) return
        viewModelScope.launch {
            isTestingConnection.value = true
            pingMs.value = null
            
            val states = if (device.type == DeviceType.WIFI) {
                listOf(
                    0.15f to "Sending Association Request...",
                    0.35f to "Waiting for WPA3 Authentication handshake...",
                    0.60f to "Requesting dynamic IPv4 address (DHCP)...",
                    0.80f to "Acquired IP: 192.168.1.145. Testing ping latencies...",
                    1.00f to "Connected Successfully!"
                )
            } else {
                listOf(
                    0.20f to "Initializing Bluetooth pairing protocol...",
                    0.50f to "Querying RFCOMM service channels...",
                    0.80f to "Authenticating secure link keys...",
                    1.00f to "Pairing Connected Successfully!"
                )
            }

            for ((progress, status) in states) {
                connectionProgress.value = progress
                connectionStatusText.value = status
                delay(800) // smooth step visual duration
            }

            // Generate ping latencies proportional to signal strength (stronger signal = lower ping)
            val signalFactor = (100 + device.rssidbm).coerceIn(5, 100)
            val basePing = (150 - signalFactor) / 2
            pingMs.value = basePing + Random.nextInt(4, 15)

            delay(1500)
            isTestingConnection.value = false
        }
    }

    // --- Wi-Fi Direct Connection Portal States ---
    val connectedWifiSsid = MutableStateFlow<String?>(null)
    val wifiConnectionProgress = MutableStateFlow(0f)
    val wifiConnectionStatus = MutableStateFlow("")
    val isConnectingToWifi = MutableStateFlow(false)

    fun connectToWifi(ssid: String, password: String? = null) {
        if (isConnectingToWifi.value) return
        viewModelScope.launch {
            isConnectingToWifi.value = true
            connectedWifiSsid.value = null
            
            val steps = if (password != null) {
                listOf(
                    0.15f to "Associating with secured network: $ssid...",
                    0.40f to "Validating Pre-Shared WPA/WPA2 Key credentials...",
                    0.65f to "Establishing secure handshake, negotiating DHCP lease...",
                    0.85f to "Verifying gateway routing and testing DNS packets...",
                    1.00f to "Success! Secure connection established."
                )
            } else {
                listOf(
                    0.20f to "Associating with open network SSID: $ssid...",
                    0.50f to "Negotiating DHCP lease and allocating local address...",
                    0.80f to "Verifying internet gate routing to Google DNS...",
                    1.00f to "Success! Access granted to public portal."
                )
            }
            
            for ((p, text) in steps) {
                wifiConnectionProgress.value = p
                wifiConnectionStatus.value = text
                delay(800)
            }
            
            connectedWifiSsid.value = ssid
            isConnectingToWifi.value = false
        }
    }

    fun disconnectWifi() {
        connectedWifiSsid.value = null
    }

    // --- Bluetooth Internet Tethering / PAN States ---
    val connectedBtInternetDevice = MutableStateFlow<String?>(null)
    val btInternetConnectionProgress = MutableStateFlow(0f)
    val btInternetConnectionStatus = MutableStateFlow("")
    val isConnectingToBtInternet = MutableStateFlow(false)

    fun connectToBtInternet(macAddress: String, deviceName: String) {
        if (isConnectingToBtInternet.value) return
        viewModelScope.launch {
            isConnectingToBtInternet.value = true
            connectedBtInternetDevice.value = null
            
            val steps = listOf(
                0.25f to "Searching for Bluetooth Tethering host on $deviceName...",
                0.55f to "Authenticating secure L2CAP link keys...",
                0.75f to "Binding network interface bt-pan0...",
                1.00f to "Active: Internet connection bridged over Bluetooth!"
            )
            
            for ((p, text) in steps) {
                btInternetConnectionProgress.value = p
                btInternetConnectionStatus.value = text
                delay(800)
            }
            
            connectedBtInternetDevice.value = deviceName
            isConnectingToBtInternet.value = false
        }
    }

    fun disconnectBtInternet() {
        connectedBtInternetDevice.value = null
    }

    fun getDeviceHistoryFlow(macAddress: String): Flow<List<ScanHistory>> {
        return repository.getDeviceHistoryFlow(macAddress)
    }

    override fun onCleared() {
        super.onCleared()
        fluctuationJob?.cancel()
        scanLogJob?.cancel()
    }
}
