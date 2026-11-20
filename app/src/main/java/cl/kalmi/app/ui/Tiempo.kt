package cl.kalmi.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FECHA_HORA = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es"))

/** "recién" · "hace 12 s" · "hace 3 min" · "a las 13:05" · "19 nov 22:10". Siempre en español. */
fun haceCuanto(momento: Long, ahora: Long = System.currentTimeMillis(), zona: ZoneId = ZoneId.systemDefault()): String {
    val seg = (ahora - momento).coerceAtLeast(0) / 1000
    if (seg < 1) return "recién"
    if (seg < 60) return "hace $seg s"
    if (seg < 3600) return "hace ${seg / 60} min"
    val cuando = Instant.ofEpochMilli(momento).atZone(zona)
    val hoy = Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate()
    return if (cuando.toLocalDate() == hoy) "a las ${cuando.format(HORA)}" else cuando.format(FECHA_HORA).replace(".", "")
}
