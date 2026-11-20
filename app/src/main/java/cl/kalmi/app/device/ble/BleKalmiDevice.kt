package cl.kalmi.app.device.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import cl.kalmi.app.device.KalmiDevice
import cl.kalmi.app.domain.EstadoConexion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Kalmi real por BLE (guía §9). Funciona con HM-10 (Uno) o NUS (ESP32) según [perfil].
 * Los callbacks GATT llegan en un hilo de Binder: todo cambio de estado se reenvía a [scope] (Main).
 * Los permisos BLUETOOTH_SCAN/CONNECT se piden en la pantalla Conexión antes de llamar a [conectar].
 */
@SuppressLint("MissingPermission")
class BleKalmiDevice(
    private val context: Context,
    private val scope: CoroutineScope,
    private val perfil: PerfilBle,
) : KalmiDevice {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private val acumulador = AcumuladorLineas()
    private var colaEscritura = Channel<String>(Channel.UNLIMITED)
    private val escrituraLista = Channel<Unit>(Channel.CONFLATED)
    private var escritor: Job? = null

    private val _estado = MutableStateFlow<EstadoConexion>(EstadoConexion.Desconectado)
    override val estado = _estado.asStateFlow()

    private val _lineas = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lineas = _lineas.asSharedFlow()

    override suspend fun conectar() {
        if (adapter?.isEnabled != true) {
            _estado.value = EstadoConexion.Error("Activa el Bluetooth del teléfono")
            return
        }
        _estado.value = EstadoConexion.Buscando
        val device = withTimeoutOrNull(10_000) { escanearPrimero() } ?: run {
            _estado.value = EstadoConexion.Error("No se encontró Kalmi")
            return
        }
        _estado.value = EstadoConexion.Conectando
        colaEscritura = Channel(Channel.UNLIMITED)
        escritor?.cancel()
        escritor = scope.launch { procesarColaEscritura() }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private suspend fun escanearPrimero(): BluetoothDevice? = suspendCancellableCoroutine { cont ->
        val scanner = adapter?.bluetoothLeScanner ?: return@suspendCancellableCoroutine cont.resume(null)
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scanner.stopScan(this)
                if (cont.isActive) cont.resume(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
        }
        val filtro = ScanFilter.Builder().setServiceUuid(ParcelUuid(perfil.servicio)).build()
        val ajustes = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filtro), ajustes, cb)
        cont.invokeOnCancellation { scanner.stopScan(cb) }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, nuevo: Int) {
            when (nuevo) {
                BluetoothProfile.STATE_CONNECTED -> g.requestMtu(247)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    scope.launch { cerrar() }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val servicio = g.getService(perfil.servicio)
            val tx = servicio?.getCharacteristic(perfil.tx)
            rx = servicio?.getCharacteristic(perfil.rx)
            if (tx == null || rx == null) {
                scope.launch { _estado.value = EstadoConexion.Error("El dispositivo no es un Kalmi") }
                g.disconnect()
                return
            }
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(PerfilBle.CCCD)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            } else {
                escrituraLista.trySend(Unit) // sin CCCD no hay onDescriptorWrite: liberar la cola ya
            }
            val nombre = g.device.name ?: "Kalmi"
            scope.launch { _estado.value = EstadoConexion.Conectado(nombre) }
        }

        // La escritura del CCCD termina acá: recién entonces se libera la cola de escritura.
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            escrituraLista.trySend(Unit)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            escrituraLista.trySend(Unit)
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) =
            recibir(value)

        // Android 12 o anterior
        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            recibir(c.value)
        }
    }

    private fun recibir(bytes: ByteArray) {
        scope.launch { acumulador.agregar(bytes).forEach { _lineas.emit(it) } }
    }

    override suspend fun enviar(linea: String) {
        if (_estado.value is EstadoConexion.Conectado) colaEscritura.send(linea + "\n")
    }

    /** Android permite una escritura GATT a la vez y el HM-10 acepta 20 bytes: se serializa trozo a trozo. */
    private suspend fun procesarColaEscritura() {
        for (texto in colaEscritura) {
            for (trozo in trozos(texto)) {
                escrituraLista.receive()
                val c = rx ?: break
                val tipo = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt?.writeCharacteristic(c, trozo, tipo)
                } else {
                    @Suppress("DEPRECATION")
                    c.writeType = tipo
                    @Suppress("DEPRECATION")
                    c.value = trozo
                    @Suppress("DEPRECATION")
                    gatt?.writeCharacteristic(c)
                }
            }
        }
    }

    // ponytail: sin reconexión automática (diseño v2 §2, no-objetivo). El cuidador vuelve a tocar "Conectar".
    override suspend fun desconectar() {
        gatt?.disconnect()
        cerrar()
    }

    private fun cerrar() {
        escritor?.cancel()
        colaEscritura.close()
        gatt = null
        rx = null
        if (_estado.value !is EstadoConexion.Error) _estado.value = EstadoConexion.Desconectado
    }
}
