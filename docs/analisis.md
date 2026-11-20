# Análisis inteligente de la calidad de la leche

La aplicación no solo muestra valores y alertas: interpreta la medición y responde cuatro preguntas
(¿qué está sucediendo?, ¿qué podría causarlo?, ¿qué revisar?, ¿qué hacer ahora?).

> Importante: es una **ayuda a la decisión basada en reglas**. No diagnostica enfermedades, las causas se
> presentan como **posibilidades** y ninguna está confirmada por los sensores. Mientras no se valide con la caja
> de sensores real, los resultados del modo DEMO se identifican como **simulados**.

## Arquitectura

| Capa | Archivos | Notas |
|---|---|---|
| Motor (Kotlin puro, sin Android ni Firebase) | `model/analysis/` | Se prueba con JUnit sin emulador |
| Adaptadores | `data/AnalysisAdapters.kt` | Convierte `SavedMeasurement` en `AnalysisReading` |
| Interfaz | `ui/components/AnalysisComponents.kt` | Solo dibuja el resultado del motor |
| Integración | `MeasureScreen` (sección "Análisis y recomendaciones") y `HistoryScreen` (análisis histórico, solo encargado y administrador) | |

No cambia el esquema de Firestore ni las reglas: el análisis se calcula al momento con las mediciones ya guardadas.

## Qué evalúa el motor

1. **Cada parámetro**: dentro de rango, desviación leve, moderada, considerable o crítica, o lectura no confiable.
   La severidad se mide por la distancia al rango configurado en la app (`QualityRanges`).
2. **Confiabilidad**: valores fuera de los límites del sensor, todo en cero, valores pegados al extremo de la escala,
   temperatura bajo 0 °C, conductividad casi nula con turbidez normal (contradicción) y lectura inestable.
   Si hay lecturas sospechosas no se emite una interpretación confiable hasta repetir la medición.
3. **Combinaciones** (escenarios A–F): 1, 2, 3 o 4 parámetros alterados, desviaciones leves y lecturas contradictorias.
   Las 16 combinaciones posibles tienen prueba. En temperatura + turbidez no se asume una causa común.
4. **Historial** (solo mediciones del mismo lote y de la misma fuente): repetición, persistencia, tendencia al
   alza o a la baja, cambio importante y varios parámetros que empiezan a alterarse a la vez.
   Las mediciones tomadas dentro de 10 minutos se agrupan como **una misma muestra**; no cuentan como muestras independientes.
5. **Prioridad**: NORMAL, PREVENTIVO, ADVERTENCIA o CRÍTICO. Depende de la magnitud de la desviación, la combinación,
   la repetición y la confiabilidad, no solo de cuántos parámetros están alterados.

## Flujo de la medición (pantalla Medir)

1. **Realizar medición**: el trabajador pulsa el botón. En DEMO se sortea una muestra simulada nueva; con la caja, se esperan lecturas del sensor.
2. **Midiendo**: avance mientras la lectura se estabiliza (4 lecturas parecidas).
3. **Resultado**: un veredicto de un vistazo, con color e ícono:
   - **Buena calidad**: los cuatro parámetros están dentro de los rangos (solo considera esos cuatro parámetros).
   - **Revisar**: desviación leve o moderada, tendencia que preocupa o lectura no confiable.
   - **No usar hasta verificar**: condición crítica.
4. **Qué hacer ahora**: de 1 a 4 pasos con casillas que el trabajador va marcando.
5. **Valores**: cada parámetro en un recuadro con su valor y su estado ("Dentro del rango", "Leve · alta", "Muy baja", "Crítica · alta") con color según la gravedad.
6. **Seguimiento**: si ya había una muestra anterior del mismo lote, indica si mejoró o empeoró (por ejemplo, después de actuar).
7. **Detalle según rol**: el lechero ve veredicto, pasos, valores y glosario; el encargado y el administrador ven además el análisis completo (causas, qué revisar, proyección, historial).
8. **Glosario** "¿Qué mide cada parámetro?" para todos los roles.

## Proyección y seguimiento

- La proyección se usa internamente (veredicto y tiempo sugerido para remedir); ya no se muestra como tarjeta aparte. Con una recta ajustada a las últimas muestras distintas del lote (mínimo 3 muestras y 20 min entre la primera y la última) se estima si un parámetro saldría del rango en las próximas 2 horas, si vuelve al rango o si se aleja. Incluye **la conductividad en ambas direcciones** (sube o baja) además de temperatura, pH y turbidez.
- Es una **estimación lineal con pocas muestras, no una predicción validada**; en temperatura se avisa que el enfriamiento real se desacelera.
- **Próxima medición sugerida**: 5 min si la lectura es dudosa, 15 min crítico, 30 min advertencia, 60 min preventivo, y antes si se prevé salir del rango pronto.
- **Medición atrasada**: en el análisis histórico se marca el lote cuyo momento de remedir ya pasó (o, si estaba normal, que lleva más de 12 h sin medirse; valor por validar).

## Datos DEMO variados

La muestra **Variada** (predeterminada) sortea cada medición: cerca de la mitad buenas y el resto con uno, dos, tres o los cuatro parámetros alterados, leves o marcados, y alguna lectura contradictoria. Los valores cambian en cada medición (por ejemplo 3,1 °C y luego 4,6 °C). Las muestras fijas siguen disponibles para probar un caso concreto. Todo se rotula como **simulado**.

## Limitaciones de interpretación

- **Turbidez**: no se interpreta como viscosidad ni espesor. La turbidez alta no se evalúa por defecto (`turbidityMax = null`).
- **Conductividad**: no diagnostica mastitis por sí sola; solo sugiere controles adicionales y evaluación veterinaria.
- **Temperatura** sobre 25 °C se trata como crítica (umbral ya existente en la app, `TEMP_AMBIENT_MAX`).

## Umbrales que requieren validación

Los rangos de referencia son los ya definidos en la aplicación (temperatura ≤ 4 °C, pH 6,6–6,8, CE 4,0–5,5 mS/cm,
turbidez ≥ 50 NTU) y los umbrales de gravedad de las alertas existentes (pH ±0,2; CE +0,7/−0,5).
Los **márgenes de desviación leve/moderada/considerable** y los de tendencia/cambio importante (`AnalysisConfig`) son una
**propuesta de trabajo** y deben validarse con referencias técnicas y con mediciones reales del sensor.
Todos son configurables sin tocar la lógica.

## Modo DEMO

Nuevos escenarios en el selector de la pantalla Medir: *Solo pH*, *pH + CE*, *Temp + turbidez*, *3 alterados*,
*4 alterados*, *Leves* y *Sospechosa*, además de los existentes. Todo resultado se rotula como **simulado**.

## Pruebas

`MilkAnalyzerTest` (43), `LotAnalyzerTest` (9), `ForecastTest` (23), `VerdictTest` (8), `ProgressAnalyzerTest` (10), `DemoAnalysisScenariosTest` (2) y 3 pruebas nuevas del generador DEMO: 1 a 4 parámetros alterados, niveles leve,
moderado y crítico, repetición, tendencias ascendentes y descendentes, lecturas inválidas, resultados contradictorios,
ausencia de historial, filtro por lote y rotulado DEMO. Se ejecutan con `./gradlew test`.
