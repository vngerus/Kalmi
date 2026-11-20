package cl.kalmi.app.device

import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.domain.Protocolo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Simula el firmware: telemetría cada 2 s, eventos aleatorios con reintento sin ACK,
 * `OK` a los comandos y batería cada minuto.
 *
 * Todo el estado mutable se toca desde [scope], que debe ser de un solo hilo (Main).
 */
class FakeKalmiDevice(private val scope: CoroutineScope) : KalmiDevice {

    private val _estado = MutableStateFlow<EstadoConexion>(EstadoConexion.Desconectado)
    override val estado = _estado.asStateFlow()

    private val _lineas = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lineas = _lineas.asSharedFlow()

    private var seq = 0
    private var presion = 0
    private var bateria = 100
    private var tareas: Job? = null
    private val pendientes = mutableMapOf<Int, Int>() // seq -> reintentos

    /** Para probar la UI: perder un % de mensajes (0.0–1.0). */
    var probabilidadPerdida = 0.0

    override suspend fun conectar() {
        if (tareas?.isActive == true) return
        _estado.value = EstadoConexion.Conectando
        delay(800)
        _estado.value = EstadoConexion.Conectado("Kalmi-SIM")
        tareas = scope.launch {
            launch { // telemetría
                while (isActive) {
                    presion = (presion + (-10..12).random()).coerceIn(0, 100)
                    emitir("TEL,${++seq},${(55..80).random()},$presion")
                    delay(2_000)
                }
            }
            launch { // batería
                while (isActive) {
                    emitir("BAT,$bateria")
                    bateria = (bateria - 1).coerceAtLeast(5)
                    delay(60_000)
                }
            }
            launch { // eventos aleatorios cada 15–40 s
                while (isActive) {
                    delay((15_000L..40_000L).random())
                    // 1 de cada 3 avisos son palabras (1–2 índices), el resto emociones.
                    if (Random.nextInt(3) == 0) emitirPalabras(indicesAzar()) else emitirEvento((1..5).random())
                }
            }
        }
    }

    /** Depuración: simula que el niño presiona una emoción. */
    fun simularBoton(codigo: Int) {
        if (estado.value is EstadoConexion.Conectado) scope.launch { emitirEvento(codigo) }
    }

    /** Depuración: simula que el niño elige palabras con el botón Quiero (índices 0–9). */
    fun simularPalabras(indices: List<Int> = indicesAzar()) {
        if (estado.value is EstadoConexion.Conectado) scope.launch { emitirPalabras(indices) }
    }

    private fun indicesAzar() = List((1..2).random()) { (0..9).random() }

    private suspend fun emitirEvento(codigo: Int) = emitirConReintento { s -> "EVT,$s,E$codigo" }

    private suspend fun emitirPalabras(indices: List<Int>) =
        emitirConReintento { s -> "PAL,$s," + indices.joinToString(",") }

    /** Envío original + hasta 3 reintentos cada 1,5 s mientras no llegue ACK (como el firmware). */
    private suspend fun emitirConReintento(cuerpo: (seq: Int) -> String) {
        val s = ++seq
        pendientes[s] = 0
        while ((pendientes[s] ?: return) <= 3) {
            emitir(cuerpo(s))
            delay(1_500)
            pendientes[s]?.let { pendientes[s] = it + 1 } ?: return
        }
        pendientes.remove(s)
    }

    override suspend fun enviar(linea: String) {
        if (estado.value !is EstadoConexion.Conectado) return // como BLE: sin enlace no llega
        if (Random.nextDouble() < probabilidadPerdida) return
        val m = linea.substringBefore('*').split(",")
        when (m[0]) {
            "ACK" -> pendientes.remove(m[1].toInt())
            "CFG" -> if (m.getOrNull(1) == "PAL") emitir("CFG,PAL,OK")
            "CMD" -> {
                delay((20L..60L).random())
                emitir("OK,${m[1]}")
            }
        }
    }

    override suspend fun desconectar() {
        tareas?.cancel()
        pendientes.clear()
        _estado.value = EstadoConexion.Desconectado
    }

    private suspend fun emitir(cuerpo: String) {
        if (Random.nextDouble() < probabilidadPerdida) return
        _lineas.emit(Protocolo.firmar(cuerpo))
    }
}
