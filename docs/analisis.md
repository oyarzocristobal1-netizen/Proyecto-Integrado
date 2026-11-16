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

`MilkAnalyzerTest` (43), `LotAnalyzerTest` (7) y `DemoAnalysisScenariosTest` (2): 1 a 4 parámetros alterados, niveles leve,
moderado y crítico, repetición, tendencias ascendentes y descendentes, lecturas inválidas, resultados contradictorios,
ausencia de historial, filtro por lote y rotulado DEMO. Se ejecutan con `./gradlew test`.
