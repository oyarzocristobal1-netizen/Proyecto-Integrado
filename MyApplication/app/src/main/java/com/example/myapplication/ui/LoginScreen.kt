package com.example.myapplication.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.myapplication.data.MIN_PASSWORD_LENGTH
import com.example.myapplication.viewmodel.AuthUiState

/** Paleta del login (fija: no depende del tema del telefono). */
private object LoginColors {
    val GreenDark = Color(0xFF173F30)
    val Green = Color(0xFF1F4D3A)
    val GreenLight = Color(0xFF2F6B4F)
    val Gold = Color(0xFFE0B44C)
    val Card = Color.White
    val Title = Color(0xFF1B2B24)
    val Muted = Color(0xFF6B7C74)
    val FieldFill = Color(0xFFF5F7F6)
    val FieldBorder = Color(0xFFE2E8E5)
    val Placeholder = Color(0xFF9AA8A1)
    val Error = Color(0xFFB3261E)
}

@Composable
fun LoginScreen(
    state: AuthUiState.LoggedOut,
    onLogin: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    // Iconos de la barra de estado en blanco sobre el encabezado verde (se restauran al salir del login).
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LoginColors.Card)
    ) {
        // Fondo verde del encabezado con circulos decorativos
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.verticalGradient(listOf(LoginColors.GreenLight, LoginColors.Green, LoginColors.GreenDark))
                )
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width * 0.82f, size.height * 0.12f)
                listOf(0.18f, 0.32f, 0.48f, 0.66f).forEach { r ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = size.width * r,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.width * 0.35f,
                    center = Offset(size.width * 0.05f, size.height * 0.95f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // ------------------------------------------------------------ encabezado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 32.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(LoginColors.Gold)
                    )
                    Text(
                        text = "FUNDO SAN CARLOS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Calidad de Leche",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Portal de Control y Monitoreo",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Mide la leche de cada estanque, la analiza, avisa si hay un problema " +
                        "y deja registrada la decisión de venta de cada lote.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // -------------------------------------------------------------- tarjeta
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(LoginColors.Card)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Iniciar sesión",
                    color = LoginColors.Title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ingresa tus credenciales asignadas",
                    color = LoginColors.Muted,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                FieldLabel("Correo electrónico")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("nombre@fundosancarlos.cl", color = LoginColors.Placeholder) },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = LoginColors.Placeholder) },
                    singleLine = true,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = loginFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FieldLabel("Contraseña", modifier = Modifier.weight(1f))
                    Text(
                        text = "¿Olvidaste tu contraseña?",
                        color = LoginColors.Green,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !state.loading) { onResetPassword(email) }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Mínimo $MIN_PASSWORD_LENGTH caracteres", color = LoginColors.Placeholder) },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = LoginColors.Placeholder) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = LoginColors.Placeholder
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = loginFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onLogin(email, password) }),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = state.error, color = LoginColors.Error, fontSize = 14.sp)
                }
                if (state.info != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = state.info, color = LoginColors.Green, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !state.loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LoginColors.Green,
                        contentColor = Color.White,
                        disabledContainerColor = LoginColors.Green.copy(alpha = 0.6f),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = LoginColors.Green, spotColor = LoginColors.Green)
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Ingresar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LoginColors.Title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LoginColors.FieldFill,
    unfocusedContainerColor = LoginColors.FieldFill,
    disabledContainerColor = LoginColors.FieldFill,
    focusedBorderColor = LoginColors.Green,
    unfocusedBorderColor = LoginColors.FieldBorder,
    disabledBorderColor = LoginColors.FieldBorder,
    focusedTextColor = LoginColors.Title,
    unfocusedTextColor = LoginColors.Title,
    disabledTextColor = LoginColors.Muted,
    cursorColor = LoginColors.Green
)
