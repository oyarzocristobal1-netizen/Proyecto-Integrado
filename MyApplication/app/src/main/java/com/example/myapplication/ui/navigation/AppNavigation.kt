package com.example.myapplication.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.myapplication.ui.MeasurementReminderEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.model.UserSession
import com.example.myapplication.ui.AlertsScreen
import com.example.myapplication.ui.DevicesScreen
import com.example.myapplication.ui.DashboardScreen
import com.example.myapplication.ui.HistoryScreen
import com.example.myapplication.ui.LoginScreen
import com.example.myapplication.ui.LotsScreen
import com.example.myapplication.ui.MeasureScreen
import com.example.myapplication.ui.MoreScreen
import com.example.myapplication.ui.RangesScreen
import com.example.myapplication.ui.TanksScreen
import com.example.myapplication.ui.ReportsScreen
import com.example.myapplication.ui.UserManagementScreen
import com.example.myapplication.viewmodel.AuthUiState
import com.example.myapplication.viewmodel.AuthViewModel
import com.example.myapplication.viewmodel.DevicesViewModel
import com.example.myapplication.viewmodel.MeasureMode
import com.example.myapplication.viewmodel.MeasureViewModel
import com.example.myapplication.viewmodel.RecordsViewModel
import com.example.myapplication.viewmodel.ReportsViewModel
import com.example.myapplication.viewmodel.UserManagementViewModel

/** Rutas de la app. Las de la barra inferior y las secundarias (con flecha para volver). */
object Routes {
    const val PANEL = "panel"
    const val MEASURE = "medir"
    const val HISTORY = "historial"
    const val ALERTS = "alertas"
    const val MORE = "mas"
    const val LOTS = "lotes"
    const val REPORTS = "reportes"
    const val RANGES = "rangos"
    const val USERS = "usuarios"
    const val TANKS = "estanques"
    const val DEVICES = "dispositivos"
}

/** Pestañas de la barra inferior: las mismas para todos los roles (cada pantalla filtra por rol). */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    PANEL(Routes.PANEL, "Panel", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    MEASURE(Routes.MEASURE, "Medir", Icons.Outlined.Sensors, Icons.Filled.Sensors),
    HISTORY(Routes.HISTORY, "Historial", Icons.Outlined.History, Icons.Filled.History),
    ALERTS(Routes.ALERTS, "Alertas", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    MORE(Routes.MORE, "Más", Icons.Outlined.Menu, Icons.Filled.Menu)
}

/** Raiz: revisa la sesion y muestra el login o la app. */
@Composable
fun AppRoot(
    authViewModel: AuthViewModel,
    recordsViewModel: RecordsViewModel,
    measureViewModel: MeasureViewModel,
    reportsViewModel: ReportsViewModel,
    userManagementViewModel: UserManagementViewModel
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    when (val state = authState) {
        is AuthUiState.Checking -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            // Cada sesion tiene su propia navegacion (al cambiar de usuario se parte desde el Panel).
            key(session.uid) {
                MainScaffold(
                    session = session,
                    recordsViewModel = recordsViewModel,
                    measureViewModel = measureViewModel,
                    reportsViewModel = reportsViewModel,
                    userManagementViewModel = userManagementViewModel,
                    onLogout = {
                        measureViewModel.setMode(MeasureMode.DEMO) // cierra la conexion con la caja
                        recordsViewModel.stop()
                        authViewModel.logout()
                    }
                )
            }
        }
    }
}

/** Estructura de la app con sesion: barra inferior, mensajes y las pantallas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScaffold(
    session: UserSession,
    recordsViewModel: RecordsViewModel,
    measureViewModel: MeasureViewModel,
    reportsViewModel: ReportsViewModel,
    userManagementViewModel: UserManagementViewModel,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }
    val records by recordsViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val role = session.role

    // Avisos de "toca medir" para las mediciones propias (se programan aunque se cierre la app).
    MeasurementReminderEffect(state = records, session = session)

    // Un solo lugar para los mensajes de registros, lotes, alertas y rangos.
    LaunchedEffect(records.message) {
        val message = records.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = message.length > 70,
            duration = if (message.length > 70) SnackbarDuration.Long else SnackbarDuration.Short
        )
        recordsViewModel.messageShown()
    }

    Scaffold(
        // Cada pantalla maneja la barra de estado con su propia barra superior.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(dest.route) },
                            icon = {
                                val icon = if (selected) dest.selectedIcon else dest.icon
                                if (dest == TopLevelDestination.ALERTS && records.pendingAlerts.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text(records.pendingAlerts.size.coerceAtMost(99).toString()) } }) {
                                        Icon(icon, contentDescription = "${dest.label}: ${records.pendingAlerts.size} pendientes")
                                    }
                                } else {
                                    Icon(icon, contentDescription = null)
                                }
                            },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = if (showBottomBar) Modifier else Modifier.navigationBarsPadding()
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PANEL,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            composable(Routes.PANEL) {
                DashboardScreen(
                    viewModel = recordsViewModel,
                    session = session,
                    onMeasure = { navController.navigateTopLevel(Routes.MEASURE) },
                    onOpenHistory = { navController.navigateTopLevel(Routes.HISTORY) },
                    onOpenAlerts = { navController.navigateTopLevel(Routes.ALERTS) }
                )
            }
            composable(Routes.MEASURE) {
                MeasureScreen(
                    measureViewModel = measureViewModel,
                    recordsViewModel = recordsViewModel,
                    session = session
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    viewModel = recordsViewModel,
                    session = session,
                    onMeasure = { navController.navigateTopLevel(Routes.MEASURE) }
                )
            }
            composable(Routes.ALERTS) {
                AlertsScreen(viewModel = recordsViewModel, session = session)
            }
            composable(Routes.MORE) {
                MoreScreen(
                    viewModel = recordsViewModel,
                    session = session,
                    onOpenLots = { navController.navigate(Routes.LOTS) },
                    onOpenTanks = { navController.navigate(Routes.TANKS) },
                    onOpenDevices = { navController.navigate(Routes.DEVICES) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                    onOpenRanges = { navController.navigate(Routes.RANGES) },
                    onOpenUsers = { navController.navigate(Routes.USERS) },
                    onLogout = onLogout
                )
            }
            // Pantallas segun el rol (Tabla 31): si el rol no tiene permiso, la ruta no existe.
            if (role.canEditLots) {
                composable(Routes.LOTS) {
                    LotsScreen(
                        viewModel = recordsViewModel,
                        canDelete = role.canDeleteRecords,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            if (role.canManageTanks) {
                composable(Routes.TANKS) {
                    TanksScreen(
                        viewModel = recordsViewModel,
                        canDelete = role.canDeleteRecords,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            if (role.canCalibrateDevice) {
                composable(Routes.DEVICES) {
                    DevicesScreen(
                        viewModel = viewModel(factory = DevicesViewModel.Factory),
                        session = session,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            if (role.canExportReports) {
                composable(Routes.REPORTS) {
                    val recordsState by recordsViewModel.state.collectAsStateWithLifecycle()
                    ReportsScreen(
                        viewModel = reportsViewModel,
                        session = session,
                        lots = recordsState.lots,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            if (role.canEditQualityRanges) {
                composable(Routes.RANGES) {
                    RangesScreen(viewModel = recordsViewModel, onBack = { navController.popBackStack() })
                }
            }
            if (role.canManageUsers) {
                composable(Routes.USERS) {
                    UserManagementScreen(
                        viewModel = userManagementViewModel,
                        session = session,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/** Cambia de pestaña sin apilar pantallas y recordando el estado de cada una. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
