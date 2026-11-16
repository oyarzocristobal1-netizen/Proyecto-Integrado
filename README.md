# Calidad de Leche · Fundo San Carlos

Proyecto Integrado TIHI43 (INACAP). App Android para medir la calidad de la leche (temperatura, pH,
conductividad y turbidez) con una caja de sensores, evaluar cada muestra con un semáforo, generar alertas
y exportar reportes. Los datos se guardan en Cloud Firestore con permisos por rol (administrador,
encargado y lechero).

Equipo: Francisco Álvarez, Fernanda Delgado y Cristóbal Epuyao.

> La caja de sensores todavía no está disponible. La app se usa en **modo DEMO** con datos simulados,
> marcados como **SIMULADOS** en toda la app y en los reportes. La conexión con la caja está implementada
> pero **pendiente de validar**.

## Estructura

| Carpeta | Contenido |
|---|---|
| `MyApplication/` | Proyecto de Android Studio (Kotlin, Jetpack Compose, Material 3) |
| `firebase/` | Reglas de seguridad, índices y pruebas de reglas con el emulador |
| `docs/` | Documentación técnica |

## Documentación

- [Configurar Firebase](docs/configurar-firebase.md): archivo de configuración, cuentas, reglas, índices y datos iniciales.
- [Arquitectura](docs/arquitectura.md): capas, navegación, flujo de una medición, modo DEMO, trabajo sin conexión.
- [Pruebas](docs/pruebas.md): cómo ejecutar las 80 pruebas unitarias y las 26 de reglas, y pruebas manuales.
- [Protocolo de la caja](docs/protocolo-caja.md): formato de datos esperado y lo pendiente con el hardware.
- [Cambios para el informe](docs/cambios-informe.md): diferencias con el diseño original.

## Ejecutar

1. Copiar `google-services.json` en `MyApplication/app/` (se pide por privado; no se sube a GitHub).
2. Abrir `MyApplication/` en Android Studio, sincronizar Gradle y ejecutar.
3. Pruebas: `cd MyApplication` y `./gradlew test`.
