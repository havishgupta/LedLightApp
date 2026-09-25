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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

class MainActivity : ComponentActivity() {
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val intent = Intent(this, BleAudioService::class.java)
            startService(intent)
        }

        launcher.launch(
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
    var showRgbSliders by remember { mutableStateOf(prefs.getBoolean("showRgbSliders", false)) }

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
            title = { Text("Select LED Strip") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(1)) {
                    items(devices.size) { i ->
                        @SuppressLint("MissingPermission")
                        val d = devices[i]
                        TextButton(onClick = { controller.connectToDevice(d); showDeviceDialog = false }) {
                            Text(d.name ?: d.address, fontSize = 16.sp)
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
                    Text("Customize UI", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showColorWheel, onCheckedChange = { showColorWheel = it; prefs.edit().putBoolean("showColorWheel", it).apply() })
                        Text("Show Color Wheel")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showSwatches, onCheckedChange = { showSwatches = it; prefs.edit().putBoolean("showSwatches", it).apply() })
                        Text("Show Quick Colors")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showModes, onCheckedChange = { showModes = it; prefs.edit().putBoolean("showModes", it).apply() })
                        Text("Show Effect Modes")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showRgbSliders, onCheckedChange = { showRgbSliders = it; prefs.edit().putBoolean("showRgbSliders", it).apply() })
                        Text("Show Manual RGB Sliders")
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showSettings = false }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LedLight Controller", fontWeight = FontWeight.SemiBold) },
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
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            
            // Connection Card
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isConn = connState.contains("Connected")
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (isConn) Color(0xFF38d996) else Color(0xFFFF5C7A)))
                        Spacer(Modifier.width(12.dp))
                        Text(connState, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(onClick = { controller.autoConnect() }) {
                        Text("Connect")
                    }
                }
            }

            // Power & Vibe
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { controller.toggleVibe() },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isVibing) Color(0xFFFF5C7A) else MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isVibing) "Stop Vibe" else "Start Vibe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Card(modifier = Modifier.weight(1f).height(64.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { controller.setPower(true) }.background(Color(0xFF38d996).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text("ON", color = Color(0xFF38d996), fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { controller.setPower(false) }.background(Color(0xFFFF5C7A).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text("OFF", color = Color(0xFFFF5C7A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Color Wheel
            if (showColorWheel) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Color Wheel", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Start))
                        Spacer(Modifier.height(16.dp))
                        ColorWheel(modifier = Modifier.fillMaxWidth(0.8f)) { rC, gC, bC ->
                            r = rC.toFloat()
                            g = gC.toFloat()
                            b = bC.toFloat()
                            controller.sendColor(rC, gC, bC)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Brightness", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Start))
                        Slider(value = brightness, onValueChange = { brightness = it; controller.setBrightness(it.toInt()) }, valueRange = 1f..100f)
                    }
                }
            }
            
            if (showRgbSliders) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Manual RGB", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(8.dp))
                        Slider(value = r, onValueChange = { r = it; controller.sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red))
                        Slider(value = g, onValueChange = { g = it; controller.sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green))
                        Slider(value = b, onValueChange = { b = it; controller.sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue))
                    }
                }
            }

            // Swatches
            if (showSwatches) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Quick Colors", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(16.dp))
                        LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(110.dp), userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(Constants.SWATCHES.size) { i ->
                                val (colorInt, _) = Constants.SWATCHES[i]
                                Box(
                                    modifier = Modifier.aspectRatio(1f).clip(CircleShape).background(Color(colorInt))
                                        .clickable { 
                                            r = android.graphics.Color.red(colorInt).toFloat()
                                            g = android.graphics.Color.green(colorInt).toFloat()
                                            b = android.graphics.Color.blue(colorInt).toFloat()
                                            controller.sendColor(r.toInt(), g.toInt(), b.toInt())
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Modes
            if (showModes) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Effect Speed", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Slider(value = speed, onValueChange = { speed = it }, valueRange = 1f..100f)
                        
                        Spacer(Modifier.height(8.dp))
                        Text("Built-in Effects", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        val modeEntries = Constants.ELK_MODES.entries.toList()
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(380.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(modeEntries.size) { i ->
                                val (name, id) = modeEntries[i]
                                Button(
                                    onClick = { controller.setMode(id, speed.toInt()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(name, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ColorWheel(modifier: Modifier = Modifier, onColorSelected: (Int, Int, Int) -> Unit) {
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset -> handleColorWheelTouch(offset, size.width.toFloat(), onColorSelected) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> handleColorWheelTouch(change.position, size.width.toFloat(), onColorSelected) }
            }
    ) {
        val colors = listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
        drawCircle(brush = Brush.sweepGradient(colors, center = center), radius = size.minDimension / 2)
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = size.minDimension / 2), radius = size.minDimension / 2)
    }
}

fun handleColorWheelTouch(offset: Offset, size: Float, onColorSelected: (Int, Int, Int) -> Unit) {
    val cx = size / 2
    val cy = size / 2
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    val radius = size / 2
    if (dist <= radius) {
        var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        val saturation = (dist / radius).coerceIn(0f, 1f)
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(angle, saturation, 1f))
        onColorSelected(
            android.graphics.Color.red(colorInt),
            android.graphics.Color.green(colorInt),
            android.graphics.Color.blue(colorInt)
        )
    }
}
