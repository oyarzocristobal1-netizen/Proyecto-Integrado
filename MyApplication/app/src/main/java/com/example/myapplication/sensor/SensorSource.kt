package com.example.myapplication.sensor

import com.example.myapplication.bluetooth.BluetoothConnectionState
import com.example.myapplication.bluetooth.BluetoothDeviceModel
import com.example.myapplication.model.MilkQualityReading
import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente de lecturas de la caja de sensores. La app depende de esta interfaz y no de Bluetooth
 * directamente, asi se puede cambiar el transporte (Bluetooth clasico, BLE, Wi-Fi) o probar con
 * una fuente falsa sin tocar las pantallas.
 *
 * Estado: implementada para Bluetooth clasico (SPP). PENDIENTE de validar con la caja real.
 */
interface SensorSource {
    val connectionState: StateFlow<BluetoothConnectionState>

    /** Ultima lectura valida recibida (null si aun no llega ninguna). */
    val latestReading: StateFlow<MilkQualityReading?>

    /** Tramas descartadas por estar incompletas, corruptas o fuera de los limites fisicos. */
    val rejectedFrames: StateFlow<Int>

    /** Motivo del ultimo descarte, para diagnosticar la caja. */
    val lastRejectReason: StateFlow<String?>

    /** true si la app tiene el permiso de Bluetooth necesario (Android 12+ lo pide en tiempo de ejecucion). */
    fun hasPermission(): Boolean

    fun pairedDevices(): List<BluetoothDeviceModel>

    suspend fun connect(address: String)

    suspend fun disconnect()

    /** Cierra la conexion de inmediato, sin esperar (al cerrar la app). */
    fun close()
}
