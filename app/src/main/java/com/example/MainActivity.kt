package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DeviceType
import com.example.data.NetworkDevice
import com.example.data.ScanHistory
import com.example.ui.SignalViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AirPulseApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AirPulseApp(
    modifier: Modifier = Modifier,
    viewModel: SignalViewModel = viewModel()
) {
    val context = LocalContext.current

    // State collections
    val wifiList by viewModel.wifiDevices.collectAsStateWithLifecycle()
    val btList by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isSimulation by viewModel.isSimulationMode.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    val minRssi by viewModel.minRssiThreshold.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val historyLogs by viewModel.scanHistory.collectAsStateWithLifecycle()

    // Permission check state
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasLocationPermission) {
            // Disable simulation automatically to let user test live scans if permission is granted
            viewModel.isSimulationMode.value = false
        }
    }

    // Filtered lists based on search query, favorites toggle, and RSSI threshold slider
    val filteredWifi = wifiList.filter { device ->
        val matchesSearch = (device.alias ?: device.ssidOrName).contains(query, ignoreCase = true) ||
                device.macAddress.contains(query, ignoreCase = true)
        val matchesFavorite = !favoritesOnly || device.isFavorite
        val matchesRssi = device.rssidbm >= minRssi
        matchesSearch && matchesFavorite && matchesRssi
    }

    val filteredBt = btList.filter { device ->
        val matchesSearch = (device.alias ?: device.ssidOrName).contains(query, ignoreCase = true) ||
                device.macAddress.contains(query, ignoreCase = true)
        val matchesFavorite = !favoritesOnly || device.isFavorite
        val matchesRssi = device.rssidbm >= minRssi
        matchesSearch && matchesFavorite && matchesRssi
    }

    Column(
        modifier = modifier
            .background(SlateDarkBackground)
    ) {
        // --- 1. HEADER BRAND ROW ---
        HeaderRow(
            isSimulation = isSimulation,
            onToggleSimulation = { viewModel.toggleSimulationMode() }
        )

        // --- 2. LIVE RADAR SCREEN ---
        RadarBanner(
            isScanning = isScanning,
            totalWifi = wifiList.size,
            totalBt = btList.size,
            onTriggerScan = { viewModel.triggerManualScan() }
        )

        // --- 3. FILTERING CONTROLS CARD ---
        FiltersCard(
            query = query,
            onQueryChanged = { viewModel.updateSearchQuery(it) },
            minRssi = minRssi,
            onMinRssiChanged = { viewModel.updateMinRssi(it) },
            favoritesOnly = favoritesOnly,
            onToggleFavorites = { viewModel.toggleFavorites() }
        )

        // --- 4. CATEGORY SELECTION TABS ---
        TabRow(
            selectedTabIndex = when (selectedTab) {
                DeviceType.WIFI -> 0
                DeviceType.BLUETOOTH -> 1
            },
            containerColor = SlateDarkCard,
            contentColor = SleekTextDark,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[when (selectedTab) {
                        DeviceType.WIFI -> 0
                        DeviceType.BLUETOOTH -> 1
                    }]),
                    color = Purple80
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == DeviceType.WIFI,
                onClick = { viewModel.selectTab(DeviceType.WIFI) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WifiIcon(color = if (selectedTab == DeviceType.WIFI) Purple80 else SleekTextMedium, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wi-Fi Networks", fontWeight = FontWeight.Bold, color = if (selectedTab == DeviceType.WIFI) Purple80 else SleekTextMedium)
                    }
                },
                modifier = Modifier.testTag("wifi_tab")
            )
            Tab(
                selected = selectedTab == DeviceType.BLUETOOTH,
                onClick = { viewModel.selectTab(DeviceType.BLUETOOTH) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BluetoothIcon(color = if (selectedTab == DeviceType.BLUETOOTH) Purple80 else SleekTextMedium, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bluetooth", fontWeight = FontWeight.Bold, color = if (selectedTab == DeviceType.BLUETOOTH) Purple80 else SleekTextMedium)
                    }
                },
                modifier = Modifier.testTag("bluetooth_tab")
            )
        }

        // --- 5. PERMISSION NOTICE ALERT ---
        if (!hasLocationPermission && !isSimulation) {
            PermissionNoticeBar {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        // --- 6. REAL-TIME DATA LISTS ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                DeviceType.WIFI -> {
                    DeviceListSection(
                        devices = filteredWifi,
                        onDeviceClick = { viewModel.selectDevice(it) },
                        emptyMessage = "No Wi-Fi signals match criteria. Clear search or check RSSI filter slider.",
                        deviceType = DeviceType.WIFI
                    )
                }
                DeviceType.BLUETOOTH -> {
                    DeviceListSection(
                        devices = filteredBt,
                        onDeviceClick = { viewModel.selectDevice(it) },
                        emptyMessage = "No Bluetooth signals match criteria. Ensure Bluetooth is enabled.",
                        deviceType = DeviceType.BLUETOOTH
                    )
                }
            }

            // Quick Diagnostic summary float banner
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = SlateDarkCard.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isSimulation) Purple80 else SignalExcellent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSimulation) "Simulation Mode Active" else "Hardware Scans Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SleekTextMedium
                    )
                }
            }

            // Floating Scan Sweep Trigger
            FloatingActionButton(
                onClick = { viewModel.triggerManualScan() },
                containerColor = Purple80,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("scan_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan Signals",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // --- 7. HISTORIC LOGS ACCESS ---
        RecentLogsPanel(
            logs = historyLogs,
            onClearLogs = { viewModel.clearHistoryLogs() }
        )
    }

    // --- DEVICE DETAIL BOTTOM SHEET / DIALOG ---
    if (selectedDevice != null) {
        DeviceDetailDialog(
            device = selectedDevice!!,
            isTesting = viewModel.isTestingConnection.collectAsStateWithLifecycle().value,
            testProgress = viewModel.connectionProgress.collectAsStateWithLifecycle().value,
            testStatus = viewModel.connectionStatusText.collectAsStateWithLifecycle().value,
            ping = viewModel.pingMs.collectAsStateWithLifecycle().value,
            deviceHistory = viewModel.getDeviceHistoryFlow(selectedDevice!!.macAddress)
                .collectAsStateWithLifecycle(initialValue = emptyList<ScanHistory>()).value,
            onDismiss = { viewModel.selectDevice(null) },
            onSaveAlias = { name, notes, fav, threshold ->
                viewModel.saveDeviceAlias(selectedDevice!!.macAddress, name, notes, fav, threshold)
            },
            onDeleteAlias = {
                viewModel.deleteDeviceAlias(selectedDevice!!.macAddress)
            },
            onStartTest = { viewModel.startConnectionTest(selectedDevice!!) }
        )
    }
}

@Composable
fun HeaderRow(
    isSimulation: Boolean,
    onToggleSimulation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Radar Logo",
                    tint = Purple80,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AirPulse",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = SleekTextDark,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Text(
                text = "Wireless Telemetry & Monitor",
                fontSize = 11.sp,
                color = SleekTextMedium,
                letterSpacing = 0.5.sp
            )
        }

        // Simulation toggle badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSimulation) Color(0xFFEADDFF) else Color(0xFFE8DEF8))
                .border(1.dp, if (isSimulation) Color(0xFFD0BCFF) else SlateBorder, RoundedCornerShape(12.dp))
                .clickable { onToggleSimulation() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isSimulation) Color(0xFFB3261E) else Purple80, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isSimulation) "DEMO" else "REAL SCAN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSimulation) Color(0xFF21005D) else Color(0xFF1D192B)
            )
        }
    }
}

@Composable
fun RadarBanner(
    isScanning: Boolean,
    totalWifi: Int,
    totalBt: Int,
    onTriggerScan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(SlateDarkCard)
            .border(width = 1.dp, color = SlateBorder)
            .clickable { onTriggerScan() }
    ) {
        // Render Canvas sweeping radar
        RadarSweep(
            isScanning = isScanning,
            modifier = Modifier.fillMaxSize()
        )

        // Text summary layer overlay
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(16.dp)
        ) {
            Text(
                text = if (isScanning) "SCANNING AIRWAVES..." else "RADAR STANDBY",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isScanning) SignalExcellent else SleekTextMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WifiIcon(
                    color = SignalExcellent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$totalWifi networks detected",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SleekTextDark
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BluetoothIcon(
                    color = Purple80,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$totalBt bluetooth beacons active",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SleekTextDark
                )
            }
        }

        // Tap instructions right corner
        Text(
            text = "TAP RADAR TO FORCE SCAN",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = SleekTextMedium.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        )
    }
}

@Composable
fun RadarSweep(isScanning: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.8f, size.height * 0.5f) // Right-biased center for layout balance
        val maxRadius = min(size.width * 0.4f, size.height * 0.9f)

        // Grid circles
        drawCircle(color = Color(0x1F6750A4), radius = maxRadius * 0.33f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = Color(0x1F6750A4), radius = maxRadius * 0.66f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = Color(0x1F6750A4), radius = maxRadius, center = center, style = Stroke(width = 1.5.dp.toPx()))

        // Axes crosshairs
        drawLine(
            color = Color(0x1F6750A4),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0x1F6750A4),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.dp.toPx()
        )

        // Sweeping beam
        val beamRad = Math.toRadians(angle.toDouble())
        val beamX = (center.x + maxRadius * Math.cos(beamRad)).toFloat()
        val beamY = (center.y + maxRadius * Math.sin(beamRad)).toFloat()

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(SignalExcellent.copy(alpha = 0.5f), Color.Transparent),
                start = center,
                end = Offset(beamX, beamY)
            ),
            start = center,
            end = Offset(beamX, beamY),
            strokeWidth = 3.dp.toPx()
        )

        // Pulsing radar ripple
        if (isScanning) {
            drawCircle(
                color = SignalExcellent.copy(alpha = 0.6f - (0.6f * pulseRadius)),
                radius = maxRadius * pulseRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Draw decorative targets
        drawCircle(color = SignalExcellent, radius = 4.dp.toPx(), center = Offset(center.x - maxRadius * 0.5f, center.y - maxRadius * 0.3f))
        drawCircle(color = Purple80, radius = 3.dp.toPx(), center = Offset(center.x + maxRadius * 0.7f, center.y + maxRadius * 0.2f))
        drawCircle(color = Color(0xFFE8DEF8), radius = 3.dp.toPx(), center = Offset(center.x - maxRadius * 0.2f, center.y + maxRadius * 0.6f))
    }
}

@Composable
fun FiltersCard(
    query: String,
    onQueryChanged: (String) -> Unit,
    minRssi: Int,
    onMinRssiChanged: (Int) -> Unit,
    favoritesOnly: Boolean,
    onToggleFavorites: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateDarkCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = { Text("Search by name, MAC, or alias...", color = SleekTextMedium, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SleekTextMedium) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SleekTextMedium)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SleekTextDark,
                    unfocusedTextColor = SleekTextDark,
                    focusedBorderColor = Purple80,
                    unfocusedBorderColor = SlateBorder
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // RSSI threshold slider and favorites chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "RSSI Cutoff Threshold",
                            fontSize = 11.sp,
                            color = SleekTextMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (minRssi <= -95) "Show All" else "$minRssi dBm",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (minRssi > -65) SignalExcellent else SignalWeak
                        )
                    }
                    Slider(
                        value = minRssi.toFloat(),
                        onValueChange = { onMinRssiChanged(it.toInt()) },
                        valueRange = -100f..-35f,
                        colors = SliderDefaults.colors(
                            thumbColor = Purple80,
                            activeTrackColor = Purple80,
                            inactiveTrackColor = SlateBorder
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Favorite filter chip
                FilterChip(
                    selected = favoritesOnly,
                    onClick = onToggleFavorites,
                    label = { Text("Favorites", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (favoritesOnly) Purple80 else SleekTextMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SleekSecondaryContainer,
                        selectedLabelColor = SleekTextDark,
                        selectedLeadingIconColor = Purple80,
                        containerColor = Color.Transparent,
                        labelColor = SleekTextMedium,
                        disabledContainerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (favoritesOnly) Purple80 else SlateBorder),
                    modifier = Modifier.testTag("favorites_chip")
                )
            }
        }
    }
}

@Composable
fun PermissionNoticeBar(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        color = SleekHighlightCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SleekHighlightBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location Access Needed",
                tint = Purple80,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Location Access Required",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SleekHighlightText
                )
                Text(
                    text = "Android requires Fine Location permission to scan physical wireless hardware networks.",
                    fontSize = 11.sp,
                    color = SleekHighlightText.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Allow", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeviceListSection(
    devices: List<NetworkDevice>,
    onDeviceClick: (NetworkDevice) -> Unit,
    emptyMessage: String,
    deviceType: DeviceType
) {
    if (devices.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (deviceType == DeviceType.WIFI) {
                WifiIcon(color = SlateBorder, modifier = Modifier.size(44.dp))
            } else {
                BluetoothIcon(color = SlateBorder, modifier = Modifier.size(44.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Devices Discovered",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SleekTextDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = emptyMessage,
                fontSize = 12.sp,
                color = SleekTextMedium,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices, key = { it.macAddress }) { device ->
                DeviceCardItem(
                    device = device,
                    onClick = { onDeviceClick(device) }
                )
            }
        }
    }
}

@Composable
fun DeviceCardItem(
    device: NetworkDevice,
    onClick: () -> Unit
) {
    // Dynamic color coding for different signal states
    val signalColor = when {
        device.rssidbm >= -55 -> SignalExcellent
        device.rssidbm >= -68 -> SignalGood
        device.rssidbm >= -82 -> SignalFair
        else -> SignalWeak
    }

    val isHighlighted = device.isFavorite
    val cardBgColor = if (isHighlighted) SleekHighlightCard else SlateDarkCard
    val cardBorderColor = if (isHighlighted) SleekHighlightBorder else SlateBorder
    val mainTextColor = if (isHighlighted) SleekHighlightText else SleekTextDark
    val subTextColor = if (isHighlighted) SleekHighlightText.copy(alpha = 0.8f) else SleekTextMedium

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("device_card_${device.macAddress}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal Strength badge
            val badgeBgColor = if (isHighlighted) Purple80 else SleekSecondaryContainer
            val badgeTextColor = if (isHighlighted) Color.White else SleekHighlightText
            val badgeBorderColor = if (isHighlighted) SleekHighlightBorder else SlateBorder
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(badgeBgColor)
                    .border(1.dp, badgeBorderColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${device.rssidbm}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = badgeTextColor
                    )
                    Text(
                        text = "dBm",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle: Device Names & Specs
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = SleekHighlightText,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = device.alias ?: device.ssidOrName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = mainTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // If customized friendly alias is active, show the original SSID as secondary tag
                if (device.alias != null) {
                    Text(
                        text = "orig: ${device.ssidOrName}",
                        fontSize = 11.sp,
                        color = subTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = device.macAddress,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = subTextColor.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Detail specs badge chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chipBg = if (isHighlighted) Color.White.copy(alpha = 0.5f) else Color(0xFFF1ECF4)
                    val chipText = if (isHighlighted) SleekHighlightText else SleekTextMedium

                    Surface(
                        color = chipBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = device.frequencyOrClass,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = chipText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (device.channelOrRssiTrend > 0) {
                        Surface(
                            color = chipBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Ch ${device.channelOrRssiTrend}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = chipText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        color = chipBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = device.securityOrType,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = chipText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right side: Sparkline History and Estimated Distance
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(54.dp)
            ) {
                // Estimated physical distance based on signal log formula
                Text(
                    text = "~${device.distanceMeters}m",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = mainTextColor,
                    fontFamily = FontFamily.Monospace
                )

                // Sparkline
                SparklineChart(
                    history = device.signalHistory,
                    color = if (isHighlighted) SleekHighlightText else signalColor,
                    modifier = Modifier
                        .width(55.dp)
                        .height(20.dp)
                )

                Text(
                    text = "Signal drift",
                    fontSize = 8.sp,
                    color = subTextColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SparklineChart(
    history: List<Int>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (history.size < 2) {
        // Draw straight standby line
        Canvas(modifier = modifier) {
            drawLine(
                color = color.copy(alpha = 0.3f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx()
            )
        }
        return
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path()

        val minSignal = -95f
        val maxSignal = -35f
        val range = maxSignal - minSignal

        val stepX = width / (history.size - 1)

        history.forEachIndexed { index, dbm ->
            val normalizedY = ((dbm.toFloat() - minSignal) / range).coerceIn(0f, 1f)
            // Invert Y to map Canvas coordinates (top = 0)
            val y = height - (normalizedY * height)
            val x = index * stepX

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.8.dp.toPx(),
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun DeviceDetailDialog(
    device: NetworkDevice,
    isTesting: Boolean,
    testProgress: Float,
    testStatus: String,
    ping: Int?,
    deviceHistory: List<ScanHistory>,
    onDismiss: () -> Unit,
    onSaveAlias: (name: String, notes: String, fav: Boolean, threshold: Int?) -> Unit,
    onDeleteAlias: () -> Unit,
    onStartTest: () -> Unit
) {
    var friendlyName by remember { mutableStateOf(device.alias ?: "") }
    var noteInput by remember { mutableStateOf(device.notes) }
    var isFav by remember { mutableStateOf(device.isFavorite) }
    var customThreshold by remember { mutableStateOf(device.alertThresholdDbm ?: -80) }
    var useAlertThreshold by remember { mutableStateOf(device.alertThresholdDbm != null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkCard,
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .wrapContentHeight()
            ) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (device.type == DeviceType.WIFI) "Wi-Fi Device Specs" else "Bluetooth Device Specs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple80,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = device.ssidOrName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = SleekTextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SleekTextMedium)
                    }
                }

                Divider(color = SlateBorder, modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable configs inside Dialog
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    // Friendly Config Nicknaming Block
                    Text(
                        text = "Rename / Custom Configuration",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekTextMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = friendlyName,
                        onValueChange = { friendlyName = it },
                        label = { Text("Friendly Nickname (Alias)", color = SleekTextMedium) },
                        placeholder = { Text("e.g. My Workspace Router", color = SleekTextMedium.copy(alpha = 0.6f)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("friendly_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SleekTextDark,
                            unfocusedTextColor = SleekTextDark,
                            focusedBorderColor = Purple80,
                            unfocusedBorderColor = SlateBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Custom Notes", color = SleekTextMedium) },
                        placeholder = { Text("e.g. Located behind printer on 2nd floor", color = SleekTextMedium.copy(alpha = 0.6f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(65.dp)
                            .testTag("notes_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SleekTextDark,
                            unfocusedTextColor = SleekTextDark,
                            focusedBorderColor = Purple80,
                            unfocusedBorderColor = SlateBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Alert threshold & Fav triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = isFav,
                                onCheckedChange = { isFav = it },
                                colors = CheckboxDefaults.colors(checkedColor = Purple80)
                            )
                            Text("Mark as Favorite", color = SleekTextDark, fontSize = 13.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Checkbox(
                                checked = useAlertThreshold,
                                onCheckedChange = { useAlertThreshold = it },
                                colors = CheckboxDefaults.colors(checkedColor = Purple80)
                            )
                            Text("Alert if below", color = SleekTextDark, fontSize = 13.sp)
                        }
                    }

                    if (useAlertThreshold) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = customThreshold.toFloat(),
                                onValueChange = { customThreshold = it.toInt() },
                                valueRange = -100f..-40f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Purple80,
                                    activeTrackColor = Purple80,
                                    inactiveTrackColor = SlateBorder
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$customThreshold dBm",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SignalWeak,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons for saving config
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (device.alias != null) {
                            TextButton(
                                onClick = {
                                    onDeleteAlias()
                                    friendlyName = ""
                                    noteInput = ""
                                    isFav = false
                                    useAlertThreshold = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3261E))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Alias")
                            }
                        }

                        Button(
                            onClick = {
                                onSaveAlias(
                                    friendlyName,
                                    noteInput,
                                    isFav,
                                    if (useAlertThreshold) customThreshold else null
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Config", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = SlateBorder, modifier = Modifier.padding(vertical = 12.dp))

                    // Handshake Connection test panel
                    Text(
                        text = "Active Handshake Diagnostics",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekTextMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (!isTesting && ping == null) {
                        Button(
                            onClick = onStartTest,
                            modifier = Modifier.fillMaxWidth().testTag("start_test_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1ECF4)),
                            border = BorderStroke(1.dp, Purple80.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Purple80)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Launch Connection & Ping Test", color = Purple80, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF7F2FA))
                                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            if (isTesting) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        progress = testProgress,
                                        modifier = Modifier.size(20.dp),
                                        color = Purple80
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(testStatus, fontSize = 12.sp, color = SleekTextDark)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = testProgress,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Purple80,
                                    trackColor = SlateBorder
                                )
                            }

                            if (ping != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalExcellent, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Diagnostics Completed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SleekTextDark)
                                    }

                                    Surface(
                                        color = if (ping < 50) SignalExcellent.copy(alpha = 0.15f) else SignalFair.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, if (ping < 50) SignalExcellent else SignalFair),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "PING: ${ping}ms",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (ping < 50) SignalExcellent else SignalFair,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Estimated packet transits: 0% loss. Handshake protocols valid.",
                                    fontSize = 11.sp,
                                    color = SleekTextMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = onStartTest,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Re-Test Connection", color = Purple80, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Historical signal logs chart for this device specifically
                    if (deviceHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Signal strength trend (last ${deviceHistory.size} logs)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekTextMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val signalColor = when {
                            device.rssidbm >= -55 -> SignalExcellent
                            device.rssidbm >= -68 -> SignalGood
                            device.rssidbm >= -82 -> SignalFair
                            else -> SignalWeak
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                SparklineChart(
                                    history = deviceHistory.map { it.signalStrength }.reversed(),
                                    color = signalColor,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentLogsPanel(
    logs: List<ScanHistory>,
    onClearLogs: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateDarkCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = Purple80,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Signal Log Database (${logs.size} entries)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekTextDark
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "Collapse" else "Expand",
                        fontSize = 11.sp,
                        color = SleekTextMedium
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = SleekTextMedium,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    Divider(color = SlateBorder)

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Database logs are empty. Scans will populate charts.",
                                color = SleekTextMedium,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("RECENT MONITOR SNAPSHOTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Purple80)
                            Text(
                                text = "CLEAR LOGS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB3261E),
                                modifier = Modifier
                                    .clickable { onClearLogs() }
                                    .testTag("clear_logs_button")
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            items(logs) { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.deviceName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SleekTextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${log.deviceType} • ${log.macAddress}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = SleekTextMedium
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        val signalColor = when {
                                            log.signalStrength >= -55 -> SignalExcellent
                                            log.signalStrength >= -68 -> SignalGood
                                            log.signalStrength >= -82 -> SignalFair
                                            else -> SignalWeak
                                        }

                                        Text(
                                            text = "${log.signalStrength} dBm",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = signalColor
                                        )

                                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                        Text(
                                            text = sdf.format(Date(log.timestamp)),
                                            fontSize = 9.sp,
                                            color = SleekTextMedium.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Divider(color = SlateBorder.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f.dp.toPx()
        
        // Draw 3 concentric arcs for Wi-Fi
        drawArc(
            color = color,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(0f, h * 0.1f),
            size = Size(w, h * 1.6f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.35f),
            size = Size(w * 0.56f, h * 1.1f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(w * 0.38f, h * 0.58f),
            size = Size(w * 0.24f, h * 0.6f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = color,
            radius = w * 0.08f,
            center = Offset(w * 0.5f, h * 0.88f)
        )
    }
}

@Composable
fun BluetoothIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f.dp.toPx()

        val path = Path().apply {
            // Main vertical spine
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.5f, h * 0.88f)
            
            // Upper and lower right wings
            moveTo(w * 0.28f, h * 0.32f)
            lineTo(w * 0.72f, h * 0.68f)
            lineTo(w * 0.5f, h * 0.88f)
            
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.72f, h * 0.32f)
            lineTo(w * 0.28f, h * 0.68f)
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = stroke,
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )
    }
}

