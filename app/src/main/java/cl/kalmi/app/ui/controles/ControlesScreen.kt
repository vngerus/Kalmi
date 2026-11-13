package cl.kalmi.app.ui.controles

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.kalmi.app.ui.KalmiViewModel

/**
 * TODO(colega): guía §15.3. Seguir el patrón de InicioScreen (Screen + Contenido con @Preview).
 * - Botones "Ya voy" / "Te quiero" / "Estoy aquí" → vm.enviar(Comando.Mensaje(0|1|2))
 * - "Respirar juntos" → vm.enviar(Comando.Respirar)
 * - Slider de brillo → vm.enviar(Comando.Brillo(v)) en onValueChangeFinished
 * - Feedback con vm.estadoComando ("Enviando… / ✓ Confirmado (38 ms) / Sin confirmación"), RTT en vm.calidad
 * - Deshabilitar todo si vm.conexion no es Conectado: "Disponible cuando estés cerca de Kalmi"
 */
@Composable
fun ControlesScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    Text("Controles (pendiente)", modifier.padding(16.dp))
}
