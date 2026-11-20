# Kalmi app · Estado y reparto de tareas

Guía completa: [kalmi-guia-app.md](kalmi-guia-app.md). Buscar `TODO(colega)` en el código.

## Hecho (base)

| Pieza | Archivo |
|---|---|
| Gradle: Compose (BOM), minSdk 26, paquete `cl.kalmi.app` | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| Modelos de dominio (sin Android) | `domain/Models.kt` |
| Parser/constructor del protocolo + tests | `domain/Protocolo.kt`, `test/.../ProtocoloTest.kt` |
| Interfaz del dispositivo + simulador | `device/KalmiDevice.kt`, `device/FakeKalmiDevice.kt` |
| Repositorio: ACK, dedupe, pérdida, RTT, tensión, comandos con reintento | `data/KalmiRepository.kt` |
| Inyección manual (Fake ↔ BLE se cambia en 1 línea) | `KalmiApp.kt` |
| Tema y colores por emoción | `ui/theme/Theme.kt` |
| Barra inferior con 4 pestañas, edge-to-edge | `MainActivity.kt` |
| ViewModel compartido | `ui/KalmiViewModel.kt` |
| **Pantalla Inicio completa (patrón de referencia)** | `ui/inicio/InicioScreen.kt` |
| Conexión mínima (conectar/desconectar) | `ui/conexion/ConexionScreen.kt` |

## Pendiente — pasos 1–4 de la guía

- [x] **Controles** (§15.3) — `ui/controles/ControlesScreen.kt`
- [x] **Historial** (§15.4) — `ui/historial/HistorialScreen.kt`
- [x] **Conexión: diagnóstico + panel debug** (§15.5) — `ui/conexion/ConexionScreen.kt`
- [x] **Tests del repositorio** (§18) — agregar `kotlinx-coroutines-test`

## v2 · Uno + palabras + IA preparada (rama `feat/kalmi-v2-palabras`)

Diseño: `docs/superpowers/specs/2026-11-19-kalmi-uno-palabras-design.md` · Plan: `docs/superpowers/plans/2026-11-19-kalmi-v2-palabras.md`

- [x] Protocolo `PAL` / `CFG,PAL`, vocabulario núcleo + personales, `ReglasInterpretador` con razón y ¿Acertó?
- [x] Simulador envía palabras · pestaña Palabras · alerta de palabras en Inicio
- [x] `BleKalmiDevice` (HM-10 / NUS) + permisos + Modo de prueba en Ajustes (cambio en caliente)
- [x] Rediseño UI: paleta Kalmi Blanco, mascota animada, Acompañar con respuesta sugerida, barra flotante
- [x] Notificaciones (3 canales) que abren Acompañar
- [x] Sketches `firmware/kalmi-uno` y `firmware/kalmi-telefono` (compilados para Uno) + guía Tinkercad (artifact)
- [ ] **Probar BLE real**: Uno + HM-10 + teléfono Android físico
- [x] Probar los sketches en Tinkercad (EEPROM y serial entre dos placas): guion completo OK, incluidas tensión y pérdida de mensajes
- [ ] Ícono de la app (hoy es el de Android por defecto)
- [ ] Actualizar el informe con la sección "Mejoras v2" (necesita el `.docx`)

## Después (pasos 5+)

~~Room (5)~~ hecho (`data/local/HistorialDb.kt`, últimos 30 días) · Supabase (6) · FCM (7) · BLE real + permisos (8) · servicio en primer plano (9) · WiFi (10) · seguridad (11).

## Flujo de trabajo (ramas, commits, PR)

1. Actualizar `main` y crear rama desde ahí:
   ```
   git switch main && git pull
   git switch -c feat/controles
   ```
   Prefijos: `feat/` funcionalidad · `fix/` error · `test/` pruebas · `docs/` documentación · `chore/` build/config.
   Una rama por tarea de la lista de arriba (ej. `feat/controles`, `feat/historial`, `feat/conexion-diagnostico`, `test/repositorio`).
2. Commits en formato Conventional Commits, en español, sin `Co-Authored-By`:
   `feat(ui): pantalla Controles con mensajes y brillo`
3. Antes de abrir el PR, debe pasar:
   ```
   ./gradlew :app:assembleDebug :app:testDebugUnitTest
   ```
4. Marcar la tarea `[x]` en este archivo dentro del mismo PR.
5. Push y PR hacia `main`; lo revisa la otra persona antes de hacer merge. Nada de commits directos a `main` salvo arreglos de documentación.

## Convenciones de código

- Pantalla = `XScreen(vm)` (lee StateFlow con `collectAsStateWithLifecycle`) + `XContenido(datos, lambdas)` con `@Preview`.
- Dominio sin imports de Android/Compose.
- Estado del repo y del simulador se toca solo desde el scope Main (sin locks).
- Skills Android oficiales en `.claude/skills/` (navigation-3, edge-to-edge, testing-setup, permissions-security, migrate-xml-to-compose).
- Instrucciones para IA: `AGENTS.md` (Claude Code lo carga vía `CLAUDE.md`).

## Compilar

```
./gradlew :app:assembleDebug :app:testDebugUnitTest
```
