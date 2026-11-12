# Configurar Firebase para la app Calidad de Leche

Proyecto de Firebase: `medidor-calidad-de-la-leche`. Paquete de la app: `com.example.myapplication`.

## 1. Archivo de configuración (cada integrante, en su computador)

1. Pide el archivo `google-services.json` al dueño del proyecto (por privado).
2. Cópialo en `MyApplication/app/google-services.json`.
3. **No lo subas a GitHub.** Ya está en `MyApplication/app/.gitignore`.
4. En Android Studio: **File → Sync Project with Gradle Files** y luego **Run**.

Sin ese archivo el proyecto no compila (error del plugin `google-services`).

## 2. Activar el inicio de sesión (una vez, en la consola)

**Build → Authentication → Sign-in method → Correo electrónico/contraseña → Habilitar.**

## 3. Crear las cuentas (lo hace el administrador)

No hay registro público. **La primera cuenta de administrador** se crea en la consola (pasos de abajo).
Después, el administrador crea las cuentas del **encargado** y de los **lecheros** desde la app:
**ícono de gestión de usuarios (arriba) → Nuevo trabajador**. La app crea la cuenta, guarda su perfil
y le envía el correo de verificación, sin cerrar la sesión del administrador.

Primera cuenta de administrador, en la consola:

1. **Authentication → Users → Agregar usuario.** Correo real y contraseña de **10 caracteres o más**.
2. Copia el **UID** que aparece en la lista de usuarios.
3. **Firestore Database → Datos → Iniciar colección** `usuarios` (la primera vez).
4. Crea un documento cuyo **ID sea el UID** copiado, con estos campos:

| Campo    | Tipo    | Valor                                   |
|----------|---------|-----------------------------------------|
| `nombre` | string  | Nombre de la persona                    |
| `correo` | string  | El mismo correo de la cuenta            |
| `rol`    | string  | `admin`, `encargado` o `lechero`        |
| `activo` | boolean | `true` (obligatorio; con `false` o sin el campo la cuenta no entra) |

Para probar los tres roles, crea al menos una cuenta de cada uno.

## 4. Verificación del correo

La política del informe (sección 6.1) exige correo verificado. La primera vez que alguien ingresa,
la app le envía un enlace de verificación y le pide abrirlo. Después de abrirlo, vuelve a ingresar.
Revisa también la carpeta de spam.

## 5. Publicar las reglas de seguridad

**Firestore Database → Reglas**: borra lo que haya, pega el contenido de `firebase/firestore.rules`
de este repositorio y pulsa **Publicar**.

Las reglas hacen cumplir la Tabla 31 en el servidor y exigen correo verificado y cuenta activa: aunque alguien modifique la app, no puede leer
ni escribir lo que su rol no permite.

## 6. Qué ve cada rol en la app

| Acción                                        | Administrador | Encargado | Lechero       |
|-----------------------------------------------|---------------|-----------|---------------|
| Medir y ver resultado                         | Sí            | Sí        | Sí            |
| Historial                                     | Todo          | Todo      | Solo el propio |
| Editar lotes, atender alertas, exportar       | Sí            | Sí        | No            |
| Calibrar, código de la caja, gestionar usuarios, cambiar rangos | Sí | No | No       |

El lechero es quien toma las muestras en la ordeña; en las mediciones su UID se guarda en el campo `uidLechero`.

## 7. Datos iniciales para registrar mediciones

**La caja de sensores** (las reglas exigen que cada medición apunte a un dispositivo existente):
colección `dispositivos`, documento con ID **`caja-01`**:

| Campo | Tipo | Valor |
|---|---|---|
| `nombre` | string | Caja de sensores 1 |
| `ubicacion` | string | Sala de ordeña |
| `estado` | string | `activo` |

**Índices compuestos** (Firestore → Índices → Compuesto → Agregar índice; ámbito Colección).
También están en `firebase/firestore.indexes.json`. ↑ = Ascendente, ↓ = Descendente.

| Colección | Campos | Para qué |
|---|---|---|
| `mediciones` | `idLote` ↑, `fechaHora` ↓ | Historial por lote (encargado y administrador) |
| `mediciones` | `uidLechero` ↑, `fechaHora` ↓ | Historial propio del lechero |
| `mediciones` | `uidLechero` ↑, `idLote` ↑, `fechaHora` ↓ | Historial propio por lote |
| `alertas` | `atendida` ↑, `fecha` ↓ | Alertas pendientes (Parte 3) |
| `calibraciones` | `idDispositivo` ↑, `fecha` ↓ | Calibraciones de la caja |

Si falta un índice, la app muestra "Falta un índice en Firestore para esta consulta".

## 8. Cómo se usan los lotes y las mediciones

1. En la pantalla principal, tarjeta **Lote activo → Elegir**: se elige un lote de hoy o se crea el de un
   estanque (ID `AAAAMMDD-estanque`, un lote por estanque y día).
2. **Registrar medición** guarda la lectura que se ve en pantalla en `mediciones`, con el lote, el lechero,
   la fecha, los cuatro sensores y el semáforo (`resultado.estado`: verde, ambar o rojo).
3. Sin conexión, la medición queda guardada en el teléfono y se sube sola al volver la red.
4. El encargado y el administrador ven todo el historial y lo filtran por lote; el lechero ve solo lo suyo.
5. El encargado y el administrador editan lotes (estanque y observación) desde el ícono **Lotes**.

Campos que la app agrega además de la Tabla 20 del informe (redundancia controlada, para mostrar nombres
sin lecturas extra): `nombreLechero` en mediciones; `nombreCreador` y `observacion` en lotes.

## 9. Alertas

Cuando el servidor confirma una medición, la app crea una alerta en `alertas` por cada valor fuera de rango
(ID `idMedicion-tipo`, sin duplicados):

| Tipo | Se genera si | Severidad alta si |
|---|---|---|
| `temperatura` | > 4 °C | > 25 °C |
| `ph` | fuera de 6,6–6,8 | < 6,4 o > 7,0 |
| `conductividad` | fuera de 4,0–6,0 mS/cm | > 6,2 o < 3,5 |
| `turbidez` | < 50 % | siempre |

Campos: `idMedicion`, `idLote`, `tipo`, `severidad` (media o alta), `fecha`, `atendida` (false al crearse),
`valor`, `mensaje` y `nombreLechero`. Todos los roles ven las alertas (campana con el número de pendientes);
solo el encargado y el administrador las marcan como atendidas, y la regla solo permite cambiar `atendida`.

## 10. Rangos de calidad (administrador)

Menú ⋮ → **Rangos de calidad**. Se guardan en `configuracion/rangos` y se aplican en todos los teléfonos
(semáforo, tarjetas, evaluación y alertas). Si el documento no existe, la app usa los valores de referencia.

| Campo | Referencia | Uso |
|---|---|---|
| `tempMax` | 4 | Leche refrigerada (°C) |
| `phMin`, `phMax` | 6,6 y 6,8 | pH normal |
| `condMin`, `condMax` | 4,0 y 5,5 | Conductividad normal (mS/cm) |
| `turbMin` | 50 | Turbidez mínima (%) |

El campo `grasaMin` de la Tabla 20 se conserva si existe, pero la app aún no estima grasa: lo hará cuando se
calibre la turbidez con la caja real. Actualizar la Tabla 20 del informe con `condMin` y `turbMin`.

## 11. Reportes (encargado y administrador)

Menú ⋮ → **Reportes**: periodo (hoy, 7 o 30 días), lote (o todos) y formato: **PDF, Excel (.xlsx), Word (.docx)
y CSV**, uno o varios a la vez. La app genera los archivos en el teléfono y abre el menú **Compartir**
(correo, WhatsApp, Drive…). Incluyen un resumen (cantidad, semáforo en %, promedios) y la tabla de mediciones.
Necesitan conexión a internet para leer las mediciones (consulta Q4; con lote usa el índice `idLote` + `fechaHora`).

## Problemas comunes

| Mensaje en la app | Causa y solución |
|---|---|
| "Correo o contraseña incorrectos." | Revisa los datos o restablece la contraseña desde la app. |
| "Tu correo aún no está verificado…" | Abre el enlace que llegó al correo y vuelve a ingresar. |
| "Tu cuenta no tiene perfil en el sistema…" | Falta el documento `usuarios/{UID}` o el ID no es el UID exacto. |
| "Tu cuenta no tiene un rol válido…" | El campo `rol` no es `admin`, `encargado` ni `lechero`, o el nombre del campo tiene un espacio (`"rol "`). |
| "Tu cuenta está desactivada o incompleta…" | Falta el campo `activo` o es `false`. |
| "…permiso denegado. Revisa las reglas de Firestore." | Las reglas no están publicadas o el correo no está verificado. |
