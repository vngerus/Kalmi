package cl.kalmi.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChangedBy
import cl.kalmi.app.service.Notificador
import android.os.Bundle
import android.app.Activity
import android.app.Application
import android.content.Context
import cl.kalmi.app.data.KalmiRepository
import cl.kalmi.app.device.FakeKalmiDevice
import cl.kalmi.app.device.DispositivoConmutable
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

    val simulador by lazy { FakeKalmiDevice(appScope) }
    private val real by lazy { BleKalmiDevice(this, appScope, PerfilBle.HM10) }

    /** Ajustes → Modo de prueba. Encendido = simulador (por defecto en builds debug). */
    val modoPrueba: Boolean get() = prefs.getBoolean(CLAVE_PRUEBA, BuildConfig.DEBUG)

    val device: DispositivoConmutable by lazy {
        DispositivoConmutable(appScope, if (modoPrueba) simulador else real)
    }

    /** Cambia en caliente entre simulador y Kalmi real; queda guardado. */
    suspend fun cambiarModoPrueba(activo: Boolean) {
        prefs.edit().putBoolean(CLAVE_PRUEBA, activo).apply()
        device.cambiar(if (activo) simulador else real)
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

    private var pantallasVisibles = 0
    val enPrimerPlano: Boolean get() = pantallasVisibles > 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { pantallasVisibles++ }
            override fun onActivityStopped(a: Activity) { pantallasVisibles-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
        // Cada aviso nuevo de Kalmi → notificación, solo si la app no está a la vista (ahí ya se ve en Inicio).
        val notificador = Notificador(this)
        appScope.launch {
            repository.alertaActual.filterNotNull().distinctUntilChangedBy { it.seq }.collect {
                if (!enPrimerPlano) notificador.mostrar(it)
            }
        }
    }

    private companion object {
        const val CLAVE_PERSONALES = "personales"
        const val CLAVE_PRUEBA = "modo_prueba"
    }
}
