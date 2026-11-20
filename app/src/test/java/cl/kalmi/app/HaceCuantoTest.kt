package cl.kalmi.app

import cl.kalmi.app.ui.haceCuanto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HaceCuantoTest {

    private val zona = ZoneId.of("America/Santiago")
    private val ahora = LocalDateTime.of(2026, 11, 20, 15, 30).atZone(zona).toInstant().toEpochMilli()

    @Test fun segundos() = assertEquals("hace 12 s", haceCuanto(ahora - 12_000, ahora, zona))

    @Test fun recienCuandoEsMenosDeUnSegundo() = assertEquals("recién", haceCuanto(ahora - 300, ahora, zona))

    @Test fun minutos() = assertEquals("hace 3 min", haceCuanto(ahora - 185_000, ahora, zona))

    @Test fun mismoDiaMuestraHora() = assertEquals("a las 13:05", haceCuanto(ahora - (2 * 3600 + 25 * 60) * 1000L, ahora, zona))

    @Test fun otroDiaMuestraFecha() = assertEquals("19 nov 22:10", haceCuanto(ahora - (17 * 3600 + 20 * 60) * 1000L, ahora, zona))
}
