package com.example.myapplication.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.PasswordRules
import com.example.myapplication.data.ManagedUser
import com.example.myapplication.model.Role
import com.example.myapplication.ui.components.ConfirmDialog
import com.example.myapplication.model.UserSession
import com.example.myapplication.viewmodel.UserManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel,
    session: UserSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ManagedUser?>(null) }
    var toggling by remember { mutableStateOf<ManagedUser?>(null) }
    var deleting by remember { mutableStateOf<ManagedUser?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Gestión de usuarios", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.clearFormError()
                    showCreate = true
                },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Nuevo trabajador") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Text(
                    text = "Crea las cuentas del encargado y de los lecheros, edita su nombre o rol, y desactívalas o actívalas. " +
                        "Lo normal es desactivar: una cuenta desactivada no puede ingresar y sus mediciones se conservan. Eliminar es el último recurso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.loading && state.users.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(state.users, key = { it.uid }) { user ->
                UserRow(
                    user = user,
                    isSelf = user.uid == session.uid,
                    busy = state.saving,
                    onEdit = {
                        viewModel.clearFormError()
                        editing = user
                    },
                    onToggleActive = { toggling = user },
                    canDelete = session.role.canDeleteRecords && user.uid != session.uid,
                    onDelete = { deleting = user }
                )
            }
        }
    }

    if (showCreate) {
        CreateUserDialog(
            saving = state.saving,
            error = state.formError,
            onDismiss = { showCreate = false },
            onCreate = { name, email, password, role ->
                viewModel.createUser(name, email, password, role) { showCreate = false }
            }
        )
    }

    deleting?.let { user ->
        ConfirmDialog(
            title = "¿Eliminar la cuenta?",
            text = "Se elimina el perfil de ${user.name.ifBlank { user.email }}: ya no podrá usar la app. Sus mediciones " +
                "se conservan con su nombre. Su cuenta de acceso sigue existiendo en Firebase Authentication (se " +
                "borra desde la consola). Queda anotado en la bitácora. Lo normal es Desactivar.",
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = {
                deleting = null
                viewModel.deleteUser(user, session) {}
            },
            onDismiss = { deleting = null }
        )
    }

    toggling?.let { user ->
        val role = user.role
        ConfirmDialog(
            title = if (user.active) "¿Desactivar la cuenta?" else "¿Activar la cuenta?",
            text = if (user.active) {
                "${user.name.ifBlank { user.email }} no podrá ingresar a la app, pero sus mediciones y registros se conservan. " +
                    "Puedes activarla de nuevo cuando quieras."
            } else {
                "${user.name.ifBlank { user.email }} podrá volver a ingresar con su correo y contraseña."
            },
            confirmLabel = if (user.active) "Desactivar" else "Activar",
            destructive = user.active,
            onConfirm = {
                toggling = null
                if (role != null) {
                    viewModel.updateUser(user, user.name, role, !user.active) {}
                }
            },
            onDismiss = { toggling = null }
        )
    }

    editing?.let { user ->
        EditUserDialog(
            user = user,
            isSelf = user.uid == session.uid,
            saving = state.saving,
            error = state.formError,
            onDismiss = { editing = null },
            onSave = { name, role, active ->
                viewModel.updateUser(user, name, role, active) { editing = null }
            },
            onSendReset = { viewModel.sendPasswordReset(user) }
        )
    }
}

@Composable
private fun UserRow(
    user: ManagedUser,
    isSelf: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
        Row(
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSelf) "${user.name.ifBlank { user.email }} (tú)" else user.name.ifBlank { user.email },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusChip(
                    text = user.role?.label?.uppercase() ?: "SIN ROL",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                )
                StatusChip(
                    text = if (user.active) "ACTIVO" else "INACTIVO",
                    container = if (user.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    content = if (user.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = onEdit, enabled = !busy) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Editar")
            }
            // Una cuenta no puede desactivarse a si misma (las reglas tambien lo impiden).
            if (!isSelf && user.role != null) {
                if (user.active) {
                    TextButton(onClick = onToggleActive, enabled = !busy) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Desactivar", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = onToggleActive, enabled = !busy) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Activar")
                    }
                }
            }
            if (canDelete) {
                TextButton(onClick = onDelete, enabled = !busy) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        }
    }
}

@Composable
private fun StatusChip(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun RoleSelector(selected: Role, enabled: Boolean, onSelect: (Role) -> Unit) {
    Column {
        Text("Rol", style = MaterialTheme.typography.labelLarge)
        Role.entries.forEach { role ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = role == selected,
                        enabled = enabled,
                        role = SemanticsRole.RadioButton,
                        onClick = { onSelect(role) }
                    )
                    .padding(vertical = 2.dp)
            ) {
                RadioButton(selected = role == selected, onClick = null, enabled = enabled)
                Spacer(modifier = Modifier.width(8.dp))
                Text(role.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (name: String, email: String, password: String, role: Role) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.LECHERO) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Nuevo trabajador") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña inicial") },
                    supportingText = { Text("${PasswordRules.HINT} Entrégasela al trabajador.") },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                RoleSelector(selected = role, enabled = !saving, onSelect = { role = it })
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, email, password, role) }, enabled = !saving) {
                Text(if (saving) "Creando…" else "Crear cuenta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}

@Composable
private fun EditUserDialog(
    user: ManagedUser,
    isSelf: Boolean,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, role: Role, active: Boolean) -> Unit,
    onSendReset: () -> Unit
) {
    var name by rememberSaveable(user.uid) { mutableStateOf(user.name) }
    var role by rememberSaveable(user.uid) { mutableStateOf(user.role ?: Role.LECHERO) }
    var active by rememberSaveable(user.uid) { mutableStateOf(user.active) }
    var confirmDeactivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Editar trabajador") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                // El administrador no puede quitarse su propio rol ni desactivarse (evita quedar sin acceso).
                RoleSelector(selected = role, enabled = !saving && !isSelf, onSelect = { role = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cuenta activa", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it }, enabled = !saving && !isSelf)
                }
                if (isSelf) {
                    Text(
                        "No puedes cambiar tu propio rol ni desactivar tu cuenta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onSendReset, enabled = !saving) {
                    Text("Enviar correo para restablecer contraseña")
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Desactivar una cuenta le impide ingresar: se pide confirmacion.
                    if (user.active && !active) confirmDeactivate = true else onSave(name, role, active)
                },
                enabled = !saving
            ) {
                Text(if (saving) "Guardando…" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )

    if (confirmDeactivate) {
        ConfirmDialog(
            title = "¿Desactivar la cuenta?",
            text = "${user.name.ifBlank { user.email }} no podrá ingresar a la app. " +
                "Sus mediciones se conservan y puedes reactivar la cuenta cuando quieras.",
            confirmLabel = "Desactivar",
            destructive = true,
            onConfirm = {
                confirmDeactivate = false
                onSave(name, role, active)
            },
            onDismiss = { confirmDeactivate = false }
        )
    }
}
