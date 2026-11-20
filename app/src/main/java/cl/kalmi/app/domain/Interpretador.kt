package cl.kalmi.app.domain

/** Lo que se sabe alrededor de las palabras. Lo calcula el repositorio. */
data class Contexto(
    val emocionReciente: Emocion? = null, // última emoción en los últimos 5 min
    val presionAlta: Boolean = false,
)

/**
 * Traduce las palabras del niño a una frase para el cuidador.
 * Hoy: [ReglasInterpretador]. Mejora futura: una IA con la misma interfaz, elegida en `KalmiApp`.
 * Regla: sugiere y explica, nunca decide.
 */
fun interface Interpretador {
    fun interpretar(palabras: List<String>, contexto: Contexto): Interpretacion
}

/** Plantillas simples y explicables. Las mismas usa el sketch Teléfono de Tinkercad. */
class ReglasInterpretador : Interpretador {

    override fun interpretar(palabras: List<String>, contexto: Contexto): Interpretacion {
        val segunda = palabras.getOrNull(1)?.lowercase()
        val texto = when {
            "Duele" in palabras -> "Dice que le duele algo"
            palabras.first() == "Quiero" && segunda != null -> "Quiere $segunda"
            palabras.first() == "No" && segunda != null -> "No quiere $segunda"
            palabras.first() == "Más" && segunda != null -> "Quiere más $segunda"
            palabras.first() == "Parar" -> "Quiere parar"
            else -> "Dice: " + palabras.joinToString(" + ") { it.lowercase() }
        }
        val razon = buildList {
            add("Eligió " + palabras.joinToString(" + "))
            contexto.emocionReciente?.let { add("hace poco marcó ${it.nombre}") }
            if (contexto.presionAlta) add("presión alta en la zona de calma")
        }.joinToString("; ")
        return Interpretacion(texto, razon)
    }
}
