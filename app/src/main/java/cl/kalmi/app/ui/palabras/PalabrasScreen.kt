package cl.kalmi.app.ui.palabras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.kalmi.app.domain.EstadoConexion
import cl.kalmi.app.domain.Vocabulario
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.theme.KalmiTheme

private const val MAX_CARACTERES = 10 // lo que cabe en la pantalla de Kalmi

/** Diseño v2 §5.6: el cuidador elige las palabras personales que el niño ve con el botón Quiero. */
@Composable
fun PalabrasScreen(vm: KalmiViewModel, modifier: Modifier = Modifier) {
    val personales by vm.personales.collectAsStateWithLifecycle()
    val conexion by vm.conexion.collectAsStateWithLifecycle()

    PalabrasContenido(
        personales = personales,
        conectado = conexion is EstadoConexion.Conectado,
        onGuardar = { vm.guardarPersonales(it) },
        modifier = modifier,
    )
}

@Composable
fun PalabrasContenido(
    personales: List<String>,
    conectado: Boolean,
    onGuardar: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Borrador local: se edita libremente y se guarda con el botón.
    val borrador = remember(personales) { mutableStateListOf(*personales.toTypedArray()) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Palabras de Kalmi", style = MaterialTheme.typography.headlineSmall)
        Text(
            "El niño las recorre con el botón Quiero. Elige las que más use en su día.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("Siempre disponibles", style = MaterialTheme.typography.titleMedium)
        Text(Vocabulario.NUCLEO.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)

        Text("Personales", style = MaterialTheme.typography.titleMedium)
        borrador.forEachIndexed { i, texto ->
            OutlinedTextField(
                value = texto,
                onValueChange = { borrador[i] = it.take(MAX_CARACTERES) },
                label = { Text("Palabra ${i + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = { onGuardar(borrador.map(String::trim)) },
            enabled = borrador.all { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar y enviar a Kalmi") }

        if (!conectado) {
            Text(
                "Kalmi no está conectado: se enviarán al conectar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PalabrasPreview() = KalmiTheme {
    PalabrasContenido(Vocabulario.PERSONALES_DEFECTO, conectado = false, onGuardar = {})
}
