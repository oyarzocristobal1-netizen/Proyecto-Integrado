package com.example.myapplication.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.bluetooth.BluetoothDeviceModel
import com.example.myapplication.model.MeasurementValidator
import com.example.myapplication.model.MilkQualityReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Caja de sensores por Bluetooth clasico (perfil serie SPP, como HC-05 o el BluetoothSerial del ESP32).
 * Lee lineas de texto y las interpreta con [SensorFrameParser]; nunca inventa valores.
 *
 * PENDIENTE de validar con la caja real: conexion, formato de las tramas y calibracion.
 */
class BluetoothSensorSource(private val context: Context) : SensorSource {

    /** UUID estandar del perfil serie (SPP). */
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val adapter: BluetoothAdapter? by lazy {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }

    @Volatile
    private var socket: BluetoothSocket? = null

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _latestReading = MutableStateFlow<MilkQualityReading?>(null)
    override val latestReading: StateFlow<MilkQualityReading?> = _latestReading.asStateFlow()

    private val _rejectedFrames = MutableStateFlow(0)
    override val rejectedFrames: StateFlow<Int> = _rejectedFrames.asStateFlow()

    private val _lastRejectReason = MutableStateFlow<String?>(null)
    override val lastRejectReason: StateFlow<String?> = _lastRejectReason.asStateFlow()

    override fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // el permiso se verifica con hasPermission()
    override fun pairedDevices(): List<BluetoothDeviceModel> {
        val a = adapter ?: return emptyList()
        if (!hasPermission()) return emptyList()
        return try {
            a.bondedDevices.orEmpty().map { device ->
                BluetoothDeviceModel(
                    name = device.name ?: "Dispositivo sin nombre",
                    address = device.address,
                    isPaired = true
                )
            }.sortedBy { it.name }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission") // el permiso se verifica con hasPermission()
    override suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        val a = adapter
        when {
            a == null -> {
                _connectionState.value = BluetoothConnectionState.Error("Este teléfono no tiene Bluetooth.")
                return@withContext
            }
            !hasPermission() -> {
                _connectionState.value = BluetoothConnectionState.Error("Falta el permiso de Bluetooth. Acéptalo para conectar la caja.")
                return@withContext
            }
            !a.isEnabled -> {
                _connectionState.value = BluetoothConnectionState.Error("Bluetooth está apagado. Actívalo y vuelve a intentar.")
                return@withContext
            }
        }
        val btAdapter = a!!

        closeSocket()
        _connectionState.value = BluetoothConnectionState.Connecting
        _latestReading.value = null
        _rejectedFrames.value = 0
        _lastRejectReason.value = null

        val btSocket = try {
            val device = btAdapter.getRemoteDevice(address)
            btAdapter.cancelDiscovery()
            device.createRfcommSocketToServiceRecord(sppUuid).also { it.connect() }
        } catch (e: Exception) {
            _connectionState.value = BluetoothConnectionState.Error(
                "No se pudo conectar con la caja. Revisa que esté encendida y emparejada."
            )
            closeSocket()
            return@withContext
        }

        socket = btSocket
        val name = runCatching { btSocket.remoteDevice.name }.getOrNull() ?: address
        _connectionState.value = BluetoothConnectionState.Connected(name)
        readLoop(btSocket)
    }

    private fun readLoop(btSocket: BluetoothSocket) {
        val parser = SensorFrameParser()
        try {
            val reader = BufferedReader(InputStreamReader(btSocket.inputStream, Charsets.US_ASCII))
            while (socket === btSocket) {
                val line = reader.readLine() ?: break
                when (val result = parser.parse(line)) {
                    is FrameResult.Reading -> {
                        val check = MeasurementValidator.validate(result.reading, System.currentTimeMillis())
                        if (check.isValid) {
                            _latestReading.value = result.reading
                        } else {
                            reject(check.firstError ?: "Lectura no válida.")
                        }
                    }
                    is FrameResult.Invalid -> reject(result.reason)
                    FrameResult.Partial, FrameResult.Ignored -> Unit
                }
            }
            if (socket === btSocket) {
                _connectionState.value = BluetoothConnectionState.Error("La caja cerró la conexión.")
            }
        } catch (e: Exception) {
            if (socket === btSocket) {
                _connectionState.value = BluetoothConnectionState.Error("Se perdió la conexión con la caja.")
            }
        } finally {
            if (socket === btSocket) closeSocket()
        }
    }

    private fun reject(reason: String) {
        _rejectedFrames.value = _rejectedFrames.value + 1
        _lastRejectReason.value = reason
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeSocket()
        _connectionState.value = BluetoothConnectionState.Disconnected
    }

    private fun closeSocket() {
        val s = socket
        socket = null
        runCatching { s?.close() }
    }
}
