package cl.kalmi.app

import cl.kalmi.app.domain.Contexto
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.ReglasInterpretador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasInterpretadorTest {

    private val reglas = ReglasInterpretador()
    private fun interpretar(vararg p: String, contexto: Contexto = Contexto()) = reglas.interpretar(p.toList(), contexto)

    @Test fun quieroMasObjeto() {
        val r = interpretar("Quiero", "Abrazo")
        assertEquals("Quiere abrazo", r.texto)
        assertTrue(r.razon, "Quiero + Abrazo" in r.razon)
    }

    @Test fun noMasObjeto() = assertEquals("No quiere jugar", interpretar("No", "Jugar").texto)

    @Test fun masMasObjeto() = assertEquals("Quiere más agua", interpretar("Más", "Agua").texto)

    @Test fun pararSolo() = assertEquals("Quiere parar", interpretar("Parar").texto)

    @Test fun dueleConEmocionReciente() {
        val r = interpretar("Duele", contexto = Contexto(emocionReciente = Emocion.FRUSTRADO))
        assertEquals("Dice que le duele algo", r.texto)
        assertTrue(r.razon, "hace poco marcó Frustrado" in r.razon)
    }

    @Test fun dueleGanaAunqueVayaSegundo() = assertEquals("Dice que le duele algo", interpretar("Quiero", "Duele").texto)

    @Test fun palabraSola() = assertEquals("Dice: agua", interpretar("Agua").texto)

    @Test fun dosPalabrasSinPlantilla() = assertEquals("Dice: agua + jugar", interpretar("Agua", "Jugar").texto)

    @Test fun presionAltaSeMencionaEnLaRazon() {
        val r = interpretar("Agua", contexto = Contexto(presionAlta = true))
        assertTrue(r.razon, "presión alta" in r.razon)
    }
}
