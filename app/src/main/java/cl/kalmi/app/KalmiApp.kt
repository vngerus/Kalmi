package cl.kalmi.app

import android.app.Application
import android.content.Context
import cl.kalmi.app.data.KalmiRepository
import cl.kalmi.app.device.FakeKalmiDevice
import cl.kalmi.app.device.KalmiDevice
import cl.kalmi.app.device.ble.BleKalmiDevice
import cl.kalmi.app.device.ble.PerfilBle
import cl.kalmi.app.domain.Vocabulario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/** Inyección manual: único lugar donde se elige Fake vs BLE e interpretador (reglas hoy, IA mañana). */
class KalmiApp : Application() {

    // Main = un solo hilo para el estado del repo y del simulador. El trabajo pesado (BLE, red) usa su propio dispatcher.
    private val appScope: CoroutineScope = MainScope()

    private val prefs by lazy { getSharedPreferences("kalmi", Context.MODE_PRIVATE) }

    /** En release siempre el Kalmi real. En debug se elige en Conexión (se aplica al reiniciar la app). */
    var usarDispositivoReal: Boolean
        get() = !BuildConfig.DEBUG || prefs.getBoolean(CLAVE_REAL, false)
        set(valor) = prefs.edit().putBoolean(CLAVE_REAL, valor).apply()

    // ponytail: se elige al iniciar; cambio en caliente si molesta en las demos.
    val device: KalmiDevice by lazy {
        if (usarDispositivoReal) BleKalmiDevice(this, appScope, PerfilBle.HM10) else FakeKalmiDevice(appScope)
    }

    val repository: KalmiRepository by lazy {
        KalmiRepository(
            device,
            appScope,
            personalesIniciales = prefs.getString(CLAVE_PERSONALES, null)?.split("\n")
                ?.takeIf { it.size == Vocabulario.PERSONALES }
                ?: Vocabulario.PERSONALES_DEFECTO,
            guardarPersonales = { prefs.edit().putString(CLAVE_PERSONALES, it.joinToString("\n")).apply() },
        )
    }

    private companion object {
        const val CLAVE_PERSONALES = "personales"
        const val CLAVE_REAL = "real"
    }
}
