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

No hay registro público: las cuentas se crean en la consola.

1. **Authentication → Users → Agregar usuario.** Correo real y contraseña de **10 caracteres o más**.
2. Copia el **UID** que aparece en la lista de usuarios.
3. **Firestore Database → Datos → Iniciar colección** `usuarios` (la primera vez).
4. Crea un documento cuyo **ID sea el UID** copiado, con estos campos:

| Campo    | Tipo    | Valor                                   |
|----------|---------|-----------------------------------------|
| `nombre` | string  | Nombre de la persona                    |
| `correo` | string  | El mismo correo de la cuenta            |
| `rol`    | string  | `admin`, `encargado` u `operario`       |
| `activo` | boolean | `true` (con `false` la cuenta no entra) |

Para probar los tres roles, crea al menos una cuenta de cada uno.

## 4. Verificación del correo

La política del informe (sección 6.1) exige correo verificado. La primera vez que alguien ingresa,
la app le envía un enlace de verificación y le pide abrirlo. Después de abrirlo, vuelve a ingresar.
Revisa también la carpeta de spam.

## 5. Publicar las reglas de seguridad

**Firestore Database → Reglas**: borra lo que haya, pega el contenido de `firebase/firestore.rules`
de este repositorio y pulsa **Publicar**.

Las reglas hacen cumplir la Tabla 31 en el servidor: aunque alguien modifique la app, no puede leer
ni escribir lo que su rol no permite.

## 6. Qué ve cada rol en la app

| Acción                                        | Administrador | Encargado | Operario      |
|-----------------------------------------------|---------------|-----------|---------------|
| Medir y ver resultado                         | Sí            | Sí        | Sí            |
| Historial                                     | Todo          | Todo      | Solo el propio |
| Editar lotes, atender alertas, exportar       | Sí            | Sí        | No            |
| Calibrar, código de la caja, gestionar usuarios, cambiar rangos | Sí | No | No       |

## Problemas comunes

| Mensaje en la app | Causa y solución |
|---|---|
| "Correo o contraseña incorrectos." | Revisa los datos o restablece la contraseña desde la app. |
| "Tu correo aún no está verificado…" | Abre el enlace que llegó al correo y vuelve a ingresar. |
| "Tu cuenta no tiene perfil en el sistema…" | Falta el documento `usuarios/{UID}` o el ID no es el UID exacto. |
| "Tu cuenta no tiene un rol válido…" | El campo `rol` no es `admin`, `encargado` ni `operario`. |
| "…permiso denegado. Revisa las reglas de Firestore." | Las reglas no están publicadas o el correo no está verificado. |
