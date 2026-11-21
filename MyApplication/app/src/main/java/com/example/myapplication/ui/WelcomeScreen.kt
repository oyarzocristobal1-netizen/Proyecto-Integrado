package com.example.myapplication.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

private object WelcomeColors {
    val Background = Color(0xFFF7FAF8)
    val GreenDark = Color(0xFF173F30)
    val Green = Color(0xFF1F4D3A)
    val CardFill = Color(0xFFE8F3EC)
    val Title = Color(0xFF1B2B24)
    val Muted = Color(0xFF5B6C64)
    val Faint = Color(0xFF9AA8A1)
}

/**
 * Pantalla de bienvenida: explica en pocas lineas que hace la app. Se muestra al abrir la app sin
 * sesion; "Ingresar" lleva al inicio de sesion (las cuentas las crea el administrador).
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomeColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // ------------------------------------------------------------ imagen y marca
        Box(modifier = Modifier.fillMaxWidth().height(330.dp)) {
            Image(
                painter = painterResource(R.drawable.welcome_farm),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.65f)
            )
            // Desvanece la foto hacia el fondo para que el titulo se lea.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, WelcomeColors.Background)))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.88f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(WelcomeColors.Green),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("MilkQuality", color = WelcomeColors.GreenDark, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Calidad de la Leche en tus manos", color = WelcomeColors.Muted, fontSize = 12.sp)
                }
            }
        }

        // ------------------------------------------------------------------- texto
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Monitorea y cuida la calidad de tu leche",
                color = WelcomeColors.Title,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Una aplicación para controlar, entender y mejorar la calidad de la leche en tu producción.",
                color = WelcomeColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureCard(
                    Icons.Outlined.Science, "Mediciones",
                    "Consulta los parámetros clave: pH, conductividad, temperatura y turbidez.",
                    Modifier.weight(1f).fillMaxHeight()
                )
                FeatureCard(
                    Icons.Outlined.Analytics, "Análisis",
                    "Conoce el estado de tu leche con interpretaciones claras.",
                    Modifier.weight(1f).fillMaxHeight()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureCard(
                    Icons.Outlined.Lightbulb, "Recomendaciones",
                    "Recibe consejos prácticos para solucionar problemas.",
                    Modifier.weight(1f).fillMaxHeight()
                )
                FeatureCard(
                    Icons.Outlined.History, "Historial y reportes",
                    "Revisa tus mediciones y observa su evolución en el tiempo.",
                    Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WelcomeColors.GreenDark, contentColor = Color.White)
            ) {
                Text("Ingresar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                text = "Fundo San Carlos · Versión de demostración",
                color = WelcomeColors.Faint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 16.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(WelcomeColors.CardFill)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = WelcomeColors.Green, modifier = Modifier.size(28.dp))
        Text(title, color = WelcomeColors.GreenDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text, color = WelcomeColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
