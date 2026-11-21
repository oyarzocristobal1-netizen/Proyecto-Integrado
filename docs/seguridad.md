# Pruebas de seguridad

Revisión de seguridad de la app y de las reglas de Firestore frente a errores y ataques: abuso de roles,
borrado (por error o a propósito), suplantación y datos manipulados. Cada amenaza tiene su prueba y su estado.

**Cómo se probó.** El atacante se modela como alguien que usa su propia cuenta y las claves públicas de
`google-services.json` para hablar directo con Firestore, sin pasar por la app. Por eso lo que importa son
las **reglas del servidor** (la app solo oculta botones). Las pruebas automáticas se ejecutan con el
emulador: `cd firebase/tests && npm test`.

## 1. Hallazgos de la revisión y correcciones

| # | Hallazgo | Riesgo | Corrección | Cómo se verifica |
|---|---|---|---|---|
| H1 | `allowBackup="true"` con reglas de ejemplo: Android respaldaba la caché local (mediciones y datos de personas) | Medio | `allowBackup="false"` y exclusión total en `data_extraction_rules.xml` | Revisar el manifiesto final (prueba SM-13) |
| H2 | El CSV no neutralizaba textos que empiezan con `=`, `+`, `-` o `@`; un lote llamado `=HYPERLINK(...)` se ejecutaría como fórmula al abrir el reporte en Excel | Medio | `CsvReportWriter.neutralizeFormula` antepone `'` en lote y lechero (los números no se tocan) | Prueba unitaria `csvNeutralizaFormulasEscritasEnLoteOLechero` y SM-12 |
| H3 | `calibraciones` permitía al administrador **borrar** (la regla era `write`), contradiciendo "nada se borra" | Bajo | `delete: if false` explícito; también en `configuracion` | Pruebas de borrado (grupo "eliminar de verdad solo lo hace el administrador"). *Actualización 20-11-2026: el equipo decidió permitir que el administrador elimine registros, con bitácora; ver 2.1* |
| H4 | Las reglas no validaban `nombreLechero`, `nombreCreador`, `mensaje`, `idLote` ni el contenido de `resultado` en alertas y mediciones: se podía atribuir un registro a otra persona o guardar textos enormes | Medio | Nuevas validaciones: el nombre debe ser el del perfil de quien escribe, textos con largo máximo, `resultado` solo con `estado` y `titulo`, y la alerta debe tener el mismo lote que su medición | Grupo "suplantación y datos manipulados" |

Revisado sin hallazgos: `google-services.json` no está en el repositorio ni en su historial; no hay
llamadas a `Log` con datos; el `FileProvider` solo expone `cache/reportes/` y no es exportado; solo la
actividad principal está exportada; los permisos son los mínimos; los errores de inicio de sesión son
genéricos ("Correo o contraseña incorrectos.") y no revelan si el correo existe; Excel y Word no sufren la
inyección de fórmulas (los textos van como cadenas).

> Importante: H4 cambia las reglas. Antes de desplegarlas, todos los teléfonos deben tener la versión actual
> de la app, y **todos los perfiles de `usuarios` deben tener el campo `nombre`** (la app guarda ese nombre
> en cada registro y las reglas lo comparan).

## 2. Matriz de amenazas y pruebas automáticas (reglas, emulador)

| Amenaza | Prueba | Resultado esperado |
|---|---|---|
| Borrado por error o a propósito (encargado o lechero) | `eliminar de verdad solo lo hace el administrador`: intenta borrar un documento de cada una de las 8 colecciones | Todo rechazado (16 intentos) |
| Borrar con la sesión del administrador sin verificar, desactivada o sin sesión | Mismo intento con 4 identidades | Rechazado |
| Borrado disfrazado (vaciar campos, sobrescribir, borrar en lote) | `deleteField`, `setDoc` sobre una medición existente, `batch.delete` | Rechazado |
| Un lechero se sube el rol o se reactiva solo | `escalada de privilegios` | Rechazado |
| Un encargado crea cuentas, cambia roles o reactiva a otros | Idem | Rechazado |
| Registrarse y entrar sin que el administrador cree el perfil | Cuenta verificada "fantasma" intenta leer y escribir | Rechazado todo |
| Administrador desactivado o con correo sin verificar | Intenta crear cuentas, cambiar roles y rangos | Rechazado |
| Encargado o lechero tocan dispositivos, calibraciones o rangos | Escrituras y lecturas | Rechazado |
| Encargado desactivado atiende alertas o edita lotes | Escrituras | Rechazado |
| Ver datos de otras personas (perfiles, listas de usuarios) | `lectura de datos ajenos` | Rechazado salvo el propio perfil y el administrador |
| Colección inventada (`secretos`) | Lectura y escritura como administrador | Rechazado (denegado por defecto) |
| Atribuir un registro a otra persona | Nombre falso en medición, lote y alerta | Rechazado |
| Alerta falsa sobre la medición de otra persona | Un lechero crea alerta sobre una medición ajena | Rechazado |
| Sobrescribir o reabrir una alerta; cambiar su valor | Lechero sobrescribe; encargado reabre o cambia valor | Rechazado |
| Alertas con lote falso, mensaje gigante, vacío o de tipo incorrecto, valor absurdo | 6 variantes | Rechazado |
| Mediciones con campos extra, `resultado` manipulado o textos enormes | 6 variantes | Rechazado |
| Editar quién creó un lote o su fecha | Encargado | Rechazado |
| Control (evita falsos positivos) | Medición y alerta legítimas con las reglas nuevas | Aceptado |

Las 26 pruebas originales siguen vigentes (permisos, límites físicos, hora del servidor, simulado ≠ sensor,
inmutabilidad). Con estas, el archivo tiene 50 pruebas de reglas.

### 2.1 Altas, bajas lógicas y notas (CRUD ampliado)

Las bajas normales son **lógicas** (desactivar, anular, cerrar) y cada cambio deja quién lo hizo (con su nombre de
perfil, no editable) y cuándo (hora del servidor). **Eliminar de verdad** es el último recurso y lo puede hacer
**solo el administrador** (la configuración de rangos no se puede eliminar, y nadie puede eliminar su propia cuenta).

| Función | Quién | Qué guarda | Qué impiden las reglas |
|---|---|---|---|
| Estanques (catálogo) | Encargado y admin | `nombre`, `activo`, `creadoPor` | Borrar sin ser administrador; cambiar otro campo; nombre vacío o de más de 40 caracteres |
| Cerrar / reabrir lote | Encargado y admin | `cerrado`, `estadoPor`, `estadoEn` | Que lo haga un lechero; firmar con otro nombre; hora falsa; **medir en un lote cerrado** |
| Anular medición | Encargado y admin | `anulada`, `motivoAnulacion` (3–200), `anuladaPor`, `anuladaEn` | Cambiar los valores medidos; anular sin motivo; desanular; anular dos veces |
| Atender alerta con nota | Encargado y admin | `atendida`, `nota` (≤300, opcional), `atendidaPor`, `atendidaEn` | Reabrir la alerta; cambiar su valor o mensaje; después solo se edita la nota |
| Liberar lote para venta | Encargado y admin | `venta` (`apto` / `no_apto`), `ventaNota` (≤200, opcional), `ventaPor`, `ventaEn` | Que lo haga un lechero; firmar con otro nombre; hora falsa; valor distinto de apto/no apto; nota de más de 200 caracteres. La app exige nota al liberar contra lo que sugiere el análisis |
| Nota y revisión pedida | Cualquier trabajador al registrar; marca la revisión el encargado y el admin | `nota` (≤200, opcional), `revision` (solo `true`); al revisar: `revisionAtendida`, `revisionPor`, `revisionEn`, `revisionNota` (≤200) | Crear la medición con la revisión ya hecha; que el lechero la marque; firmar con otro nombre; hora falsa; revisar dos veces o donde no se pidió; cambiar los valores medidos |
| Dispositivos | Solo admin | `nombre`, `ubicacion`, `estado` | Borrar sin ser administrador; campos extra |
| Calibraciones | Solo admin | `idDispositivo`, `fecha`, `realizadaPor`, `parametro`, `nota` | Calibrar un dispositivo inexistente; fecha del teléfono; firmar con otro nombre; reescribirla (solo la nota) |

**Bitácora de eliminaciones** (`eliminaciones`): cada vez que el administrador elimina algo, la app anota en la misma
escritura el tipo, el ID, una descripción, quién lo hizo (nombre de su perfil) y cuándo (hora del servidor). Solo el
administrador la lee y **nadie puede editarla ni borrarla**. Al eliminar un lote se eliminan también sus mediciones y
alertas; una medición, sus alertas; un dispositivo, sus calibraciones.

| Eliminar… | Qué pasa con lo asociado | Límite conocido |
|---|---|---|
| Estanque | Nada: los lotes guardan el nombre como texto | — |
| Lote | Se eliminan sus mediciones y alertas (por grupos de 400 escrituras) | Necesita conexión; si falla a la mitad se puede reintentar |
| Medición | Se eliminan sus alertas | — |
| Dispositivo | Se eliminan sus calibraciones; las mediciones conservan su ID | Sin el dispositivo no se pueden registrar mediciones nuevas con él (si es el simulador o la caja, la app los vuelve a crear al entrar el administrador) |
| Cuenta | Se elimina el perfil: pierde el acceso. Las mediciones se conservan con su nombre | La cuenta de acceso sigue en Firebase Authentication: se borra desde la consola |

Las mediciones anuladas siguen en el historial (marcadas ANULADA, con motivo y responsable) pero no cuentan en el
análisis, el panel ni los reportes. Los documentos antiguos no cambian: todos los campos nuevos son opcionales
(un lote sin `cerrado` se considera abierto).

## 3. Pruebas manuales de seguridad en el teléfono

Se necesitan 2 teléfonos o 2 cuentas (una de administrador y una de lechero).

| # | Prueba | Resultado esperado |
|---|---|---|
| SM-01 | Lechero: revisar Más | No aparecen Lotes, Reportes, Rangos, Usuarios ni código de la caja |
| SM-02 | Dos lecheros registran mediciones; cada uno abre Historial | Cada uno ve solo las suyas |
| SM-03 | El administrador desactiva a un lechero con la sesión abierta; el lechero intenta registrar | Se rechaza con un mensaje claro, no se guarda y no puede volver a ingresar |
| SM-04 | El administrador cambia el rol de un lechero a encargado | Los permisos del servidor cambian de inmediato; la app los refleja al reingresar |
| SM-05 | Crear una cuenta y no verificar el correo; intentar ingresar | No entra y la app avisa que falta verificar |
| SM-06 | Crear un usuario con contraseña de menos de 10 caracteres | Se rechaza |
| SM-07 | 6 contraseñas incorrectas seguidas | Mensaje genérico; Firebase puede bloquear temporalmente |
| SM-08 | Ingresar con correo inexistente y con correo real y contraseña mala | Mismo mensaje en ambos casos |
| SM-09 | Recorrer todas las pantallas buscando cómo borrar algo | No existe ninguna opción de eliminar |
| SM-10 | Desactivar cuenta, marcar alerta atendida y cerrar sesión con pendientes | Cada acción pide confirmación |
| SM-11 | Tocar Registrar dos veces seguidas | Una sola medición ("Esta lectura ya fue registrada") |
| SM-12 | Crear el lote `=1+1`, registrar una medición, exportar CSV y abrir en Excel | El lote aparece como texto `'=1+1`, no calcula |
| SM-13 | Compilar y revisar `app/build/intermediates/merged_manifests/debug/.../AndroidManifest.xml` | Contiene `android:allowBackup="false"` |
| SM-14 | Generar dos reportes seguidos | Solo queda el último en la carpeta de reportes; se comparte por el selector de Android |

## 4. Riesgos que quedan

| Riesgo | Por qué queda | Mitigación recomendada |
|---|---|---|
| Un lechero con cuenta válida puede enviar muchas escrituras (consumo del plan gratuito) | Las reglas no limitan la frecuencia | Presupuesto y alertas de cuota en Firebase; desactivar la cuenta |
| Borrado desde la consola de Firebase por alguien con acceso al proyecto | Las reglas no aplican a la consola | Pocos propietarios del proyecto y copias de seguridad programadas de Firestore |
| Al cerrar sesión no se limpia la caché local de Firestore | La app no llama a `clearPersistence` | Las consultas de la app están filtradas por rol; considerar limpiar la caché al cerrar sesión |
| Política de contraseñas en el servidor | La regla de 10 caracteres está en la app; el servidor de Firebase acepta 6 | Activar la política de contraseñas en Authentication; sin perfil creado por el administrador la cuenta no accede a nada |
| Un administrador puede eliminar registros (incluido el historial de un lote) | Decisión del equipo para poder corregir errores; las reglas no obligan a anotar la bitácora (la app lo hace siempre, pero una escritura hecha desde otra herramienta podría omitirla) | Pocos administradores; copias de seguridad programadas de Firestore; revisar la bitácora |
| El enlace Bluetooth no autentica a la caja | Solo emparejamiento | Revisar al definir el hardware definitivo |
