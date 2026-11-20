package cl.kalmi.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val PERMISOS_BLE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

/**
 * Acción "Conectar" para cualquier pantalla: con el Kalmi real pide antes los permisos de Bluetooth;
 * con el simulador (modo de prueba) conecta directo.
 */
@Composable
fun rememberConectar(vm: KalmiViewModel): () -> Unit {
    val modoPrueba by vm.modoPrueba.collectAsStateWithLifecycle()
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) vm.conectar()
    }
    return { if (modoPrueba) vm.conectar() else pedirPermisos.launch(PERMISOS_BLE) }
}
