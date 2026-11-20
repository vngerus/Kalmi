package cl.kalmi.app.ui.controles

import cl.kalmi.app.ui.Encabezado
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.Comando
import cl.kalmi.app.domain.EstadoComando
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.theme.KalmiTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Textos de `Comando.Mensaje(k)`: el índice es el `k` del protocolo (MSG,0..2). */
private val MENSAJES = listOf("Ya voy", "Te quiero", "Estoy aquí")

private const val BRILLO_INICIAL = 60
private const val CAMBIO_MINIMO_BRILLO = 5

/** Guía §15.3. Mensajes, respiración y brillo. Todo se deshabilita sin conexión BLE. */
@Composable
fun ControlesScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val conexion by vm.conexion.collectAsStateWithLifecycle()
    val estado by vm.estadoComando.collectAsStateWithLifecycle()
    val calidad by vm.calidad.collectAsStateWithLifecycle()

    ControlesContenido(
        conectado = conexion is EstadoConexion.Conectado,
        estado = estado,
        rttMs = calidad.ultimoRttMs,
        onEnviar = { vm.enviar(it) },
        modifier = modifier,
    )
}

@Composable
fun ControlesContenido(
    conectado: Boolean,
    estado: Pair<Comando, EstadoComando>?,
    rttMs: Long?,
    onEnviar: (Comando) -> Unit,
    modifier: Modifier = Modifier,
) {
    // El brillo se envía al soltar el deslizador (cubre el máximo de 1 envío cada 300 ms)
    // y solo si cambió lo suficiente respecto del último valor enviado.
    var brillo by rememberSaveable { mutableFloatStateOf(BRILLO_INICIAL.toFloat()) }
    var brilloEnviado by rememberSaveable { mutableIntStateOf(BRILLO_INICIAL) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Encabezado("Controles")

        if (!conectado) {
            Text(
                "Disponible cuando estés cerca de Kalmi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MENSAJES.forEachIndexed { k, texto ->
            Button(
                onClick = { onEnviar(Comando.Mensaje(k)) },
                enabled = conectado,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(texto) }
        }

        OutlinedButton(
            onClick = { onEnviar(Comando.Respirar) },
            enabled = conectado,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Respirar juntos") }

        Text("Brillo ${brillo.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = brillo,
            onValueChange = { brillo = it },
            onValueChangeFinished = {
                val v = brillo.roundToInt()
                if (abs(v - brilloEnviado) >= CAMBIO_MINIMO_BRILLO) {
                    brilloEnviado = v
                    onEnviar(Comando.Brillo(v))
                }
            },
            valueRange = 10f..100f,
            enabled = conectado,
        )

        textoEstado(estado, rttMs)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Ya voy: ✓ Confirmado (38 ms)". Null si todavía no se envió ningún comando. */
private fun textoEstado(estado: Pair<Comando, EstadoComando>?, rttMs: Long?): String? {
    val (comando, e) = estado ?: return null
    val detalle = when (e) {
        EstadoComando.ENVIANDO -> "Enviando…"
        EstadoComando.CONFIRMADO -> "✓ Confirmado" + (rttMs?.let { " ($it ms)" } ?: "")
        EstadoComando.SIN_CONFIRMACION -> "Sin confirmación"
    }
    return "${comando.etiqueta()}: $detalle"
}

private fun Comando.etiqueta(): String = when (this) {
    is Comando.Mensaje -> MENSAJES.getOrElse(k) { "Mensaje" }
    Comando.Respirar -> "Respirar juntos"
    is Comando.Brillo -> "Brillo $valor%"
    Comando.Atendido -> "Atendido"
    is Comando.Preguntar -> "Pregunta"
}

@Preview(showBackground = true)
@Composable
private fun ControlesPreview() = KalmiTheme {
    ControlesContenido(
        conectado = true,
        estado = Comando.Mensaje(0) to EstadoComando.CONFIRMADO,
        rttMs = 38,
        onEnviar = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ControlesSinConexionPreview() = KalmiTheme {
    ControlesContenido(conectado = false, estado = null, rttMs = null, onEnviar = {})
}
