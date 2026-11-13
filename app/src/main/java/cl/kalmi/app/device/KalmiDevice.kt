package cl.kalmi.app.device

import cl.kalmi.app.domain.EstadoConexion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Regla de oro: la app solo conoce esta interfaz. Fake hoy, BLE cuando llegue el hardware. */
interface KalmiDevice {
    val estado: StateFlow<EstadoConexion>

    /** Líneas de texto recibidas desde Kalmi (sin procesar). */
    val lineas: Flow<String>

    suspend fun conectar()
    suspend fun desconectar()
    suspend fun enviar(linea: String)
}
