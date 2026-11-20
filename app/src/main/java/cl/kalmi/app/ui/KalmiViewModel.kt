package cl.kalmi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.kalmi.app.KalmiApp
import cl.kalmi.app.data.KalmiRepository
import cl.kalmi.app.device.FakeKalmiDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Comando
import kotlinx.coroutines.launch

// ponytail: un ViewModel compartido por las 5 pantallas. Separar por pantalla si alguna crece.
class KalmiViewModel(
    private val repo: KalmiRepository,
    private val app: KalmiApp,
) : ViewModel() {

    /** Ajustes → Modo de prueba: true = simulador + diagnóstico + panel de pruebas. */
    val modoPrueba: StateFlow<Boolean> = app.device.actual
        .map { it is FakeKalmiDevice }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.modoPrueba)

    fun cambiarModoPrueba(activo: Boolean) = viewModelScope.launch { app.cambiarModoPrueba(activo) }

    val conexion = repo.estadoConexion
    val telemetria = repo.telemetria
    val bateria = repo.bateria
    val alerta = repo.alertaActual
    val calidad = repo.calidad
    val estadoComando = repo.estadoComando
    val historial = repo.historial
    val personales = repo.personales

    val simulador: FakeKalmiDevice get() = app.simulador

    fun conectar() = viewModelScope.launch { repo.conectar() }
    fun desconectar() = viewModelScope.launch { repo.desconectar() }
    fun marcarAtendido() = viewModelScope.launch { repo.marcarAtendido() }
    fun enviar(c: Comando) = viewModelScope.launch { repo.enviarComando(c) }
    fun guardarPersonales(lista: List<String>) = viewModelScope.launch { repo.actualizarPersonales(lista) }
    fun marcarAcierto(evento: Evento, acerto: Boolean) = repo.marcarAcierto(evento, acerto)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KalmiApp
                KalmiViewModel(app.repository, app)
            }
        }
    }
}
