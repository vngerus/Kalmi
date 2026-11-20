# Kalmi v2 · Un solo nodo (Arduino Uno) + Palabras personalizables + IA preparada

Fecha: 2026-11-19 · Estado: borrador para aprobación

## 1. Intención

Kalmi ayuda a niñas y niños autistas (en general, no solo al usuario beta) a **expresar** lo que sienten, **regularse** y que su **cuidador se entere**. Esta versión:

- Reemplaza los dos nodos del informe (A niño + B cuidador) por **un solo dispositivo** (Arduino Uno como prototipo de prueba) que habla con la **app Android**, que asume el rol del Nodo B.
- Hace a Kalmi **más sociable**: además de emociones, el niño puede decir **1–2 palabras** de un vocabulario corto, como en la Comunicación Aumentativa y Alternativa (CAA).
- Es **funcional y didáctica ahora** (todo se demuestra en Tinkercad + app con simulador) y queda **preparada para un upgrade** (ESP32 con pictogramas, IA con consentimiento). El docente pide mostrar mejoras: este diseño es esa mejora y su hoja de ruta.

## 2. Objetivos, no-objetivos y anti-objetivos

**Objetivos (esta entrega)**
1. Sketch único para Arduino Uno que funciona en Tinkercad y en un Uno real, con el mismo protocolo que ya entiende la app.
2. Vocabulario: **núcleo fijo** (igual para todos) + **palabras personales** (las elige el cuidador desde la app).
3. La app recibe palabras, las **interpreta con reglas** (explicables, testeables, sin internet) y avisa al cuidador.
4. El cuidador confirma cada interpretación (**¿Acertó? Sí/No**): base de datos etiquetada para una IA futura.
5. Mascota "viva" en el LCD: parpadeo en reposo, ronroneo con presión suave, duerme con poca luz.
6. Informe actualizado con sección de mejoras y hoja de ruta.

**No-objetivos (quedan como upgrade documentado)**
- IA real (LLM en la nube o en el teléfono), voz/micrófono, pictogramas en pantalla, GPS, nube/Supabase, BLE real.

**Anti-objetivos (aunque funcione, sería un fracaso)**
- Que la IA **decida o hable por el niño**. Siempre sugiere ("Kalmi cree que…") y explica por qué.
- Que la mascota pida atención, sufra o tenga rachas/recordatorios (principios §1.4 del informe).
- Sobrecarga sensorial: sonidos, colores saturados, vibración fuerte.
- Enviar datos del menor fuera del teléfono sin consentimiento explícito.

## 3. Restricciones

- **Arduino Uno:** 32 KB flash, 2 KB RAM, 1 KB EEPROM. El informe ya reportó ~24 KB para el Nodo A → textos y caras en `PROGMEM`/`F()`.
- **Tinkercad:** sin Bluetooth ni micrófono; LCD 16×2 paralelo (8 caracteres personalizados); el **Monitor serie** hace de app (se escriben `ACK`/`CMD`/`CFG` a mano).
- **Pines del informe (§4) se mantienen.** Libres: 2 y 3. El enlace pasa de SoftwareSerial 2/3 a **`Serial` (pines 0/1)**.
- El niño puede no leer: en el Uno las palabras son texto (didáctico); en el ESP32 final, pictograma ARASAAC.
- Seguir `AGENTS.md`: dominio sin Android, patrón Screen + Contenido, estado del repo solo en Main, sin `Co-Authored-By`.

## 4. Enfoques considerados

| Enfoque | Qué es | Trade-off |
|---|---|---|
| A. Mascota viva | Solo animaciones y reacciones; IA solo en papel | Seguro, pero "interpretar" queda sin demostrar |
| **B. A + palabras + interpretación por reglas (elegido)** | Botón "Quiero", vocabulario, app interpreta y pide confirmación | Demostrable de punta a punta; IA preparada sin depender de ella |
| C. B + IA real ahora | LLM en la app, voz después | Más impacto, pero internet, claves y privacidad de un menor: riesgo alto para esta entrega |

Se eligió **B**: real y demostrable, deja piezas claras para repartir, y C queda como upgrade con camino definido.

## 5. Diseño

### 5.1 Hardware (Tinkercad y Uno real)

| Elemento | Pin | Nota |
|---|---|---|
| Botones Bien/Triste/Frustrado/Muy enojado | 4 / 5 / 6 / 7 | `INPUT_PULLUP`, igual que el informe |
| Respirar / Ayuda | 8 / 12 | igual |
| **Quiero (nuevo)** | **2** | `INPUT_PULLUP` |
| LDR / FSR | A0 / A1 | igual |
| LCD RS, E, D4–D7 | 10, 11, A2–A5 | igual |
| LED brillo (PWM) / motor | 9 / 13 | igual (motor con transistor) |
| **Enlace** | **0/1 (`Serial`, 9600)** | Tinkercad: Monitor serie. Uno real: módulo **HM-10** en 0/1 (divisor de tensión en su RX; desconectarlo al subir el sketch) |

Usar `Serial` en ambos casos evita cualquier `#define`: el mismo sketch sirve para Tinkercad y para el Uno real.

### 5.2 Vocabulario

| Índice | Tipo | Por defecto | Editable |
|---|---|---|---|
| 0–3 | **Núcleo** | Quiero · No · Más · Parar | No |
| 4–9 | **Personales** | Agua · Comer · Baño · Duele · Abrazo · Jugar | Sí, desde la app |

- Máximo 10 palabras: el barrido con un botón sigue siendo corto.
- Texto en el dispositivo: máximo 10 caracteres ASCII, sin comas ni `*` (la app normaliza acentos; la ñ la resuelve quien construya: carácter personalizado o "n").

### 5.3 Interacción en el dispositivo

- **Barrido con un botón:** cada toque de **Quiero** muestra la siguiente palabra (núcleo primero). Si pasan **3 s sin tocar**, la palabra queda elegida. Hay **5 s** para elegir una segunda palabra; si no, se envía.
- Pantalla: cara + palabra + posición (`3/10`). Al enviar: "Aviso listo"; al recibir `ATN`: "Ya viene alguien".
- **Ayuda** sigue funcionando siempre, en cualquier estado (principio "Ayuda a un toque").
- **Mascota viva:** parpadeo cada 4–8 s en reposo; presión suave → ronroneo (cara + vibración breve); presión fuerte sostenida → invita a respirar; luz baja → duerme y baja el brillo.
- Máquina de estados: reposo, emoción, respiración, ayuda, **palabras**, mensaje, atendido, noche. Todo con `millis()`, sin `delay()`.

### 5.4 Protocolo (cambios sobre la guía §3)

Formato igual: `CUERPO*XX` (XOR en hexadecimal) + salto de línea.

| Mensaje | Dirección | Cambio |
|---|---|---|
| `TEL,seq,luz,presion` | Kalmi → App | **Sin lat/lon** (no hay ubicación en esta etapa) |
| `EVT,seq,E1..E5` | Kalmi → App | Igual |
| **`PAL,seq,i` / `PAL,seq,i,j`** | Kalmi → App | **Nuevo.** 1–2 índices de palabra. Lleva `ACK` y reintentos como `EVT` (3 × 1,5 s) |
| `ACK,seq` / `CMD,id,…` / `OK,id` | — | Igual (incluye `ASK`) |
| **`CFG,PAL,i,texto`** | App → Kalmi | **Nuevo.** Guarda la palabra personal `i` (4–9) en la EEPROM |
| **`CFG,PAL,OK` / `CFG,PAL,ERR`** | Kalmi → App | Respuesta |
| `CFG,ID?` → `CFG,ID,kalmi-uno-XXXX` | ambos | Responder con un ID fijo guardado en la EEPROM |
| `BAT` | — | El Uno no lo envía (la app ya tolera batería desconocida) |

- **`seq` se guarda en la EEPROM** (cada N envíos o al cambiar de estado) para que no vuelva a 1 al reiniciar. Así se corrige del lado del firmware el bug "eventos perdidos al reiniciar".
- La app envía la lista personal completa (`CFG,PAL` × 6) **al conectar** y cada vez que el cuidador la edita. La app es la dueña de la lista.

### 5.5 EEPROM del Uno

| Dirección | Contenido |
|---|---|
| 0–1 | Marca de formato (si no coincide: cargar valores por defecto) |
| 2–5 | Último `seq` (uint32) |
| 6–13 | ID del dispositivo |
| 14–79 | 6 palabras personales × 11 bytes (10 + terminador) |

### 5.6 App Android

- **Dominio:**
  - `MensajeKalmi.Pal(seq, indices)`.
  - `Protocolo.cfgPalabra(i, texto)`.
  - `Palabra` con el núcleo fijo.
  - `Interpretacion(texto, razon)`.
  - El historial debe poder guardar eventos de **emoción** y de **palabras**. Cómo modelarlo (`Evento` con `emocion` opcional y `palabras`, o un tipo sellado) lo decide quien construya, cuidando no romper Inicio, Historial y los tests.
- **Interpretación, preparada para la IA:**
  - `interface Interpretador { fun interpretar(palabras: List<Palabra>, contexto: Contexto): Interpretacion }`.
  - **`ReglasInterpretador` (ahora):** usa plantillas por combinación ("Quiero + X" → "quiere X"; "No + X" → "no quiere X"; "Duele" + emoción reciente → "dice que le duele; antes marcó Frustrado"). El `Contexto` incluye la última emoción en los últimos 5 min, la presión alta y la hora.
  - **`IaInterpretador` (upgrade):** misma interfaz; se cambia en `KalmiApp` como Fake ↔ BLE.
  - **La razón siempre se muestra.**
- **Repositorio:** trata `PAL` como `EVT` (ACK siempre, deduplicación por `seq`, alerta). Envía `CFG,PAL` al conectar. Guarda la respuesta del cuidador **¿Acertó?** junto al evento.
- **Persistencia de la lista personal:** `SharedPreferences` (alcanza para 6 textos). Room sigue siendo el paso 5 para el historial.
- **UI:**
  - **Inicio:** la alerta de palabras muestra la interpretación, el "porque…" y los botones **Sí / No**, además de "Marcar como atendido".
  - **Nueva sección "Palabras de Kalmi":** el núcleo fijo es visible y no editable; las 6 personales son editables; botón "Enviar a Kalmi". Quien construya decide si va como 5.ª pestaña o dentro de Conexión.
  - **Historial:** muestra las palabras y si la interpretación acertó.
- **Simulador** (`FakeKalmiDevice`): emite `PAL` (aleatorio y desde el panel de depuración) y responde `CFG,PAL,OK`.
- **Tests:** parser de `PAL` y `CFG,PAL`, `ReglasInterpretador` (cada plantilla, más el caso sin contexto) y el repositorio (`PAL` duplicado aparece una vez y recibe ACK siempre).

### 5.7 Informe (upgrade)

Agregar una sección **"Mejoras v2"** y corregir:

- **Arquitectura:**
  - 1 nodo + app (antes A + B).
  - Enlace BLE (antes ESP-NOW).
  - Tinkercad: Monitor serie.
- **Protocolo:** `PAL`, `CFG,PAL` y `TEL` sin GPS.
- **Componentes:** sale el GPS; entran el botón Quiero y el HM-10 (Uno real).
- **Seguridad:**
  - Configuración solo por el enlace emparejado.
  - Datos mínimos, sin ubicación.
  - La IA, solo con consentimiento y datos anonimizados.
- **Hoja de ruta:** Uno (prototipo de prueba) → ESP32-S3 + OLED con pictogramas ARASAAC → IA que sugiere con explicación y confirmación → voz (micrófono I2S) solo si se valida.
- **Validación:** revisión del vocabulario con fonoaudiólogo o terapeuta ocupacional.

### 5.8 Fuera del alcance

- **Mockup:** el mockup de la actividad formativa (individual) es otra entrega; puede usar las pantallas de este diseño.

## 6. Decidido por el usuario

- Un solo nodo Arduino Uno que habla con la app; Tinkercad para emularlo.
- Enfoque B.
- Lista de palabras personalizable desde la app.
- Base estandarizada + personalización.
- IA solo como mejora futura, pero con la arquitectura preparada.
- Informe actualizado con mejoras.

## 7. Decisiones de Claude (corregibles)

- Núcleo **Quiero · No · Más · Parar** y lista personal por defecto.
- Botón Quiero en el pin 2, barrido con 3 s / 5 s.
- Enlace por `Serial` 0/1 (también con el HM-10).
- Máximo 10 palabras de 10 caracteres.
- `SharedPreferences` para la lista.
- Formato exacto de `PAL` y `CFG,PAL`.
- `seq` en la EEPROM.

## 8. Para quien construya

- Mapa exacto de caracteres personalizados del LCD (caras, ñ).
- Cada cuántos envíos se guarda `seq` en la EEPROM (desgaste: ~100 000 escrituras).
- Modelo del historial (emoción + palabras).
- Ubicación de la pantalla "Palabras".
- Textos exactos de las plantillas.

**Por verificar en Tinkercad:** que la librería `EEPROM` persista dentro de la simulación y que el sketch quepa en la flash con el modo palabras.
