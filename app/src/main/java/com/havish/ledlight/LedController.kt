package com.havish.ledlight

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import kotlin.math.hypot

@SuppressLint("MissingPermission")
class LedController(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("led_prefs", Context.MODE_PRIVATE)
    
    val connectionState = MutableStateFlow("Disconnected")
    val isVibing = MutableStateFlow(false)
    val availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var visualizer: Visualizer? = null
    private var lastSendTime = 0L
    private var scanJob: Job? = null
    private val scannedDevices = mutableSetOf<BluetoothDevice>()
    
    fun getSavedMacs(): List<String> {
        val macs = prefs.getString("last_macs", "") ?: ""
        return macs.split(",").filter { it.isNotBlank() }
    }
    
    private fun saveMac(mac: String) {
        val macs = getSavedMacs().toMutableList()
        macs.remove(mac)
        macs.add(0, mac)
        val toSave = macs.take(3).joinToString(",")
        prefs.edit().putString("last_macs", toSave).apply()
    }
    
    fun autoConnect() {
        val macs = getSavedMacs()
        if (macs.isEmpty()) {
            startScan()
            return
        }
        
        connectionState.value = "Auto-connecting..."
        CoroutineScope(Dispatchers.IO).launch {
            for (mac in macs) {
                val device = bluetoothAdapter?.getRemoteDevice(mac)
                if (device != null) {
                    tryConnect(device)
                    delay(3000)
                    if (bluetoothGatt != null && writeCharacteristic != null) return@launch
                }
            }
            if (writeCharacteristic == null) {
                startScan()
            }
        }
    }
    
    fun startScan() {
        connectionState.value = "Scanning..."
        scannedDevices.clear()
        availableDevices.value = emptyList()
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val name = device.name ?: ""
                    if (name.contains("ELK", true) || name.contains("LED", true) || name.contains("Triones", true)) {
                        if (scannedDevices.add(device)) {
                            availableDevices.value = scannedDevices.toList()
                        }
                    }
                }
            }
        }
        
        scanner?.startScan(callback)
        scanJob = CoroutineScope(Dispatchers.Main).launch {
            delay(10000)
            scanner?.stopScan(callback)
            if (connectionState.value == "Scanning...") {
                connectionState.value = "Select device manually"
            }
        }
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        scanJob?.cancel()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(object : ScanCallback() {})
        tryConnect(device)
    }
    
    private fun tryConnect(device: BluetoothDevice) {
        connectionState.value = "Connecting to ${device.name ?: device.address}..."
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState.value = "Discovering services..."
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState.value = "Disconnected"
                writeCharacteristic = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                var charac = gatt.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))?.getCharacteristic(UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"))
                if (charac == null) {
                    charac = gatt.getService(UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"))?.getCharacteristic(UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb"))
                }
                
                if (charac != null) {
                    writeCharacteristic = charac
                    connectionState.value = "Connected"
                    saveMac(gatt.device.address)
                } else {
                    for (s in gatt.services) {
                        for (c in s.characteristics) {
                            if ((c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                                writeCharacteristic = c
                                connectionState.value = "Connected (Fallback)"
                                saveMac(gatt.device.address)
                                return
                            }
                        }
                    }
                    connectionState.value = "Failed: No write characteristic"
                }
            }
        }
    }
    
    fun sendCommand(bytes: ByteArray) {
        val charac = writeCharacteristic ?: return
        charac.value = bytes
        try { bluetoothGatt?.writeCharacteristic(charac) } catch (e: Exception) {}
    }
    
    fun sendColor(r: Int, g: Int, b: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSendTime < 40) return
        lastSendTime = now
        sendCommand(byteArrayOf(0x7E, 0x07, 0x05, 0x03, r.toByte(), g.toByte(), b.toByte(), 0x10, 0xEF.toByte()))
    }
    
    fun setPower(on: Boolean) {
        val b = if (on) 0x01.toByte() else 0x00.toByte()
        sendCommand(byteArrayOf(0x7E, 0x04, 0x04, b, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xEF.toByte()))
    }
    
    fun setBrightness(level: Int) {
        sendCommand(byteArrayOf(0x7E, 0x04, 0x01, level.toByte(), 0x01, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xEF.toByte()))
    }
    
    fun setMode(mode: Int, speed: Int) {
        sendCommand(byteArrayOf(0x7E, 0x05, 0x03, mode.toByte(), 0x03, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xEF.toByte()))
        CoroutineScope(Dispatchers.IO).launch {
            delay(100)
            sendCommand(byteArrayOf(0x7E, 0x04, 0x02, speed.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xEF.toByte()))
        }
    }
    
    fun toggleVibe() {
        if (isVibing.value) stopVibe() else startVibe()
    }
    
    fun startVibe() {
        if (isVibing.value) return
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, r: Int) {
                        if (fft == null) return
                        var maxMag = 0f
                        var domBin = 0
                        var totalMag = 0f
                        
                        val n = fft.size
                        for (i in 2 until n / 2 step 2) {
                            val real = fft[i].toFloat()
                            val imag = fft[i + 1].toFloat()
                            val mag = hypot(real, imag)
                            totalMag += mag
                            if (mag > maxMag) {
                                maxMag = mag
                                domBin = i / 2
                            }
                        }
                        
                        val cappedBin = domBin.coerceAtMost(100)
                        val hue = (cappedBin / 100f) * 360f
                        val brightness = (totalMag / 1500f).coerceIn(0.0f, 1.0f)
                        
                        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, brightness))
                        val rC = android.graphics.Color.red(color)
                        val gC = android.graphics.Color.green(color)
                        val bC = android.graphics.Color.blue(color)
                        sendColor(rC, gC, bC)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            isVibing.value = true
        } catch (e: Exception) {
            Log.e("LedController", "Failed to start visualizer", e)
        }
    }
    
    fun stopVibe() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        isVibing.value = false
    }
}
