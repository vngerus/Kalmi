package cl.kalmi.app.ui.historial

import cl.kalmi.app.ui.theme.tintaTema
import cl.kalmi.app.ui.TarjetaFranja
import cl.kalmi.app.ui.Encabezado
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Interpretacion
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.theme.KalmiTheme
import cl.kalmi.app.ui.theme.colorTema
import cl.kalmi.app.ui.theme.titulo
import cl.kalmi.app.ui.theme.fondoTema
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FORMATO_DIA = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

/** Guía §15.4. Línea de tiempo por día: hora, emoción con su color y si fue atendida. */
@Composable
fun HistorialScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val historial by vm.historial.collectAsStateWithLifecycle()

    HistorialContenido(historial, modifier)
}

@Composable
fun HistorialContenido(
    historial: List<Evento>,
    modifier: Modifier = Modifier,
    hoy: LocalDate = LocalDate.now(),
) {
    // Días disponibles: siempre hoy, más los que tengan eventos (más reciente primero).
    val dias = remember(historial, hoy) {
        (listOf(hoy) + historial.map { it.dia() }).distinct().sortedDescending()
    }
    // Se guarda como epochDay para que sobreviva a rotaciones. Null = hoy.
    var diaElegido by rememberSaveable { mutableStateOf<Long?>(null) }
    val dia = diaElegido?.let(LocalDate::ofEpochDay) ?: hoy
    val eventos = remember(historial, dia) { historial.filter { it.dia() == dia } }

    Column(modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Encabezado("Historial")

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dias) { d ->
                FilterChip(
                    selected = d == dia,
                    onClick = { diaElegido = d.toEpochDay() },
                    label = { Text(etiquetaDia(d, hoy)) },
                )
            }
        }

        if (eventos.isEmpty()) {
            Text(
                "Sin registros este día",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // seq puede repetirse si Kalmi reinicia, por eso la clave incluye el momento.
                items(eventos, key = { "${it.momento}-${it.seq}" }) { FilaEvento(it) }
            }
        }
    }
}

@Composable
private fun FilaEvento(e: Evento) {
    TarjetaFranja(franja = e.colorTema, fondo = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(e.hora(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = e.tintaTema)
                if (e.palabras.isNotEmpty()) {
                    Text("Eligió: " + e.palabras.joinToString(" + "), style = MaterialTheme.typography.bodySmall)
                }
                e.acerto?.let { Text(if (it) "✓ acertó" else "✗ no acertó", style = MaterialTheme.typography.bodySmall) }
                Text(textoAtencion(e), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Evento.fechaHora() = Instant.ofEpochMilli(momento).atZone(ZoneId.systemDefault())
private fun Evento.dia(): LocalDate = fechaHora().toLocalDate()
private fun Evento.hora(): String = fechaHora().format(FORMATO_HORA)

private fun etiquetaDia(d: LocalDate, hoy: LocalDate): String = when (d) {
    hoy -> "Hoy"
    hoy.minusDays(1) -> "Ayer"
    else -> d.format(FORMATO_DIA)
}

/** "Atendida en 12 s" / "Atendida en 2 min" / "Sin atender". */
private fun textoAtencion(e: Evento): String {
    if (!e.atendido) return "Sin atender"
    val segundos = ((e.atendidoEn ?: return "Atendida") - e.momento).coerceAtLeast(0) / 1000
    return if (segundos < 60) "Atendida en $segundos s" else "Atendida en ${segundos / 60} min"
}

@Preview(showBackground = true)
@Composable
private fun HistorialPreview() = KalmiTheme {
    val ahora = System.currentTimeMillis()
    HistorialContenido(
        historial = listOf(
            Evento(15, Emocion.FRUSTRADO, ahora - 60_000, Canal.BLE),
            Evento(12, Emocion.TRISTE, ahora - 600_000, Canal.BLE, atendido = true, atendidoEn = ahora - 588_000),
            Evento(
                14, null, ahora - 300_000, Canal.BLE, palabras = listOf("No", "Jugar"),
                interpretacion = Interpretacion("No quiere jugar", "Eligió No + Jugar"), acerto = true,
            ),
            Evento(9, Emocion.BIEN, ahora - 3_600_000, Canal.NUBE, atendido = true, atendidoEn = ahora - 3_400_000),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun HistorialVacioPreview() = KalmiTheme {
    HistorialContenido(historial = emptyList())
}
