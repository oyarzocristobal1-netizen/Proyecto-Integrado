# Limitaciones y trabajo futuro

Texto base para la sección "Limitaciones y trabajo futuro" del informe. Cada limitación está declarada a propósito: sabemos que existe, sabemos por qué y sabemos cómo se resolvería.

## 1. Limitaciones conocidas

### Datos y sensores
| Limitación | Por qué existe | Cómo se maneja hoy |
|---|---|---|
| La caja con sensores (ESP32) no existe: todas las mediciones son simuladas. | El hardware no forma parte de lo que se construye en este proyecto. | Cada dato guarda su origen (simulado o sensor). La app muestra una etiqueta discreta "DEMO" y los reportes (CSV/PDF) incluyen la columna Origen y un aviso con el número de mediciones simuladas. |
| El análisis de calidad no se ha validado con leche real. | Sin sensores ni muestras no hay datos reales para contrastar. | Los rangos son referenciales y el administrador puede ajustarlos. La decisión de venta la toma una persona y queda registrada con su nombre y hora. |
| El protocolo de la caja (formato de lectura, calibración) está definido en papel, no probado en equipo. | Mismo motivo. | Documentado en `docs/protocolo-caja.md`. La app ya separa el origen "sensor" del "simulado". |

### Seguridad
| Limitación | Detalle | Mitigación actual |
|---|---|---|
| La caché local de Firestore no se borra al cerrar sesión. | En un teléfono compartido, datos ya descargados podrían quedar en el dispositivo. | Decisión del equipo de no cambiarlo por ahora. Se recomienda un teléfono por persona y bloqueo de pantalla. |
| Sin App Check ni límite de peticiones. | Un cliente que no sea la app podría intentar muchas llamadas. | Las reglas exigen sesión iniciada, usuario activo y rol, y validan forma y rangos de cada documento. |
| Parte de la lógica confía en el cliente. | El estado del lote y la generación de alertas los calcula la app. Las reglas validan permisos y forma, pero no recalculan el análisis. | Documentado en `docs/seguridad.md`. Solución definitiva: mover esos cálculos a Cloud Functions. |
| Sin respaldos programados de Firestore. | No se configuró copia periódica. | Borrado lógico por defecto, borrado real solo del administrador y registro en `eliminaciones`. |
| Sin pruebas de penetración externas. | Fuera del alcance del curso. | 89 pruebas de reglas con el emulador de Firebase, que cubren permisos por rol y casos de rechazo. |
| Contraseñas sin segundo factor. | Solo correo y contraseña. | Política de 10 caracteres con mayúscula, minúscula y número, exigida por la app y por el servidor. |

### Avisos y funcionamiento
| Limitación | Detalle |
|---|---|
| Las alertas no llegan como notificación al encargado. | Hoy el encargado las ve al abrir la app. Un aviso automático requiere un servidor (Cloud Functions + FCM). |
| Los recordatorios de medición son aproximados. | Usan alarmas inexactas de Android para ahorrar batería y se reprograman al abrir la app (por ejemplo, tras reiniciar el teléfono). No se verificó el aviso en un dispositivo real [completar con el resultado de la prueba]. |
| La hora mostrada depende de la zona horaria del teléfono. | Si el teléfono tiene otra zona, la hora se ve desplazada, aunque el dato guardado es exacto. |
| El funcionamiento sin conexión depende de la caché de Firestore y no tiene pruebas dedicadas. | Sin conexión, las lecturas ya descargadas siguen disponibles y las escrituras se envían al reconectar. |

### Calidad del código
| Limitación | Detalle |
|---|---|
| No hay pruebas automáticas de la interfaz. | La lógica (análisis, reglas de lotes, venta, resumen diario, contraseñas) y las reglas de seguridad sí tienen pruebas. La interfaz se probó a mano (ver `docs/pruebas.md`). |
| Textos escritos directamente en el código. | Dificulta traducir la app a otro idioma. |
| Nombre de paquete `com.example.myapplication`. | Hay que cambiarlo antes de publicar en una tienda. |
| La etiqueta "DEMO" usa texto pequeño (10 sp). | Se dejó discreta a propósito para una app más limpia, a costa de leerse menos. |

## 2. Trabajo futuro (ordenado por prioridad)

1. **Conectar la caja real**: firmware ESP32, calibración con soluciones patrón y validación contra análisis de laboratorio.
2. **Cloud Functions**: generar alertas y estado del lote en el servidor, y enviar notificaciones push al encargado.
3. **Endurecer la seguridad**: App Check, segundo factor para el administrador, limpieza de la caché al cerrar sesión y respaldos programados de Firestore.
4. **Pruebas de interfaz automáticas** (Compose UI tests) y pruebas con el emulador de reglas dentro de la integración continua.
5. **Preparar la publicación**: nombre de paquete propio, textos en recursos, ícono y firma de la app.
6. **Mejoras de uso**: modo sin conexión probado a fondo y reportes programados.

## 3. Mensaje para la defensa

Las limitaciones se dividen en dos tipos. Las del primer tipo vienen de no tener la caja (datos simulados, sin validar con leche real). Las del segundo tipo son decisiones de alcance que cualquier proyecto de este tamaño tiene, y cada una tiene un siguiente paso claro. Nada de lo anterior esconde un error: son límites declarados.
