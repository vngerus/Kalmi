package cl.kalmi.app

import cl.kalmi.app.data.local.EventoEntity
import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Interpretacion
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que se guarda en Room vuelve igual: emoción, palabras con tildes, nulos. */
class EventoEntityTest {

    private fun idaYVuelta(e: Evento) = assertEquals(e, EventoEntity.desde(e).aDominio())

    @Test fun emocionSinPalabras() = idaYVuelta(
        Evento(7, Emocion.FRUSTRADO, 1_000, Canal.BLE, atendido = true, atendidoEn = 2_000),
    )

    @Test fun palabrasConTildesInterpretacionYAcierto() = idaYVuelta(
        Evento(
            8, null, 3_000, Canal.BLE,
            palabras = listOf("Baño", "Mamá"),
            interpretacion = Interpretacion("Quiere baño", "Eligió \"Baño\""),
            acerto = false,
        ),
    )

    @Test fun nulosSeMantienen() = idaYVuelta(Evento(9, Emocion.AYUDA, 4_000, Canal.NUBE))
}
