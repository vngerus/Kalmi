package cl.kalmi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.Evento

/**
 * Paleta "Kalmi Blanco": blanco predominante, emociones como acento, lavanda de marca.
 * Contrastes verificados (WCAG AA) — ver docs/superpowers/specs/2026-11-19-kalmi-uno-palabras-design.md.
 * Regla: los colores BASE nunca van como texto sobre blanco; para texto se usa la TINTA.
 */
object KalmiColors {
    // Base: puntos, franjas, LCD del dispositivo (Zonas de Regulación, apagados).
    val Bien = Color(0xFF7FAF8E)
    val Triste = Color(0xFF6E8FB8)
    val Frustrado = Color(0xFFD4B26A)
    val MuyEnojado = Color(0xFFC9786B)
    val Calma = Color(0xFF9A8FC7)

    // Tinta: texto e íconos sobre blanco (≥ 5,9:1).
    val BienTinta = Color(0xFF3F6E4D)
    val TristeTinta = Color(0xFF3E5F8A)
    val FrustradoTinta = Color(0xFF7A5F1E)
    val MuyEnojadoTinta = Color(0xFF9A4A3F)
    val CalmaTinta = Color(0xFF5E5596) // también es el primario de la app

    // Neutros
    val Texto = Color(0xFF1F1D26)
    val TextoSecundario = Color(0xFF5C5868)
    val TextoDeshabilitado = Color(0xFF8E8A99)
    val Superficie = Color(0xFFF7F6FA)
    val Superficie2 = Color(0xFFEFEDF4)
    val Borde = Color(0xFFE2DFEA)
}

private fun Emocion.base(): Color = when (this) {
    Emocion.BIEN -> KalmiColors.Bien
    Emocion.TRISTE -> KalmiColors.Triste
    Emocion.FRUSTRADO, Emocion.TENSION -> KalmiColors.Frustrado
    Emocion.MUY_ENOJADO -> KalmiColors.MuyEnojado
    Emocion.AYUDA -> KalmiColors.Calma
}

private fun Emocion.tintaClara(): Color = when (this) {
    Emocion.BIEN -> KalmiColors.BienTinta
    Emocion.TRISTE -> KalmiColors.TristeTinta
    Emocion.FRUSTRADO, Emocion.TENSION -> KalmiColors.FrustradoTinta
    Emocion.MUY_ENOJADO -> KalmiColors.MuyEnojadoTinta
    Emocion.AYUDA -> KalmiColors.CalmaTinta
}

/** Color base de la emoción: franjas, puntos, indicadores. */
val Emocion.color: Color get() = base()

/** Fondo de alerta: color de la emoción al 14 %, nunca saturado. */
val Emocion.fondo: Color get() = base().copy(alpha = 0.14f)

/** Color base de un evento: el de su emoción, o Calma si es un evento de palabras. */
val Evento.colorTema: Color get() = emocion?.color ?: KalmiColors.Calma
val Evento.fondoTema: Color get() = colorTema.copy(alpha = 0.14f)

/** Color para TEXTO de un evento: tinta en claro (contraste AA); base en oscuro (también AA). */
val Evento.tintaTema: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) colorTema else (emocion?.tintaClara() ?: KalmiColors.CalmaTinta)

/** Título corto de un evento: la emoción, o lo que Kalmi cree que dijo. */
val Evento.titulo: String
    get() = emocion?.nombre ?: interpretacion?.texto ?: palabras.joinToString(" + ")

private val Claro = lightColorScheme(
    primary = KalmiColors.CalmaTinta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE9F7),
    onPrimaryContainer = Color(0xFF2E2750),
    secondary = KalmiColors.BienTinta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF4EF),
    onSecondaryContainer = Color(0xFF1F3A28),
    tertiary = KalmiColors.TristeTinta,
    onTertiary = Color.White,
    error = KalmiColors.MuyEnojadoTinta,
    onError = Color.White,
    errorContainer = Color(0xFFF7ECEA),
    onErrorContainer = Color(0xFF4A1F19),
    background = Color.White,
    onBackground = KalmiColors.Texto,
    surface = Color.White,
    onSurface = KalmiColors.Texto,
    surfaceVariant = KalmiColors.Superficie2,
    onSurfaceVariant = KalmiColors.TextoSecundario,
    outline = KalmiColors.TextoDeshabilitado,
    outlineVariant = KalmiColors.Borde,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAFD),
    surfaceContainer = KalmiColors.Superficie,
    surfaceContainerHigh = KalmiColors.Superficie,
    surfaceContainerHighest = KalmiColors.Superficie2,
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFC9C1EE),
    onPrimary = Color(0xFF2E2750),
    primaryContainer = Color(0xFF3F3870),
    onPrimaryContainer = Color(0xFFECE9F7),
    secondary = KalmiColors.Bien,
    onSecondary = Color(0xFF14261A),
    secondaryContainer = Color(0xFF2B4433),
    onSecondaryContainer = Color(0xFFDCEBE0),
    tertiary = KalmiColors.Triste,
    error = KalmiColors.MuyEnojado,
    onError = Color(0xFF2E120E),
    background = Color(0xFF15141A),
    onBackground = Color(0xFFECEAF2),
    surface = Color(0xFF15141A),
    onSurface = Color(0xFFECEAF2),
    surfaceVariant = Color(0xFF27252F),
    onSurfaceVariant = Color(0xFFB5B1C0),
    outline = Color(0xFF7D798A),
    outlineVariant = Color(0xFF34313D),
    surfaceContainerLowest = Color(0xFF110F15),
    surfaceContainerLow = Color(0xFF1A1820),
    surfaceContainer = Color(0xFF1E1C24),
    surfaceContainerHigh = Color(0xFF1E1C24),
    surfaceContainerHighest = Color(0xFF27252F),
)

private val Formas = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun KalmiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, shapes = Formas, content = content)
}
