package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.navigation.AppRoot
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.AuthViewModel
import com.example.myapplication.viewmodel.MeasureViewModel
import com.example.myapplication.viewmodel.RecordsViewModel
import com.example.myapplication.viewmodel.ReportsViewModel
import com.example.myapplication.viewmodel.UserManagementViewModel

/**
 * Unica actividad de la app. Los ViewModels se crean con sus fabricas (dependencias desde
 * MilkApp.container) y viven mientras la actividad exista, incluso al girar la pantalla.
 */
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels { AuthViewModel.Factory }
    private val recordsViewModel: RecordsViewModel by viewModels { RecordsViewModel.Factory }
    private val measureViewModel: MeasureViewModel by viewModels { MeasureViewModel.Factory }
    private val reportsViewModel: ReportsViewModel by viewModels { ReportsViewModel.Factory }
    private val userManagementViewModel: UserManagementViewModel by viewModels { UserManagementViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        authViewModel = authViewModel,
                        recordsViewModel = recordsViewModel,
                        measureViewModel = measureViewModel,
                        reportsViewModel = reportsViewModel,
                        userManagementViewModel = userManagementViewModel
                    )
                }
            }
        }
    }
}
