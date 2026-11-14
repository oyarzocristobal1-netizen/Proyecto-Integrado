# Arquitectura de la app Calidad de Leche

App Android (Kotlin + Jetpack Compose + Material 3) que mide la calidad de la leche del Fundo San Carlos
con una caja de sensores (temperatura, pH, conductividad y turbidez) y guarda los registros en Cloud Firestore.

> **Estado del hardware:** la caja de sensores aún no está disponible. La app funciona completa en **modo DEMO**
> (datos simulados y marcados como tales). La conexión con la caja está implementada pero **no validada**:
> ver [protocolo-caja.md](protocolo-caja.md) y la sección *Pendiente con la caja real* al final.

## 1. Capas

```
ui/            Pantallas Compose (sin lógica de negocio ni Firebase)
  navigation/  Raíz, barra inferior y rutas según el rol
  components/  Tarjetas, avisos, gráfico de tendencia, diálogos
  theme/       Colores de marca (claro y oscuro) y colores del semáforo
viewmodel/     Estado de cada pantalla (StateFlow) y acciones del usuario
data/          Firebase: autenticación, usuarios, lotes, mediciones, alertas, rangos, conectividad
sensor/        Fuente de lecturas: caja Bluetooth, lector de tramas y simulador DEMO
model/         Reglas puras (sin Android): evaluación, alertas, validaciones, estabilidad, panel
report/        Reportes PDF, Excel, Word y CSV
```

Regla de dependencias: `ui → viewmodel → data / sensor → model`. El paquete `model` no depende de Android ni de
Firebase, por eso casi toda la lógica se prueba con pruebas unitarias normales (ver [pruebas.md](pruebas.md)).

### Inyección de dependencias

Manual, como recomienda Android para apps pequeñas:

- `MilkApp` (la `Application`, registrada en el manifiesto) crea un `AppContainer` al iniciar.
- `AppContainer` crea una sola vez Firestore (con caché sin conexión), Auth, los repositorios,
  el observador de conectividad y la fuente de sensores.
- Cada ViewModel tiene una `Factory` que toma sus dependencias del contenedor. `MainActivity` crea los
  ViewModels con `by viewModels { XViewModel.Factory }`.

Así ninguna pantalla crea objetos de Firebase o Bluetooth, y en las pruebas se reemplazan por dobles
(por ejemplo `FakeSensorSource`).

## 2. Navegación

Navigation Compose con barra inferior (`ui/navigation/AppNavigation.kt`):

| Pestaña | Pantalla | Para qué |
|---|---|---|
| Panel | `DashboardScreen` | Mediciones de hoy, % conforme, alertas pendientes, última medición, tendencia de 7 días |
| Medir | `MeasureScreen` | Lectura en vivo (DEMO o caja), evaluación y registro en el lote activo |
| Historial | `HistoryScreen` | Mediciones guardadas, filtros por lote, origen y semáforo, paginado |
| Alertas | `AlertsScreen` | Alertas pendientes o todas; atender con confirmación |
| Más | `MoreScreen` | Perfil, estado de conexión, opciones según el rol y cerrar sesión |

Pantallas secundarias (con flecha para volver), **solo se registran si el rol tiene permiso** (Tabla 31):
Lotes (encargado y admin), Reportes (encargado y admin), Rangos de calidad (admin), Gestión de usuarios (admin).
La barra inferior guarda el estado de cada pestaña al cambiar entre ellas.

## 3. Flujo de una medición

```
Caja (Bluetooth) ─► SensorFrameParser ─► MeasurementValidator ─► latestReading
Simulador DEMO ──────────────────────────────────────────────► (lectura SIMULADA)
                                   │
                         MeasureViewModel
                 (estabilidad: 4 lecturas parecidas;
                  antigüedad: máximo 15 s)
                                   │  botón Registrar (solo con lectura estable y reciente)
                         RecordsViewModel.recordMeasurement
          valida lote, límites físicos, antigüedad, que no esté repetida y que el dispositivo exista
                                   │
                      RecordsRepository.recordMeasurement
           una sola escritura atómica (batch): medición + sus alertas
                                   │
            Firestore (caché local) ──► servidor (cuando hay red) ──► reglas validan formato y permisos
```

- **Nunca se inventan valores.** Si falta un dato o no es un número, la trama se descarta y se cuenta como
  "lectura descartada" (se muestra el motivo en la tarjeta de la caja). Antes, el código anterior completaba con
  valores por defecto y confundía `TURB` con `T`; ambos errores se corrigieron.
- **Estabilidad:** se exige que las últimas 4 lecturas varíen poco (temperatura ≤ 0,5 °C, pH ≤ 0,05,
  conductividad ≤ 0,15 mS/cm, turbidez ≤ 2 %). Evita registrar mientras el sensor se ajusta a la muestra.
- **Alertas en la misma escritura:** antes se creaban después de que el servidor confirmaba la medición y se
  podían perder si se cerraba la app. Ahora medición y alertas se guardan juntas (todo o nada); las reglas
  usan `existsAfter`/`getAfter` para validarlas en conjunto. El ID `idMedicion-tipo` evita duplicados.

## 4. Modo DEMO (datos simulados)

- Selector **Simulación | Caja de sensores** en la pestaña Medir. En DEMO hay 4 muestras de prueba
  (Normal, Ácida, Mastitis, Diluida) con ruido pequeño, una lectura cada 2,5 s, solo mientras la pantalla se ve.
- Toda lectura simulada lleva `origen = "simulado"` y se guarda con el dispositivo `simulador`.
  Las reglas de Firestore **impiden** guardar un dato simulado como si fuera del sensor (y al revés).
- En la app se distinguen con la etiqueta violeta **SIMULADA** (historial, alertas, panel, evaluación) y el aviso
  "MODO DEMO · Datos simulados". El panel avisa cuántas simuladas incluye y permite ver solo "Sensor".
- Los reportes **excluyen las simuladas por defecto**; si se incluyen, cada fila dice "Simulada" en la columna
  Origen y el resumen lo advierte.

## 5. Sin conexión, sincronización y errores

- Firestore usa **caché persistente de 100 MB**: lo registrado sin red queda en el teléfono y se envía solo al
  volver la conexión. La app lo indica con el aviso "Sin conexión" y la etiqueta **Sin sincronizar** en cada
  medición pendiente (metadato `hasPendingWrites`).
- `ConnectivityObserver` informa si hay internet. Sin red, editar un lote, atender una alerta o guardar rangos
  no bloquea la pantalla: el cambio queda en cola y, si el servidor lo rechaza después, se avisa.
- Los reportes leen **solo del servidor** (`Source.SERVER`) para no generar un reporte incompleto con la copia
  local; sin red muestran un mensaje claro.
- Todos los errores de Firebase pasan por `Throwable.toUserMessage()` (`data/FirebaseErrors.kt`): mensajes en
  español, sin detalles internos (permiso denegado, sesión expirada, falta índice, cuota, sin conexión…).
- Validaciones en la app **y** en las reglas: nombre de estanque (1–40 caracteres permitidos), observación
  (≤ 200), nombre (≤ 80), correo (≤ 120), rangos coherentes (mín < máx, dentro de límites físicos),
  límites físicos de los sensores (temperatura −5 a 80 °C, pH 0–14, conductividad 0–20 mS/cm, turbidez 0–100 %).

## 6. Modelo de datos (Firestore)

| Colección | Documento | Campos principales |
|---|---|---|
| `usuarios` | UID | `nombre`, `correo`, `rol` (admin/encargado/lechero), `activo` |
| `dispositivos` | `caja-01`, `simulador` | `nombre`, `ubicacion`, `estado` |
| `lotes` | `AAAAMMDD-estanque` | `estanque`, `fecha`, `creadoPor`, `nombreCreador`, `observacion` |
| `mediciones` | UUID | `idDispositivo`, `idLote`, `uidLechero`, `nombreLechero`, `fechaHora` (hora de la lectura), **`registradoEn`** (hora del servidor), **`origen`** (simulado/sensor), `sensores{temp,ph,cond,turb}`, `resultado{estado,titulo}` |
| `alertas` | `idMedicion-tipo` | `idMedicion`, `idLote`, `tipo`, `severidad`, `fecha`, `atendida`, `valor`, `mensaje`, `nombreLechero`, **`origen`** |
| `configuracion` | `rangos` | `tempMax`, `phMin`, `phMax`, `condMin`, `condMax`, `turbMin` |

Las mediciones son **inmutables** y nada se borra (trazabilidad). Reglas: `firebase/firestore.rules`;
índices: `firebase/firestore.indexes.json`.

## 7. Integración futura con la caja (preparada, no validada)

- La app depende de la interfaz `sensor/SensorSource` (estado de conexión, última lectura, lecturas
  descartadas, permiso, cajas emparejadas, conectar/desconectar). La implementación actual es
  `BluetoothSensorSource` (Bluetooth clásico SPP, como HC-05 o `BluetoothSerial` del ESP32).
- Si la caja final usa **BLE o Wi-Fi**, se crea otra clase que implemente `SensorSource` y se cambia en
  `AppContainer`; las pantallas no cambian.
- Permisos: solo `BLUETOOTH_CONNECT` (Android 12+). No se escanea ni se usa ubicación: se conecta a cajas
  ya emparejadas en los ajustes del teléfono.

## 8. Pendiente con la caja real

Nada de lo siguiente se ha probado con hardware; no se debe informar como validado:

1. Conexión Bluetooth con la caja (emparejar, conectar, reconectar, pérdida de señal).
2. Formato real de las tramas (el código de referencia usa `DATA:temp,ph,ce,turb` cada 2,5 s).
3. Calibración de cada sensor (pH con soluciones tampón 4,0 / 7,0; conductividad con solución patrón;
   turbidez contra muestras conocidas; temperatura contra termómetro de referencia).
4. Umbrales de estabilidad y de antigüedad con el ruido real de los sensores.
5. Rangos de calidad definitivos (`configuracion/rangos`) con muestras del fundo.
6. Si la caja será ESP32 (Bluetooth clásico o BLE) o Arduino + HC-05: ajustar `SensorSource` según corresponda.
