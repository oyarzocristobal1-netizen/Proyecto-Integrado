package com.example.myapplication.viewmodel

import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.bluetooth.BluetoothDeviceModel
import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.sensor.SensorSource
import kotlinx.coroutines.flow.MutableStateFlow

/** Caja de sensores falsa para probar la pantalla Medir sin hardware ni Bluetooth. */
class FakeSensorSource : SensorSource {
    override val connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    override val latestReading = MutableStateFlow<MilkQualityReading?>(null)
    override val rejectedFrames = MutableStateFlow(0)
    override val lastRejectReason = MutableStateFlow<String?>(null)

    var permission = true
    var connectedTo: String? = null
    var disconnectCalls = 0

    override fun hasPermission(): Boolean = permission

    override fun pairedDevices(): List<BluetoothDeviceModel> =
        if (permission) listOf(BluetoothDeviceModel(name = "Caja 01", address = "00:11:22:33:44:55", isPaired = true)) else emptyList()

    override suspend fun connect(address: String) {
        connectedTo = address
        connectionState.value = BluetoothConnectionState.Connected("Caja 01")
    }

    override suspend fun disconnect() {
        disconnectCalls++
        connectionState.value = BluetoothConnectionState.Disconnected
    }

    override fun close() {
        connectionState.value = BluetoothConnectionState.Disconnected
    }
}
