# AGENTS.md

Instructions for AI coding agents (Claude Code, Copilot, Cursor, Codex, etc.) working in this repository. `CLAUDE.md` only imports this file, so this is the single source of truth.

## Before any task (mandatory)

1. Read `docs/TAREAS.md` first. It holds the task status, the branch/commit/PR workflow, and the code conventions.
   @docs/TAREAS.md
2. Read the relevant section of `docs/kalmi-guia-app.md` before writing code for a feature. The section numbers (§) are listed in each `TODO(colega)` and in TAREAS.md.
3. Work on a branch, never directly on `main` (docs-only fixes excepted). Follow the commit format in TAREAS.md, and **never add a `Co-Authored-By` trailer**.
4. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` before claiming a task is done.

## Project

Kalmi: Android caregiver app (Kotlin + Jetpack Compose) for a child's emotion device (ESP32-S3). The device sends emotion events and telemetry over BLE (Nordic UART) or, when far away, over WiFi → Supabase → FCM. The full spec lives in `docs/kalmi-guia-app.md`, and section numbers (§) in code comments refer to it.

This is a 2-person student project. Code marked `TODO(colega)` is deliberately left for the teammate. Do not implement it unless asked.

## Commands

Windows host. Use `./gradlew` from Git Bash or `.\gradlew.bat` from PowerShell. The JDK is Android Studio's bundled JBR (`JAVA_HOME`).

```
./gradlew :app:assembleDebug                      # build APK
./gradlew :app:testDebugUnitTest                  # JVM unit tests
./gradlew :app:testDebugUnitTest --tests "cl.kalmi.app.ProtocoloTest"            # one class
./gradlew :app:testDebugUnitTest --tests "cl.kalmi.app.ProtocoloTest.rechazaBasura"  # one test
./gradlew :app:installDebug                       # install on device/emulator
```

No linter is configured.

## Build setup notes

- AGP 9.x with **built-in Kotlin**. Do not apply `org.jetbrains.kotlin.android`. Only the `org.jetbrains.kotlin.plugin.compose` plugin is applied, and its version is `kotlin` in `gradle/libs.versions.toml`.
- All versions live in the version catalog `gradle/libs.versions.toml`.
- `buildConfig = true` is enabled so the UI can use `BuildConfig.DEBUG` to show the simulator debug panel.
- The XML theme (`res/values/themes.xml`) is only the window theme. The real theming is `ui/theme/Theme.kt` (`KalmiTheme`).

## Architecture

MVVM with a single repository. The device sits behind an interface so the app runs fully without hardware.

```
UI (Compose) → KalmiViewModel → KalmiRepository → KalmiDevice
                                                   ├─ FakeKalmiDevice (current)
                                                   └─ BleKalmiDevice  (future, guide §9)
```

- **Wiring** is manual DI in `KalmiApp` (Application). Swapping Fake ↔ BLE is one line there. `KalmiViewModel.Factory` pulls the repository and device from `KalmiApp`.
- **Protocol** (`domain/Protocolo.kt`): one text line per message, `BODY*XX`, where XX is the XOR checksum in hex. Device → app: `TEL`, `EVT`, `PAL` (1–2 word indices 0–9), `OK`, `BAT`, `CFG`. App → device: `ACK`, `CMD`, `CFG` (incl. `CFG,PAL,i,texto` for personal words 4–9). Lines with an invalid checksum or format parse to `null` and count as `descartados`. `ContratoArduinoTest` pins the exact lines the Arduino sketch prints.
- **Words (v2)**: `Vocabulario` = fixed core (Quiero, No, Más, Parar) + 6 caregiver-edited personal words (saved in SharedPreferences, pushed to Kalmi on connect). `Interpretador` turns words into a suggestion with its reason; today `ReglasInterpretador`, later an AI implementation swapped in `KalmiApp`. The AI never decides: the caregiver confirms with ¿Acertó?.
- **Device switching**: `KalmiApp.device` is a `DispositivoConmutable`; Ajustes → Modo de prueba swaps live between `FakeKalmiDevice` and `BleKalmiDevice` (HM-10 profile; NUS ready for ESP32).
- **Notifications**: `service/Notificador` shows a notification per new alert while the app is in background (3 channels from `domain/Avisos`), tapping opens Acompañar.
- **Firmware**: `firmware/kalmi-uno` (device) and `firmware/kalmi-telefono` (Tinkercad stand-in for the app). Verify with `arduino-cli compile -b arduino:avr:uno firmware/<sketch>`.
- **Repository** (`data/KalmiRepository.kt`) holds all link logic. It collects `device.lineas` and:
  - ACKs every `EVT`, including duplicates.
  - Dedupes events by `seq`.
  - Counts lost messages from `seq` gaps. `seq` is shared between `TEL` and `EVT`.
  - Detects tension from 3 consecutive pressure readings ≥75, re-armed when pressure drops below 60.
  - Sends commands with a fresh `id`, up to 3 tries with a 1 s timeout each, and measures RTT.
  - Exposes everything as `StateFlow`. History is in-memory for now; Room is step 5.
- **Threading invariant:** the repository and `FakeKalmiDevice` mutate plain maps and counters without locks. They must only run on the single-threaded `MainScope()` created in `KalmiApp`. Keep that invariant, or add synchronization if you introduce another dispatcher.
- **Domain** (`domain/`) has no Android or Compose imports. Emotion colors are extension properties in `ui/theme` (`Emocion.color` for the full color, `Emocion.fondo` for a ~18% alpha alert background).

## UI conventions

- Navigation is a `Pestana` enum plus `when` in `MainActivity` (tabs: Inicio, Acompañar, Historial, Palabras, Ajustes), with a custom floating bottom bar and edge-to-edge (`Scaffold` padding is passed down as `modifier`). Move to Navigation 3 (skill `navigation-3`) once there are detail screens.
- One shared `KalmiViewModel` serves all screens.
- Screen pattern, with `ui/inicio/InicioScreen.kt` as the reference:
  - `XScreen(vm, modifier)` collects flows with `collectAsStateWithLifecycle`.
  - It delegates to a stateless `XContenido(data, lambdas)` that has a `@Preview`.
- UI text is in Spanish. The tone must stay calm, never alarmist (guide §13.3, §15.6).
- Material icons come from `material-icons-core` pinned at 1.7.8 (not in the Compose BOM).
- Palette "Kalmi Blanco" in `ui/theme/Theme.kt`: emotion BASE colors are accents only (stripes, dots); text uses the TINTA variants (WCAG AA). Shared pieces in `ui/Componentes.kt` (`Encabezado`, `CaraKalmi`, `TarjetaFranja`, `Indicador`, `BarraSuave`).

## Android skills

Official Google Android skills live in `.claude/skills/`: navigation-3, edge-to-edge, testing-setup, android-permissions-security, and migrate-xml-views-to-jetpack-compose. Claude Code loads them automatically. Other agents can read each `SKILL.md` and its `references/` directly. Use them for those areas, for example the BLE runtime permissions at step 8.
