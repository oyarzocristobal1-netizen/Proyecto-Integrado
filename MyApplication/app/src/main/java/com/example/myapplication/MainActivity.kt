package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.LoginScreen
import com.example.myapplication.ui.MilkQualityScreen
import com.example.myapplication.ui.UserManagementScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.AuthUiState
import com.example.myapplication.viewmodel.AuthViewModel
import com.example.myapplication.viewmodel.MilkQualityViewModel
import com.example.myapplication.viewmodel.UserManagementViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MilkQualityViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val userManagementViewModel: UserManagementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by authViewModel.state.collectAsState()
                    when (val state = authState) {
                        is AuthUiState.Checking -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        is AuthUiState.LoggedOut -> LoginScreen(
                            state = state,
                            onLogin = authViewModel::login,
                            onResetPassword = authViewModel::resetPassword
                        )

                        is AuthUiState.LoggedIn -> {
                            // Navegacion simple: pantalla principal o gestion de usuarios (solo administrador)
                            var showUsers by rememberSaveable(state.session.uid) { mutableStateOf(false) }
                            if (showUsers && state.session.role.canManageUsers) {
                                BackHandler { showUsers = false }
                                UserManagementScreen(
                                    viewModel = userManagementViewModel,
                                    session = state.session,
                                    onBack = { showUsers = false }
                                )
                            } else {
                                MilkQualityScreen(
                                    viewModel = viewModel,
                                    session = state.session,
                                    onLogout = authViewModel::logout,
                                    onOpenUserManagement = { showUsers = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
