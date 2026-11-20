package cl.kalmi.app.device

import cl.kalmi.app.domain.EstadoConexion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Permite cambiar en caliente entre el simulador y el Kalmi real (Ajustes → Modo de prueba)
 * sin reiniciar la app: el repositorio sigue hablando con la misma interfaz.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DispositivoConmutable(scope: CoroutineScope, inicial: KalmiDevice) : KalmiDevice {

    private val _actual = MutableStateFlow(inicial)
    val actual: StateFlow<KalmiDevice> = _actual.asStateFlow()

    override val estado: StateFlow<EstadoConexion> =
        _actual.flatMapLatest { it.estado }.stateIn(scope, SharingStarted.Eagerly, inicial.estado.value)

    override val lineas: Flow<String> = _actual.flatMapLatest { it.lineas }

    /** Desconecta el dispositivo actual y deja [nuevo] en su lugar (desconectado hasta que el cuidador conecte). */
    suspend fun cambiar(nuevo: KalmiDevice) {
        if (nuevo === _actual.value) return
        _actual.value.desconectar()
        _actual.value = nuevo
    }

    override suspend fun conectar() = _actual.value.conectar()
    override suspend fun desconectar() = _actual.value.desconectar()
    override suspend fun enviar(linea: String) = _actual.value.enviar(linea)
}
