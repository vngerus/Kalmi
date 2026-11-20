package cl.kalmi.app

import cl.kalmi.app.domain.Comando
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.MensajeKalmi
import cl.kalmi.app.domain.Protocolo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contrato con firmware/kalmi-uno: líneas literales tal como las imprime el sketch
 * (Serial.println → termina en \r\n, checksum en HEX mayúsculas). Si el sketch o el parser cambian
 * de formato, este test falla.
 */
class ContratoArduinoTest {

    @Test fun lineasDelArduinoSeEntienden() {
        assertEquals(MensajeKalmi.Tel(61, 70, 35), Protocolo.parsear("TEL,61,70,35*77\r\n"))
        assertEquals(MensajeKalmi.Evt(62, Emocion.FRUSTRADO), Protocolo.parsear("EVT,62,E3*35\r\n"))
        assertEquals(MensajeKalmi.Pal(63, listOf(0, 8)), Protocolo.parsear("PAL,63,0,8*7C\r\n"))
        assertEquals(MensajeKalmi.Pal(64, listOf(5)), Protocolo.parsear("PAL,64,5*6A\r\n"))
        assertEquals(MensajeKalmi.Ok(7), Protocolo.parsear("OK,7*1F\r\n"))
        assertEquals(MensajeKalmi.Cfg(listOf("PAL", "OK")), Protocolo.parsear("CFG,PAL,OK*1B\r\n"))
        assertEquals(MensajeKalmi.Cfg(listOf("ID", "kalmi-uno-1A2B")), Protocolo.parsear("CFG,ID,kalmi-uno-1A2B*59"))
    }

    @Test fun lineasDeLaAppSonLasQueEsperaElArduino() {
        // El sketch compara el texto antes del '*' y el XOR: estas son las formas exactas que procesa.
        assertEquals("ACK,62*35".substringBefore('*'), Protocolo.ack(62).substringBefore('*'))
        assertEquals("CMD,7,MSG,1", Protocolo.comando(7, Comando.Mensaje(1)).substringBefore('*'))
        assertEquals("CFG,PAL,8,Mama", Protocolo.cfgPalabra(8, "Mamá").substringBefore('*'))
    }
}
