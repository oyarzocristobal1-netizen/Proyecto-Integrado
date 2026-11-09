package com.example.myapplication.bluetooth

data class BluetoothDeviceModel(
    val name: String,
    val address: String,
    val isPaired: Boolean = true
)

sealed class BluetoothConnectionState {
    object Disconnected : BluetoothConnectionState()
    object Connecting : BluetoothConnectionState()
    data class Connected(val deviceName: String) : BluetoothConnectionState()
    data class Error(val message: String) : BluetoothConnectionState()
}
