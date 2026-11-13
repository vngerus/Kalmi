# Kalmi · Guía de desarrollo de la app Android (Kotlin)

Guía para construir la app del cuidador en Android Studio, con el flujo completo del sistema: dispositivo Kalmi, Bluetooth, nube, notificaciones y pantallas.

> **Regla de oro:** la app se construye primero contra un **Kalmi simulado** (`FakeKalmiDevice`). Cuando llegue el hardware, solo se cambia la implementación del dispositivo; las pantallas y la lógica no cambian.

---

## Índice

1. [Visión general del sistema](#1-visión-general-del-sistema)
2. [Flujos completos](#2-flujos-completos)
3. [Protocolo de mensajes](#3-protocolo-de-mensajes)
4. [Arquitectura de la app](#4-arquitectura-de-la-app)
5. [Configuración del proyecto](#5-configuración-del-proyecto)
6. [Capa de dominio: modelos](#6-capa-de-dominio-modelos)
7. [Parser del protocolo](#7-parser-del-protocolo)
8. [Dispositivo simulado](#8-dispositivo-simulado)
9. [Cliente Bluetooth real (BLE)](#9-cliente-bluetooth-real-ble)
10. [Repositorio: lógica central](#10-repositorio-lógica-central)
11. [Historial local con Room](#11-historial-local-con-room)
12. [Nube: Supabase](#12-nube-supabase)
13. [Notificaciones push (FCM)](#13-notificaciones-push-fcm)
14. [Servicio en segundo plano](#14-servicio-en-segundo-plano)
15. [Pantallas (Jetpack Compose)](#15-pantallas-jetpack-compose)
16. [Configuración del WiFi de Kalmi](#16-configuración-del-wifi-de-kalmi)
17. [Seguridad](#17-seguridad)
18. [Pruebas](#18-pruebas)
19. [Plan de desarrollo paso a paso](#19-plan-de-desarrollo-paso-a-paso)

---

## 1. Visión general del sistema

```
                       ┌───────────── BLE (cerca, ~10–30 m) ─────────────┐
                       │                                                  ▼
[Kalmi: ESP32-S3] ─────┤                                         [App del cuidador]
 botones, zona de      │                                          Kotlin + Compose
 calma, pantalla,      └── WiFi ──> [Supabase] ──> [FCM push] ──────────▲
 vibración              (lejos)    tabla eventos   notificación
```

| Componente | Responsabilidad |
|---|---|
| **Kalmi** | Expresar emociones, respirar, zona de calma. Funciona solo, sin conexión |
| **App** | Recibir alertas y telemetría, enviar comandos, guardar historial, configurar Kalmi |
| **Supabase** | Recibir eventos cuando Kalmi está lejos, guardar el historial en la nube, autenticar al cuidador |
| **FCM** | Avisar al cuidador aunque la app esté cerrada |

**Dos caminos para un mismo evento:**

| Situación | Camino | Latencia | Qué llega |
|---|---|---|---|
| Cuidador cerca | Kalmi → BLE → App | < 1 s | Todo: telemetría cada 2 s, eventos, comandos |
| Cuidador lejos | Kalmi → WiFi → Supabase → FCM → App | 1–5 s | Eventos al instante, telemetría cada 1 min |
| Sin conexión | Kalmi guarda en memoria | — | Se envía al reconectarse |

---

## 2. Flujos completos

### 2.1 Primer uso: emparejar y configurar

```
Cuidador                     App                          Kalmi                    Supabase
   │ Crea cuenta / inicia sesión ──────────────────────────────────────────────────>│
   │ "Agregar Kalmi" ──────────>│                                                    │
   │                            │ Escanea BLE (servicio NUS) ─>│                     │
   │                            │<──── anuncio "Kalmi-XXXX" ────│                    │
   │ Elige su Kalmi ───────────>│ Conecta y empareja (código) ─>│                    │
   │                            │──── CFG,ID? ─────────────────>│                    │
   │                            │<─── CFG,ID,kalmi-a1b2 ────────│                    │
   │                            │ Registra el dispositivo ─────────────────────────────>│
   │                            │<──────────── clave del dispositivo ──────────────────│
   │                            │──── CFG,KEY,<clave> ─────────>│ guarda cifrado      │
   │ Ingresa red WiFi ─────────>│──── CFG,WIFI,<ssid>,<clave> ─>│ guarda cifrado      │
   │                            │<─── CFG,OK ───────────────────│                    │
   │                            │ Pantalla "Kalmi listo" ✓      │                    │
```

### 2.2 Evento con el cuidador cerca (BLE)

```
Niño            Kalmi                         App                       Cuidador
 │ Botón ámbar ─>│ Cara "Frustrado"           │                          │
 │               │── EVT,15,E3*4A ──────────>│ Valida checksum          │
 │               │<───────── ACK,15*2C ───────│ Guarda en Room           │
 │               │ "Aviso listo"              │ Muestra alerta ámbar ───>│
 │               │ A los 2,5 s: respiración   │ Vibración suave          │
 │               │                            │<─── "Marcar atendido" ───│
 │               │<──── CMD,7,ATN*5B ─────────│                          │
 │               │ "Ya viene alguien" + vibra │                          │
 │               │──── OK,7*1F ─────────────>│ "✓ Confirmado (42 ms)"   │
```

### 2.3 Evento con el cuidador lejos (WiFi + nube)

```
Kalmi                       Supabase                          FCM            App (cerrada)
 │ Sin BLE conectado         │                                  │                  │
 │ Conecta WiFi conocida     │                                  │                  │
 │── POST /ingest (HTTPS) ──>│ Edge Function valida la clave    │                  │
 │   {EVT, seq 15, E3}       │ INSERT en tabla eventos          │                  │
 │<──── 200 OK ──────────────│ Webhook ─> Edge Function notify ─>│                  │
 │ Desconecta WiFi (ahorro)  │                                  │── push ─────────>│
 │                           │                                  │  "Frustrado"     │
 │                           │<──────── la app abre y sincroniza el historial ──────│
```

**Desde lejos, el cuidador no envía comandos en tiempo real**, porque Kalmi no está conectado a internet todo el tiempo (para ahorrar batería). Puede ver el historial y la última telemetría. Los comandos remotos quedan como mejora futura (Kalmi revisaría comandos pendientes cada cierto tiempo).

### 2.4 Kalmi sin conexión

```
Kalmi registra eventos en una cola local (hasta ~50)
     │
     ├── Se conecta por BLE ──> envía la cola a la app ──> la app los sube a Supabase
     └── Se conecta por WiFi ─> envía la cola a Supabase
```

La app **descarta duplicados por `seq`**, porque un mismo evento puede llegar por ambos caminos.

### 2.5 Anticipación (fase "si alcanza")

```
App detecta un patrón (ej.: presión alta 3 lecturas seguidas)
     │──── CMD,9,ASK,3 ────> Kalmi muestra cara ámbar + "¿Te sientes así?"
     │                            │
     │      ┌─────────────────────┼──────────────────────────┐
     │   Presiona ámbar        Presiona otro color        No responde (10 s)
     │   EVT,...,E3            EVT,...,E1                  Kalmi vuelve a reposo
     │   confirmado            corregido                   sin insistir
     │<───────────────────────────┘
     │ La app registra la respuesta (confirmado / corregido / sin respuesta)
```

---

## 3. Protocolo de mensajes

Mensajes de texto, una línea por mensaje, terminados en `*XX` (checksum XOR en hexadecimal) y salto de línea.

### 3.1 Kalmi → App

| Mensaje | Ejemplo | Significado |
|---|---|---|
| `TEL,seq,luz,presion` | `TEL,12,70,35*3B` | Telemetría cada 2 s (luz y presión en %) |
| `EVT,seq,En` | `EVT,15,E3*4A` | E1 Bien, E2 Triste, E3 Frustrado, E4 Muy enojado, E5 Ayuda, E6 Tensión |
| `OK,id` | `OK,7*1F` | Comando ejecutado |
| `BAT,pct` | `BAT,82*..` | Batería (cada 1 min) |
| `CFG,...` | `CFG,OK*..` | Respuestas de configuración |

### 3.2 App → Kalmi

| Mensaje | Significado |
|---|---|
| `ACK,seq` | Confirma un evento (Kalmi deja de reintentar) |
| `CMD,id,ATN` | Atendido: "Ya viene alguien" + vibración |
| `CMD,id,MSG,k` | Mensaje: 0 "Ya voy", 1 "Te quiero", 2 "Estoy aquí" |
| `CMD,id,RESP` | Inicia la respiración guiada |
| `CMD,id,BRI,v` | Brillo de la pantalla (10–100) |
| `CMD,id,ASK,n` | Pregunta "¿Te sientes así?" con la emoción n |
| `CFG,WIFI,ssid,clave` | Configura una red WiFi |
| `CFG,KEY,clave` | Guarda la clave del dispositivo para la nube |
| `CFG,ID?` | Pide el identificador del dispositivo |

### 3.3 Reglas

- **`seq`**: lo genera Kalmi y crece siempre. Sirve para detectar pérdidas y duplicados.
- **`id`**: lo genera la app para cada comando. Si llega repetido, Kalmi no lo ejecuta dos veces.
- **Reintentos**: Kalmi reenvía un `EVT` hasta 3 veces si no recibe `ACK` en 1,5 s. La app reenvía un `CMD` hasta 2 veces si no recibe `OK` en 1 s.
- **Checksum**: XOR de todos los caracteres antes del `*`. Si no coincide, el mensaje se descarta.

---

## 4. Arquitectura de la app

**MVVM + repositorio único**, con el dispositivo detrás de una interfaz para poder cambiar simulado ↔ real.

```
ui/ (Compose)  ──>  ViewModel  ──>  KalmiRepository  ──┬──> KalmiDevice (interfaz)
                                                       │      ├── FakeKalmiDevice   (desarrollo)
                                                       │      └── BleKalmiDevice    (hardware)
                                                       ├──> Room (historial local)
                                                       └──> Supabase (nube)
```

### Estructura de paquetes

```
cl.kalmi.app/
├── KalmiApp.kt                     Application: inicializa dependencias
├── MainActivity.kt
├── di/
│   └── AppContainer.kt             Inyección manual (o Hilt si lo prefieren)
├── domain/
│   ├── Models.kt                   Emocion, Evento, Telemetria, Comando, EstadoConexion
│   └── Protocolo.kt                Parser y constructor de mensajes
├── device/
│   ├── KalmiDevice.kt              Interfaz
│   ├── FakeKalmiDevice.kt          Simulador
│   └── ble/
│       ├── BleKalmiDevice.kt       Implementación real
│       └── NusUuids.kt
├── data/
│   ├── KalmiRepository.kt
│   ├── local/                      Room: EventoEntity, EventoDao, KalmiDatabase
│   └── remote/                     Supabase: SupabaseClient, EventosRemote
├── service/
│   ├── KalmiConnectionService.kt   Servicio en primer plano
│   └── KalmiMessagingService.kt    Recepción de FCM
└── ui/
    ├── theme/                      Paleta Kalmi
    ├── navigation/
    ├── inicio/
    ├── controles/
    ├── historial/
    └── conexion/
```

---

## 5. Configuración del proyecto

### 5.1 Crear el proyecto

1. Android Studio → **New Project** → **Empty Activity** (Compose).
2. Nombre: `Kalmi`. Paquete: `cl.kalmi.app`. Lenguaje: Kotlin.
3. **Minimum SDK: API 26** (Android 8.0). Cubre la gran mayoría de teléfonos y simplifica BLE.

### 5.2 Dependencias

Usa siempre las **versiones estables más recientes** que sugiera Android Studio. En `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")   // para Supabase
    id("com.google.devtools.ksp")                       // para Room
    id("com.google.gms.google-services")                // para FCM (al final)
}

dependencies {
    // Compose (BOM)
    implementation(platform("androidx.compose:compose-bom:<versión>"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:<versión>")
    implementation("androidx.navigation:navigation-compose:<versión>")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<versión>")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:<versión>")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<versión>")

    // Room
    implementation("androidx.room:room-runtime:<versión>")
    implementation("androidx.room:room-ktx:<versión>")
    ksp("androidx.room:room-compiler:<versión>")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:<versión>"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:<versión>")

    // Firebase Cloud Messaging
    implementation(platform("com.google.firebase:firebase-bom:<versión>"))
    implementation("com.google.firebase:firebase-messaging")
}
```

### 5.3 Permisos (`AndroidManifest.xml`)

```xml
<!-- Bluetooth: Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Bluetooth: Android 11 o anterior -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />

<!-- Internet, notificaciones y servicio en primer plano -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

Los permisos de Bluetooth y notificaciones se piden **en tiempo de ejecución**, en la pantalla de Conexión, explicando para qué sirven.

---

## 6. Capa de dominio: modelos

```kotlin
// domain/Models.kt
package cl.kalmi.app.domain

import androidx.compose.ui.graphics.Color

enum class Emocion(val codigo: Int, val nombre: String, val color: Color) {
    BIEN(1, "Bien", Color(0xFF7FAF8E)),
    TRISTE(2, "Triste", Color(0xFF6E8FB8)),
    FRUSTRADO(3, "Frustrado", Color(0xFFD4B26A)),
    MUY_ENOJADO(4, "Muy enojado", Color(0xFFC9786B)),
    AYUDA(5, "Necesita ayuda", Color(0xFF9A8FC7)),
    TENSION(6, "Tensión alta", Color(0xFFD4B26A));

    companion object {
        fun desdeCodigo(c: Int) = entries.firstOrNull { it.codigo == c }
    }
}

enum class Canal { BLE, NUBE }

data class Evento(
    val seq: Int,
    val emocion: Emocion,
    val momento: Long,          // epoch ms
    val canal: Canal,
    val atendido: Boolean = false,
    val atendidoEn: Long? = null,
)

data class Telemetria(
    val seq: Int,
    val luz: Int,               // 0–100
    val presion: Int,           // 0–100
    val momento: Long,
)

sealed interface Comando {
    data object Atendido : Comando
    data class Mensaje(val k: Int) : Comando        // 0 Ya voy, 1 Te quiero, 2 Estoy aquí
    data object Respirar : Comando
    data class Brillo(val valor: Int) : Comando     // 10–100
    data class Preguntar(val emocion: Emocion) : Comando
}

enum class EstadoComando { ENVIANDO, CONFIRMADO, SIN_CONFIRMACION }

sealed interface EstadoConexion {
    data object Desconectado : EstadoConexion
    data object Buscando : EstadoConexion
    data object Conectando : EstadoConexion
    data class Conectado(val nombre: String) : EstadoConexion
    data class Error(val mensaje: String) : EstadoConexion
}

data class CalidadEnlace(
    val recibidos: Int = 0,
    val perdidos: Int = 0,
    val descartados: Int = 0,   // checksum inválido
    val ultimoRttMs: Long? = null,
) {
    val porcentajePerdida: Float
        get() = if (recibidos + perdidos == 0) 0f else perdidos * 100f / (recibidos + perdidos)
}
```

---

## 7. Parser del protocolo

```kotlin
// domain/Protocolo.kt
package cl.kalmi.app.domain

sealed interface MensajeKalmi {
    data class Tel(val seq: Int, val luz: Int, val presion: Int) : MensajeKalmi
    data class Evt(val seq: Int, val emocion: Emocion) : MensajeKalmi
    data class Ok(val id: Int) : MensajeKalmi
    data class Bat(val porcentaje: Int) : MensajeKalmi
    data class Cfg(val partes: List<String>) : MensajeKalmi
}

object Protocolo {

    fun checksum(s: String): Int = s.fold(0) { acc, c -> acc xor c.code } and 0xFF

    /** Agrega "*XX" al final. */
    fun firmar(cuerpo: String): String = "%s*%02X".format(cuerpo, checksum(cuerpo))

    /** Devuelve null si el checksum no coincide o el formato es inválido. */
    fun parsear(linea: String): MensajeKalmi? {
        val limpia = linea.trim()
        val ast = limpia.lastIndexOf('*')
        if (ast < 0) return null
        val cuerpo = limpia.substring(0, ast)
        val esperado = limpia.substring(ast + 1).toIntOrNull(16) ?: return null
        if (checksum(cuerpo) != esperado) return null

        val p = cuerpo.split(",")
        return runCatching {
            when (p[0]) {
                "TEL" -> MensajeKalmi.Tel(p[1].toInt(), p[2].toInt().coerceIn(0, 100), p[3].toInt().coerceIn(0, 100))
                "EVT" -> Emocion.desdeCodigo(p[2].removePrefix("E").toInt())?.let { MensajeKalmi.Evt(p[1].toInt(), it) }
                "OK"  -> MensajeKalmi.Ok(p[1].toInt())
                "BAT" -> MensajeKalmi.Bat(p[1].toInt().coerceIn(0, 100))
                "CFG" -> MensajeKalmi.Cfg(p.drop(1))
                else  -> null
            }
        }.getOrNull()
    }

    fun ack(seq: Int) = firmar("ACK,$seq")

    fun comando(id: Int, c: Comando): String = firmar(
        when (c) {
            Comando.Atendido       -> "CMD,$id,ATN"
            is Comando.Mensaje     -> "CMD,$id,MSG,${c.k}"
            Comando.Respirar       -> "CMD,$id,RESP"
            is Comando.Brillo      -> "CMD,$id,BRI,${c.valor.coerceIn(10, 100)}"
            is Comando.Preguntar   -> "CMD,$id,ASK,${c.emocion.codigo}"
        }
    )
}
```

**Prueba unitaria mínima** (`test/ProtocoloTest.kt`):

```kotlin
@Test fun checksumValidaYRechaza() {
    val linea = Protocolo.firmar("EVT,15,E3")
    assertEquals(MensajeKalmi.Evt(15, Emocion.FRUSTRADO), Protocolo.parsear(linea))
    assertNull(Protocolo.parsear(linea.replace("E3", "E4")))   // alterado
}
```

---

## 8. Dispositivo simulado

### 8.1 Interfaz común

```kotlin
// device/KalmiDevice.kt
package cl.kalmi.app.device

import cl.kalmi.app.domain.EstadoConexion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface KalmiDevice {
    val estado: StateFlow<EstadoConexion>
    /** Líneas de texto recibidas desde Kalmi (sin procesar). */
    val lineas: Flow<String>
    suspend fun conectar()
    suspend fun desconectar()
    suspend fun enviar(linea: String)
}
```

### 8.2 Simulador

Reproduce el comportamiento del firmware: telemetría cada 2 s, eventos aleatorios, reintentos sin ACK y respuesta `OK` a los comandos.

```kotlin
// device/FakeKalmiDevice.kt
class FakeKalmiDevice(private val scope: CoroutineScope) : KalmiDevice {

    private val _estado = MutableStateFlow<EstadoConexion>(EstadoConexion.Desconectado)
    override val estado = _estado.asStateFlow()

    private val _lineas = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lineas = _lineas.asSharedFlow()

    private var seq = 0
    private var presion = 0
    private var tareas: Job? = null
    private val pendientes = mutableMapOf<Int, Int>()   // seq -> reintentos

    /** Para probar la UI: perder un % de mensajes. */
    var probabilidadPerdida = 0.0

    override suspend fun conectar() {
        _estado.value = EstadoConexion.Conectando
        delay(800)
        _estado.value = EstadoConexion.Conectado("Kalmi-SIM")
        tareas = scope.launch {
            launch { // telemetría
                while (isActive) {
                    presion = (presion + (-10..12).random()).coerceIn(0, 100)
                    emitir("TEL,${++seq},${(55..80).random()},$presion")
                    delay(2000)
                }
            }
            launch { // eventos aleatorios cada 15–40 s
                while (isActive) {
                    delay((15_000L..40_000L).random())
                    emitirEvento((1..5).random())
                }
            }
        }
    }

    /** Botón de depuración en la app: simula que el niño presiona una emoción. */
    fun simularBoton(codigo: Int) = scope.launch { emitirEvento(codigo) }

    private suspend fun emitirEvento(codigo: Int) {
        val s = ++seq
        pendientes[s] = 0
        while ((pendientes[s] ?: return) <= 3) {
            emitir("EVT,$s,E$codigo")
            delay(1500)
            pendientes[s]?.let { pendientes[s] = it + 1 } ?: return
        }
        pendientes.remove(s)
    }

    override suspend fun enviar(linea: String) {
        val m = linea.substringBefore('*').split(",")
        when (m[0]) {
            "ACK" -> pendientes.remove(m[1].toInt())
            "CMD" -> { delay((20L..60L).random()); emitir("OK,${m[1]}") }
        }
    }

    override suspend fun desconectar() {
        tareas?.cancel()
        _estado.value = EstadoConexion.Desconectado
    }

    private suspend fun emitir(cuerpo: String) {
        if (Math.random() < probabilidadPerdida) return
        _lineas.emit(Protocolo.firmar(cuerpo))
    }
}
```

> Con el simulador se construyen y prueban **todas las pantallas** sin hardware. Agrega en la pantalla de Conexión, solo en modo debug, botones para "simular emoción" y un deslizador de pérdida.

---

## 9. Cliente Bluetooth real (BLE)

### 9.1 UUIDs del Nordic UART Service

```kotlin
// device/ble/NusUuids.kt
object NusUuids {
    val SERVICIO: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val RX: UUID       = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // App escribe
    val TX: UUID       = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Kalmi notifica
    val CCCD: UUID     = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
```

### 9.2 Secuencia de conexión

```
1. Escanear con filtro de servicio NUS        (BluetoothLeScanner + ScanFilter)
2. connectGatt(context, autoConnect = false, callback, TRANSPORT_LE)
3. onConnectionStateChange -> STATE_CONNECTED -> requestMtu(247)
4. onMtuChanged -> discoverServices()
5. onServicesDiscovered -> buscar TX y RX
6. setCharacteristicNotification(TX, true) + escribir CCCD = ENABLE_NOTIFICATION_VALUE
7. onCharacteristicChanged -> acumular bytes -> cortar por '\n' -> emitir líneas
8. Escribir comandos en RX (de a uno; esperar onCharacteristicWrite antes del siguiente)
```

### 9.3 Esqueleto

```kotlin
// device/ble/BleKalmiDevice.kt
@SuppressLint("MissingPermission") // los permisos se verifican antes de llamar
class BleKalmiDevice(
    private val context: Context,
    private val scope: CoroutineScope,
) : KalmiDevice {

    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private val buffer = StringBuilder()
    private val colaEscritura = Channel<String>(Channel.UNLIMITED)
    private val escrituraLista = Channel<Unit>(Channel.CONFLATED)

    private val _estado = MutableStateFlow<EstadoConexion>(EstadoConexion.Desconectado)
    override val estado = _estado.asStateFlow()
    private val _lineas = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lineas = _lineas.asSharedFlow()

    override suspend fun conectar() {
        _estado.value = EstadoConexion.Buscando
        val device = escanearPrimero() ?: run {
            _estado.value = EstadoConexion.Error("No se encontró Kalmi"); return
        }
        _estado.value = EstadoConexion.Conectando
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        scope.launch { procesarColaEscritura() }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, nuevo: Int) {
            when (nuevo) {
                BluetoothProfile.STATE_CONNECTED -> g.requestMtu(247)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _estado.value = EstadoConexion.Desconectado
                    g.close()
                    // Reconexión automática con espera creciente
                    scope.launch { delay(3000); conectar() }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val servicio = g.getService(NusUuids.SERVICIO) ?: return
            val tx = servicio.getCharacteristic(NusUuids.TX)
            rx = servicio.getCharacteristic(NusUuids.RX)
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(NusUuids.CCCD)
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION") g.writeDescriptor(cccd)
            }
            _estado.value = EstadoConexion.Conectado(g.device.name ?: "Kalmi")
            escrituraLista.trySend(Unit)
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) =
            recibir(value)

        // Android 12 o anterior
        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") recibir(c.value)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            escrituraLista.trySend(Unit)
        }
    }

    /** Las notificaciones pueden traer media línea o varias: se acumula y corta por '\n'. */
    private fun recibir(bytes: ByteArray) {
        buffer.append(bytes.decodeToString())
        while (true) {
            val i = buffer.indexOf("\n")
            if (i < 0) break
            val linea = buffer.substring(0, i).trim()
            buffer.delete(0, i + 1)
            if (linea.isNotEmpty()) scope.launch { _lineas.emit(linea) }
        }
    }

    override suspend fun enviar(linea: String) { colaEscritura.send(linea + "\n") }

    /** Android permite una escritura GATT a la vez: se serializan. */
    private suspend fun procesarColaEscritura() {
        for (linea in colaEscritura) {
            escrituraLista.receive()
            val c = rx ?: continue
            val datos = linea.encodeToByteArray()
            if (Build.VERSION.SDK_INT >= 33) {
                gatt?.writeCharacteristic(c, datos, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION") c.value = datos
                @Suppress("DEPRECATION") gatt?.writeCharacteristic(c)
            }
        }
    }

    private suspend fun escanearPrimero(): BluetoothDevice? { /* ScanFilter por NusUuids.SERVICIO, timeout 10 s */ TODO() }

    override suspend fun desconectar() { gatt?.disconnect() }
}
```

**Alternativa más simple:** la librería **Kable** (JuulLabs) envuelve todo esto con corrutinas. Si el código BLE nativo se complica, cámbienlo por Kable sin tocar el resto de la app: solo cambia esta clase.

---

## 10. Repositorio: lógica central

Toda la lógica del antiguo Nodo B vive aquí: ACK, deduplicación, pérdida, RTT, detección de tensión, comandos con reintento.

```kotlin
// data/KalmiRepository.kt
class KalmiRepository(
    private val device: KalmiDevice,
    private val dao: EventoDao,
    private val remoto: EventosRemote,
    private val scope: CoroutineScope,
) {
    val estadoConexion = device.estado

    private val _telemetria = MutableStateFlow<Telemetria?>(null)
    val telemetria = _telemetria.asStateFlow()

    private val _alertaActual = MutableStateFlow<Evento?>(null)
    val alertaActual = _alertaActual.asStateFlow()

    private val _calidad = MutableStateFlow(CalidadEnlace())
    val calidad = _calidad.asStateFlow()

    private val _estadoComando = MutableStateFlow<Pair<Comando, EstadoComando>?>(null)
    val estadoComando = _estadoComando.asStateFlow()

    val historial: Flow<List<Evento>> = dao.observarHoy().map { it.map(EventoEntity::aDominio) }

    private var ultimoSeq = 0
    private val eventosVistos = mutableSetOf<Int>()
    private var lecturasAltas = 0
    private var siguienteId = 1
    private val okPendientes = mutableMapOf<Int, CompletableDeferred<Long>>()

    init {
        scope.launch {
            device.lineas.collect { linea ->
                when (val m = Protocolo.parsear(linea)) {
                    null -> _calidad.update { it.copy(descartados = it.descartados + 1) }
                    is MensajeKalmi.Tel -> alRecibirTelemetria(m)
                    is MensajeKalmi.Evt -> alRecibirEvento(m)
                    is MensajeKalmi.Ok  -> okPendientes.remove(m.id)?.complete(System.currentTimeMillis())
                    else -> Unit
                }
            }
        }
    }

    private fun registrarSeq(seq: Int): Boolean {
        if (seq <= ultimoSeq) return false
        if (ultimoSeq > 0 && seq > ultimoSeq + 1)
            _calidad.update { it.copy(perdidos = it.perdidos + (seq - ultimoSeq - 1)) }
        ultimoSeq = seq
        _calidad.update { it.copy(recibidos = it.recibidos + 1) }
        return true
    }

    private fun alRecibirTelemetria(m: MensajeKalmi.Tel) {
        registrarSeq(m.seq)
        _telemetria.value = Telemetria(m.seq, m.luz, m.presion, System.currentTimeMillis())
        // Detección de tensión a partir de lecturas remotas
        if (m.presion >= 75) {
            if (++lecturasAltas == 3) crearAlerta(Evento(m.seq, Emocion.TENSION, System.currentTimeMillis(), Canal.BLE))
        } else if (m.presion < 60) lecturasAltas = 0
    }

    private suspend fun alRecibirEvento(m: MensajeKalmi.Evt) {
        device.enviar(Protocolo.ack(m.seq))           // siempre confirmar, aunque sea duplicado
        registrarSeq(m.seq)
        if (!eventosVistos.add(m.seq)) return         // duplicado por reintento: ya se mostró
        val evento = Evento(m.seq, m.emocion, System.currentTimeMillis(), Canal.BLE)
        crearAlerta(evento)
    }

    private fun crearAlerta(e: Evento) {
        _alertaActual.value = e
        scope.launch {
            dao.insertar(EventoEntity.desde(e))
            runCatching { remoto.subir(e) }           // si no hay internet, se reintenta luego
        }
    }

    /** Envía un comando con hasta 2 reintentos. Devuelve el RTT o null. */
    suspend fun enviarComando(c: Comando): Long? {
        val id = siguienteId++
        _estadoComando.value = c to EstadoComando.ENVIANDO
        repeat(3) {
            val respuesta = CompletableDeferred<Long>()
            okPendientes[id] = respuesta
            val inicio = System.currentTimeMillis()
            device.enviar(Protocolo.comando(id, c))
            val fin = withTimeoutOrNull(1000) { respuesta.await() }
            if (fin != null) {
                val rtt = fin - inicio
                _calidad.update { it.copy(ultimoRttMs = rtt) }
                _estadoComando.value = c to EstadoComando.CONFIRMADO
                return rtt
            }
        }
        okPendientes.remove(id)
        _estadoComando.value = c to EstadoComando.SIN_CONFIRMACION
        return null
    }

    suspend fun marcarAtendido() {
        val actual = _alertaActual.value ?: return
        enviarComando(Comando.Atendido)
        dao.marcarAtendido(actual.seq, System.currentTimeMillis())
        _alertaActual.value = null
        lecturasAltas = 0
    }
}
```

> **Nota:** si el comando ATN no llega (Kalmi lejos), la alerta igual se marca como atendida en la app; Kalmi simplemente no muestra "Ya viene alguien".

---

## 11. Historial local con Room

```kotlin
@Entity(tableName = "eventos")
data class EventoEntity(
    @PrimaryKey val seq: Int,
    val emocion: Int,
    val momento: Long,
    val canal: String,
    val atendido: Boolean = false,
    val atendidoEn: Long? = null,
    val subido: Boolean = false,          // sincronizado con Supabase
) {
    fun aDominio() = Evento(seq, Emocion.desdeCodigo(emocion)!!, momento, Canal.valueOf(canal), atendido, atendidoEn)
    companion object {
        fun desde(e: Evento) = EventoEntity(e.seq, e.emocion.codigo, e.momento, e.canal.name)
    }
}

@Dao
interface EventoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)          // ignora duplicados por seq
    suspend fun insertar(e: EventoEntity)

    @Query("SELECT * FROM eventos WHERE momento >= :desde ORDER BY momento DESC")
    fun observarDesde(desde: Long): Flow<List<EventoEntity>>

    fun observarHoy() = observarDesde(inicioDelDia())   // inicioDelDia(): epoch ms de hoy a las 00:00

    @Query("UPDATE eventos SET atendido = 1, atendidoEn = :t WHERE seq = :seq")
    suspend fun marcarAtendido(seq: Int, t: Long)

    @Query("SELECT * FROM eventos WHERE subido = 0")
    suspend fun pendientesDeSubir(): List<EventoEntity>
}
```

> **Importante:** `seq` es único **por dispositivo y por sesión de firmware**. Si Kalmi se reinicia, `seq` vuelve a 1. Solución en el firmware: guardar el último `seq` en memoria no volátil (NVS) y continuar desde ahí.

---

## 12. Nube: Supabase

### 12.1 Tablas (SQL)

```sql
-- Dispositivos Kalmi
create table dispositivos (
  id text primary key,                         -- ej. 'kalmi-a1b2'
  nombre text not null default 'Kalmi',
  clave_hash text not null,                    -- hash de la clave del dispositivo
  creado_en timestamptz default now()
);

-- Relación cuidador <-> dispositivo
create table cuidadores_dispositivos (
  cuidador_id uuid references auth.users(id) on delete cascade,
  dispositivo_id text references dispositivos(id) on delete cascade,
  primary key (cuidador_id, dispositivo_id)
);

-- Eventos (emociones, ayuda, tensión)
create table eventos (
  dispositivo_id text references dispositivos(id) on delete cascade,
  seq int not null,
  emocion smallint not null check (emocion between 1 and 6),
  momento timestamptz not null default now(),
  canal text not null check (canal in ('BLE', 'NUBE')),
  atendido_en timestamptz,
  primary key (dispositivo_id, seq)            -- evita duplicados
);

-- Última telemetría (una fila por dispositivo)
create table telemetria_actual (
  dispositivo_id text primary key references dispositivos(id) on delete cascade,
  luz smallint, presion smallint, bateria smallint,
  actualizado_en timestamptz default now()
);

-- Tokens FCM de los teléfonos de los cuidadores
create table tokens_push (
  cuidador_id uuid references auth.users(id) on delete cascade,
  token text primary key
);
```

### 12.2 Row Level Security

```sql
alter table eventos enable row level security;
alter table telemetria_actual enable row level security;
alter table cuidadores_dispositivos enable row level security;
alter table tokens_push enable row level security;

-- Un cuidador ve solo los eventos de sus dispositivos
create policy "cuidador lee sus eventos" on eventos for select
using (dispositivo_id in (
  select dispositivo_id from cuidadores_dispositivos where cuidador_id = auth.uid()
));

-- La app también sube eventos recibidos por BLE
create policy "cuidador inserta sus eventos" on eventos for insert
with check (dispositivo_id in (
  select dispositivo_id from cuidadores_dispositivos where cuidador_id = auth.uid()
));

create policy "cuidador marca atendido" on eventos for update
using (dispositivo_id in (
  select dispositivo_id from cuidadores_dispositivos where cuidador_id = auth.uid()
));

create policy "cuidador lee su telemetria" on telemetria_actual for select
using (dispositivo_id in (
  select dispositivo_id from cuidadores_dispositivos where cuidador_id = auth.uid()
));

create policy "cuidador gestiona sus tokens" on tokens_push for all
using (cuidador_id = auth.uid()) with check (cuidador_id = auth.uid());
```

### 12.3 Cómo escribe Kalmi (sin exponer claves)

Kalmi **nunca** usa la clave de servicio de Supabase. Escribe a través de una **Edge Function** que valida la clave propia del dispositivo:

```
Kalmi ──POST https://<proyecto>.supabase.co/functions/v1/ingest
        Headers: x-kalmi-id: kalmi-a1b2
                 x-kalmi-key: <clave del dispositivo>
        Body:    {"tipo":"EVT","seq":15,"emocion":3}
             o   {"tipo":"TEL","luz":70,"presion":35,"bateria":82}
```

```typescript
// supabase/functions/ingest/index.ts (esqueleto)
Deno.serve(async (req) => {
  const id = req.headers.get("x-kalmi-id");
  const key = req.headers.get("x-kalmi-key");
  // 1. Buscar dispositivo y comparar hash(key) con clave_hash  -> 401 si no coincide
  // 2. Validar cuerpo (tipo, rangos)                           -> 400 si es inválido
  // 3. EVT: insert into eventos (on conflict do nothing)        -> evita duplicados
  //    TEL: upsert en telemetria_actual
  return new Response("ok");
});
```

### 12.4 Cómo lee la app

```kotlin
val supabase = createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
    install(Auth); install(Postgrest); install(Realtime)
}

// Historial
suspend fun historialRemoto(dispositivo: String) =
    supabase.from("eventos").select {
        filter { eq("dispositivo_id", dispositivo) }
        order("momento", Order.DESCENDING)
    }.decodeList<EventoRemoto>()

// Tiempo real mientras la app está abierta
val cambios = supabase.channel("eventos")
    .postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "eventos" }
```

> La clave **anon** sí puede ir en la app: es pública por diseño y RLS limita lo que cada usuario puede ver. Guárdala en `local.properties` + `BuildConfig`, no en el repositorio.

---

## 13. Notificaciones push (FCM)

### 13.1 Flujo

```
INSERT en eventos ──> Database Webhook ──> Edge Function "notificar"
                                              │ busca cuidadores del dispositivo
                                              │ busca sus tokens en tokens_push
                                              └─> FCM HTTP v1 ──> teléfono
```

### 13.2 En la app

1. Crear el proyecto en Firebase y descargar `google-services.json` en `app/`.
2. Al iniciar sesión, obtener el token y guardarlo en `tokens_push`.
3. Implementar `KalmiMessagingService`:

```kotlin
class KalmiMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { /* upsert en tokens_push */ }

    override fun onMessageReceived(msg: RemoteMessage) {
        val emocion = msg.data["emocion"]?.toIntOrNull()?.let(Emocion::desdeCodigo) ?: return
        mostrarNotificacion(emocion)
    }
}
```

### 13.3 Tono de las notificaciones

| Evento | Canal de notificación | Importancia | Texto |
|---|---|---|---|
| Bien, Triste | `emociones` | Por defecto | "Kalmi: se siente triste" |
| Frustrado, Muy enojado, Tensión | `atencion` | Alta | "Kalmi: se siente frustrado" |
| Ayuda | `ayuda` | Alta, con vibración | "Kalmi: pide ayuda" |

Sin sonidos estridentes ni lenguaje alarmista. Ícono y color de la emoción.

---

## 14. Servicio en segundo plano

Para que la conexión BLE siga activa con la app minimizada:

```kotlin
class KalmiConnectionService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ServiceCompat maneja las diferencias entre versiones de Android
        ServiceCompat.startForeground(
            this,
            ID_NOTIF,
            notificacionPersistente("Kalmi conectado"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        // El repositorio ya mantiene la conexión; el servicio solo evita que Android cierre el proceso
        return START_STICKY
    }
    override fun onBind(intent: Intent?) = null
}
```

En el manifiesto:

```xml
<service
    android:name=".service.KalmiConnectionService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

---

## 15. Pantallas (Jetpack Compose)

### 15.1 Navegación

```
[Conexión] ── (Kalmi listo) ──> [Inicio] ⇄ [Controles] ⇄ [Historial]
     ▲                              (barra de navegación inferior)
     └──────────── desde el ícono de estado en Inicio
```

### 15.2 Inicio

```
┌─────────────────────────────────┐
│ ● Conectado (BLE)    🔋 82%      │
├─────────────────────────────────┤
│   ┌───────────────────────────┐ │
│   │  (cara ámbar)              │ │  <- fondo con el color de la emoción, suave
│   │  Frustrado                 │ │
│   │  hace 12 s                 │ │
│   │  [ Marcar como atendido ]  │ │
│   └───────────────────────────┘ │
│                                 │
│  Luz ▓▓▓▓▓▓▓░░░ 70%             │
│  Presión ▓▓▓░░░░░░░ 35%         │
│                                 │
│  Hoy: 4 emociones · 1 ayuda      │
└─────────────────────────────────┘
```

```kotlin
@Composable
fun InicioScreen(vm: InicioViewModel = viewModel(factory = ...)) {
    val alerta by vm.alerta.collectAsStateWithLifecycle()
    val tel by vm.telemetria.collectAsStateWithLifecycle()
    val conexion by vm.conexion.collectAsStateWithLifecycle()

    Column(Modifier.padding(16.dp)) {
        EstadoConexionChip(conexion)
        alerta?.let { TarjetaAlerta(it, onAtendido = vm::marcarAtendido) }
            ?: TarjetaTranquilo()
        tel?.let { BarrasTelemetria(it) }
        ResumenDelDia(vm.resumen.collectAsStateWithLifecycle().value)
    }
}
```

### 15.3 Controles

| Control | Comando | Retroalimentación |
|---|---|---|
| Botón "Ya voy" | `MSG,0` | "Enviando… / ✓ Confirmado (38 ms) / Sin confirmación" |
| Botón "Te quiero" | `MSG,1` | Igual |
| Botón "Estoy aquí" | `MSG,2` | Igual |
| Botón "Respirar juntos" | `RESP` | Igual |
| Deslizador de brillo | `BRI,v` (solo si cambia ≥ 5 y cada 300 ms como máximo) | Igual |

Los controles se **deshabilitan** si no hay conexión BLE, con el texto "Disponible cuando estés cerca de Kalmi".

### 15.4 Historial

Línea de tiempo del día: hora, emoción con su color, si fue atendida y en cuánto tiempo. Filtro por día. Más adelante: un resumen semanal ("más frustración en las tardes").

### 15.5 Conexión

- Estado del enlace, nombre del dispositivo, batería.
- Botón **Agregar Kalmi** (flujo 2.1) y **Configurar WiFi** (sección 16).
- **Diagnóstico:** recibidos, perdidos, % de pérdida, descartados por checksum y último RTT. Sirve como evidencia para la rúbrica (criterios 1.1.4.5 y 1.1.4.6).
- Solo en debug: botones para simular emociones y un deslizador de pérdida.

### 15.6 Tema

```kotlin
object KalmiColors {
    val Bien = Color(0xFF7FAF8E)
    val Triste = Color(0xFF6E8FB8)
    val Frustrado = Color(0xFFD4B26A)
    val MuyEnojado = Color(0xFFC9786B)
    val Calma = Color(0xFF9A8FC7)
}
```

Fondos de alerta con el color al **15–20% de opacidad**, nunca saturados.

---

## 16. Configuración del WiFi de Kalmi

```
App                                         Kalmi
 │ Usuario ingresa SSID y clave               │
 │──── CFG,WIFI,MiCasa,clave123*XX ──────────>│ Guarda cifrado en NVS
 │                                            │ Intenta conectar
 │<─── CFG,WIFI,OK*XX  o  CFG,WIFI,ERR*XX ────│
 │ Muestra "Red guardada ✓" o "No se pudo conectar"
```

- Solo por **BLE emparejado** (nunca por internet).
- Kalmi recuerda hasta 3 redes (casa, colegio, otra).
- La clave del WiFi **no** se guarda en la app ni en Supabase.

---

## 17. Seguridad

| Riesgo | Mitigación en la app |
|---|---|
| Otro teléfono se conecta a Kalmi | Emparejamiento BLE con código (bonding); Kalmi acepta solo dispositivos emparejados |
| Mensajes alterados | Checksum en cada línea; se descartan los inválidos |
| Mensajes repetidos o reenviados | `seq` en eventos e `id` en comandos |
| Claves en el código | Clave anon en `local.properties`; nunca la clave de servicio |
| Un cuidador ve datos de otro niño | Row Level Security en todas las tablas |
| Datos interceptados | HTTPS en toda comunicación con Supabase |
| Datos sensibles de un menor | Solo emociones y telemetría mínima; sin ubicación en esta etapa; consentimiento de la familia |

---

## 18. Pruebas

| Tipo | Qué probar | Herramienta |
|---|---|---|
| Unitarias | Parser, checksum, deduplicación, pérdida, tensión, reintentos de comandos | JUnit + `kotlinx-coroutines-test` |
| Integración | Repositorio con `FakeKalmiDevice` (incluida pérdida simulada) | JUnit |
| UI | Pantallas con datos simulados | Compose Preview + Compose UI Test |
| BLE | Comunicación con el ESP32 real | nRF Connect + app |
| Extremo a extremo | Kalmi lejos → Supabase → push | Dos personas, en lugares distintos |

**Casos que no pueden faltar:**
1. Evento duplicado (reintento) → aparece una sola vez.
2. Pérdida del 30% → el % de pérdida se calcula bien y los eventos igual llegan.
3. Desconexión en medio de un comando → "Sin confirmación", sin bloquear la app.
4. Kalmi reinicia y `seq` vuelve a 1 → no se pierden eventos nuevos.
5. App cerrada + evento por la nube → llega la notificación.

---

## 19. Plan de desarrollo paso a paso

| # | Paso | Resultado | Depende del hardware |
|---|---|---|---|
| 1 | Crear el proyecto, tema y navegación | App vacía con 4 pantallas | No |
| 2 | Modelos y `Protocolo` + pruebas unitarias | Parser probado | No |
| 3 | `FakeKalmiDevice` + `KalmiRepository` | Datos fluyendo | No |
| 4 | Pantallas Inicio, Controles e Historial con el simulador | App usable de punta a punta | No |
| 5 | Room: historial persistente | El historial sobrevive al cerrar la app | No |
| 6 | Supabase: Auth, tablas, RLS y lectura del historial | Cuenta del cuidador y nube | No |
| 7 | FCM + Edge Function `notificar` | Notificaciones push | No |
| 8 | `BleKalmiDevice` contra el ESP32 (probado antes con nRF Connect) | Conexión real | **Sí** |
| 9 | Servicio en primer plano + reconexión | Conexión estable en segundo plano | Sí |
| 10 | Configuración de WiFi + Edge Function `ingest` | Kalmi avisa desde lejos | Sí |
| 11 | Emparejamiento seguro y revisión de seguridad | Cumple el informe | Sí |
| 12 | Prueba con el usuario beta y su familia | Validación real | Sí |

**Los pasos 1 a 7 no necesitan hardware**: se pueden completar mientras llegan los componentes.

---

*Kalmi es una herramienta de apoyo y no reemplaza la orientación de profesionales.*
