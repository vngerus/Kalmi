package cl.kalmi.app.ui.inicio

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import cl.kalmi.app.ui.rememberConectar
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Interpretacion
import cl.kalmi.app.domain.Telemetria
import cl.kalmi.app.ui.BarraSuave
import cl.kalmi.app.ui.CaraKalmi
import cl.kalmi.app.ui.Encabezado
import cl.kalmi.app.ui.Indicador
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.TarjetaFranja
import cl.kalmi.app.ui.haceCuanto
import cl.kalmi.app.ui.theme.KalmiColors
import cl.kalmi.app.ui.theme.KalmiTheme
import cl.kalmi.app.ui.theme.colorTema
import cl.kalmi.app.ui.theme.fondoTema
import cl.kalmi.app.ui.theme.tintaTema
import cl.kalmi.app.ui.theme.titulo

/**
 * Patrón de referencia para las demás pantallas:
 * - `XScreen(vm)` lee los StateFlow con collectAsStateWithLifecycle.
 * - `XContenido(...)` recibe datos simples + lambdas: sin ViewModel, así tiene @Preview.
 */
@Composable
fun InicioScreen(vm: KalmiViewModel, modifier: Modifier = Modifier, onResponder: () -> Unit = {}) {
    val conexion by vm.conexion.collectAsStateWithLifecycle()
    val bateria by vm.bateria.collectAsStateWithLifecycle()
    val alerta by vm.alerta.collectAsStateWithLifecycle()
    val tel by vm.telemetria.collectAsStateWithLifecycle()
    val historial by vm.historial.collectAsStateWithLifecycle()

    InicioContenido(
        conexion, bateria, alerta, tel, historial,
        onAtendido = { vm.marcarAtendido() },
        onAcierto = { evento, acerto -> vm.marcarAcierto(evento, acerto) },
        onConectar = rememberConectar(vm),
        onResponder = onResponder,
        modifier = modifier,
    )
}

@Composable
fun InicioContenido(
    conexion: EstadoConexion,
    bateria: Int?,
    alerta: Evento?,
    tel: Telemetria?,
    historial: List<Evento>,
    onAtendido: () -> Unit,
    onAcierto: (evento: Evento, acerto: Boolean) -> Unit = { _, _ -> },
    onConectar: () -> Unit = {},
    onResponder: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Encabezado("Inicio")

        Heroe(conexion, bateria, alerta, onConectar)
        alerta?.let { TarjetaAlerta(it, onAtendido, onAcierto, onResponder) }

        val ayudas = historial.count { it.emocion == Emocion.AYUDA }
        val conPalabras = historial.count { it.emocion == null }
        Text("Hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Indicador("${historial.size - ayudas - conPalabras}", "Emociones", KalmiColors.Bien, Modifier.weight(1f))
            Indicador("$conPalabras", "Palabras", KalmiColors.Calma, Modifier.weight(1f))
            Indicador("$ayudas", "Ayuda", KalmiColors.MuyEnojado, Modifier.weight(1f))
        }
        if (historial.isNotEmpty()) TiraDelDia(historial)

        tel?.let {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sensores de Kalmi", style = MaterialTheme.typography.titleSmall)
                    BarraSuave("Luz", it.luz, KalmiColors.Frustrado)
                    BarraSuave("Presión en la zona de calma", it.presion, KalmiColors.Calma)
                }
            }
        }
    }
}

@Composable
private fun TarjetaAlerta(e: Evento, onAtendido: () -> Unit, onAcierto: (Evento, Boolean) -> Unit, onResponder: () -> Unit) {
    TarjetaFranja(franja = e.colorTema, fondo = e.fondoTema) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (e.emocion != null) {
                Text(
                    "Avisó ${haceCuanto(e.momento)} · ¿vas a acompañarlo?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.titulo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = e.tintaTema,
                    modifier = Modifier.weight(1f),
                )
                Text(haceCuanto(e.momento), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.interpretacion?.let { i ->
                Text("Kalmi cree esto porque: ${i.razon.replaceFirstChar { it.lowercase() }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.acerto == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("¿Acertó?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { onAcierto(e, true) }) { Text("Sí") }
                        OutlinedButton(onClick = { onAcierto(e, false) }) { Text("No") }
                    }
                } else {
                    Text(
                        if (e.acerto) "✓ Confirmaste la interpretación" else "Anotado: no acertó",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onResponder, modifier = Modifier.weight(1f).height(48.dp)) { Text("Responder") }
                OutlinedButton(onClick = onAtendido, modifier = Modifier.weight(1f).height(48.dp)) { Text("Atendido") }
            }
        }
    }
}

/** Kalmi en grande, como en las apps de los robots compañeros: su cara y el fondo cuentan el estado. */
@Composable
private fun Heroe(conexion: EstadoConexion, bateria: Int?, alerta: Evento?, onConectar: () -> Unit) {
    val conectado = conexion is EstadoConexion.Conectado
    val color = alerta?.colorTema ?: if (conectado) KalmiColors.Bien else KalmiColors.Calma
    val (titulo, subtitulo) = when {
        alerta != null && alerta.emocion == null -> "Kalmi quiere decirte algo" to "Mira lo que eligió abajo."
        alerta != null -> "Se siente ${alerta.titulo.lowercase()}" to "Kalmi ya lo está acompañando."
        conectado -> "Todo tranquilo" to "Te avisaremos si necesita algo."
        conexion is EstadoConexion.Buscando || conexion is EstadoConexion.Conectando -> "Buscando a Kalmi…" to "Mantén el teléfono cerca."
        conexion is EstadoConexion.Error -> "No encontramos a Kalmi" to conexion.mensaje
        else -> "Kalmi descansa" to "Conéctalo para recibir sus avisos."
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.06f))))
            .padding(vertical = 28.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Responsivo: en teléfonos bajos (≤ 700 dp) la mascota se achica para dejar ver el resumen.
            val tamano = if (LocalConfiguration.current.screenHeightDp <= 700) 96.dp else 132.dp
            CaraKalmi(color, tamano = tamano, dormido = !conectado && alerta == null, expresion = alerta?.emocion)
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (conectado && bateria != null) {
                Text("Batería de Kalmi: $bateria %", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!conectado && alerta == null) {
                Button(onClick = onConectar, modifier = Modifier.padding(top = 8.dp).height(48.dp)) { Text("Conectar con Kalmi") }
            }
        }
    }
}

/** El día de un vistazo: un punto por aviso, del más antiguo al más nuevo. */
@Composable
private fun TiraDelDia(historial: List<Evento>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        historial.take(16).reversed().forEach { e ->
            Box(Modifier.size(14.dp).clip(CircleShape).background(e.colorTema))
        }
        if (historial.size > 16) Text("+${historial.size - 16}", style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun InicioPreview() = KalmiTheme {
    val e = Evento(15, Emocion.FRUSTRADO, System.currentTimeMillis() - 12_000, Canal.BLE)
    InicioContenido(
        conexion = EstadoConexion.Conectado("Kalmi-SIM"),
        bateria = 82,
        alerta = e,
        tel = Telemetria(16, 70, 35, 0),
        historial = listOf(e),
        onAtendido = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun InicioPalabrasPreview() = KalmiTheme {
    val e = Evento(
        20, null, System.currentTimeMillis() - 8_000, Canal.BLE,
        palabras = listOf("Quiero", "Abrazo"),
        interpretacion = Interpretacion("Quiere abrazo", "Eligió Quiero + Abrazo; hace poco marcó Triste"),
    )
    InicioContenido(EstadoConexion.Conectado("Kalmi-SIM"), 82, e, Telemetria(21, 60, 30, 0), listOf(e), onAtendido = {})
}
