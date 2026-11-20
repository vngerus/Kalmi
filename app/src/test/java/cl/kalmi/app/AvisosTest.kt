package cl.kalmi.app

import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.CanalAviso
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Interpretacion
import cl.kalmi.app.domain.Avisos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisosTest {

    private fun emocion(e: Emocion) = Evento(1, e, 0, Canal.BLE)
    private fun palabras(vararg p: String, texto: String) =
        Evento(2, null, 0, Canal.BLE, palabras = p.toList(), interpretacion = Interpretacion(texto, "Eligió " + p.joinToString(" + ")))

    @Test fun tristeVaAlCanalEmocionesConTextoCalmado() {
        val a = Avisos.texto(emocion(Emocion.TRISTE))
        assertEquals(CanalAviso.EMOCIONES, a.canal)
        assertEquals("Kalmi: se siente triste", a.titulo)
    }

    @Test fun frustradoVaAlCanalAtencion() = assertEquals(CanalAviso.ATENCION, Avisos.texto(emocion(Emocion.FRUSTRADO)).canal)

    @Test fun ayudaTieneCanalPropio() {
        val a = Avisos.texto(emocion(Emocion.AYUDA))
        assertEquals(CanalAviso.AYUDA, a.canal)
        assertEquals("Kalmi: pide ayuda", a.titulo)
    }

    @Test fun palabrasUsanLaInterpretacion() {
        val a = Avisos.texto(palabras("Quiero", "Abrazo", texto = "Quiere abrazo"))
        assertEquals(CanalAviso.ATENCION, a.canal)
        assertEquals("Kalmi: quiere abrazo", a.titulo)
        assertEquals("Eligió Quiero + Abrazo", a.cuerpo)
    }

    @Test fun respuestaSugeridaSegunElAviso() {
        assertEquals(0, Avisos.respuestaSugerida(emocion(Emocion.AYUDA))) // Ya voy
        assertEquals(1, Avisos.respuestaSugerida(emocion(Emocion.TRISTE))) // Te quiero
        assertNull(Avisos.respuestaSugerida(emocion(Emocion.BIEN)))
        assertEquals(0, Avisos.respuestaSugerida(palabras("Quiero", "Abrazo", texto = "Quiere abrazo")))
        assertEquals(0, Avisos.respuestaSugerida(palabras("Duele", texto = "Dice que le duele algo")))
        assertEquals(2, Avisos.respuestaSugerida(palabras("Parar", texto = "Quiere parar"))) // Estoy aquí
    }
}
