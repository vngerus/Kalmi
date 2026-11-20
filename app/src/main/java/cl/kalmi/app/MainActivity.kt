package cl.kalmi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kalmi.app.ui.KalmiViewModel
import cl.kalmi.app.ui.conexion.ConexionScreen
import cl.kalmi.app.ui.controles.ControlesScreen
import cl.kalmi.app.ui.historial.HistorialScreen
import cl.kalmi.app.ui.inicio.InicioScreen
import cl.kalmi.app.ui.palabras.PalabrasScreen
import cl.kalmi.app.ui.theme.KalmiTheme

// ponytail: 4 pestañas planas → enum + when. Pasar a Navigation 3 (skill navigation-3) cuando haya pantallas de detalle.
private enum class Pestana(val titulo: String, val icono: ImageVector) {
    INICIO("Inicio", Icons.Filled.Home),
    CONTROLES("Controles", Icons.Filled.Star),
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
                    bottomBar = {
                        NavigationBar {
                            Pestana.entries.forEach { p ->
                                NavigationBarItem(
                                    selected = p == actual,
                                    onClick = { actual = p },
                                    icon = { Icon(p.icono, contentDescription = null) },
                                    label = { Text(p.titulo) },
                                )
                            }
                        }
                    },
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
