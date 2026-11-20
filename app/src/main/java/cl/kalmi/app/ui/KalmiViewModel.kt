package cl.kalmi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.kalmi.app.KalmiApp
import cl.kalmi.app.data.KalmiRepository
import cl.kalmi.app.device.FakeKalmiDevice
import cl.kalmi.app.device.KalmiDevice
import cl.kalmi.app.domain.Comando
import kotlinx.coroutines.launch

// ponytail: un ViewModel compartido por las 4 pantallas. Separar por pantalla si alguna crece.
class KalmiViewModel(
    private val repo: KalmiRepository,
    device: KalmiDevice,
) : ViewModel() {

    val conexion = repo.estadoConexion
    val telemetria = repo.telemetria
    val bateria = repo.bateria
    val alerta = repo.alertaActual
    val calidad = repo.calidad
    val estadoComando = repo.estadoComando
    val historial = repo.historial
    val personales = repo.personales

    /** No null solo con el simulador: habilita el panel de depuración. */
    val simulador = device as? FakeKalmiDevice

    fun conectar() = viewModelScope.launch { repo.conectar() }
    fun desconectar() = viewModelScope.launch { repo.desconectar() }
    fun marcarAtendido() = viewModelScope.launch { repo.marcarAtendido() }
    fun enviar(c: Comando) = viewModelScope.launch { repo.enviarComando(c) }
    fun guardarPersonales(lista: List<String>) = viewModelScope.launch { repo.actualizarPersonales(lista) }
    fun marcarAcierto(seq: Int, acerto: Boolean) = repo.marcarAcierto(seq, acerto)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KalmiApp
                KalmiViewModel(app.repository, app.device)
            }
        }
    }
}
