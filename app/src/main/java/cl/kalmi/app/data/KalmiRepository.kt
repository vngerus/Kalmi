package cl.kalmi.app.data

import cl.kalmi.app.device.KalmiDevice
import cl.kalmi.app.domain.CalidadEnlace
import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.Comando
import cl.kalmi.app.domain.Contexto
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.EstadoComando
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Interpretador
import cl.kalmi.app.domain.MensajeKalmi
import cl.kalmi.app.domain.Protocolo
import cl.kalmi.app.domain.ReglasInterpretador
import cl.kalmi.app.domain.Telemetria
import cl.kalmi.app.domain.Vocabulario
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lógica central: ACK, deduplicación, pérdida, RTT, detección de tensión y comandos con reintento.
 *
 * [scope] debe ser de un solo hilo (Main): así los mapas/contadores no necesitan locks.
 */
class KalmiRepository(
    private val device: KalmiDevice,
    private val scope: CoroutineScope,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val interpretador: Interpretador = ReglasInterpretador(),
    personalesIniciales: List<String> = Vocabulario.PERSONALES_DEFECTO,
    private val guardarPersonales: (List<String>) -> Unit = {},
    cargarHistorial: suspend () -> List<Evento> = { emptyList() },
    private val guardarEvento: suspend (Evento) -> Unit = {},
) {
    val estadoConexion = device.estado

    private val _telemetria = MutableStateFlow<Telemetria?>(null)
    val telemetria: StateFlow<Telemetria?> = _telemetria.asStateFlow()

    private val _bateria = MutableStateFlow<Int?>(null)
    val bateria: StateFlow<Int?> = _bateria.asStateFlow()

    private val _alertaActual = MutableStateFlow<Evento?>(null)
    val alertaActual: StateFlow<Evento?> = _alertaActual.asStateFlow()

    private val _calidad = MutableStateFlow(CalidadEnlace())
    val calidad: StateFlow<CalidadEnlace> = _calidad.asStateFlow()

    private val _estadoComando = MutableStateFlow<Pair<Comando, EstadoComando>?>(null)
    val estadoComando: StateFlow<Pair<Comando, EstadoComando>?> = _estadoComando.asStateFlow()

    // Historial: en memoria para la UI y guardado en Room (paso 5) a través de guardarEvento.
    private val _historial = MutableStateFlow<List<Evento>>(emptyList())
    val historial: StateFlow<List<Evento>> = _historial.asStateFlow()

    /** Palabras personales (índices 4–9). La app es la dueña de la lista; Kalmi recibe una copia. */
    private val _personales = MutableStateFlow(personalesIniciales)
    val personales: StateFlow<List<String>> = _personales.asStateFlow()

    private var ultimoSeq = 0
    private val eventosVistos = mutableSetOf<Int>()
    private var lecturasAltas = 0
    private var siguienteId = 1
    private val okPendientes = mutableMapOf<Int, CompletableDeferred<Long>>()

    init {
        scope.launch {
            val guardado = cargarHistorial()
            // Si llegó algo mientras se cargaba, queda arriba y no se duplica.
            _historial.update { nuevos -> nuevos + guardado.filter { g -> nuevos.none { it.mismoQue(g) } } }
        }
        scope.launch {
            device.lineas.collect { linea ->
                when (val m = Protocolo.parsear(linea)) {
                    null -> _calidad.update { it.copy(descartados = it.descartados + 1) }
                    is MensajeKalmi.Tel -> alRecibirTelemetria(m)
                    is MensajeKalmi.Evt -> alRecibirEvento(m)
                    is MensajeKalmi.Ok -> okPendientes.remove(m.id)?.complete(reloj())
                    is MensajeKalmi.Pal -> alRecibirPalabras(m)
                    is MensajeKalmi.Bat -> _bateria.value = m.porcentaje
                    is MensajeKalmi.Cfg -> Unit // CFG,PAL,OK / ERR: sin acción por ahora
                }
            }
        }
        scope.launch {
            device.estado.collect {
                if (it is EstadoConexion.Conectado) {
                    reiniciarSeq() // Kalmi pudo reiniciarse: su seq salta y no son mensajes perdidos
                    enviarPersonales()
                }
            }
        }
    }

    /** Guarda la lista (6 palabras) y, si Kalmi está conectado, se la envía. Si no, se envía al conectar. */
    suspend fun actualizarPersonales(lista: List<String>) {
        require(lista.size == Vocabulario.PERSONALES) { "se esperan ${Vocabulario.PERSONALES} palabras" }
        _personales.value = lista
        guardarPersonales(lista)
        if (device.estado.value is EstadoConexion.Conectado) enviarPersonales()
    }

    private suspend fun enviarPersonales() {
        _personales.value.forEachIndexed { i, texto -> device.enviar(Protocolo.cfgPalabra(i + 4, texto)) }
    }

    /** Respuesta del cuidador a "¿Acertó?": queda junto al evento (datos para una IA futura). */
    fun marcarAcierto(evento: Evento, acerto: Boolean) {
        actualizar(evento) { it.copy(acerto = acerto) }
        _alertaActual.update { if (it?.mismoQue(evento) == true) it.copy(acerto = acerto) else it }
    }

    /** Cambia un evento del historial (identificado por momento + seq) y lo guarda. */
    private fun actualizar(evento: Evento, cambio: (Evento) -> Evento) {
        var guardar: Evento? = null
        _historial.update { lista -> lista.map { if (it.mismoQue(evento)) cambio(it).also { c -> guardar = c } else it } }
        guardar?.let { scope.launch { guardarEvento(it) } }
    }

    suspend fun conectar() = device.conectar()
    suspend fun desconectar() = device.desconectar()

    private fun registrarSeq(seq: Int): Boolean {
        if (seq <= ultimoSeq) return false
        if (ultimoSeq > 0 && seq > ultimoSeq + 1) {
            _calidad.update { it.copy(perdidos = it.perdidos + (seq - ultimoSeq - 1)) }
        }
        ultimoSeq = seq
        _calidad.update { it.copy(recibidos = it.recibidos + 1) }
        return true
    }

    // ponytail: la dedupe vale por conexión; un reintento que cruce una reconexión se mostraría dos veces (raro).
    private fun reiniciarSeq() {
        ultimoSeq = 0
        eventosVistos.clear()
    }

    private fun alRecibirTelemetria(m: MensajeKalmi.Tel) {
        // TEL nunca se reintenta: si su seq no avanza, es otro Kalmi o uno con EEPROM nueva.
        if (m.seq <= ultimoSeq) reiniciarSeq()
        registrarSeq(m.seq)
        _telemetria.value = Telemetria(m.seq, m.luz, m.presion, reloj())
        // Tensión: 3 lecturas seguidas ≥ 75; se rearma bajo 60 (histéresis).
        if (m.presion >= 75) {
            if (++lecturasAltas == 3) crearAlerta(Evento(m.seq, Emocion.TENSION, reloj(), Canal.BLE))
        } else if (m.presion < 60) {
            lecturasAltas = 0
        }
    }

    private suspend fun alRecibirEvento(m: MensajeKalmi.Evt) {
        device.enviar(Protocolo.ack(m.seq)) // siempre confirmar, aunque sea duplicado
        registrarSeq(m.seq)
        if (!eventosVistos.add(m.seq)) return // duplicado por reintento: ya se mostró
        crearAlerta(Evento(m.seq, m.emocion, reloj(), Canal.BLE))
    }

    private suspend fun alRecibirPalabras(m: MensajeKalmi.Pal) {
        device.enviar(Protocolo.ack(m.seq)) // igual que EVT: confirmar siempre
        registrarSeq(m.seq)
        if (!eventosVistos.add(m.seq)) return
        val vocabulario = Vocabulario.completo(_personales.value)
        val palabras = m.indices.map { vocabulario[it] }
        val ahora = reloj()
        val contexto = Contexto(
            emocionReciente = _historial.value
                .firstOrNull { it.emocion != null && it.momento >= ahora - CINCO_MIN }?.emocion,
            presionAlta = (_telemetria.value?.presion ?: 0) >= 75,
        )
        crearAlerta(
            Evento(m.seq, null, ahora, Canal.BLE, palabras = palabras, interpretacion = interpretador.interpretar(palabras, contexto)),
        )
    }

    private fun crearAlerta(e: Evento) {
        _alertaActual.value = e
        _historial.update { listOf(e) + it }
        scope.launch { guardarEvento(e) }
    }

    /** Envía un comando con hasta 2 reintentos (1 s c/u). Devuelve el RTT en ms o null. */
    suspend fun enviarComando(c: Comando): Long? {
        val id = siguienteId++
        _estadoComando.value = c to EstadoComando.ENVIANDO
        repeat(3) {
            val respuesta = CompletableDeferred<Long>()
            okPendientes[id] = respuesta
            val inicio = reloj()
            device.enviar(Protocolo.comando(id, c))
            val fin = withTimeoutOrNull(1_000) { respuesta.await() }
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

    /** Se marca en la app al instante; si ATN no llega, Kalmi solo no muestra "Ya viene alguien". */
    suspend fun marcarAtendido() {
        val actual = _alertaActual.value ?: return
        val ahora = reloj()
        actualizar(actual) { it.copy(atendido = true, atendidoEn = ahora) }
        _alertaActual.value = null
        lecturasAltas = 0
        enviarComando(Comando.Atendido)
    }

    private companion object {
        const val CINCO_MIN = 5 * 60_000L
    }
}
