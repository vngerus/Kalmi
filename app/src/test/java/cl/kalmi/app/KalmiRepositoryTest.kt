package cl.kalmi.app

import cl.kalmi.app.data.KalmiRepository
import cl.kalmi.app.device.KalmiDevice
import cl.kalmi.app.domain.Comando
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.EstadoComando
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.domain.Protocolo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guía §18. El repositorio corre sobre el tiempo virtual de runTest (un solo hilo),
 * con un dispositivo determinista en vez de FakeKalmiDevice, que es aleatorio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KalmiRepositoryTest {

    private val device = DispositivoDePrueba()

    private fun TestScope.crearRepo(): KalmiRepository {
        val repo = KalmiRepository(device, backgroundScope) { currentTime }
        runCurrent() // el repo queda suscrito a device.lineas antes de emitir nada
        return repo
    }

    /** Simula líneas que llegan desde Kalmi (se firman acá) y deja que el repo las procese. */
    private suspend fun TestScope.recibir(vararg cuerpos: String) {
        cuerpos.forEach { device.lineas.emit(Protocolo.firmar(it)) }
        runCurrent()
    }

    @Test fun eventoDuplicadoApareceUnaVez() = runTest {
        val repo = crearRepo()

        recibir("EVT,5,E3", "EVT,5,E3") // el segundo es un reintento de Kalmi

        assertEquals(1, repo.historial.value.size)
        assertEquals(Emocion.FRUSTRADO, repo.alertaActual.value?.emocion)
        assertEquals(1, repo.calidad.value.recibidos)
        // Se confirma siempre, también el duplicado: así Kalmi deja de reintentar.
        assertEquals(2, device.enviadas.count { it == Protocolo.ack(5) })
    }

    @Test fun perdidaSeCalculaPorHuecosDeSeq() = runTest {
        val repo = crearRepo()

        // De seq 1..10 se pierden 3, 6 y 9: 30 % de pérdida.
        recibir("TEL,1,70,20", "TEL,2,70,20", "TEL,4,70,20", "TEL,5,70,20", "TEL,7,70,20", "TEL,8,70,20", "EVT,10,E2")

        val calidad = repo.calidad.value
        assertEquals(7, calidad.recibidos)
        assertEquals(3, calidad.perdidos)
        assertEquals(30f, calidad.porcentajePerdida, 0.01f)
        // El evento igual llega aunque se hayan perdido mensajes antes.
        assertEquals(Emocion.TRISTE, repo.historial.value.single().emocion)
    }

    @Test fun lineaCorruptaSeDescarta() = runTest {
        val repo = crearRepo()

        device.lineas.emit(Protocolo.firmar("EVT,1,E3").replace("E3", "E4")) // checksum ya no coincide
        runCurrent()

        assertEquals(1, repo.calidad.value.descartados)
        assertEquals(0, repo.calidad.value.recibidos)
        assertTrue(repo.historial.value.isEmpty())
    }

    @Test fun tensionConTresLecturasAltasYSeRearmaBajo60() = runTest {
        val repo = crearRepo()

        recibir("TEL,1,70,80", "TEL,2,70,80")
        assertNull(repo.alertaActual.value)

        recibir("TEL,3,70,80")
        assertEquals(Emocion.TENSION, repo.alertaActual.value?.emocion)

        // Sigue alta, o baja solo a la zona 60–74: no se rearma, no hay alerta nueva.
        recibir("TEL,4,70,90", "TEL,5,70,65", "TEL,6,70,80")
        assertEquals(1, repo.historial.value.size)

        // Baja de 60 (se rearma) y vuelve a subir tres veces: segunda alerta.
        recibir("TEL,7,70,40", "TEL,8,70,80", "TEL,9,70,80", "TEL,10,70,80")
        assertEquals(2, repo.historial.value.size)
    }

    @Test fun comandoConfirmadoMideRtt() = runTest {
        val repo = crearRepo()
        device.retardoOkMs = 40

        val rtt = repo.enviarComando(Comando.Mensaje(0))

        assertEquals(40L, rtt)
        assertEquals(40L, repo.calidad.value.ultimoRttMs)
        assertEquals(Comando.Mensaje(0) to EstadoComando.CONFIRMADO, repo.estadoComando.value)
        assertEquals(1, device.enviadas.count { it.startsWith("CMD,") })
    }

    @Test fun comandoSinOkQuedaSinConfirmacion() = runTest {
        val repo = crearRepo()
        device.respondeOk = false // como una desconexión en medio del comando

        val rtt = repo.enviarComando(Comando.Respirar)

        assertNull(rtt)
        assertEquals(Comando.Respirar to EstadoComando.SIN_CONFIRMACION, repo.estadoComando.value)
        assertEquals(3, device.enviadas.count { it.startsWith("CMD,") }) // envío + 2 reintentos
        assertEquals(3_000L, currentTime) // 1 s de espera por intento, sin quedar bloqueado
    }

    @Test fun marcarAtendidoCierraLaAlertaYAvisaAKalmi() = runTest {
        val repo = crearRepo()
        recibir("EVT,1,E2")

        repo.marcarAtendido()

        assertNull(repo.alertaActual.value)
        assertTrue(repo.historial.value.single().atendido)
        assertTrue(Protocolo.comando(1, Comando.Atendido) in device.enviadas)
    }
}

/** Dispositivo determinista: registra lo que la app envía y responde `OK` a los comandos si [respondeOk]. */
private class DispositivoDePrueba : KalmiDevice {
    override val estado = MutableStateFlow<EstadoConexion>(EstadoConexion.Conectado("Kalmi-TEST"))
    override val lineas = MutableSharedFlow<String>(extraBufferCapacity = 64)

    val enviadas = mutableListOf<String>()
    var respondeOk = true
    var retardoOkMs = 0L

    override suspend fun conectar() = Unit
    override suspend fun desconectar() = Unit

    override suspend fun enviar(linea: String) {
        enviadas += linea
        if (respondeOk && linea.startsWith("CMD,")) {
            delay(retardoOkMs)
            lineas.emit(Protocolo.firmar("OK," + linea.substringBefore('*').split(",")[1]))
        }
    }
}
