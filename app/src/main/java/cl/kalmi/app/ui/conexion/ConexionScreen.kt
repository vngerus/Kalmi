package cl.kalmi.app.ui.conexion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.ui.KalmiViewModel

/** Guía §15.5. Hoy: solo conectar/desconectar para que fluyan datos del simulador. */
@Composable
fun ConexionScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val conexion by vm.conexion.collectAsStateWithLifecycle()

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Conexión", style = MaterialTheme.typography.headlineSmall)
        Text(
            when (val c = conexion) {
                EstadoConexion.Desconectado -> "Desconectado"
                EstadoConexion.Buscando -> "Buscando Kalmi…"
                EstadoConexion.Conectando -> "Conectando…"
                is EstadoConexion.Conectado -> "Conectado a ${c.nombre}"
                is EstadoConexion.Error -> "Error: ${c.mensaje}"
            },
        )
        if (conexion is EstadoConexion.Conectado) {
            OutlinedButton(onClick = { vm.desconectar() }) { Text("Desconectar") }
        } else {
            Button(onClick = { vm.conectar() }) { Text("Conectar") }
        }

        // TODO(colega): bloque "Diagnóstico" con vm.calidad (recibidos, perdidos, % pérdida, descartados, último RTT).
        // TODO(colega): si BuildConfig.DEBUG && vm.simulador != null → botones "simular emoción"
        //               (vm.simulador.simularBoton(1..5)) y Slider de pérdida (vm.simulador.probabilidadPerdida).
    }
}
