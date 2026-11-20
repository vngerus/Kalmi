package cl.kalmi.app.ui

import cl.kalmi.app.domain.Emocion
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.ui.theme.KalmiColors

/** Título de pantalla + chip de estado de Kalmi (igual en todas las pantallas). */
@Composable
fun Encabezado(titulo: String, conexion: EstadoConexion? = null, bateria: Int? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            titulo,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        conexion?.let { ChipEstado(it, bateria) }
    }
}

@Composable
fun ChipEstado(conexion: EstadoConexion, bateria: Int?) {
    val (texto, punto) = when (conexion) {
        is EstadoConexion.Conectado -> "Conectado" to KalmiColors.Bien
        EstadoConexion.Buscando, EstadoConexion.Conectando -> "Buscando…" to KalmiColors.Frustrado
        is EstadoConexion.Error -> "Sin Kalmi" to KalmiColors.MuyEnojado
        EstadoConexion.Desconectado -> "Sin conexión" to MaterialTheme.colorScheme.outline
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(punto))
            Text(texto + (bateria?.let { " · $it%" } ?: ""), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Tarjeta con franja de color a la izquierda: el color de la emoción como acento, no como texto. */
@Composable
fun TarjetaFranja(
    franja: Color,
    fondo: Color,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = fondo, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(franja))
            Box(Modifier.padding(16.dp)) { contenido() }
        }
    }
}

/** Recuadro de resumen al estilo "Salud": número grande + etiqueta. */
@Composable
fun Indicador(valor: String, etiqueta: String, punto: Color, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = punto.copy(alpha = 0.16f), modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(punto))
            Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Kalmi, la mascota: respira (ciclo 4 + 4 s, como la respiración guiada) y parpadea.
 * La cara cambia según la emoción, como en el dispositivo (informe §2.2). Animaciones lentas: nada que sobresalte.
 */
@Composable
fun CaraKalmi(color: Color, tamano: Dp = 72.dp, dormido: Boolean = false, expresion: Emocion? = null) {
    val trazo = Color(0xFF2B2833)
    val t = rememberInfiniteTransition(label = "kalmi")
    val respira by t.animateFloat(
        initialValue = 0.94f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "respira",
    )
    val parpadeo by t.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5_200
            1f at 0; 1f at 4_900; 0.1f at 5_000; 1f at 5_200
        }),
        label = "parpadeo",
    )
    Canvas(Modifier.size(tamano).scale(respira)) {
        val w = size.width
        drawCircle(color.copy(alpha = 0.18f))
        drawCircle(color, radius = w * 0.40f)
        // brillo suave arriba a la izquierda: da volumen sin ser llamativo
        drawCircle(Color.White.copy(alpha = 0.25f), radius = w * 0.12f, center = Offset(w * 0.36f, w * 0.28f))
        val ojoY = w * 0.46f
        val g = w * 0.045f
        if (dormido) {
            drawLine(trazo, Offset(w * 0.32f, ojoY), Offset(w * 0.43f, ojoY), g, StrokeCap.Round)
            drawLine(trazo, Offset(w * 0.57f, ojoY), Offset(w * 0.68f, ojoY), g, StrokeCap.Round)
        } else {
            listOf(0.38f, 0.62f).forEach { x ->
                val r = w * 0.045f
                drawOval(trazo, topLeft = Offset(w * x - r, ojoY - r * parpadeo), size = Size(r * 2, r * 2 * parpadeo))
            }
        }
        fun ceja(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(trazo, Offset(w * x1, w * y1), Offset(w * x2, w * y2), g * 0.8f, StrokeCap.Round)
        val boca = Stroke(width = g, cap = StrokeCap.Round)
        when (expresion) {
            Emocion.TRISTE -> {
                ceja(0.31f, 0.36f, 0.43f, 0.33f); ceja(0.57f, 0.33f, 0.69f, 0.36f)
                drawArc(trazo, 200f, 140f, false, Offset(w * 0.38f, w * 0.60f), Size(w * 0.24f, w * 0.14f), style = boca)
            }
            Emocion.FRUSTRADO, Emocion.TENSION -> {
                ceja(0.32f, 0.35f, 0.43f, 0.365f); ceja(0.57f, 0.365f, 0.68f, 0.35f)
                drawLine(trazo, Offset(w * 0.40f, w * 0.63f), Offset(w * 0.60f, w * 0.63f), g, StrokeCap.Round)
            }
            Emocion.MUY_ENOJADO -> {
                ceja(0.30f, 0.32f, 0.44f, 0.39f); ceja(0.56f, 0.39f, 0.70f, 0.32f)
                drawOval(trazo, topLeft = Offset(w * 0.43f, w * 0.58f), size = Size(w * 0.14f, w * 0.10f))
            }
            Emocion.AYUDA -> drawCircle(trazo, radius = w * 0.045f, center = Offset(w * 0.5f, w * 0.64f), style = boca)
            else -> drawArc(trazo, 20f, 140f, false, Offset(w * 0.36f, w * 0.48f), Size(w * 0.28f, w * 0.2f), style = boca)
        }
    }
}

/** Barra fina y redondeada para sensores (0–100). */
@Composable
fun BarraSuave(nombre: String, valor: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(nombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$valor%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth(valor / 100f).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
    }
}
