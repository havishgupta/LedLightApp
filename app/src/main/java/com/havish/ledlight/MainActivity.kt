package com.havish.ledlight

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.hypot
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private var visualizer: Visualizer? = null
    private var isVibing = mutableStateOf(false)
    private var connectionState = mutableStateOf("Disconnected")

    private var lastSendTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request all necessary permissions on start
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

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
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun MainScreen() {
        var r by remember { mutableFloatStateOf(255f) }
        var g by remember { mutableFloatStateOf(0f) }
        var b by remember { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "LED Light Vibe", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Status: ${connectionState.value}")

            Button(onClick = { scanAndConnect() }) {
                Text("Scan & Connect")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    isVibing.value = !isVibing.value
                    if (isVibing.value) startVisualizer() else stopVisualizer()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVibing.value) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isVibing.value) "Stop Music Vibe" else "Start Music Vibe")
            }
            Text("Tip: Bass = Red, Mids = Green, Treble = Blue", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(32.dp))
            Text("Manual RGB Control")

            Slider(value = r, onValueChange = { r = it; sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f)
            Text("Red: ${r.toInt()}")

            Slider(value = g, onValueChange = { g = it; sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f)
            Text("Green: ${g.toInt()}")

            Slider(value = b, onValueChange = { b = it; sendColor(r.toInt(), g.toInt(), b.toInt()) }, valueRange = 0f..255f)
            Text("Blue: ${b.toInt()}")

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { sendCommand(hexOf(0x7E, 0x04, 0x04, 0x01, 0xFF, 0xFF, 0xFF, 0x00, 0xEF)) }) {
                    Text("ON")
                }
                Button(onClick = { sendCommand(hexOf(0x7E, 0x04, 0x04, 0x00, 0xFF, 0xFF, 0xFF, 0x00, 0xEF)) }) {
                    Text("OFF")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { sendCommand(hexOf(0x7E, 0x05, 0x03, 0x86, 0x03, 0xFF, 0xFF, 0x00, 0xEF)) }) {
                Text("Rainbow Fade Effect")
            }
        }
    }

    private fun hexOf(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()

    @SuppressLint("MissingPermission")
    private fun scanAndConnect() {
        connectionState.value = "Scanning..."
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val name = device.name ?: ""
                    // Looking for common cheap BLE strip identifiers
                    if (name.contains("ELK", true) || name.contains("LED", true) || name.contains("Triones", true)) {
                        scanner?.stopScan(this)
                        connectionState.value = "Connecting to $name..."
                        bluetoothGatt = device.connectGatt(this@MainActivity, false, gattCallback)
                    }
                }
            }
        }
        scanner?.startScan(callback)

        // Stop scan after 10s if nothing is found
        CoroutineScope(Dispatchers.Main).launch {
            delay(10000)
            scanner?.stopScan(callback)
            if (connectionState.value == "Scanning...") {
                connectionState.value = "Scan Timeout. Try again."
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { connectionState.value = "Discovering services..." }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread { connectionState.value = "Disconnected" }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Try ELK-BLEDOM FFF0 / FFF3
                var service = gatt.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))
                var charac = service?.getCharacteristic(UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"))

                if (charac == null) {
                    // Try older ELK FFE5 / FFE9
                    service = gatt.getService(UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"))
                    charac = service?.getCharacteristic(UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb"))
                }

                if (charac != null) {
                    writeCharacteristic = charac
                    runOnUiThread { connectionState.value = "Connected!" }
                } else {
                    // Fallback to first writable characteristic
                    for (s in gatt.services) {
                        for (c in s.characteristics) {
                            if ((c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                                writeCharacteristic = c
                                runOnUiThread { connectionState.value = "Connected (Fallback)!" }
                                return
                            }
                        }
                    }
                    runOnUiThread { connectionState.value = "No writable characteristic found." }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(bytes: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val charac = writeCharacteristic ?: return
        charac.value = bytes
        try {
            gatt.writeCharacteristic(charac)
        } catch (e: Exception) {
            Log.e("BLE", "Write failed", e)
        }
    }

    private fun sendColor(r: Int, g: Int, b: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSendTime < 40) return // Max ~25 updates per second to avoid flooding BLE
        lastSendTime = now

        // ELK protocol: 7E 07 05 03 R G B 10 EF
        sendCommand(hexOf(0x7E, 0x07, 0x05, 0x03, r, g, b, 0x10, 0xEF))
    }

    @SuppressLint("MissingPermission")
    private fun startVisualizer() {
        try {
            // Session 0 captures the global output mix
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] // Highest resolution
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null) return

                        var bass = 0f
                        var mid = 0f
                        var treble = 0f

                        // Parse FFT array.
                        // fft[i] is real, fft[i+1] is imaginary.
                        val n = fft.size
                        for (i in 2 until n / 2 step 2) {
                            val real = fft[i].toFloat()
                            val imag = fft[i + 1].toFloat()
                            val mag = hypot(real, imag)

                            if (i < 10) bass += mag           // Low freq
                            else if (i < 50) mid += mag       // Mid freq
                            else treble += mag                // High freq
                        }

                        // Map magnitudes to RGB (values tuned for standard music profiles)
                        val r = (bass * 0.7f).coerceIn(0f, 255f).toInt()
                        val g = (mid * 1.0f).coerceIn(0f, 255f).toInt()
                        val b = (treble * 1.5f).coerceIn(0f, 255f).toInt()

                        sendColor(r, g, b)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("Audio", "Visualizer error", e)
            runOnUiThread { connectionState.value = "Audio Visualizer Failed. Check permissions." }
        }
    }

    private fun stopVisualizer() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        stopVisualizer()
        bluetoothGatt?.close()
    }
}
