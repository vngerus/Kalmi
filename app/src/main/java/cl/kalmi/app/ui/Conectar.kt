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

/** Android 13+: avisos de Kalmi como notificación. Se pide en el mismo momento que Bluetooth. */
private val PERMISO_AVISOS = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

/**
 * Acción "Conectar" para cualquier pantalla: con el Kalmi real pide antes los permisos de Bluetooth;
 * con el simulador (modo de prueba) conecta directo.
 */
@Composable
fun rememberConectar(vm: KalmiViewModel): () -> Unit {
    val modoPrueba by vm.modoPrueba.collectAsStateWithLifecycle()
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        // Notificaciones son opcionales; Bluetooth es necesario solo con el Kalmi real.
        if (modoPrueba || PERMISOS_BLE.all { r[it] != false }) vm.conectar()
    }
    return {
        val permisos = (if (modoPrueba) emptyArray() else PERMISOS_BLE) + PERMISO_AVISOS
        if (permisos.isEmpty()) vm.conectar() else pedirPermisos.launch(permisos)
    }
}
