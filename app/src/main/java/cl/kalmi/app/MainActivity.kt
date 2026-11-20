package cl.kalmi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.conexion.ConexionScreen
import cl.kalmi.app.ui.controles.ControlesScreen
import cl.kalmi.app.ui.historial.HistorialScreen
import cl.kalmi.app.ui.inicio.InicioScreen
import cl.kalmi.app.ui.palabras.PalabrasScreen
import cl.kalmi.app.ui.theme.KalmiTheme

// ponytail: pestañas planas → enum + when. Pasar a Navigation 3 (skill navigation-3) cuando haya pantallas de detalle.
private enum class Pestana(val titulo: String, val icono: ImageVector) {
    INICIO("Inicio", Icons.Filled.Home),
    CONTROLES("Acompañar", Icons.Filled.Favorite),
    HISTORIAL("Historial", Icons.Filled.DateRange),
    PALABRAS("Palabras", Icons.Filled.Edit),
    CONEXION("Conexión", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KalmiTheme {
                val vm: KalmiViewModel = viewModel(factory = KalmiViewModel.Factory)
                var actual by rememberSaveable { mutableStateOf(Pestana.CONEXION) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { BarraNavegacion(actual) { actual = it } },
                ) { padding ->
                    val mod = Modifier.padding(padding)
                    when (actual) {
                        Pestana.INICIO -> InicioScreen(vm, mod)
                        Pestana.CONTROLES -> ControlesScreen(vm, mod)
                        Pestana.HISTORIAL -> HistorialScreen(vm, mod)
                        Pestana.PALABRAS -> PalabrasScreen(vm, mod)
                        Pestana.CONEXION -> ConexionScreen(vm, mod)
                    }
                }
            }
        }
    }
}

/**
 * Barra flotante tipo píldora: la pestaña activa se expande con ícono + texto,
 * las demás muestran solo el ícono (con descripción para lectores de pantalla).
 */
@Composable
private fun BarraNavegacion(actual: Pestana, onElegir: (Pestana) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pestana.entries.forEach { p -> ItemBarra(p, p == actual) { onElegir(p) } }
            }
        }
    }
}

@Composable
private fun ItemBarra(p: Pestana, elegida: Boolean, onClick: () -> Unit) {
    val fondo by animateColorAsState(
        if (elegida) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "fondo",
    )
    val tinta = if (elegida) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(CircleShape)
            .background(fondo)
            .selectable(selected = elegida, onClick = onClick, role = Role.Tab)
            .animateContentSize()
            .height(48.dp)
            .padding(horizontal = if (elegida) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(p.icono, contentDescription = if (elegida) null else p.titulo, tint = tinta)
        if (elegida) {
            Text(p.titulo, color = tinta, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
