package cl.kalmi.app.ui.conexion

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.BuildConfig
import cl.kalmi.app.domain.CalidadEnlace
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.theme.KalmiTheme
import kotlin.math.roundToInt

private val PERMISOS_BLE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

/** Emociones que el niño puede marcar con los botones físicos (E1..E5). */
private val EMOCIONES_BOTON = Emocion.entries.filter { it.codigo in 1..5 }

/** Guía §15.5. Estado del enlace, diagnóstico y, solo en debug con simulador, panel de pruebas. */
@Composable
fun ConexionScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val conexion by vm.conexion.collectAsStateWithLifecycle()
    val bateria by vm.bateria.collectAsStateWithLifecycle()
    val calidad by vm.calidad.collectAsStateWithLifecycle()

    val simulador = vm.simulador.takeIf { BuildConfig.DEBUG }
    var usarReal by remember { mutableStateOf(vm.usarReal) }

    // El Kalmi real necesita permisos de Bluetooth; se piden recién al tocar "Conectar".
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) vm.conectar()
    }
    // probabilidadPerdida es un var simple del simulador: la UI guarda su copia para redibujar.
    var perdida by remember { mutableFloatStateOf(simulador?.probabilidadPerdida?.toFloat() ?: 0f) }

    ConexionContenido(
        conexion = conexion,
        bateria = bateria,
        calidad = calidad,
        onConectar = { if (vm.usandoReal) pedirPermisos.launch(PERMISOS_BLE) else vm.conectar() },
        onDesconectar = { vm.desconectar() },
        modifier = modifier,
        explicarPermisos = vm.usandoReal,
        modoReal = if (BuildConfig.DEBUG) usarReal else null,
        onModoReal = { usarReal = it; vm.usarReal = it },
        onSimular = simulador?.let { sim -> { e: Emocion -> sim.simularBoton(e.codigo) } },
        onSimularPalabras = { simulador?.simularPalabras() },
        perdida = perdida,
        onPerdida = {
            perdida = it
            simulador?.probabilidadPerdida = it.toDouble()
        },
    )
}

/** [onSimular] null = sin panel de depuración (release o dispositivo real). */
@Composable
fun ConexionContenido(
    conexion: EstadoConexion,
    bateria: Int?,
    calidad: CalidadEnlace,
    onConectar: () -> Unit,
    onDesconectar: () -> Unit,
    modifier: Modifier = Modifier,
    explicarPermisos: Boolean = false,
    modoReal: Boolean? = null, // null = no mostrar el interruptor (release)
    onModoReal: (Boolean) -> Unit = {},
    onSimular: ((Emocion) -> Unit)? = null,
    onSimularPalabras: () -> Unit = {},
    perdida: Float = 0f,
    onPerdida: (Float) -> Unit = {},
) {
    val conectado = conexion is EstadoConexion.Conectado

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Conexión", style = MaterialTheme.typography.headlineSmall)
        Text(
            when (conexion) {
                EstadoConexion.Desconectado -> "Desconectado"
                EstadoConexion.Buscando -> "Buscando Kalmi…"
                EstadoConexion.Conectando -> "Conectando…"
                is EstadoConexion.Conectado -> "Conectado a ${conexion.nombre}"
                is EstadoConexion.Error -> "Error: ${conexion.mensaje}"
            },
        )
        bateria?.let { Text("Batería $it%", style = MaterialTheme.typography.bodyMedium) }

        if (conectado) {
            OutlinedButton(onClick = onDesconectar) { Text("Desconectar") }
        } else {
            Button(onClick = onConectar) { Text("Conectar") }
        }
        if (explicarPermisos && !conectado) {
            Text(
                "Kalmi usa Bluetooth para avisarte al instante. Te pediremos permiso para buscarlo y conectarte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        modoReal?.let { real ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = real, onCheckedChange = onModoReal)
                Column {
                    Text("Usar Kalmi real (HM-10)", style = MaterialTheme.typography.bodyMedium)
                    Text("Se aplica al reiniciar la app", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Diagnostico(calidad)

        if (onSimular != null) PanelDepuracion(conectado, onSimular, onSimularPalabras, perdida, onPerdida)
    }
}

@Composable
private fun Diagnostico(c: CalidadEnlace) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Diagnóstico", style = MaterialTheme.typography.titleMedium)
            Dato("Recibidos", c.recibidos.toString())
            Dato("Perdidos", c.perdidos.toString())
            Dato("Pérdida", "%.1f %%".format(c.porcentajePerdida))
            Dato("Descartados por checksum", c.descartados.toString())
            Dato("Último RTT", c.ultimoRttMs?.let { "$it ms" } ?: "—")
        }
    }
}

@Composable
private fun Dato(nombre: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(nombre, style = MaterialTheme.typography.bodyMedium)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PanelDepuracion(
    conectado: Boolean,
    onSimular: (Emocion) -> Unit,
    onSimularPalabras: () -> Unit,
    perdida: Float,
    onPerdida: (Float) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Simulador (solo debug)", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EMOCIONES_BOTON.forEach { e ->
                    OutlinedButton(onClick = { onSimular(e) }, enabled = conectado) { Text(e.nombre) }
                }
            }
            OutlinedButton(onClick = onSimularPalabras, enabled = conectado) { Text("Simular palabras") }
            Text("Pérdida simulada ${(perdida * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(value = perdida, onValueChange = onPerdida, valueRange = 0f..0.9f)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConexionPreview() = KalmiTheme {
    ConexionContenido(
        conexion = EstadoConexion.Conectado("Kalmi-SIM"),
        bateria = 82,
        calidad = CalidadEnlace(recibidos = 140, perdidos = 6, descartados = 1, ultimoRttMs = 38),
        onConectar = {},
        onDesconectar = {},
        onSimular = {},
        perdida = 0.3f,
    )
}

@Preview(showBackground = true)
@Composable
private fun ConexionDesconectadoPreview() = KalmiTheme {
    ConexionContenido(
        conexion = EstadoConexion.Desconectado,
        bateria = null,
        calidad = CalidadEnlace(),
        onConectar = {},
        onDesconectar = {},
    )
}
