package cl.kalmi.app.ui.palabras

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import cl.kalmi.app.ui.Encabezado
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado("Palabras")
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("¿Cómo funciona?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                listOf(
                    "1. El niño recorre estas palabras con el botón Quiero de Kalmi.",
                    "2. Te llega un aviso con lo que Kalmi cree que quiso decir.",
                    "3. Respondes desde Acompañar y nos dices si acertó.",
                ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }

        Text("Siempre disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Vocabulario.NUCLEO.forEach { palabra ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(palabra, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        Text("Personales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Hasta $MAX_CARACTERES letras: es lo que cabe en la pantalla de Kalmi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        borrador.indices.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { i ->
                    OutlinedTextField(
                        value = borrador[i],
                        onValueChange = { borrador[i] = it.take(MAX_CARACTERES) },
                        label = { Text("${i + 5}") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Button(
            onClick = { onGuardar(borrador.map(String::trim)) },
            enabled = borrador.all { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
