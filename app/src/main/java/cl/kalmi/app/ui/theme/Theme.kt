package cl.kalmi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.Evento

object KalmiColors {
    val Bien = Color(0xFF7FAF8E)
    val Triste = Color(0xFF6E8FB8)
    val Frustrado = Color(0xFFD4B26A)
    val MuyEnojado = Color(0xFFC9786B)
    val Calma = Color(0xFF9A8FC7)
}

val Emocion.color: Color
    get() = when (this) {
        Emocion.BIEN -> KalmiColors.Bien
        Emocion.TRISTE -> KalmiColors.Triste
        Emocion.FRUSTRADO, Emocion.TENSION -> KalmiColors.Frustrado
        Emocion.MUY_ENOJADO -> KalmiColors.MuyEnojado
        Emocion.AYUDA -> KalmiColors.Calma
    }

/** Fondos de alerta: color de la emoción al 15–20 %, nunca saturado. */
val Emocion.fondo: Color get() = color.copy(alpha = 0.18f)

/** Color de un evento: el de su emoción, o Calma si es un evento de palabras. */
val Evento.colorTema: Color get() = emocion?.color ?: KalmiColors.Calma
val Evento.fondoTema: Color get() = colorTema.copy(alpha = 0.18f)

/** Título corto de un evento: la emoción, o lo que Kalmi cree que dijo. */
val Evento.titulo: String
    get() = emocion?.nombre ?: interpretacion?.texto ?: palabras.joinToString(" + ")

@Composable
fun KalmiTheme(content: @Composable () -> Unit) {
    val esquema = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = KalmiColors.Calma, secondary = KalmiColors.Bien)
    } else {
        lightColorScheme(primary = KalmiColors.Calma, secondary = KalmiColors.Bien)
    }
    MaterialTheme(colorScheme = esquema, content = content)
}
