package com.example.myapplication.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.myapplication.model.MilkQualityReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class BluetoothService(private val context: Context) {

    // Standard SPP UUID for Serial Bluetooth modules (HC-05, HC-06, etc.)
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private var socket: BluetoothSocket? = null

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _latestReading = MutableStateFlow<MilkQualityReading?>(null)
    val latestReading: StateFlow<MilkQualityReading?> = _latestReading.asStateFlow()

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceModel> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices.map { device ->
                BluetoothDeviceModel(
                    name = device.name ?: "Dispositivo sin nombre",
                    address = device.address,
                    isPaired = true
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String) = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth no está activado en el teléfono.")
            return@withContext
        }

        _connectionState.value = BluetoothConnectionState.Connecting

        try {
            disconnect()
            val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
            val btSocket = device.createRfcommSocketToServiceRecord(sppUuid)
            adapter.cancelDiscovery()
            btSocket.connect()

            socket = btSocket
            val deviceName = device.name ?: deviceAddress
            _connectionState.value = BluetoothConnectionState.Connected(deviceName)

            listenForData(btSocket)

        } catch (e: Exception) {
            _connectionState.value = BluetoothConnectionState.Error("Falló la conexión: ${e.localizedMessage ?: "Error desconocido"}")
            disconnect()
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        _connectionState.value = BluetoothConnectionState.Disconnected
    }

    private suspend fun listenForData(btSocket: BluetoothSocket) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(btSocket.inputStream))
        var currentTemp = 25.0f
        var currentPh = 6.7f
        var currentCond = 5.0f
        var currentTurb = 15.0f

        try {
            while (socket?.isConnected == true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // 1. Try parsing CSV / Key-Value format: "T:25.5,pH:6.7,CE:5.2,TURB:15.0" or "DATA:25.5,6.7,5.2,15.0"
                if (trimmed.contains(",") || trimmed.startsWith("DATA:")) {
                    val parsed = parseStructuredLine(trimmed)
                    if (parsed != null) {
                        _latestReading.value = parsed
                        continue
                    }
                }

                // 2. Parse multiline format from original Arduino code:
                // "Temperatura: 25.5 C"
                // "pH: 6.70"
                // "Conductividad: 5.20 mS/cm"
                // "Turbidez: 15.0 %"
                when {
                    trimmed.startsWith("Temperatura:", ignoreCase = true) -> {
                        extractFloat(trimmed)?.let { currentTemp = it }
                    }
                    trimmed.startsWith("pH:", ignoreCase = true) -> {
                        extractFloat(trimmed)?.let { currentPh = it }
                    }
                    trimmed.startsWith("Conductividad:", ignoreCase = true) || trimmed.startsWith("CE:", ignoreCase = true) -> {
                        extractFloat(trimmed)?.let { currentCond = it }
                    }
                    trimmed.startsWith("Turbidez:", ignoreCase = true) -> {
                        extractFloat(trimmed)?.let { currentTurb = it }
                        // Turbidez is usually the last line in the Arduino loop, emit reading update
                        _latestReading.value = MilkQualityReading(
                            temperature = currentTemp,
                            ph = currentPh,
                            conductivity = currentCond,
                            turbidity = currentTurb
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (_connectionState.value is BluetoothConnectionState.Connected) {
                _connectionState.value = BluetoothConnectionState.Error("Conexión interrumpida: ${e.localizedMessage}")
            }
        }
    }

    private fun parseStructuredLine(line: String): MilkQualityReading? {
        val raw = line.removePrefix("DATA:").trim()
        val parts = raw.split(",")
        if (parts.size >= 4) {
            // Check key:value vs raw numbers
            var temp = 0.0f
            var ph = 0.0f
            var cond = 0.0f
            var turb = 0.0f

            try {
                if (parts[0].contains(":")) {
                    for (part in parts) {
                        val kv = part.split(":")
                        if (kv.size == 2) {
                            val key = kv[0].trim().uppercase()
                            val valNum = kv[1].trim().toFloatOrNull() ?: 0.0f
                            when {
                                key.contains("T") -> temp = valNum
                                key.contains("PH") -> ph = valNum
                                key.contains("CE") || key.contains("COND") -> cond = valNum
                                key.contains("TURB") -> turb = valNum
                            }
                        }
                    }
                } else {
                    temp = parts[0].trim().toFloatOrNull() ?: 25.0f
                    ph = parts[1].trim().toFloatOrNull() ?: 6.7f
                    cond = parts[2].trim().toFloatOrNull() ?: 5.0f
                    turb = parts[3].trim().toFloatOrNull() ?: 15.0f
                }
                return MilkQualityReading(temp, ph, cond, turb)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractFloat(text: String): Float? {
        val regex = Regex("""[-+]?[0-9]*\.?[0-9]+""")
        val match = regex.find(text)
        return match?.value?.toFloatOrNull()
    }
}
