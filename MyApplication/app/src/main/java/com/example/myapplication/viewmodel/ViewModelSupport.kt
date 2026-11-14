package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.myapplication.MilkApp
import com.example.myapplication.data.AppContainer

/** Contenedor de dependencias de la app, para las fabricas de ViewModels. */
internal fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MilkApp).container
