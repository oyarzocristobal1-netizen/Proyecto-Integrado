package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.AppContainer

/** Punto de inicio de la app: crea las dependencias compartidas una sola vez. */
class MilkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
