# Kalmi v2 (palabras + Uno + BLE HM-10) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kalmi becomes one Arduino Uno device that talks to the Android app, with a small word vocabulary (fixed core words plus caregiver-edited personal words) that the app interprets with rules. It ships as two adjustments: Tinkercad (Uno "Kalmi" + Uno "Teléfono") and the app (words + real BLE link to an HM-10).

**Architecture:** The protocol grows by `PAL` (device → app) and `CFG,PAL` (app → device). The app adds an `Interpretador` interface: today `ReglasInterpretador`, later an AI implementation, swapped in `KalmiApp`. The repository treats `PAL` like `EVT` (ACK, dedupe, alert) and pushes the personal list on connect. `BleKalmiDevice` implements `KalmiDevice` with configurable UUID profiles (HM-10 / NUS). There are two Arduino sketches in `firmware/`, and a Tinkercad guide is published as an artifact.

**Tech Stack:** Kotlin, Jetpack Compose, coroutines and Flow, JUnit + kotlinx-coroutines-test, Android BLE APIs, Arduino C++ (LiquidCrystal, EEPROM, SoftwareSerial for plan B).

**Spec:** `docs/superpowers/specs/2026-11-19-kalmi-uno-palabras-design.md`

## Global Constraints

- **Vocabulary:**
  - Indices 0–3 are the core: `Quiero`, `No`, `Más`, `Parar`. They are not editable.
  - Indices 4–9 are the personal words. Defaults: `Agua`, `Comer`, `Baño`, `Duele`, `Abrazo`, `Jugar`.
  - At most 10 words. On the device, each word is ≤10 ASCII characters, with no `,` or `*`.
- **Protocol:**
  - `PAL,seq,i` / `PAL,seq,i,j` (1–2 indices, each 0–9). ACK and retries work like `EVT`: 3 tries, 1.5 s apart.
  - `CFG,PAL,i,texto` with i in 4–9. Replies: `CFG,PAL,OK` / `CFG,PAL,ERR`.
  - `TEL,seq,luz,presion` has no lat/lon.
  - `CFG,ID?` → `CFG,ID,kalmi-uno-XXXX`.
  - The Uno does not send `BAT`.
- **Word selection on the device:** button Quiero on pin 2. Each press moves to the next word. After 3 s idle the word is chosen; the child then has 5 s to pick a second word.
- **AI never decides:** every interpretation shows its reason ("porque…") and the caregiver confirms with **¿Acertó? Sí/No**.
- **Code rules:**
  - Domain stays free of Android/Compose.
  - Screens follow the Screen + Contenido pattern with `@Preview`.
  - Repository state is mutated only on the Main scope.
  - UI text is in Spanish with a calm tone.
- **Commits:** Conventional Commits in Spanish, **no `Co-Authored-By`**. Work on branch `feat/kalmi-v2-palabras`.
- **Verify** each task with `./gradlew :app:assembleDebug :app:testDebugUnitTest`.

## Review Focus

1. **Personal word with accents, ñ, a comma or more than 10 chars** (e.g. "Baño", "jugo, pan"). It must arrive sanitized on the device and never break the line format. → test in Task 1.
2. **`PAL` index out of range or more than 2 indices** (`PAL,5,12`, `PAL,5,1,2,3`). It must be discarded (counted as `descartados`), not crash. → test in Task 1.
3. **A `PAL` retry arriving after an `EVT` with a higher seq.** It shows once and is always ACKed. → test in Task 3.
4. **The caregiver edits the list while disconnected.** It is saved, and sent on the next connect. → test in Task 3.
5. **The caregiver answers ¿Acertó? on an event that is no longer the current alert.** It updates the history entry by seq without touching the current alert. → test in Task 3.

---

### Task 1: Dominio (vocabulario, Evento con palabras, protocolo PAL/CFG,PAL)

**Files:**
- Modify: `app/src/main/java/cl/kalmi/app/domain/Models.kt`, `app/src/main/java/cl/kalmi/app/domain/Protocolo.kt`
- Test: `app/src/test/java/cl/kalmi/app/ProtocoloTest.kt`

**Interfaces:**
- **Produces:**
  - `object Vocabulario { val NUCLEO: List<String>; val PERSONALES_DEFECTO: List<String>; const val PERSONALES = 6; fun completo(personales: List<String>): List<String> }`
  - `data class Interpretacion(val texto: String, val razon: String)`
  - `Evento(seq, emocion: Emocion?, momento, canal, atendido, atendidoEn, palabras: List<String> = emptyList(), interpretacion: Interpretacion? = null, acerto: Boolean? = null)`. `emocion == null` means a word event.
  - `MensajeKalmi.Pal(seq: Int, indices: List<Int>)`
  - `Protocolo.cfgPalabra(i: Int, texto: String): String`
  - `Protocolo.sanear(texto: String): String`: NFD, drops diacritics (ñ→n), drops `,` and `*`, trims, cuts to 10.

- [ ] **Step 1: Failing tests in `ProtocoloTest`**

```kotlin
@Test fun palConUnaYDosPalabras() {
    assertEquals(MensajeKalmi.Pal(7, listOf(0, 8)), Protocolo.parsear(Protocolo.firmar("PAL,7,0,8")))
    assertEquals(MensajeKalmi.Pal(8, listOf(5)), Protocolo.parsear(Protocolo.firmar("PAL,8,5")))
}
@Test fun palInvalidoSeDescarta() {
    assertNull(Protocolo.parsear(Protocolo.firmar("PAL,5,12")))
    assertNull(Protocolo.parsear(Protocolo.firmar("PAL,5,1,2,3")))
    assertNull(Protocolo.parsear(Protocolo.firmar("PAL,5")))
}
@Test fun cfgPalabraSaneaTexto() {
    assertEquals(Protocolo.firmar("CFG,PAL,6,Bano"), Protocolo.cfgPalabra(6, "Baño"))
    assertEquals(Protocolo.firmar("CFG,PAL,4,jugo pan"), Protocolo.cfgPalabra(4, "jugo, pan"))
    assertEquals("Superheroe", Protocolo.sanear("Superhéroes favoritos"))
}
```

- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests "cl.kalmi.app.ProtocoloTest"`. Expected: fails to compile (Pal / cfgPalabra missing).
- [ ] **Step 3: Implement.**
  - Make `Evento.emocion` nullable.
  - Fix every compile error in `ui/` the minimal way. A null emotion uses `KalmiColors.Calma`: add `val Evento.colorTema` and `val Evento.fondoTema` in `ui/theme/Theme.kt` and use them in Inicio and Historial.
  - Fix `KalmiRepositoryTest` the same way if needed.
  - `"PAL"` parsing: requires 1–2 indices, each in 0..9, else null.
  - `cfgPalabra` validates `i in 4..9` (`require`).
- [ ] **Step 4:** Run the full `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Expected: BUILD SUCCESSFUL, all tests green.
- [ ] **Step 5:** Commit `feat(domain): vocabulario y mensajes PAL/CFG,PAL`.

### Task 2: ReglasInterpretador

**Files:**
- Create: `app/src/main/java/cl/kalmi/app/domain/Interpretador.kt`
- Test: `app/src/test/java/cl/kalmi/app/ReglasInterpretadorTest.kt`

**Interfaces:**
- **Consumes:** `Interpretacion`, `Emocion` (Task 1).
- **Produces:**
  - `data class Contexto(val emocionReciente: Emocion? = null, val presionAlta: Boolean = false)`. `emocionReciente` is the last emotion within 5 min; the repository computes it. The spec's "hora" is omitted because no template uses it.
  - `fun interface Interpretador { fun interpretar(palabras: List<String>, contexto: Contexto): Interpretacion }`
  - `class ReglasInterpretador : Interpretador`

- [ ] **Step 1: Failing tests.** Each case is one `@Test` with assertions on `texto` and `razon`.

| Input | Expected `texto` | Expected `razon` contains |
|---|---|---|
| `["Quiero","Abrazo"]` | `"Quiere abrazo"` | `"Quiero + Abrazo"` |
| `["No","Jugar"]` | `"No quiere jugar"` | — |
| `["Más","Agua"]` | `"Quiere más agua"` | — |
| `["Parar"]` | `"Quiere parar"` | — |
| `["Duele"]` with `Contexto(emocionReciente = FRUSTRADO)` | `"Dice que le duele algo"` | `"hace poco marcó Frustrado"` |
| `["Agua"]` | `"Dice: agua"` | — |
| `["Agua","Jugar"]` (no template) | `"Dice: agua + jugar"` | — |
| any input with `presionAlta = true` | — | `"presión alta"` |

- [ ] **Step 2:** Run `--tests "cl.kalmi.app.ReglasInterpretadorTest"`. Expected: FAIL (class missing).
- [ ] **Step 3: Implement.** The second word goes in lowercase. `"Duele"` in any position wins over the other templates. The context clauses are appended to `razon` with `"; "`.
- [ ] **Step 4:** Run the same test. Expected: PASS.
- [ ] **Step 5:** Commit `feat(domain): interpretador de palabras por reglas`.

### Task 3: Repositorio (PAL, lista personal, ¿Acertó?)

**Files:**
- Modify: `app/src/main/java/cl/kalmi/app/data/KalmiRepository.kt`, `app/src/main/java/cl/kalmi/app/KalmiApp.kt`
- Test: `app/src/test/java/cl/kalmi/app/KalmiRepositoryTest.kt`

**Interfaces:**
- **Consumes:** Tasks 1–2.
- **Produces:**
  - Constructor `KalmiRepository(device, scope, reloj = …, interpretador: Interpretador = ReglasInterpretador(), personalesIniciales: List<String> = Vocabulario.PERSONALES_DEFECTO, guardarPersonales: (List<String>) -> Unit = {})`
  - `val personales: StateFlow<List<String>>`
  - `suspend fun actualizarPersonales(lista: List<String>)`: requires size 6; sanitizes; saves; sends `CFG,PAL` × 6 if connected.
  - `fun marcarAcierto(seq: Int, acerto: Boolean)`

- [ ] **Step 1: Failing tests in `KalmiRepositoryTest`.** Reuse `DispositivoDePrueba`; make `estado` start `Desconectado` where needed.
  - `palabrasSeInterpretanYGeneranAlerta`: `PAL,3,0,8` → alert with `palabras == ["Quiero","Abrazo"]`, `interpretacion.texto == "Quiere abrazo"`, ACK sent.
  - `palDuplicadoTrasEvtApareceUnaVez`: `PAL,3,0,8`, `EVT,4,E2`, `PAL,3,0,8` → history size 2, `ack(3)` sent twice.
  - `palUsaEmocionReciente`: `EVT,1,E3` then `PAL,2,7` → razon contains `"Frustrado"`.
  - `listaSeEnviaAlConectar`: estado → `Conectado` → `enviadas` has 6 lines starting `CFG,PAL,` for i 4..9.
  - `editarDesconectadoSeEnviaAlReconectar`: Desconectado, `actualizarPersonales(…)` → no CFG sent and `guardarPersonales` called; then Conectado → new list sent.
  - `aciertoActualizaHistorialSinTocarAlertaActual`: two events, `marcarAcierto(seqDelPrimero, true)` → first entry `acerto == true`, `alertaActual` still the second.
- [ ] **Step 2:** Run `--tests "cl.kalmi.app.KalmiRepositoryTest"`. Expected: the new tests FAIL.
- [ ] **Step 3: Implement.**
  - Handle `is MensajeKalmi.Pal` like `Evt`: ACK, `registrarSeq`, shared `eventosVistos`.
  - Resolve indices with `Vocabulario.completo(personales.value)`.
  - Build `Contexto` from the history (latest event with `emocion != null` and `momento >= reloj() - 5 min`) and the last telemetry (`presion >= 75`).
  - `init` collects `device.estado` and sends the list on each `Conectado`.
  - `KalmiApp` wires `SharedPreferences("kalmi")` key `"personales"`, joined with `"\n"`.
- [ ] **Step 4:** Run the full build + tests. Expected: green.
- [ ] **Step 5:** Commit `feat(data): palabras, lista personal y confirmación de interpretación`.

### Task 4: Simulador emite PAL y responde CFG

**Files:**
- Modify: `app/src/main/java/cl/kalmi/app/device/FakeKalmiDevice.kt`, `app/src/main/java/cl/kalmi/app/ui/conexion/ConexionScreen.kt`

**Interfaces:**
- **Produces:** `FakeKalmiDevice.simularPalabras(indices: List<Int>)`, with the same retry loop as events.

- [ ] **Step 1: Implement.**
  - Extract the existing retry loop of `emitirEvento` into `emitirConReintento(cuerpo: (Int) -> String)` and reuse it for `PAL`.
  - Random events: 1 in 3 are `PAL` with 1–2 random indices.
  - `enviar("CFG,PAL,…")` → emit `CFG,PAL,OK`.
  - Debug panel in Conexión: button "Simular palabras" → random 1–2 indices. Add the parameter `onSimularPalabras: (() -> Unit)?` to `ConexionContenido`.
- [ ] **Step 2:** Run the full build + tests. Expected: green. This is UI and simulator behaviour without logic tests; manual check in Task 5.
- [ ] **Step 3:** Commit `feat(device): el simulador envía palabras`.

### Task 5: UI (alerta de palabras, historial, pestaña Palabras)

**Files:**
- Modify: `app/src/main/java/cl/kalmi/app/ui/inicio/InicioScreen.kt`, `app/src/main/java/cl/kalmi/app/ui/historial/HistorialScreen.kt`, `app/src/main/java/cl/kalmi/app/ui/KalmiViewModel.kt`, `app/src/main/java/cl/kalmi/app/MainActivity.kt`
- Create: `app/src/main/java/cl/kalmi/app/ui/palabras/PalabrasScreen.kt`

**Interfaces:**
- **Consumes:** `personales`, `actualizarPersonales`, `marcarAcierto` (Task 3).
- **Produces:**
  - ViewModel adds `val personales`, `fun guardarPersonales(lista: List<String>)` and `fun marcarAcierto(seq: Int, acerto: Boolean)`.
  - `PalabrasContenido(personales: List<String>, conectado: Boolean, onGuardar: (List<String>) -> Unit, modifier)`

- [ ] **Step 1: Implement.**
  - **Inicio:** `TarjetaAlerta` for an event with `emocion == null` shows:
    - title `interpretacion.texto`;
    - `"porque: " + razon`;
    - chips with the words;
    - row "¿Acertó?" with `Sí` / `No` (hidden once `acerto != null`);
    - plus "Marcar como atendido".
  - **Historial:** a word row shows `palabras.joinToString(" + ")`, the interpretation, and "✓ acertó" / "✗ no acertó" when set.
  - **Pestaña `PALABRAS("Palabras", Icons.Filled.Edit)`** between Historial and Conexión:
    - core shown read-only;
    - 6 `OutlinedTextField` capped at 10 chars;
    - button "Guardar y enviar a Kalmi";
    - text "Se enviará al conectar" when disconnected.
  - **Previews** for the word alert and `PalabrasContenido`.
- [ ] **Step 2:** Run the full build + tests. Expected: green.
- [ ] **Step 3: Manual check** in the emulator (`./gradlew :app:installDebug`):
  1. Connect the simulator.
  2. "Simular palabras" → word alert with reason, Sí/No.
  3. Edit a personal word → Diagnóstico shows no new `descartados`.
- [ ] **Step 4:** Commit `feat(ui): alerta de palabras, historial y pestaña Palabras`.

### Task 6: BLE real (HM-10 / NUS), permisos e interruptor debug

**Files:**
- Create: `app/src/main/java/cl/kalmi/app/device/ble/PerfilBle.kt`, `app/src/main/java/cl/kalmi/app/device/ble/Lineas.kt`, `app/src/main/java/cl/kalmi/app/device/ble/BleKalmiDevice.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/java/cl/kalmi/app/KalmiApp.kt`, `app/src/main/java/cl/kalmi/app/ui/conexion/ConexionScreen.kt`, `app/src/main/java/cl/kalmi/app/ui/KalmiViewModel.kt`
- Test: `app/src/test/java/cl/kalmi/app/LineasTest.kt`

**Interfaces:**
- **Produces:**
  - `data class PerfilBle(val servicio: UUID, val tx: UUID, val rx: UUID) { companion object { val HM10; val NUS } }`
    - HM10 = service `0000FFE0-0000-1000-8000-00805F9B34FB`, tx = rx = `0000FFE1-…`.
    - NUS uses the UUIDs from guide §9.1.
  - `class AcumuladorLineas { fun agregar(bytes: ByteArray): List<String> }`
  - `fun trozos(linea: String, max: Int = 20): List<ByteArray>`
  - `class BleKalmiDevice(context: Context, scope: CoroutineScope, perfil: PerfilBle) : KalmiDevice`
  - `KalmiApp.usarDispositivoReal: Boolean`, stored in SharedPreferences `"real"`.

- [ ] **Step 1: Failing `LineasTest`:**

```kotlin
@Test fun acumulaYCortaPorSaltoDeLinea() {
    val a = AcumuladorLineas()
    assertEquals(emptyList<String>(), a.agregar("TEL,1,7".toByteArray()))
    assertEquals(listOf("TEL,1,70,20*3A", "OK,2*1B"), a.agregar("0,20*3A\r\nOK,2*1B\n".toByteArray()))
}
@Test fun trozosDe20Bytes() {
    val t = trozos("CMD,10,MSG,1*4F\nCFG,PAL,4,Abrazo*11\n")
    assertTrue(t.all { it.size <= 20 })
    assertEquals("CMD,10,MSG,1*4F\nCFG,PAL,4,Abrazo*11\n", t.joinToString("") { it.decodeToString() })
}
```

- [ ] **Step 2:** Run `--tests "cl.kalmi.app.LineasTest"`. Expected: FAIL.
- [ ] **Step 3: Implement `Lineas.kt`, then `BleKalmiDevice`** following guide §9.2–9.3, with:
  - **Scan:** `ScanFilter` on `perfil.servicio`, 10 s timeout → `EstadoConexion.Error("No se encontró Kalmi")`.
  - **Write:** `trozos(linea + "\n")` sent one GATT write at a time, waiting for `onCharacteristicWrite` (existing queue pattern). HM-10 uses `WRITE_TYPE_NO_RESPONSE` if the characteristic lacks `PROPERTY_WRITE`.
  - **Disconnect:** set `Desconectado`. No auto-reconnect; that is a non-goal.
- [ ] **Step 4: Manifest and permissions.**
  - **Manifest:** permissions from guide §5.3, Bluetooth only (`BLUETOOTH_SCAN` neverForLocation, `BLUETOOTH_CONNECT`, legacy ≤30 with `ACCESS_FINE_LOCATION`). Use `uses-feature … bluetooth_le android:required="false"` so the emulator can still install.
  - **Conexión:**
    - when real mode is on, "Conectar" first requests the runtime permissions (`rememberLauncherForActivityResult(RequestMultiplePermissions)`), with a line explaining why;
    - only in debug, a `Switch` "Usar Kalmi real (HM-10)" that saves to prefs, shown with the text "Se aplica al reiniciar la app".
  - **`KalmiApp`:** picks `BleKalmiDevice(this, appScope, PerfilBle.HM10)` when real (always in release), else `FakeKalmiDevice`. Add `ponytail:` comment "elige al iniciar; cambio en caliente si molesta en demos".
- [ ] **Step 5:** Run the full build + tests. Expected: green. BLE end-to-end needs a physical phone + Uno + HM-10; it cannot be verified here. Say so in the report.
- [ ] **Step 6:** Commit `feat(device): conexión BLE con HM-10 y permisos`.

### Task 7: Sketches Arduino (Kalmi + Teléfono)

**Files:**
- Create: `firmware/kalmi-uno/kalmi-uno.ino`, `firmware/kalmi-telefono/kalmi-telefono.ino`, `firmware/README.md`

**Interfaces:**
- **Consumes:** the protocol (Global Constraints) and the pins from spec §5.1 and §5.8.
- **Produces:** two single-file sketches that paste directly into Tinkercad. They must not exceed 32 KB of flash on the Uno.

- [ ] **Step 1: `kalmi-uno.ino`**
  - Pins: spec §5.1. `Serial` at 9600 is the link.
  - Non-blocking state machine (reposo, emoción, respiración 4+4 s, ayuda, palabras, mensaje, atendido, noche).
  - XOR checksum; EVT/PAL retries 3 × 1.5 s until ACK; CMD dedupe by last id.
  - EEPROM layout from spec §5.5, saving `seq` every 10 sends.
  - Faces as LCD custom chars; texts with `F()` / `PROGMEM`.
  - "Mascota viva": blink every 4–8 s, purr on light press, sleep with low light.
  - Sends `TEL` every 2 s.
  - Responds to `CFG,PAL`, `CFG,ID?`, and `CMD` ATN/MSG/RESP/BRI/ASK with `OK,id`.
- [ ] **Step 2: `kalmi-telefono.ino`**
  - LCD on the same pins.
  - Buttons: Atendido 4, Mensaje 5, Respirar 6, Sí 7, No 8. Pot A0. Motor 9.
  - ACK + dedupe + lost/descartados count.
  - Tension rule: 3 readings ≥75, re-arm <60.
  - Interprets `PAL` with the same templates as Task 2, compact.
  - `CMD` with 2 retries, 1 s each, and RTT on the LCD.
  - Sends `CFG,PAL` × 6 defaults at start.
  - `BRI` only when the pot changes ≥5.
- [ ] **Step 3: Compile check.** Download portable `arduino-cli` into the scratchpad, `core install arduino:avr`, then `arduino-cli compile -b arduino:avr:uno firmware/kalmi-uno` and the same for `kalmi-telefono`. Expected: success and flash < 32256 bytes. If arduino-cli can't be installed, report that compilation is unverified.
- [ ] **Step 4:** `firmware/README.md`: three lines (what each sketch is, the Tinkercad guide link, the HM-10 wiring note).
- [ ] **Step 5:** Commit `feat(firmware): sketches Kalmi y Teléfono para Uno`.

### Task 8: Artifact "Guía Tinkercad"

**Files:**
- Create: `<scratchpad>/tinkercad-kalmi.html` (published as an artifact)

- [ ] **Step 1:** Run `Artifact` quickstart (`intent: "other"`), then write the page:
  - parts list per board;
  - wiring tables (spec §5.1 and §5.8);
  - inline SVG schematic of both Unos and the crossed serial link;
  - both sketches with copy buttons (from Task 7 files);
  - step-by-step Tinkercad build;
  - test script: what to press, what each Serial Monitor must show, and how to show loss/RTT;
  - plan B (SoftwareSerial 2/3);
  - how the real Uno + HM-10 replaces the Teléfono.
- [ ] **Step 2:** Publish and give the link.

### Task 9: Docs

**Files:**
- Modify: `AGENTS.md` (protocol line: add `PAL`, `CFG,PAL`; mention `Interpretador`, `firmware/`), `docs/TAREAS.md` (new "v2" section with done/pending, incl. the BLE physical test as pending)

- [ ] **Step 1:** Edit both files.
- [ ] **Step 2:** Commit `docs: tareas y guía de agentes para v2`. Run the full build + tests one last time.
