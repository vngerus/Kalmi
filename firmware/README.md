# Firmware Kalmi (Arduino Uno)

| Sketch | Qué es |
|---|---|
| `kalmi-uno/` | Kalmi: botones de emoción, Respirar, Ayuda, **Quiero** (palabras), LDR, sensor de fuerza, LCD 16×2, LED y motor. Enlace por `Serial` (0/1, 9600). |
| `kalmi-telefono/` | Solo para Tinkercad: un segundo Uno que hace de app (mismo protocolo). En la vida real lo reemplaza el teléfono. |

- Guía de armado en Tinkercad (plano, cableado, código y guion de prueba): https://claude.ai/artifact/VcuuxJNpaGneWWUh74uXmu (privada: compártela desde su menú Share).
- Uno real + app: módulo BLE **HM-10** en los pines 0/1 (divisor de tensión 1 kΩ/2 kΩ en su RX; desconéctalo al subir el sketch). En la app: Ajustes → apagar Modo de prueba.
- Compilado y verificado con `arduino-cli compile -b arduino:avr:uno` (Kalmi 41 % flash / 46 % RAM; Teléfono 29 % / 36 %).
- Contrato con la app: `app/src/test/.../ContratoArduinoTest.kt`.
