package cl.kalmi.app

import android.app.Application
import cl.kalmi.app.data.KalmiRepository
import cl.kalmi.app.device.FakeKalmiDevice
import cl.kalmi.app.device.KalmiDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/** Inyección manual: único lugar donde se elige Fake vs BLE. */
class KalmiApp : Application() {

    // Main = un solo hilo para el estado del repo y del simulador. El trabajo pesado (BLE, red) usa su propio dispatcher.
    private val appScope: CoroutineScope = MainScope()

    val device: KalmiDevice by lazy { FakeKalmiDevice(appScope) } // paso 8: BleKalmiDevice(this, appScope)
    val repository: KalmiRepository by lazy { KalmiRepository(device, appScope) }
}
