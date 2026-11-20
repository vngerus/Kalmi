package cl.kalmi.app

import cl.kalmi.app.device.ble.AcumuladorLineas
import cl.kalmi.app.device.ble.trozos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineasTest {

    @Test fun acumulaYCortaPorSaltoDeLinea() {
        val a = AcumuladorLineas()
        assertEquals(emptyList<String>(), a.agregar("TEL,1,7".toByteArray()))
        assertEquals(listOf("TEL,1,70,20*3A", "OK,2*1B"), a.agregar("0,20*3A\r\nOK,2*1B\n".toByteArray()))
    }

    @Test fun lineasVaciasSeIgnoran() {
        assertEquals(listOf("OK,1*1A"), AcumuladorLineas().agregar("\r\n\nOK,1*1A\n".toByteArray()))
    }

    @Test fun trozosDe20Bytes() {
        val texto = "CMD,10,MSG,1*4F\nCFG,PAL,4,Abrazo*11\n"
        val t = trozos(texto)
        assertTrue(t.all { it.size <= 20 })
        assertEquals(texto, t.joinToString("") { it.decodeToString() })
    }
}
