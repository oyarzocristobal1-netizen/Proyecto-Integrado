# Pruebas de la app Calidad de Leche

Todo lo que se puede validar **sin la caja de sensores** tiene pruebas automáticas. Lo que depende del
hardware está listado al final como pendiente.

## 1. Cómo ejecutarlas

**Pruebas unitarias (232)** — en la carpeta `MyApplication`:

```
.\gradlew test          (Windows)
./gradlew test          (macOS / Linux)
```

El informe queda en `MyApplication/app/build/reports/tests/testDebugUnitTest/index.html`.
En Android Studio también se pueden ejecutar con clic derecho sobre `app/src/test` → *Run Tests*.

**Pruebas de las reglas de Firestore (89: 50 anteriores + 28 del CRUD ampliado y las eliminaciones + 4 de liberar lotes para venta + 7 de nota y revisión)** — necesitan Node.js 18+ y Java 11+ (usan el emulador de Firestore,
no tocan la base real):

```
cd firebase/tests
npm install
npm test
```

## 2. Pruebas unitarias

| Clase | Pruebas | Qué valida |
|---|---|---|
| `model/QualityEvaluationTest` | 11 | Evaluación de la leche (excelente, aceptable, precaución, rechazo), uso de los rangos configurados, validación de rangos y semáforo verde/ámbar/rojo |
| `model/AlertRulesTest` | 7 | Cuándo se genera cada alerta (temperatura, pH, conductividad, turbidez), su severidad y que respeta los rangos configurados |
| `model/MeasurementValidatorTest` | 12 | Rechazo de valores no numéricos, infinitos, sensor desconectado (−127 °C), fuera de límites físicos, lecturas viejas, con hora futura o repetidas; nombre de estanque |
| `model/StabilityTrackerTest` | 5 | La lectura solo es estable con 4 muestras parecidas; se reinicia al cambiar de muestra o de origen |
| `model/DeletionRulesTest` | 7 | Solo el administrador elimina; alertas de una medición; tipos de alerta iguales a los que genera la app; grupos de 400 escrituras; texto de la bitácora dentro de los máximos; tipos iguales a los de las reglas |
| `model/CrudRulesTest` | 9 | Validaciones de los formularios de administración (motivo de anulación 3–200, nota de alerta ≤300, nombre de dispositivo, nota de calibración), ID del dispositivo, parámetros de calibración iguales a los que aceptan las reglas, permisos nuevos por rol |
| `model/RecordRulesTest` | 6 | ID de lote (fecha local + estanque sin tildes), dispositivo según el origen, origen desconocido = simulado |
| `model/DashboardStatsTest` | 5 | Indicadores del panel: mediciones de hoy, % conforme, corte a medianoche en hora de Chile, promedios diarios, conteo de simuladas |
| `sensor/SensorFrameParserTest` | 12 | Lector de tramas de la caja: formato `DATA:`, clave:valor, ciclo de 4 líneas, tramas incompletas o corruptas, coma decimal, `TURB` no se confunde con `T` |
| `sensor/DemoReadingGeneratorTest` | 5 | Las lecturas del modo DEMO salen marcadas como simuladas y dentro de los límites; la muestra Variada cambia en cada medición e incluye casos buenos y alterados |
| `report/ReportWritersTest` | 8 | CSV (BOM, punto y coma, coma decimal, neutralización de fórmulas), Excel y Word válidos, reporte vacío, caracteres especiales, resumen con advertencia de datos simulados |
| `viewmodel/MeasureViewModelTest` | 10 | Pantalla Medir con una caja falsa: sin conexión no hay lecturas inventadas, lecturas de la caja solo en modo caja, registrar solo con lectura estable y reciente, desconexión al volver a DEMO, simulación solo con la pantalla visible, permiso de Bluetooth |
| `viewmodel/RecordsUiStateTest` | 4 | Filtros del historial por origen y semáforo, contadores de pendientes de sincronizar y simuladas, y el filtro por origen solo aparece si hay datos del sensor y simulados |
| `model/analysis/MilkAnalyzerTest` | 43 | Análisis inteligente: 1 a 4 parámetros alterados, niveles leve/moderado/crítico, prioridad, lecturas inválidas y contradictorias, historial (repetición, tendencias, sin historial), configuración y rotulado DEMO (ver `docs/analisis.md`) |
| `model/analysis/LotAnalyzerTest` | 9 | Análisis por lote: orden por prioridad, no mezcla mediciones de otro lote, mediciones sin lote, mediciones atrasadas |
| `model/analysis/ForecastTest` | 23 | Proyección: datos insuficientes, conductividad que sube o baja, temperatura que enfría, pH, turbidez, cambios lentos, tiempo sugerido para remedir |
| `model/analysis/VerdictTest` | 11 | Veredicto (buena calidad, revisar, no usar hasta verificar), pasos, lectura dudosa, glosario, aviso flotante |
| `model/analysis/ProgressAnalyzerTest` | 10 | Seguimiento: mejoró, empeoró o sin cambios respecto de la muestra anterior del lote |
| `model/analysis/FindingTextTest` | 4 | Texto de estado de cada recuadro ("Dentro del rango", "Leve · alta", etc.) y que cabe en el recuadro |
| `sensor/DemoAnalysisScenariosTest` | 2 | Los 7 escenarios DEMO nuevos producen el análisis esperado con 300 lecturas con ruido cada uno |
| `model/SaleReleaseTest` | 8 | Liberar lote para venta: sugerencia según el veredicto, nota obligatoria al liberar contra el análisis o con medición atrasada, sin mediciones no se decide, límite de la nota |
| `model/SupervisionTest` | 10 | Supervisión del encargado: agrupar por lechero o estanque, % conforme, promedios, conteo de simuladas, mediciones desde el inicio del día, texto del resumen diario (con aviso de SIMULADAS) |
| `model/PasswordRulesTest` | 4 | Política de contraseñas para cuentas nuevas: 10 caracteres como mínimo y con mayúscula, minúscula y número; el texto de ayuda coincide |
| `model/MyDayTest` | 7 | "Mi día" del trabajador (mediciones, % conformes, última, simuladas), inicio del día, recordatorios de la próxima medición (atrasados, próximos, lotes cerrados o de otro día, horizonte de 12 h) y límite de la nota de revisión |
| **Total** | **232** | 228 verificados con Gradle (`.\gradlew test --rerun-tasks`) el 21-11-2026 + 4 (PasswordRulesTest), por confirmar |

## 3. Pruebas de las reglas de Firestore (`firebase/tests/firestore.rules.test.mjs`)

| Grupo | Pruebas | Qué valida |
|---|---|---|
| Sesión y cuenta | 4 | Sin sesión, correo sin verificar o cuenta desactivada no leen datos |
| Usuarios | 4 | Cada uno lee su perfil; solo el admin crea o edita cuentas válidas; no puede quitarse su rol; nada se borra |
| Lotes | 3 | Creación con formato válido y a nombre propio; solo encargado/admin editan estanque y observación |
| Mediciones | 9 | Medición + alerta en una escritura; dato simulado no puede pasar por sensor; límites físicos; hora del servidor; lote y caja existentes; inmutables; lectura según el rol |
| Alertas | 4 | Requieren su medición, ID `medicion-tipo`, tipo y estado válidos; solo encargado/admin las atienden |
| Rangos | 2 | Solo el admin guarda rangos coherentes; todos los usuarios activos los leen |
| Seguridad: eliminar solo el administrador | 6 | Encargado y lechero no borran nada; sin sesión, correo sin verificar o cuenta desactivada tampoco; el admin borra todo menos los rangos y su propio perfil; borrado disfrazado; medición + alertas + bitácora en una escritura (ver [seguridad.md](seguridad.md)) |
| Bitácora de eliminaciones | 3 | Solo el admin anota y lee, con su nombre y hora del servidor; formato válido; no se edita ni se borra |
| Seguridad: escalada de privilegios | 6 | Cambio de rol, cuentas sin perfil, administrador desactivado |
| Seguridad: lectura de datos ajenos | 4 | Perfiles de otros, colecciones no definidas |
| Seguridad: suplantación y datos manipulados | 8 | Nombres falsos, alertas ajenas o malformadas, campos extra |
| Estanques (catálogo) | 4 | Solo encargado/admin crean y renombran; se desactivan; formato del nombre |
| Cierre de lotes | 5 | Cerrar y reabrir deja quién y cuándo; un lote cerrado no admite mediciones; el lechero no puede cerrar |
| Anular mediciones (baja lógica) | 6 | Solo encargado/admin; exige motivo (3–200), nombre propio y hora del servidor; no se tocan los valores medidos ni se desanula |
| Liberar lote para venta | 4 | Solo encargado/admin deciden apto o no apto; exige quién y hora del servidor; nota ≤200; el lechero no puede; se puede cambiar la decisión |
| Nota y petición de revisión | 7 | El trabajador guarda nota (≤200) y pide revisión al registrar; no puede crear la revisión ya hecha ni campos extra; solo encargado/admin la marcan, una vez, con su nombre y la hora del servidor; solo donde se pidió y sin tocar otros campos |
| Alertas atendidas con nota | 6 | Atender con nota opcional, quién y cuándo; editar solo la nota; el formato anterior sigue valiendo |
| Dispositivos y calibraciones (admin) | 4 | Solo el admin; calibración con dispositivo existente, fecha del servidor y responsable; solo se corrige la nota |
| **Total** | **89** | Ejecutadas con el emulador en el computador del equipo el 21-11-2026: 89 correctas, 0 fallidas (el emulador no se puede descargar en el entorno de desarrollo en la nube) |

## 4. Resultados obtenidos

| Prueba | Resultado | Dónde se ejecutó |
|---|---|---|
| 67 pruebas de `model`, `sensor` y `report` | 67 correctas | Entorno de desarrollo, con un ejecutor compatible con JUnit 4 (sin Gradle) |
| 10 pruebas de `MeasureViewModelTest` y 3 de `RecordsUiStateTest` | 13 correctas | Mismo entorno, con dobles mínimos de `ViewModel` y corrutinas |
| Sintaxis de `firestore.rules` | Válida | Validador de reglas de Firebase |
| Pruebas unitarias con `gradlew test` (Gradle 9.6.0) | BUILD SUCCESSFUL, sin fallas | Computador del equipo, 16-11-2026 |
| **81 pruebas unitarias** (total de los resultados XML de Gradle) | **81 correctas, 0 fallas**, BUILD SUCCESSFUL en 13 s | Computador del equipo, 16-11-2026 |
| **133 pruebas unitarias** tras agregar el análisis inteligente (`gradlew test --rerun-tasks`, 25 tareas ejecutadas) | **133 correctas, 0 fallas, 0 errores**, BUILD SUCCESSFUL en 15 s | Computador del equipo, 16-11-2026 |
| **179 pruebas unitarias** tras la medición guiada y la proyección (`gradlew test --rerun-tasks`, 25 tareas ejecutadas) | **179 correctas, 0 fallas**, BUILD SUCCESSFUL en 16 s | Computador del equipo, 20-11-2026 |
| **228 pruebas unitarias** tras las mejoras del encargado y del trabajador (`gradlew test --rerun-tasks`, 25 tareas ejecutadas) | **228 correctas, 0 fallas**, BUILD SUCCESSFUL en 21 s | Computador del equipo, 21-11-2026 |
| 26 pruebas de reglas originales con el emulador (`npm test`) | 26 correctas, 0 fallidas | Computador del equipo, 16-11-2026 (primera corrida, antes de endurecer las reglas) |
| **50 pruebas de reglas** (26 + 24 de seguridad) con las reglas endurecidas | **50 correctas, 0 fallidas** (10 grupos, 6,8 s) | Computador del equipo, 16-11-2026 |
| **89 pruebas de reglas** (50 + CRUD ampliado, eliminaciones, liberar lote para venta, nota y revisión; `npm test`, 18 grupos) | **89 correctas, 0 fallidas, 0 canceladas** en 10,5 s | Computador del equipo, 21-11-2026 |
| Compilación de la app y prueba en teléfono | Correcta (app instalada y usada en modo DEMO) | Android Studio y teléfono del equipo |



## 5. Pruebas manuales en el teléfono (modo DEMO)

| # | Rol | Pasos | Resultado esperado |
|---|---|---|---|
| 1 | Cualquiera | Ingresar con correo verificado | Se abre el Panel con la barra inferior |
| 2 | Lechero | Medir → elegir o crear lote "Estanque 1" → esperar "Lectura estable" → Registrar medición simulada | Mensaje de registro; aparece en Historial y el título de la pantalla muestra el letrero DEMO |
| 3 | Lechero | Medir → muestra "Mastitis" → registrar | Se generan alertas (pH, conductividad…) en la pestaña Alertas |
| 4 | Lechero | Tocar Registrar dos veces seguidas | La segunda vez: "Esta lectura ya fue registrada" |
| 5 | Cualquiera | Activar modo avión → registrar | Aviso "Sin conexión"; la medición queda "Sin sincronizar" y se sube al quitar el modo avión |
| 6 | Encargado | Alertas → Marcar atendida → confirmar | Pasa a "ATENDIDA"; el contador baja |
| 7 | Encargado | Historial → filtros lote / Sensor / Fuera de rango | La lista se filtra; "Quitar filtros" la restablece |
| 8 | Encargado | Más → Reportes → 7 días → PDF y Excel | Se comparte el archivo con la columna Origen; las mediciones simuladas salen marcadas como SIMULADAS y con advertencia en el resumen |
| 9 | Admin | Más → Rangos → pH mínimo mayor que el máximo → Guardar | Mensaje de error, no se guarda |
| 10 | Admin | Más → Gestión de usuarios → desactivar una cuenta | Pide confirmación; esa cuenta ya no puede ingresar |
| 11 | Lechero | Revisar la pestaña Más | No aparecen Lotes, Reportes, Rangos ni Usuarios |
| 12 | Cualquiera | Medir → Caja de sensores sin caja emparejada | No muestra valores; indica conectar la caja |
| 13 | Cualquiera | Cambiar el teléfono a tema oscuro | Colores legibles; semáforo y letrero DEMO se distinguen |

## 6. Pendiente de probar con la caja de sensores

1. Emparejar y conectar la caja (permiso "Dispositivos cercanos" en Android 12+).
2. Recepción de tramas reales y conteo de lecturas descartadas.
3. Pérdida de señal y reconexión durante una medición.
4. Estabilidad con el ruido real (4 lecturas parecidas) y antigüedad máxima de 15 s.
5. Calibración y exactitud de cada sensor frente a instrumentos de referencia.
6. Registro de una medición con origen `sensor` en `caja-01` y su reporte sin datos simulados.
7. Pruebas de terreno en la sala de ordeña (distancia, interferencias, batería).
