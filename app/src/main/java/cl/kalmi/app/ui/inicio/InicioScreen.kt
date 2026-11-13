package cl.kalmi.app.ui.inicio

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Telemetria
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.theme.KalmiTheme
import cl.kalmi.app.ui.theme.color
import cl.kalmi.app.ui.theme.fondo

/**
 * Patrón de referencia para las demás pantallas:
 * - `XScreen(vm)` lee los StateFlow con collectAsStateWithLifecycle.
 * - `XContenido(...)` recibe datos simples + lambdas: sin ViewModel, así tiene @Preview.
 */
@Composable
fun InicioScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val conexion by vm.conexion.collectAsStateWithLifecycle()
    val bateria by vm.bateria.collectAsStateWithLifecycle()
    val alerta by vm.alerta.collectAsStateWithLifecycle()
    val tel by vm.telemetria.collectAsStateWithLifecycle()
    val historial by vm.historial.collectAsStateWithLifecycle()

    InicioContenido(conexion, bateria, alerta, tel, historial, onAtendido = { vm.marcarAtendido() }, modifier)
}

@Composable
fun InicioContenido(
    conexion: EstadoConexion,
    bateria: Int?,
    alerta: Evento?,
    tel: Telemetria?,
    historial: List<Evento>,
    onAtendido: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = (if (conexion is EstadoConexion.Conectado) "● Conectado (BLE)" else "○ Sin conexión") +
                (bateria?.let { "    🔋 $it%" } ?: ""),
            style = MaterialTheme.typography.labelLarge,
        )

        if (alerta != null) TarjetaAlerta(alerta, onAtendido) else TarjetaTranquilo()

        tel?.let {
            Barra("Luz", it.luz)
            Barra("Presión", it.presion)
        }

        val ayudas = historial.count { it.emocion == Emocion.AYUDA }
        Text("Hoy: ${historial.size - ayudas} emociones · $ayudas ayuda", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TarjetaAlerta(e: Evento, onAtendido: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = e.emocion.fondo),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.emocion.nombre, style = MaterialTheme.typography.headlineSmall, color = e.emocion.color)
            Text(DateUtils.getRelativeTimeSpanString(e.momento).toString())
            Button(onClick = onAtendido) { Text("Marcar como atendido") }
        }
    }
}

@Composable
private fun TarjetaTranquilo() {
    Card(Modifier.fillMaxWidth()) {
        Text("Todo tranquilo", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Barra(nombre: String, valor: Int) {
    Column {
        Text("$nombre $valor%", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(progress = { valor / 100f }, modifier = Modifier.fillMaxWidth())
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
