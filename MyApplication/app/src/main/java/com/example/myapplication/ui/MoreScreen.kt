package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.UserSession
import com.example.myapplication.ui.components.ArduinoCodeDialog
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.ui.components.ScreenTitle
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.viewmodel.RecordsViewModel

/** Opciones segun el rol (Tabla 31), estado de conexion y cierre de sesion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: RecordsViewModel,
    session: UserSession,
    onOpenLots: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenRanges: () -> Unit,
    onOpenUsers: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val role = session.role
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var showCode by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { ScreenTitle("Más opciones") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ProfileCard(session) }

            item {
                val status = AppTheme.status
                val tone = if (state.online) status.good else status.warning
                val pending = state.pendingSyncCount
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = tone.container),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(if (state.online) Icons.Outlined.Cloud else Icons.Outlined.CloudOff, contentDescription = null, tint = tone.content)
                        Column {
                            Text(
                                if (state.online) "Conectado a la nube" else "Sin conexión",
                                color = tone.content,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    pending > 0 -> "$pending medición(es) guardada(s) en el teléfono, por enviar."
                                    state.online -> "Todo lo registrado está sincronizado."
                                    else -> "Lo que registres se enviará al volver la red."
                                },
                                color = tone.content,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (role.canEditLots) {
                            MenuRow("Lotes", "Corregir estanque y observaciones", Icons.Outlined.Inventory2, onOpenLots)
                        }
                        if (role.canExportReports) {
                            MenuRow("Reportes", "PDF, Excel, Word y CSV", Icons.Outlined.Description, onOpenReports)
                        }
                        if (role.canEditQualityRanges) {
                            MenuRow("Rangos de calidad", "Límites de temperatura, pH, conductividad y turbidez", Icons.Outlined.Tune, onOpenRanges)
                        }
                        if (role.canManageUsers) {
                            MenuRow("Gestión de usuarios", "Crear cuentas, cambiar rol o desactivar", Icons.Outlined.ManageAccounts, onOpenUsers)
                        }
                        if (role.canCalibrateDevice) {
                            MenuRow("Código de referencia de la caja", "Ejemplo de firmware y formato de datos", Icons.Outlined.Code) { showCode = true }
                        }
                        if (!role.canEditLots && !role.canExportReports) {
                            Text(
                                "Tu rol (${role.label}) registra mediciones y recibe alertas. " +
                                    "Los lotes, reportes y la configuración los gestionan el encargado y el administrador.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        HorizontalDivider()
                        MenuRow(
                            title = "Cerrar sesión",
                            subtitle = null,
                            icon = Icons.AutoMirrored.Filled.Logout,
                            onClick = { confirmLogout = true },
                            danger = true
                        )
                    }
                }
            }

            item {
                Text(
                    "Proyecto Integrado TIHI43 · INACAP · Fundo San Carlos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmLogout) {
        val pending = state.pendingSyncCount
        ConfirmDialog(
            title = "¿Cerrar sesión?",
            text = if (pending > 0) {
                "Hay $pending medición(es) que aún no llegan al servidor. Si cierras sesión sin conexión, " +
                    "podrían no enviarse. Te recomendamos esperar a tener internet."
            } else {
                "Tendrás que ingresar de nuevo con tu correo y contraseña."
            },
            confirmLabel = "Cerrar sesión",
            destructive = true,
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false }
        )
    }

    if (showCode) {
        ArduinoCodeDialog(onDismiss = { showCode = false })
    }
}

@Composable
private fun ProfileCard(session: UserSession) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    session.name.trim().take(1).uppercase().ifEmpty { "?" },
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(session.role.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(session.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = color, fontWeight = FontWeight.SemiBold) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        trailingContent = if (danger) {
            null
        } else {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}
