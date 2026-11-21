# Guía para probar y sacar las capturas (compañeros)

Para quien no hizo la app: qué probar, qué capturar y cómo anotarlo. Los resultados alimentan el informe, así que importa que sean **reales**: si algo falla, se anota que falló.

## 0. Antes de empezar (lo prepara Cristóbal)

- Limpiar los datos de prueba (lotes "yyy" y "estanque 6" y la cuenta de trabajador de prueba) con el administrador.
- Dejar una cuenta por rol: administrador, encargado y lechero, con el correo verificado. Las contraseñas se entregan en privado, nunca en el repositorio ni en un chat grupal.
- Entregar la app instalada en un teléfono, o el archivo APK. El archivo `google-services.json` no está en GitHub, así que sin él no se puede compilar: se prueba con la app ya instalada.

## 1. Reglas para las capturas

0. **Usar siempre la última versión de la app** (la que tiene la pantalla de bienvenida con el nombre MilkQuality). Las capturas antiguas del informe se reemplazan, no se mezclan con las nuevas: la interfaz cambió mucho (etiqueta DEMO, panel de supervisión, "Mi día", venta de lotes, avisos).

1. Tapar los **correos** (en la pestaña Más aparece el de la cuenta). Los nombres de pila de prueba se pueden dejar.
2. Capturar la pantalla completa del teléfono, sin recortes ni retoques.
3. Nombre del archivo: `NN-rol-que-muestra.png`, por ejemplo `07-encargado-panel-supervision.png`.
4. Anotar la fecha de la prueba. Si el teléfono tiene otra hora, no importa: se cita la fecha.
5. Los datos son simulados y la app lo muestra con la etiqueta **DEMO**. No hay que ocultarla ni aclarar nada en la captura.

## 2. Capturas que faltan

| # | Rol | Qué se ve | Cómo llegar |
|---|---|---|---|
| 1 | Lechero | Resultado "Buena calidad" | Medir → muestra Normal → esperar "Lectura estable" |
| 2 | Lechero | Resultado "Revisar" | Medir → muestra **Solo pH** (deslizar los botones de muestras hacia la derecha). Ácida, Mastitis y Diluida dan "No usar hasta verificar" |
| 3 | Lechero | Resultado "No usar hasta verificar" | Medir → muestra Mastitis o Ácida |
| 4 | Lechero | Nota y "Pedir revisión" en la medición | En Medir, antes de registrar |
| 5 | Encargado | "Análisis histórico" con una tarjeta abierta y "Ver detalle del análisis" | Historial → tocar la flecha de un lote → "Ver detalle del análisis" (el lechero no tiene esta sección) |
| 6 | Encargado | Alertas con filtros y "Revisiones pedidas" | Pestaña Alertas |
| 7 | Encargado | Panel de supervisión | Pestaña Panel |
| 8 | Encargado | Lotes con "Apto para venta" y su nota | Pestaña Más → Lotes |
| 9 | Encargado | Resumen diario al compartir | Panel de supervisión → Compartir resumen |
| 10 | Administrador | Gestión de usuarios con el mensaje de contraseña | Más → Gestión de usuarios → Nuevo trabajador, con una clave corta |
| 11 | Los 3 roles | Pestaña **Más** (cada rol ve opciones distintas) | Una captura por rol, con el correo tapado |
| 12 | Lechero | Tarjeta "Mi día" | Pestaña Panel |
| 13 | Sin sesión | Pantalla de bienvenida (MilkQuality) | Abrir la app con la sesión cerrada |
| 14 | Sin sesión | Inicio de sesión | Tocar "Ingresar" en la bienvenida |
| 15 | Lechero | Medir con la muestra y el selector de lote | Pestaña Medir → Cambiar lote |
| 16 | Encargado | Historial filtrado por "Conforme" y el "Análisis histórico" sin filtrar | Pestaña Historial |
| 17 | Cualquiera | Notificación de aviso en la pantalla de bloqueo | Ver sección 4 |
| 18 | Lechero | Mis mediciones con "Ver qué hacer" desplegado en una medición fuera de rango | Pestaña Historial → "Ver qué hacer" |

Las capturas de la consola de Firebase (reglas publicadas, política de contraseñas) y de GitHub las saca Cristóbal, porque requieren su cuenta.

## 3. Pruebas manuales

Las tablas están en `docs/pruebas.md` (sección 5, pruebas 1 a 13) y en `docs/seguridad.md` (SM-01 a SM-14). Hacer cada una con el rol indicado y anotar el resultado:

| Prueba | Resultado (OK / FALLA) | Quién la hizo | Teléfono | Fecha | Observaciones |
|---|---|---|---|---|---|
| (ej.) Manual 4 | | | | | |

Notas:
- SM-12 y SM-13 requieren compilar la app. Las hace Cristóbal.
- SM-07 puede bloquear la cuenta unos minutos: hacerla con una cuenta de prueba.
- Si una prueba **falla**, no se arregla ni se repite hasta que salga bien. Se anota qué pasó, se saca captura y se avisa. Un fallo bien documentado también sirve para el informe.

## 4. Prueba de los avisos del teléfono

1. Entrar como encargado en un teléfono y aceptar el permiso de notificaciones. Cerrar la app y esperar. Si hay alertas pendientes, llega "Alertas por atender".
2. Como lechero (en otro teléfono, o cerrando sesión), medir una muestra mala y marcar "Pedir revisión".
3. Como encargado, resolver la revisión (Alertas → Revisiones pedidas).
4. En el teléfono del lechero, con la app cerrada, debería llegar "Revisión respondida".

Los avisos son periódicos (cada 15 minutos como mínimo) y no instantáneos: esperar hasta 20 minutos. Anotar cuánto tardó cada aviso y sacar captura de la notificación.

## 5. Qué entregar a Cristóbal

- Las capturas con los nombres indicados.
- La tabla de resultados completa, con las fallas incluidas.
- El tiempo que tardaron los avisos, y si no llegaron.
