package cl.kalmi.app

import cl.kalmi.app.device.DispositivoConmutable
import cl.kalmi.app.device.KalmiDevice
import cl.kalmi.app.domain.EstadoConexion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DispositivoConmutableTest {

    private class Disp(nombre: String) : KalmiDevice {
        override val estado = MutableStateFlow<EstadoConexion>(EstadoConexion.Conectado(nombre))
        override val lineas = MutableSharedFlow<String>(extraBufferCapacity = 8)
        var desconectado = false
        val enviadas = mutableListOf<String>()
        override suspend fun conectar() = Unit
        override suspend fun desconectar() { desconectado = true; estado.value = EstadoConexion.Desconectado }
        override suspend fun enviar(linea: String) { enviadas += linea }
    }

    @Test fun cambiarDesconectaElAnteriorYReenviaElNuevo() = runTest {
        val a = Disp("A")
        val b = Disp("B")
        val c = DispositivoConmutable(backgroundScope, a)
        val recibidas = mutableListOf<String>()
        backgroundScope.launch { c.lineas.collect { recibidas += it } }
        runCurrent()

        a.lineas.emit("de A")
        runCurrent()
        c.cambiar(b)
        runCurrent()
        b.lineas.emit("de B")
        a.lineas.emit("A ya no cuenta")
        c.enviar("hola")
        runCurrent()

        assertTrue(a.desconectado)
        assertEquals(listOf("de A", "de B"), recibidas)
        assertEquals(EstadoConexion.Conectado("B"), c.estado.value)
        assertEquals(listOf("hola"), b.enviadas)
    }
}
