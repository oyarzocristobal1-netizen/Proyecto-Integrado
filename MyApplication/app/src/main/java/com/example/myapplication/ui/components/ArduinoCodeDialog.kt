package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("DEPRECATION")
@Composable
fun ArduinoCodeDialog(
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    val recommendedArduinoCode = """
// ==========================================
// CÓDIGO OPTIMIZADO BLUETOOTH HC-05 / HC-06
// ==========================================
// Agregue comunicación Bluetooth con SoftwareSerial (Pines 10 RX, 11 TX)
#include <SoftwareSerial.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

SoftwareSerial BT(10, 11); // RX=10 (conectar a TX de HC-05), TX=11 (conectar a RX de HC-05)

#define PIN_DS18B20 2
#define PIN_PH A0
#define PIN_CE A1
#define PIN_TURBIDEZ A2
#define PIN_LED_TURBIDEZ 7

OneWire oneWire(PIN_DS18B20);
DallasTemperature sensorTemperatura(&oneWire);
LiquidCrystal_I2C lcd(0x27, 16, 2);

void setup() {
  Serial.begin(9600);
  BT.begin(9600); // Inicio comunicación Bluetooth
  sensorTemperatura.begin();
  pinMode(PIN_LED_TURBIDEZ, OUTPUT);
  lcd.init();
  lcd.backlight();
}

void loop() {
  // Mediciones
  sensorTemperatura.requestTemperatures();
  float temp = sensorTemperatura.getTempCByIndex(0);
  float ph = (analogRead(PIN_PH) * 14.0) / 1023.0;
  float ce = (analogRead(PIN_CE) * 10.0) / 1023.0;
  
  digitalWrite(PIN_LED_TURBIDEZ, HIGH);
  delay(100);
  long suma = 0;
  for(int i=0; i<10; i++) { suma += analogRead(PIN_TURBIDEZ); delay(10); }
  digitalWrite(PIN_LED_TURBIDEZ, LOW);
  float turb = map(suma/10.0, 0, 1023, 100, 0);

  // Enviado a Android en formato rápido DATA:Temp,pH,CE,Turb
  String dataString = "DATA:" + String(temp, 1) + "," + String(ph, 2) + "," + String(ce, 2) + "," + String(turb, 1);
  
  BT.println(dataString);     // Envia por Bluetooth
  Serial.println(dataString); // Envia por Monitor Serial

  delay(2500);
}
""".trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Código Arduino con Bluetooth")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Para enviar los datos directamente a esta App vía Bluetooth HC-05/HC-06 o Serial:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "C++ Arduino",
                                color = Color(0xFF00E676),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(recommendedArduinoCode))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copiar Código",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = recommendedArduinoCode,
                                color = Color(0xFFD4D4D4),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                clipboardManager.setText(AnnotatedString(recommendedArduinoCode))
                onDismiss()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copiar Código")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
