package cl.kalmi.app.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.CalidadEnlace
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.ui.CaraKalmi
import cl.kalmi.app.ui.Encabezado
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.rememberConectar
import cl.kalmi.app.ui.theme.KalmiColors
import cl.kalmi.app.ui.theme.KalmiTheme
import kotlin.math.roundToInt

/** Emociones que el niño puede marcar con los botones físicos (E1..E5). */
private val EMOCIONES_BOTON = Emocion.entries.filter { it.codigo in 1..5 }

/** Ajustes: tu Kalmi (conexión) y el Modo de prueba (simulador + diagnóstico, guía §15.5). */
@Composable
fun AjustesScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val conexion by vm.conexion.collectAsStateWithLifecycle()
    val bateria by vm.bateria.collectAsStateWithLifecycle()
    val calidad by vm.calidad.collectAsStateWithLifecycle()
    val modoPrueba by vm.modoPrueba.collectAsStateWithLifecycle()
    // probabilidadPerdida es un var simple del simulador: la UI guarda su copia para redibujar.
    var perdida by remember { mutableFloatStateOf(vm.simulador.probabilidadPerdida.toFloat()) }

    AjustesContenido(
        conexion = conexion,
        bateria = bateria,
        calidad = calidad,
        modoPrueba = modoPrueba,
        onConectar = rememberConectar(vm),
        onDesconectar = { vm.desconectar() },
        onModoPrueba = { vm.cambiarModoPrueba(it) },
        onSimular = { vm.simulador.simularBoton(it.codigo) },
        onSimularPalabras = { vm.simulador.simularPalabras() },
        perdida = perdida,
        onPerdida = {
            perdida = it
            vm.simulador.probabilidadPerdida = it.toDouble()
        },
        modifier = modifier,
    )
}

@Composable
fun AjustesContenido(
    conexion: EstadoConexion,
    bateria: Int?,
    calidad: CalidadEnlace,
    modoPrueba: Boolean,
    onConectar: () -> Unit,
    onDesconectar: () -> Unit,
    onModoPrueba: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSimular: (Emocion) -> Unit = {},
    onSimularPalabras: () -> Unit = {},
    perdida: Float = 0f,
    onPerdida: (Float) -> Unit = {},
) {
    val conectado = conexion is EstadoConexion.Conectado

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado("Ajustes")

        TarjetaTuKalmi(conexion, bateria, modoPrueba, onConectar, onDesconectar)

        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Modo de prueba", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Usa un Kalmi simulado y muestra el diagnóstico del enlace. Apágalo para usar el Kalmi real.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = modoPrueba, onCheckedChange = onModoPrueba)
            }
        }

        if (modoPrueba) {
            Diagnostico(calidad)
            PanelSimulador(conectado, onSimular, onSimularPalabras, perdida, onPerdida)
        }

        Text(
            "Kalmi es una herramienta de apoyo y no reemplaza la orientación de profesionales.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TarjetaTuKalmi(
    conexion: EstadoConexion,
    bateria: Int?,
    modoPrueba: Boolean,
    onConectar: () -> Unit,
    onDesconectar: () -> Unit,
) {
    val conectado = conexion is EstadoConexion.Conectado
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CaraKalmi(if (conectado) KalmiColors.Bien else KalmiColors.Calma, tamano = 60.dp, dormido = !conectado)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        when (conexion) {
                            is EstadoConexion.Conectado -> conexion.nombre
                            EstadoConexion.Buscando -> "Buscando a Kalmi…"
                            EstadoConexion.Conectando -> "Conectando…"
                            is EstadoConexion.Error -> conexion.mensaje
                            EstadoConexion.Desconectado -> "Tu Kalmi"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            conectado -> "Conectado" + (bateria?.let { " · batería $it%" } ?: "")
                            modoPrueba -> "Simulador listo para conectar"
                            else -> "Acércate a Kalmi y toca Conectar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (conectado) {
                OutlinedButton(onClick = onDesconectar, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Desconectar") }
            } else {
                Button(onClick = onConectar, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Conectar") }
            }
        }
    }
}

@Composable
private fun Diagnostico(c: CalidadEnlace) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Diagnóstico del enlace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Dato("Recibidos", c.recibidos.toString())
            Dato("Perdidos", "${c.perdidos} (${"%.1f".format(c.porcentajePerdida)} %)")
            Dato("Descartados por checksum", c.descartados.toString())
            Dato("Último RTT", c.ultimoRttMs?.let { "$it ms" } ?: "—")
        }
    }
}

@Composable
private fun Dato(nombre: String, valor: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(nombre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PanelSimulador(
    conectado: Boolean,
    onSimular: (Emocion) -> Unit,
    onSimularPalabras: () -> Unit,
    perdida: Float,
    onPerdida: (Float) -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Simular botones de Kalmi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!conectado) {
                Text("Conecta el simulador para probar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EMOCIONES_BOTON.forEach { e ->
                    FilledTonalButton(onClick = { onSimular(e) }, enabled = conectado) { Text(e.nombre) }
                }
                FilledTonalButton(onClick = onSimularPalabras, enabled = conectado) { Text("Palabras") }
            }
            Text("Pérdida simulada: ${(perdida * 100).roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
            Slider(value = perdida, onValueChange = onPerdida, valueRange = 0f..0.9f)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AjustesPreview() = KalmiTheme {
    AjustesContenido(
        conexion = EstadoConexion.Conectado("Kalmi-SIM"),
        bateria = 82,
        calidad = CalidadEnlace(recibidos = 140, perdidos = 6, descartados = 1, ultimoRttMs = 38),
        modoPrueba = true,
        onConectar = {},
        onDesconectar = {},
        onModoPrueba = {},
        perdida = 0.3f,
    )
}

@Preview(showBackground = true)
@Composable
private fun AjustesRealPreview() = KalmiTheme {
    AjustesContenido(EstadoConexion.Desconectado, null, CalidadEnlace(), modoPrueba = false, onConectar = {}, onDesconectar = {}, onModoPrueba = {})
}
