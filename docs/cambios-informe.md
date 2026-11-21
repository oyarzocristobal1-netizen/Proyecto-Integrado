# Cambios respecto del diseño original (para actualizar el informe)

Lista de diferencias entre lo implementado y lo descrito en las evaluaciones anteriores, para revisar
las secciones correspondientes del informe.

## Implementado distinto al diseño

| Tema | Diseño original | Implementación actual | Motivo |
|---|---|---|---|
| Trabajo sin conexión | Base local propia (si el informe menciona Room o WorkManager) | Caché persistente de Firestore (100 MB) con cola de escrituras | Sincroniza sola, sin duplicar el modelo de datos ni escribir código de sincronización |
| Roles (Tabla 31) | Administrador y encargado | Administrador, encargado y **lechero** | El lechero toma las muestras; ve solo sus mediciones |
| Modelo de datos (Tabla 20) | Campos base | + `origen`, `registradoEn`, `nombreLechero` (mediciones); `origen` (alertas); `nombreCreador`, `observacion` (lotes) | Trazabilidad, distinguir datos simulados y no depender del reloj del teléfono |
| Rangos de calidad | `grasaMin` y otros | `tempMax`, `phMin`, `phMax`, **`condMin`**, `condMax`, **`turbMin`** | La grasa se estimará cuando se calibre la turbidez con la caja |
| Dispositivos | Solo la caja | `caja-01` y **`simulador`** | Las mediciones del modo DEMO quedan separadas de las reales |
| Alertas | Se generan al confirmar la medición | Se guardan **en la misma escritura** que la medición | Evita perder alertas si se cierra la app o falla la red |
| Navegación | Pantalla principal con menú | Barra inferior: Panel, Medir, Historial, Alertas, Más | Acceso directo a las tareas frecuentes; actualizar diagramas de pantallas |
| Reportes | Exportar historial | PDF, Excel, Word y CSV; simuladas excluidas por defecto | Un reporte de calidad no debe mezclar datos simulados |

## Nuevas reglas de integridad (requerimientos no funcionales)

- Límites físicos de los sensores en la app y en las reglas del servidor.
- Lectura estable (4 lecturas parecidas) y reciente (≤ 15 s) para poder registrar.
- No se registra dos veces la misma lectura; los valores medidos no se pueden cambiar; las bajas son lógicas (anular, desactivar, cerrar) y eliminar de verdad es solo del administrador, con bitácora.
- Un dato simulado no puede guardarse como medición del sensor (regla del servidor).

## Pruebas (sección de validación)

- 227 pruebas unitarias automáticas (por confirmar con Gradle) y 89 pruebas de reglas de Firestore (verificadas con el emulador) (detalle en [pruebas.md](pruebas.md)).
- Pruebas con hardware pendientes hasta contar con la caja de sensores.

## Pendiente o no implementado

| Tema | Estado |
|---|---|
| Conexión con la caja real, formato de tramas y calibración | Implementado sin validar (ver [protocolo-caja.md](protocolo-caja.md)) |
| Bluetooth de baja energía (BLE), si la caja final es ESP32 con BLE | No implementado; la interfaz `SensorSource` permite agregarlo |
| Diseño adaptado a tabletas (clases de tamaño de ventana) | No implementado; la app está pensada para teléfono |
| Textos en `strings.xml` (traducción) | Pendiente; los textos están en el código |
| Nombre de paquete definitivo (hoy `com.example.myapplication`) | Pendiente; cambiarlo exige registrar de nuevo la app en Firebase |
| Estimación de grasa (`grasaMin`) | Pendiente de calibrar la turbidez |

## Mejoras para el encargado (supervisión)

- **Liberar lote para venta**: el encargado (o el administrador) marca el lote como *apto* o *no apto*. El análisis solo sugiere; si se libera contra lo que sugiere (o con una medición atrasada) la nota es obligatoria. Queda guardado quién, cuándo y por qué. Con datos simulados el diálogo avisa que la decisión es de demostración.
- **Panel de supervisión** (pantalla Panel, solo encargado y administrador): una tarjeta por lote de hoy con veredicto del análisis, última medición o atraso, alertas pendientes y estado de venta; actividad de los lecheros del día.
- **Resumen diario y comparativas**: botón *Compartir resumen* (texto listo para WhatsApp o correo, con aviso de mediciones SIMULADAS) y comparativa de 7 días por estanque o por lechero (% conformes y promedios).
- **Alertas más ágiles**: filtros por severidad y por lote, selección múltiple con una nota común y *Ver medición* dentro de cada alerta.
- Pruebas nuevas: `SaleReleaseTest` (8), `SupervisionTest` (10) y 4 de reglas (liberar lote para venta). Todos los datos del modo DEMO se siguen marcando como simulados.

## Mejoras para el trabajador (lechero)

- **Mi día** (pantalla Panel): cuántas mediciones hizo hoy, cuántas salieron conformes, cuál fue la última, qué lote le toca medir y cómo van las revisiones que pidió. Usa solo sus propias mediciones y marca lo simulado.
- **Recordatorio de la próxima medición**: el análisis ya calcula a qué hora conviene volver a medir cada lote; con eso la app programa una notificación ("Hora de medir") que funciona aunque la app esté cerrada. Pide el permiso de notificaciones una vez (Android 13 o superior). Solo avisa de lotes de hoy que siguen abiertos y de las mediciones propias, y no muestra avisos si se cerró la sesión. Si el teléfono se reinicia, se reprograman al abrir la app. Los avisos son aproximados (alarmas sin permiso especial).
- **Nota y "Pedir revisión"**: al registrar, el trabajador puede escribir una nota (≤200 caracteres) y pedir que el encargado revise la medición; si el análisis dice "No usar hasta verificar", la casilla viene marcada. El encargado ve las revisiones pedidas en Alertas (y un aviso en el panel de supervisión), las marca como hechas con una nota opcional, y el trabajador ve en su historial que fue revisada, por quién y qué se anotó.
- Pruebas nuevas: `MyDayTest` (7) y 7 de reglas (nota y petición de revisión). Todo sigue marcado como simulado cuando viene del modo DEMO.

## Identificación de datos simulados (limpieza visual)

- Para no cargar la app con una marca en cada dato, se quitaron las insignias "SIMULADA" repetidas y los banners grandes de cada pantalla. En su lugar aparece un letrero pequeño **DEMO** junto al título, mientras haya datos simulados o el modo Simulación esté activo.
- Los reportes (columna Origen y advertencia en el resumen) y el resumen diario compartido siguen indicando que los datos son simulados, porque salen de la app.
- Lo simulado sigue guardándose con `origen = "simulado"` y el dispositivo `simulador`, y las reglas de Firestore impiden hacerlo pasar por dato del sensor.- En la pantalla Medir se ocultó el selector Simulación | Caja de sensores, porque la caja no estará disponible; queda la selección de la muestra simulada (Variada, Normal, Ácida, Mastitis, Diluida). La conexión por Bluetooth sigue implementada en el código y documentada en [protocolo-caja.md](protocolo-caja.md), pero no se puede activar desde la interfaz.- Los botones de filtro por origen (Todas / Sensor / Simuladas) del Panel y del Historial solo aparecen si hay mediciones del sensor y simuladas a la vez; con solo datos simulados quedan ocultos (y si quedaran activos, vuelven a "Todas").

## Política de contraseñas

- Las cuentas nuevas exigen contraseña de **10 caracteres o más, con mayúscula, minúscula y número**. La app lo valida al crear el usuario (con el motivo del rechazo) y la misma política se activa en Firebase Authentication para que el servidor también la exija, no solo la app. El inicio de sesión no aplica la regla de complejidad, para no dejar fuera a cuentas ya creadas.
- Prueba nueva: `PasswordRulesTest` (4).
- En "Mi día" la hora de la próxima medición dice "a las 16:03", "mañana a las 04:03" o "ayer a las 22:00" según el día, para que no haya confusión.

## Avisos por rol (notificaciones con la app cerrada)

- Además del recordatorio "Hora de medir", la app avisa según el rol: **encargado y administrador** reciben "Alertas por atender", "Revisión pedida" y "Medición atrasada" (lote de hoy, abierto, al que ya le tocaba medir); el **lechero** recibe "Revisión respondida" cuando el encargado revisa una medición suya.
- Funciona con una tarea periódica del teléfono (WorkManager, cada 15 minutos como mínimo, solo con conexión) que consulta Firestore con las mismas reglas de seguridad de siempre: cada rol solo lee lo que ya puede leer. Muestra un aviso solo cuando hay algo **nuevo** y no repite lo ya avisado.
- **No es en tiempo real**: puede demorar varios minutos porque Android agrupa las tareas para ahorrar batería. El aviso inmediato requiere un servidor (Cloud Functions + notificaciones push, plan de pago de Firebase) y queda como trabajo futuro.
- El permiso de notificaciones se pide una vez al iniciar sesión (Android 13 o superior), para cualquier rol. Sin permiso no se pierde nada: los avisos pendientes llegan cuando se acepta.
- Consumo de lecturas: cada revisión lee pocas decenas de documentos (alertas pendientes, revisiones pedidas y las mediciones y lotes de hoy). No cambia las reglas de seguridad.
- Prueba nueva: `RoleNoticesTest` (10).

## Pantalla de inicio de sesión

- Se agregó, bajo el título, una frase que explica qué hace la app: "Mide la leche de cada estanque, la analiza, avisa si hay un problema y deja registrada la decisión de venta de cada lote."
- **Nombre de la app: MilkQuality** (subtítulo "Calidad de la Leche"). Se cambió el nombre que se ve bajo el ícono, el título del inicio de sesión y del Panel y los títulos de la documentación. El identificador interno del proyecto (`com.example.myapplication`) no cambió, porque está atado a Firebase.
- **Pantalla de bienvenida**: al abrir la app sin sesión aparece una portada con una imagen del campo, el nombre, una frase ("Monitorea y cuida la calidad de tu leche"), cuatro recuadros que resumen lo que hace (Mediciones, Análisis, Recomendaciones, Historial y reportes) y el botón "Ingresar". Arriba a la derecha hay un acceso directo "Iniciar sesión". Al cerrar sesión se va directo al inicio de sesión. Es solo interfaz; no cambia ninguna regla ni la lógica.
- La imagen de portada es una ilustración de apoyo, no una foto del Fundo San Carlos. [Indicar en el informe el origen de la imagen.]
