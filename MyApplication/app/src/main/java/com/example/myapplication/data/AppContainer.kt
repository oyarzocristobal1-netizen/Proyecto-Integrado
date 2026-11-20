package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.sensor.BluetoothSensorSource
import com.example.myapplication.sensor.SensorSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Contenedor de dependencias (inyeccion manual, recomendada por Android para apps pequenas).
 * Cada repositorio se crea una vez y se entrega a los ViewModels por su fabrica; asi las pantallas
 * no crean objetos de Firebase ni de Bluetooth por su cuenta y se pueden probar por separado.
 */
class AppContainer(context: Context) {

    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance().also { configureOfflineCache(it) }
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val authRepository = AuthRepository(auth, firestore)
    val userAdminRepository = UserAdminRepository(auth, firestore)
    val recordsRepository = RecordsRepository(firestore)
    val devicesRepository = DevicesRepository(firestore)
    val deletionRepository = DeletionRepository(firestore)

    val connectivity = ConnectivityObserver(context.applicationContext)

    /** Caja de sensores real (Bluetooth). El modo DEMO no usa esta fuente. */
    val sensorSource: SensorSource = BluetoothSensorSource(context.applicationContext)

    private fun configureOfflineCache(db: FirebaseFirestore) {
        // Cache persistente de 100 MB: lecturas y escrituras siguen funcionando sin conexion
        // y se sincronizan solas al volver la red. Debe configurarse antes de cualquier consulta.
        runCatching {
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(OFFLINE_CACHE_BYTES)
                        .build()
                )
                .build()
        }
    }

    private companion object {
        const val OFFLINE_CACHE_BYTES = 100L * 1024 * 1024
    }
}
