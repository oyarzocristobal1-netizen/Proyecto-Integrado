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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.AlertsScreen
import com.example.myapplication.ui.LoginScreen
import com.example.myapplication.ui.LotsScreen
import com.example.myapplication.ui.MilkQualityScreen
import com.example.myapplication.ui.RangesScreen
import com.example.myapplication.ui.ReportsScreen
import com.example.myapplication.ui.UserManagementScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.AuthUiState
import com.example.myapplication.viewmodel.AuthViewModel
import com.example.myapplication.viewmodel.MilkQualityViewModel
import com.example.myapplication.viewmodel.RecordsViewModel
import com.example.myapplication.viewmodel.ReportsViewModel
import com.example.myapplication.viewmodel.UserManagementViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MilkQualityViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val userManagementViewModel: UserManagementViewModel by viewModels()
    private val recordsViewModel: RecordsViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels()

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
                            val session = state.session
                            LaunchedEffect(session.uid, session.role) { recordsViewModel.start(session) }
                            val logout = {
                                recordsViewModel.stop()
                                authViewModel.logout()
                            }
                            // Navegacion simple entre la pantalla principal, lotes y gestion de usuarios
                            var screen by rememberSaveable(session.uid) { mutableStateOf(SCREEN_MAIN) }
                            if (screen != SCREEN_MAIN) {
                                BackHandler { screen = SCREEN_MAIN }
                            }
                            when {
                                screen == SCREEN_USERS && session.role.canManageUsers -> UserManagementScreen(
                                    viewModel = userManagementViewModel,
                                    session = session,
                                    onBack = { screen = SCREEN_MAIN }
                                )

                                screen == SCREEN_REPORTS && session.role.canExportReports -> {
                                    val records by recordsViewModel.state.collectAsState()
                                    ReportsScreen(
                                        viewModel = reportsViewModel,
                                        session = session,
                                        lots = records.lots,
                                        onBack = { screen = SCREEN_MAIN }
                                    )
                                }

                                screen == SCREEN_RANGES && session.role.canEditQualityRanges -> RangesScreen(
                                    viewModel = recordsViewModel,
                                    onBack = { screen = SCREEN_MAIN }
                                )

                                screen == SCREEN_ALERTS -> AlertsScreen(
                                    viewModel = recordsViewModel,
                                    session = session,
                                    onBack = { screen = SCREEN_MAIN }
                                )

                                screen == SCREEN_LOTS && session.role.canEditLots -> LotsScreen(
                                    viewModel = recordsViewModel,
                                    onBack = { screen = SCREEN_MAIN }
                                )

                                else -> MilkQualityScreen(
                                    viewModel = viewModel,
                                    recordsViewModel = recordsViewModel,
                                    session = session,
                                    onLogout = logout,
                                    onOpenUserManagement = { screen = SCREEN_USERS },
                                    onOpenLots = { screen = SCREEN_LOTS },
                                    onOpenAlerts = { screen = SCREEN_ALERTS },
                                    onOpenReports = { screen = SCREEN_REPORTS },
                                    onOpenRanges = { screen = SCREEN_RANGES }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SCREEN_MAIN = "principal"
private const val SCREEN_LOTS = "lotes"
private const val SCREEN_USERS = "usuarios"
private const val SCREEN_ALERTS = "alertas"
private const val SCREEN_REPORTS = "reportes"
private const val SCREEN_RANGES = "rangos"
