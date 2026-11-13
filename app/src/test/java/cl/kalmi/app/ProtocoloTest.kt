package cl.kalmi.app

import cl.kalmi.app.domain.Comando
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.MensajeKalmi
import cl.kalmi.app.domain.Protocolo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocoloTest {

    @Test fun checksumValidaYRechaza() {
        val linea = Protocolo.firmar("EVT,15,E3")
        assertEquals(MensajeKalmi.Evt(15, Emocion.FRUSTRADO), Protocolo.parsear(linea))
        assertNull(Protocolo.parsear(linea.replace("E3", "E4"))) // alterado
    }

    @Test fun rechazaBasura() {
        assertNull(Protocolo.parsear("sin asterisco"))
        assertNull(Protocolo.parsear(Protocolo.firmar("EVT,1,E9"))) // emoción inexistente
        assertNull(Protocolo.parsear(Protocolo.firmar("TEL,1"))) // faltan campos
    }

    @Test fun telemetriaSeRecorta() {
        assertEquals(MensajeKalmi.Tel(3, 100, 0), Protocolo.parsear(Protocolo.firmar("TEL,3,150,-5")))
    }

    @Test fun comandoBrilloSeRecorta() {
        assertEquals(Protocolo.firmar("CMD,7,BRI,10"), Protocolo.comando(7, Comando.Brillo(2)))
    }

    // TODO(colega): KalmiRepositoryTest con kotlinx-coroutines-test (guía §18):
    //   evento duplicado aparece una vez · % pérdida · comando sin OK → SIN_CONFIRMACION
}
