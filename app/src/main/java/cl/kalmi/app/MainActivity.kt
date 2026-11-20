package cl.kalmi.app

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
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
import cl.kalmi.app.ui.ajustes.AjustesScreen
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
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KalmiTheme {
                val vm: KalmiViewModel = viewModel(factory = KalmiViewModel.Factory)
                var actual by rememberSaveable { mutableStateOf(Pestana.INICIO) }

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
                        Pestana.AJUSTES -> AjustesScreen(vm, mod)
                    }
                }
            }
        }
    }
}

/**
 * Barra de navegación flotante: ítems de igual ancho (se adapta a cualquier pantalla),
 * ícono + texto siempre visibles, indicador suave detrás del ícono activo.
 */
@Composable
private fun BarraNavegacion(actual: Pestana, onElegir: (Pestana) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).selectableGroup()) {
                Pestana.entries.forEach { p -> ItemBarra(p, p == actual, Modifier.weight(1f)) { onElegir(p) } }
            }
        }
    }
}

@Composable
private fun ItemBarra(p: Pestana, elegida: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fondo by animateColorAsState(
        if (elegida) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "fondo",
    )
    val tinta = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = elegida, onClick = onClick, role = Role.Tab)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.clip(CircleShape).background(fondo).padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(p.icono, contentDescription = null, tint = if (elegida) MaterialTheme.colorScheme.onPrimaryContainer else tinta)
        }
        Text(
            p.titulo,
            color = tinta,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
