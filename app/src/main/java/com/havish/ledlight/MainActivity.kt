package com.havish.ledlight

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

class MainActivity : ComponentActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val intent = Intent(this, BleAudioService::class.java)
            startService(intent)
            BleAudioService.controller?.autoConnect()
        }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            )
        )

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val controller = BleAudioService.controller
                    if (controller != null) {
                        MainScreen(controller, prefs)
                    } else {
                        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(controller: LedController, prefs: android.content.SharedPreferences) {
    val connState by controller.connectionState.collectAsState()
    val isVibing by controller.isVibing.collectAsState()
    val devices by controller.availableDevices.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    var showColorWheel by remember { mutableStateOf(prefs.getBoolean("showColorWheel", true)) }
    var showSwatches by remember { mutableStateOf(prefs.getBoolean("showSwatches", true)) }
    var showModes by remember { mutableStateOf(prefs.getBoolean("showModes", true)) }

    var r by remember { mutableFloatStateOf(255f) }
    var g by remember { mutableFloatStateOf(0f) }
    var b by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(100f) }
    var speed by remember { mutableFloatStateOf(50f) }
    
    var showDeviceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(connState) {
        if (connState == "Select device manually") showDeviceDialog = true
    }

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("Select Device") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(1)) {
                    items(devices.size) { i ->
                        @SuppressLint("MissingPermission")
                        val d = devices[i]
                        TextButton(onClick = { controller.connectToDevice(d); showDeviceDialog = false }) {
                            Text(d.name ?: d.address)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDeviceDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Settings", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showColorWheel, onCheckedChange = { showColorWheel = it; prefs.edit().putBoolean("showColorWheel", it).apply() })
                        Text("Show RGB Sliders")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showSwatches, onCheckedChange = { showSwatches = it; prefs.edit().putBoolean("showSwatches", it).apply() })
                        Text("Show Quick Colors")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showModes, onCheckedChange = { showModes = it; prefs.edit().putBoolean("showModes", it).apply() })
                        Text("Show Effect Modes")
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showSettings = false }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LED Strip Controller", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (connState.contains("Connected")) Color(0xFF38d996) else Color(0xFFFF5C7A)))
                        Spacer(Modifier.width(8.dp))
                        Text(connState, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { controller.autoConnect() }, modifier = Modifier.weight(1f)) { Text("Auto Connect") }
                        Button(onClick = { controller.startScan(); showDeviceDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Scan") }
                    }
                }
            }

            // Power & Vibe
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { controller.setPower(true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38d996), contentColor = Color.Black)) { Text("Power ON") }
                        Button(onClick = { controller.setPower(false) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C7A), contentColor = Color.White)) { Text("Power OFF") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { controller.toggleVibe() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isVibing) Color(0xFFFF5C7A) else MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (isVibing) "Stop Music Vibe" else "Start Music Vibe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Colors
            if (showColorWheel || showSwatches) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        if (showColorWheel) {
                            Text("RGB Controls", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(Modifier.height(8.dp))
                            Slider(value = r, onValueChange = { r = it; controller.sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red))
                            Slider(value = g, onValueChange = { g = it; controller.sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green))
                            Slider(value = b, onValueChange = { b = it; controller.sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue))
                        }
                        
                        Text("Brightness", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Slider(value = brightness, onValueChange = { brightness = it; controller.setBrightness(it.toInt()) }, valueRange = 1f..100f)
                        
                        if (showSwatches) {
                            Spacer(Modifier.height(16.dp))
                            Text("Quick Colors", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(Modifier.height(8.dp))
                            LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(100.dp), userScrollEnabled = false) {
                                items(Constants.SWATCHES.size) { i ->
                                    val (colorInt, _) = Constants.SWATCHES[i]
                                    Box(
                                        modifier = Modifier.padding(4.dp).aspectRatio(1f).clip(CircleShape).background(Color(colorInt))
                                            .clickable { 
                                                val c = android.graphics.Color.valueOf(colorInt)
                                                r = c.red() * 255f
                                                g = c.green() * 255f
                                                b = c.blue() * 255f
                                                controller.sendColor(r.toInt(), g.toInt(), b.toInt())
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Modes
            if (showModes) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Effect Speed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Slider(value = speed, onValueChange = { speed = it }, valueRange = 1f..100f)
                        
                        Text("Built-in Effects", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(8.dp))
                        val modeEntries = Constants.ELK_MODES.entries.toList()
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(300.dp)) {
                            items(modeEntries.size) { i ->
                                val (name, id) = modeEntries[i]
                                Button(
                                    onClick = { controller.setMode(id, speed.toInt()) },
                                    modifier = Modifier.padding(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(name, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}
