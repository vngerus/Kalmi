package cl.kalmi.app.ui.controles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.Comando
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.EstadoComando
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.ui.CaraKalmi
import cl.kalmi.app.ui.Encabezado
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.haceCuanto
import cl.kalmi.app.ui.rememberConectar
import cl.kalmi.app.ui.theme.KalmiColors
import cl.kalmi.app.ui.theme.KalmiTheme
import cl.kalmi.app.ui.theme.colorTema
import cl.kalmi.app.ui.theme.titulo
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mensajes de `Comando.Mensaje(k)`: el índice es el `k` del protocolo (MSG,0..2). */
private data class Mensaje(val texto: String, val icono: ImageVector, val color: Color)

private val MENSAJES = listOf(
    Mensaje("Ya voy", Icons.Filled.ThumbUp, KalmiColors.Bien),
    Mensaje("Te quiero", Icons.Filled.Favorite, KalmiColors.MuyEnojado),
    Mensaje("Estoy aquí", Icons.Filled.Person, KalmiColors.Triste),
)

private const val BRILLO_INICIAL = 60
private const val CAMBIO_MINIMO_BRILLO = 5 // guía §15.3: BRI solo si cambia ≥ 5

/** Guía §15.3: responder a Kalmi en contexto. Mensajes, respiración y brillo. */
@Composable
fun ControlesScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val conexion by vm.conexion.collectAsStateWithLifecycle()
    val estado by vm.estadoComando.collectAsStateWithLifecycle()
    val calidad by vm.calidad.collectAsStateWithLifecycle()
    val alerta by vm.alerta.collectAsStateWithLifecycle()

    ControlesContenido(
        conectado = conexion is EstadoConexion.Conectado,
        alerta = alerta,
        estado = estado,
        rttMs = calidad.ultimoRttMs,
        onEnviar = { vm.enviar(it) },
        onAtendido = { vm.marcarAtendido() },
        onConectar = rememberConectar(vm),
        modifier = modifier,
    )
}

@Composable
fun ControlesContenido(
    conectado: Boolean,
    alerta: Evento?,
    estado: Pair<Comando, EstadoComando>?,
    rttMs: Long?,
    onEnviar: (Comando) -> Unit,
    onAtendido: () -> Unit,
    onConectar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // El brillo se envía al soltar el deslizador y solo si cambió lo suficiente (máx. 1 envío por gesto).
    var brillo by rememberSaveable { mutableFloatStateOf(BRILLO_INICIAL.toFloat()) }
    var brilloEnviado by rememberSaveable { mutableIntStateOf(BRILLO_INICIAL) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Encabezado("Acompañar")

        Contexto(conectado, alerta, onAtendido, onConectar)

        Text("Envíale un mensaje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MENSAJES.forEachIndexed { k, m ->
                TarjetaMensaje(m, habilitado = conectado, modifier = Modifier.weight(1f)) { onEnviar(Comando.Mensaje(k)) }
            }
        }

        RespirarJuntos(conectado) { onEnviar(Comando.Respirar) }

        AnimatedVisibility(visible = estado != null) {
            estado?.let { EstadoEnvio(it, rttMs) }
        }

        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text("Brillo de la pantalla de Kalmi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${brillo.roundToInt()} %", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
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
            }
        }
    }
}

/** Arriba, lo que está pasando: así el cuidador responde en contexto. */
@Composable
private fun Contexto(conectado: Boolean, alerta: Evento?, onAtendido: () -> Unit, onConectar: () -> Unit) {
    val color = alerta?.colorTema ?: if (conectado) KalmiColors.Bien else KalmiColors.Calma
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.06f))))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CaraKalmi(color, tamano = 64.dp, dormido = !conectado && alerta == null, expresion = alerta?.emocion)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        alerta?.emocion != null -> "Se siente ${alerta.titulo.lowercase()}"
                        alerta != null -> alerta.titulo
                        conectado -> "Kalmi está tranquilo"
                        else -> "Kalmi no está conectado"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        alerta != null -> "Avisó ${haceCuanto(alerta.momento)}"
                        conectado -> "Envíale cariño cuando quieras."
                        else -> "Los mensajes llegan cuando estás cerca."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    alerta != null -> Button(onClick = onAtendido, modifier = Modifier.padding(top = 4.dp).height(40.dp)) { Text("Marcar como atendido") }
                    !conectado -> Button(onClick = onConectar, modifier = Modifier.padding(top = 4.dp).height(40.dp)) { Text("Conectar con Kalmi") }
                }
            }
        }
    }
}

@Composable
private fun TarjetaMensaje(m: Mensaje, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val alfa = if (habilitado) 1f else 0.45f
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = m.color.copy(alpha = 0.16f * alfa),
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick),
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(m.color.copy(alpha = alfa)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(m.icono, contentDescription = null, tint = Color.White)
            }
            Text(
                m.texto,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alfa),
            )
        }
    }
}

@Composable
private fun RespirarJuntos(habilitado: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (habilitado) 1f else 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CaraKalmi(KalmiColors.Calma, tamano = 52.dp)
            Column(Modifier.weight(1f)) {
                Text("Respirar juntos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Kalmi guía 1 minuto de respiración: 4 s inhalar, 4 s exhalar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** "Ya voy: ✓ Kalmi lo recibió (38 ms)". Lenguaje simple, sin tecnicismos. */
@Composable
private fun EstadoEnvio(estado: Pair<Comando, EstadoComando>, rttMs: Long?) {
    val (comando, e) = estado
    val (texto, color) = when (e) {
        EstadoComando.ENVIANDO -> "Enviando…" to MaterialTheme.colorScheme.onSurfaceVariant
        EstadoComando.CONFIRMADO -> ("✓ Kalmi lo recibió" + (rttMs?.let { " ($it ms)" } ?: "")) to KalmiColors.BienTinta
        EstadoComando.SIN_CONFIRMACION -> "No llegó. Acércate a Kalmi e inténtalo otra vez." to KalmiColors.MuyEnojadoTinta
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            "${comando.etiqueta()}: $texto",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

private fun Comando.etiqueta(): String = when (this) {
    is Comando.Mensaje -> MENSAJES.getOrNull(k)?.texto ?: "Mensaje"
    Comando.Respirar -> "Respirar juntos"
    is Comando.Brillo -> "Brillo $valor %"
    Comando.Atendido -> "Atendido"
    is Comando.Preguntar -> "Pregunta"
}

@Preview(showBackground = true)
@Composable
private fun ControlesPreview() = KalmiTheme {
    ControlesContenido(
        conectado = true,
        alerta = Evento(4, Emocion.TRISTE, System.currentTimeMillis() - 60_000, Canal.BLE),
        estado = Comando.Mensaje(0) to EstadoComando.CONFIRMADO,
        rttMs = 38,
        onEnviar = {},
        onAtendido = {},
        onConectar = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ControlesSinConexionPreview() = KalmiTheme {
    ControlesContenido(conectado = false, alerta = null, estado = null, rttMs = null, onEnviar = {}, onAtendido = {}, onConectar = {})
}
