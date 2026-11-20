package cl.kalmi.app.domain

// Dominio puro: sin Android ni Compose. Los colores viven en ui/theme.

enum class Emocion(val codigo: Int, val nombre: String) {
    BIEN(1, "Bien"),
    TRISTE(2, "Triste"),
    FRUSTRADO(3, "Frustrado"),
    MUY_ENOJADO(4, "Muy enojado"),
    AYUDA(5, "Necesita ayuda"),
    TENSION(6, "Tensión alta");

    companion object {
        fun desdeCodigo(c: Int) = entries.firstOrNull { it.codigo == c }
    }
}

enum class Canal { BLE, NUBE }

/** Palabras que el niño puede elegir con el botón Quiero: índices 0–3 núcleo fijo, 4–9 personales. */
object Vocabulario {
    val NUCLEO = listOf("Quiero", "No", "Más", "Parar")
    val PERSONALES_DEFECTO = listOf("Agua", "Comer", "Baño", "Duele", "Abrazo", "Jugar")
    const val PERSONALES = 6

    fun completo(personales: List<String>): List<String> = NUCLEO + personales
}

/** Lo que Kalmi cree que el niño quiso decir. La razón siempre se muestra al cuidador. */
data class Interpretacion(val texto: String, val razon: String)

/** `emocion == null` = evento de palabras (`PAL`). */
data class Evento(
    val seq: Int,
    val emocion: Emocion?,
    val momento: Long, // epoch ms
    val canal: Canal,
    val atendido: Boolean = false,
    val atendidoEn: Long? = null,
    val palabras: List<String> = emptyList(),
    val interpretacion: Interpretacion? = null,
    val acerto: Boolean? = null, // respuesta del cuidador a "¿Acertó?"
)

data class Telemetria(
    val seq: Int,
    val luz: Int, // 0–100
    val presion: Int, // 0–100
    val momento: Long,
)

sealed interface Comando {
    data object Atendido : Comando
    data class Mensaje(val k: Int) : Comando // 0 Ya voy, 1 Te quiero, 2 Estoy aquí
    data object Respirar : Comando
    data class Brillo(val valor: Int) : Comando // 10–100
    data class Preguntar(val emocion: Emocion) : Comando
}

enum class EstadoComando { ENVIANDO, CONFIRMADO, SIN_CONFIRMACION }

sealed interface EstadoConexion {
    data object Desconectado : EstadoConexion
    data object Buscando : EstadoConexion
    data object Conectando : EstadoConexion
    data class Conectado(val nombre: String) : EstadoConexion
    data class Error(val mensaje: String) : EstadoConexion
}

data class CalidadEnlace(
    val recibidos: Int = 0,
    val perdidos: Int = 0,
    val descartados: Int = 0, // checksum inválido
    val ultimoRttMs: Long? = null,
) {
    val porcentajePerdida: Float
        get() = if (recibidos + perdidos == 0) 0f else perdidos * 100f / (recibidos + perdidos)
}
