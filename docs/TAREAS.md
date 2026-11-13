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

- [ ] **Controles** (§15.3) — `ui/controles/ControlesScreen.kt`
- [ ] **Historial** (§15.4) — `ui/historial/HistorialScreen.kt`
- [ ] **Conexión: diagnóstico + panel debug** (§15.5) — `ui/conexion/ConexionScreen.kt`
- [ ] **Tests del repositorio** (§18) — agregar `kotlinx-coroutines-test`

## Después (pasos 5+)

Room (5) · Supabase (6) · FCM (7) · BLE real + permisos (8) · servicio en primer plano (9) · WiFi (10) · seguridad (11).

## Convenciones

- Pantalla = `XScreen(vm)` (lee StateFlow con `collectAsStateWithLifecycle`) + `XContenido(datos, lambdas)` con `@Preview`.
- Dominio sin imports de Android/Compose.
- Estado del repo y del simulador se toca solo desde el scope Main (sin locks).
- Skills Android oficiales en `.claude/skills/` (navigation-3, edge-to-edge, testing-setup, permissions-security, migrate-xml-to-compose).

## Compilar

```
./gradlew :app:assembleDebug :app:testDebugUnitTest
```
