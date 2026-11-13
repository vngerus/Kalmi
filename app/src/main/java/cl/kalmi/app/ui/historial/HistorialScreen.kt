package cl.kalmi.app.ui.historial

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.kalmi.app.ui.KalmiViewModel

/**
 * TODO(colega): guía §15.4. Seguir el patrón de InicioScreen (Screen + Contenido con @Preview).
 * - LazyColumn con vm.historial (ya viene ordenado, más reciente primero)
 * - Cada fila: hora, emoción con su color (Emocion.color / Emocion.fondo en ui/theme), atendida y en cuánto tiempo
 */
@Composable
fun HistorialScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    Text("Historial (pendiente)", modifier.padding(16.dp))
}
