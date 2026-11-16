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
- No se registra dos veces la misma lectura; mediciones inmutables; nada se borra.
- Un dato simulado no puede guardarse como medición del sensor (regla del servidor).

## Pruebas (sección de validación)

- 81 pruebas unitarias automáticas y 50 pruebas de reglas de Firestore (detalle en [pruebas.md](pruebas.md)).
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
