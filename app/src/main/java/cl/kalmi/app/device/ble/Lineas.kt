package cl.kalmi.app.device.ble

/** Las notificaciones BLE traen media línea o varias: se acumulan y se cortan por '\n'. */
class AcumuladorLineas {
    private val buffer = StringBuilder()

    fun agregar(bytes: ByteArray): List<String> {
        buffer.append(bytes.decodeToString())
        val lineas = mutableListOf<String>()
        while (true) {
            val i = buffer.indexOf("\n")
            if (i < 0) break
            buffer.substring(0, i).trim().takeIf { it.isNotEmpty() }?.let(lineas::add)
            buffer.delete(0, i + 1)
        }
        return lineas
    }
}

/** El HM-10 acepta como máximo 20 bytes por escritura GATT: el texto se envía en trozos. */
fun trozos(texto: String, max: Int = 20): List<ByteArray> =
    texto.encodeToByteArray().toList().chunked(max).map { it.toByteArray() }
