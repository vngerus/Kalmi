package cl.kalmi.app.domain

/** Canales de notificación (guía §13.3): sin sonidos estridentes ni lenguaje alarmista. */
enum class CanalAviso { EMOCIONES, ATENCION, AYUDA }

data class TextoAviso(val canal: CanalAviso, val titulo: String, val cuerpo: String)

object Avisos {

    fun texto(e: Evento): TextoAviso = when (val emo = e.emocion) {
        null -> TextoAviso(
            CanalAviso.ATENCION,
            "Kalmi: " + (e.interpretacion?.texto ?: "dice algo").replaceFirstChar { it.lowercase() },
            e.interpretacion?.razon ?: e.palabras.joinToString(" + "),
        )
        Emocion.AYUDA -> TextoAviso(CanalAviso.AYUDA, "Kalmi: pide ayuda", "Ya está respirando mientras llegas.")
        Emocion.BIEN, Emocion.TRISTE -> TextoAviso(CanalAviso.EMOCIONES, "Kalmi: se siente ${emo.nombre.lowercase()}", "Toca para acompañarlo.")
        Emocion.FRUSTRADO, Emocion.MUY_ENOJADO, Emocion.TENSION ->
            TextoAviso(CanalAviso.ATENCION, "Kalmi: se siente ${emo.nombre.lowercase()}", "Kalmi lo invita a respirar. Toca para acompañarlo.")
    }

    /**
     * Mensaje que conviene destacar en Acompañar (índice k de `CMD,MSG,k`: 0 Ya voy, 1 Te quiero, 2 Estoy aquí).
     * Es solo una sugerencia: el cuidador elige.
     */
    fun respuestaSugerida(e: Evento): Int? = when (e.emocion) {
        null -> when {
            "Duele" in e.palabras -> 0
            e.palabras.firstOrNull() in setOf("No", "Parar") -> 2
            else -> 0
        }
        Emocion.BIEN -> null
        Emocion.TRISTE -> 1
        Emocion.AYUDA, Emocion.FRUSTRADO, Emocion.MUY_ENOJADO, Emocion.TENSION -> 0
    }
}
