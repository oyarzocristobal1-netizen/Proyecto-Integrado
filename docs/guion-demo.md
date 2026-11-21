# Guion de demostración (5 minutos)

Para mostrar la app en vivo en la defensa. Cada paso dice qué hacer, qué decir y cuánto dura. El hilo conductor: **medir, analizar, alertar, decidir**.

## Antes de la defensa (10 minutos antes)

- Última versión instalada y **sesión cerrada** (que parta en la bienvenida).
- Datos de prueba limpios y un estanque creado (Más → Estanques), por ejemplo "Estanque 1".
- Tres cuentas a mano: lechero, encargado y administrador, con las contraseñas en un lugar que no se vea.
- Con **dos teléfonos** es mejor: uno con el lechero y otro con el encargado, para mostrar los avisos. Con uno solo, se cierra y se abre sesión.
- Permiso de notificaciones aceptado y la zona horaria del teléfono en "Santiago".
- Ensayar una vez el recorrido completo con los tres casos (Normal, Solo pH y Mastitis).

## Recorrido

| Min | Pantalla | Qué hacer | Qué decir |
|---|---|---|---|
| 0:00 | Bienvenida | Mostrar y tocar "Ingresar" | "MilkQuality ayuda a controlar la calidad de la leche del Fundo San Carlos, desde la medición hasta la decisión de venta. Todos los datos de esta demostración son simulados, porque la caja con sensores no forma parte del proyecto." |
| 0:30 | Inicio de sesión | Entrar como **lechero** | "Cada persona entra con su cuenta y ve solo lo que le corresponde según su rol." |
| 0:45 | Medir (lechero) | Muestra **Normal** → Realizar medición | "El lechero elige el lote y mide. Si todo está en rango, el resultado es Buena calidad." |
| 1:15 | Medir | Muestra **Solo pH** | "Con un solo parámetro fuera de rango, el análisis dice Revisar antes de usar y propone pocos pasos." |
| 1:45 | Medir | Muestra **Mastitis**, escribir una nota y marcar "Pedir revisión", y registrar | "Con varios parámetros alterados el resultado es No usar hasta verificar. Explica por qué y qué hacer. El lechero puede dejar una nota y pedir que el encargado revise. Al registrar, se genera una alerta." |
| 2:30 | Panel (lechero) | Mostrar "Mi día" | "El lechero ve su día: cuántas mediciones lleva, su próxima medición y el estado de la revisión que pidió." |
| 2:50 | Cambiar de cuenta | Cerrar sesión y entrar como **encargado** | "Ahora, el encargado." |
| 3:00 | Panel de supervisión | Mostrar | "El encargado supervisa el día: actividad por lechero, comparativas por estanque y la tendencia del pH." |
| 3:20 | Alertas | Mostrar la alerta y la revisión pedida; marcar la revisión como hecha con una nota y **atender** las alertas | "Atiende las alertas y la revisión. Queda registrado quién y cuándo." (Si hay dos teléfonos, mostrar aquí el aviso en el otro.) |
| 4:00 | Lotes | "Liberar para venta" | "Para liberar un lote como apto hay que haber atendido lo pendiente, y si el análisis pedía revisar, la nota es obligatoria. La decisión es de una persona y queda con su nombre y hora. Declarar un lote no apto siempre se puede." |
| 4:30 | Más (administrador) | Mostrar rápidamente, si hay tiempo | "El administrador además crea usuarios, define los rangos y es el único que puede borrar de verdad, con registro." |
| 4:50 | Cierre | Volver a la bienvenida | "Los límites están declarados en el informe: datos simulados, avisos aproximados cada 15 minutos y mejoras futuras como las notificaciones inmediatas." |

## Si algo falla en vivo

- **No llega el aviso al teléfono:** decir que son periódicos (cada 15 minutos como mínimo) y mostrar la captura del informe. No esperar.
- **Sin internet:** la app guarda lo medido y lo envía al reconectar; mostrar el aviso de "sin conexión" si aparece.
- **Un botón no responde:** cerrar y abrir la app, y seguir con el paso siguiente. No improvisar arreglos.
- **El profesor pide algo fuera del guion:** mostrarlo desde Más; casi todo está ahí.

## Preguntas que suelen venir en la demo

- *¿Y si el sensor se desconecta?* No inventa valores: pide conectar la caja.
- *¿Qué pasa si el lechero intenta liberar un lote?* No puede: el servidor lo rechaza por su rol (hay pruebas automáticas).
- *¿Dónde se ve que un dato es simulado?* Etiqueta DEMO en cada pantalla, columna Origen y aviso en los reportes.
