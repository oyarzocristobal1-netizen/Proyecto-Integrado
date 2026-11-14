# Protocolo de datos entre la caja de sensores y la app

> **Estado:** propuesto e implementado en la app, **pendiente de validar con la caja real**.
> Si el firmware final usa otro formato, se ajusta `sensor/SensorFrameParser.kt` y sus pruebas.

## Transporte

- Bluetooth clásico, perfil serie (SPP, UUID `00001101-0000-1000-8000-00805F9B34FB`).
  Sirve para HC-05/HC-06 y para `BluetoothSerial` del ESP32.
- La caja debe estar **emparejada** en los ajustes de Bluetooth del teléfono antes de conectarla desde la app.
- Texto ASCII, **una lectura por línea**, terminada en `\n` (se acepta `\r\n`).
- Frecuencia sugerida: una lectura cada 2 a 3 segundos.

## Formato principal

```
DATA:<temperatura>,<ph>,<conductividad>,<turbidez>
```

Ejemplo: `DATA:3.8,6.70,4.80,85.0`

| Posición | Magnitud | Unidad | Decimales sugeridos |
|---|---|---|---|
| 1 | Temperatura | °C | 1 |
| 2 | pH | — | 2 |
| 3 | Conductividad eléctrica | mS/cm | 2 |
| 4 | Turbidez | % (100 = opaca) | 1 |

- Decimales con **punto** (`6.70`). Una coma decimal se rechaza para no confundirla con el separador.
- Deben venir los 4 valores. Si falta uno o no es un número, la línea completa se descarta.

## Formatos alternativos aceptados

- Clave:valor en una línea, en cualquier orden: `T:3.8,PH:6.70,CE:4.80,TURB:85.0`
  (claves válidas: `T`/`TEMP`/`TEMPERATURA`, `PH`, `CE`/`COND`/`CONDUCTIVIDAD`, `TU`/`TURB`/`TURBIDEZ`).
- Ciclo de 4 líneas (`Temperatura: 3.8`, `pH: 6.70`, `Conductividad: 4.80`, `Turbidez: 85.0`).
  Si el ciclo queda incompleto, no se completa con valores inventados.
- Cualquier otra línea (mensajes de depuración, líneas vacías) se ignora.

## Validación en la app

Cada lectura recibida pasa por `MeasurementValidator`:

| Regla | Límite | Ejemplo de rechazo |
|---|---|---|
| Temperatura | −5 a 80 °C | `-127.0` (DS18B20 desconectado) |
| pH | 0 a 14 | `15.2` |
| Conductividad | 0 a 20 mS/cm | `-1.0` |
| Turbidez | 0 a 100 % | `140` |
| Antigüedad | máximo 15 s al registrar | lectura vieja en pantalla |
| Repetida | no se registra dos veces la misma lectura | doble toque en Registrar |

Las lecturas rechazadas se cuentan en la tarjeta de la caja junto con el motivo del último rechazo.
Para registrar, además, las **últimas 4 lecturas deben ser estables** (ver `StabilityTracker`).

## Código de referencia

En la app (Más → Código de referencia de la caja, solo administrador) hay un ejemplo para Arduino + HC-05 que
envía el formato principal. Es una referencia: las fórmulas de pH, conductividad y turbidez **no están
calibradas** y deben reemplazarse por las curvas obtenidas al calibrar cada sensor.

## Pendiente de validar con el hardware

- [ ] La caja envía exactamente el formato principal (o se actualiza el parser).
- [ ] Emparejamiento y conexión desde Android 12+ (permiso "Dispositivos cercanos").
- [ ] Comportamiento al perder la señal y al reconectar.
- [ ] Ruido real de cada sensor frente a los umbrales de estabilidad.
- [ ] Calibración de pH, conductividad, turbidez y temperatura.
- [ ] Decidir ESP32 (Bluetooth clásico o BLE) o Arduino + HC-05. Con BLE se agrega otra implementación de `SensorSource`.
