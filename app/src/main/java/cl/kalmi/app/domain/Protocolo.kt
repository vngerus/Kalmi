package cl.kalmi.app.domain

sealed interface MensajeKalmi {
    data class Tel(val seq: Int, val luz: Int, val presion: Int) : MensajeKalmi
    data class Evt(val seq: Int, val emocion: Emocion) : MensajeKalmi
    data class Ok(val id: Int) : MensajeKalmi
    data class Bat(val porcentaje: Int) : MensajeKalmi
    data class Cfg(val partes: List<String>) : MensajeKalmi
}

/** Protocolo de texto Kalmi: `CUERPO*XX`, XX = XOR de los caracteres del cuerpo en hex. */
object Protocolo {

    fun checksum(s: String): Int = s.fold(0) { acc, c -> acc xor c.code } and 0xFF

    /** Agrega "*XX" al final. */
    fun firmar(cuerpo: String): String = "%s*%02X".format(cuerpo, checksum(cuerpo))

    /** Devuelve null si el checksum no coincide o el formato es inválido. */
    fun parsear(linea: String): MensajeKalmi? {
        val limpia = linea.trim()
        val ast = limpia.lastIndexOf('*')
        if (ast < 0) return null
        val cuerpo = limpia.substring(0, ast)
        val esperado = limpia.substring(ast + 1).toIntOrNull(16) ?: return null
        if (checksum(cuerpo) != esperado) return null

        val p = cuerpo.split(",")
        return runCatching {
            when (p[0]) {
                "TEL" -> MensajeKalmi.Tel(p[1].toInt(), p[2].toInt().coerceIn(0, 100), p[3].toInt().coerceIn(0, 100))
                "EVT" -> Emocion.desdeCodigo(p[2].removePrefix("E").toInt())?.let { MensajeKalmi.Evt(p[1].toInt(), it) }
                "OK" -> MensajeKalmi.Ok(p[1].toInt())
                "BAT" -> MensajeKalmi.Bat(p[1].toInt().coerceIn(0, 100))
                "CFG" -> MensajeKalmi.Cfg(p.drop(1))
                else -> null
            }
        }.getOrNull()
    }

    fun ack(seq: Int) = firmar("ACK,$seq")

    fun comando(id: Int, c: Comando): String = firmar(
        when (c) {
            Comando.Atendido -> "CMD,$id,ATN"
            is Comando.Mensaje -> "CMD,$id,MSG,${c.k}"
            Comando.Respirar -> "CMD,$id,RESP"
            is Comando.Brillo -> "CMD,$id,BRI,${c.valor.coerceIn(10, 100)}"
            is Comando.Preguntar -> "CMD,$id,ASK,${c.emocion.codigo}"
        },
    )
}
