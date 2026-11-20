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
}
