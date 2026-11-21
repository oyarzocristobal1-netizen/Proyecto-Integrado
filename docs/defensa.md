# Preparación de la defensa (Evaluación 3)

Preguntas que probablemente haga el profesor, con una respuesta corta y verdadera. Ajústenlas con sus palabras.

## Sobre los datos simulados
**¿Los datos son reales?** No. No existe la caja con sensores, así que todas las mediciones son simuladas. La app lo guarda en cada dato (origen simulado o sensor) y los reportes lo declaran. La etiqueta "DEMO" en pantalla es discreta, pero el reporte incluye la columna Origen y un aviso con el número de mediciones simuladas.

**Entonces, ¿qué demuestra la app?** Que el flujo completo funciona: medir, analizar, alertar, decidir sobre el lote y dejar trazabilidad. Cuando exista la caja, solo cambia la fuente de la lectura.

## Sobre la arquitectura
**¿Por qué Firebase y no un servidor propio?** Es un BaaS: da autenticación, base de datos en tiempo real y reglas de seguridad sin mantener un servidor, adecuado para el tamaño del fundo y para el plazo.

**¿Dónde está la lógica del servidor?** En las reglas de seguridad de Firestore (permisos por rol, validación de campos, rangos y estados). El análisis de calidad corre en la app y está separado en clases puras que se prueban sin Android.

**¿Qué patrón usa la app?** Kotlin con Compose y Material 3, ViewModel compartido para registros, repositorios para el acceso a Firebase y un contenedor manual de dependencias.

## Sobre seguridad
**¿Quién puede hacer qué?** Tres roles. El lechero mide, ve lo suyo y puede pedir revisión. El encargado además gestiona lotes, atiende alertas, libera lotes para la venta y supervisa. El administrador además crea usuarios, gestiona equipos y rangos, y es el único que borra de verdad.

**¿Por qué el encargado no borra ni administra usuarios?** Mínimo privilegio: lo que puede dañar datos o accesos queda en un solo rol.

**¿Cómo saben que las reglas funcionan?** Con 89 pruebas automáticas en el emulador de Firebase que verifican qué se permite y qué se rechaza por rol.

**¿Qué falta en seguridad?** Está en `docs/limitaciones.md`: caché local al cerrar sesión, App Check, respaldos y mover cálculos críticos a Cloud Functions.

## Sobre calidad y pruebas
**¿Cuántas pruebas hay?** 89 de reglas y 248 unitarias (243 verificadas con Gradle y 5 nuevas por confirmar), y las pruebas manuales en teléfono (SM-01..SM-14 y las 13 pruebas manuales). La interfaz no tiene pruebas automáticas.

**¿Cómo decide el análisis si una leche está bien?** Compara cada variable con los rangos configurados y entrega un veredicto: bien, revisar o no usar. Es un apoyo: la decisión de venta es de una persona, queda registrada, y si contradice al análisis debe escribir una nota.

## Sobre decisiones de diseño
**¿Por qué la nota es obligatoria al contradecir el análisis?** Para dejar constancia de por qué se tomó esa decisión.

**¿Qué hace "Pedir revisión"?** El lechero deja una nota en su medición y el encargado la ve en Alertas, la resuelve con su propia nota y queda quién, cuándo y qué respondió.

**¿Cómo avisa la app al encargado de una alerta?** Con una notificación del teléfono que se revisa cada 15 minutos como mínimo (WorkManager) y solo avisa de lo nuevo. No es instantáneo; lo instantáneo requiere Cloud Functions y push, que quedó como trabajo futuro porque exige el plan de pago de Firebase.

**¿Por qué "Mi día" y recordatorios?** Para que el lechero sepa cuándo le toca medir. Los avisos son aproximados por cómo Android ahorra batería.

## Preguntas incómodas (respuestas honestas)
- **"¿Está terminado?"** Está completo en funciones con datos simulados. Las limitaciones están declaradas y priorizadas.
- **"¿Probaron con leche real?"** No, y lo decimos en el informe.
- **"¿Qué cambiarían primero?"** Conectar la caja real y mover alertas y estado del lote a Cloud Functions.
- **"¿Quién hizo qué?"** [completar con la división del equipo: Francisco, Fernanda, Cristóbal].
