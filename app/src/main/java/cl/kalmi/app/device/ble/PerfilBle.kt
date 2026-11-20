package cl.kalmi.app.device.ble

import java.util.UUID

/** UUIDs del servicio serie BLE. HM-10 (Arduino Uno) hoy; NUS (ESP32) en la versión final. */
data class PerfilBle(val servicio: UUID, val tx: UUID, val rx: UUID) {
    companion object {
        /** HM-10: una sola característica FFE1 para escribir y notificar. */
        val HM10 = PerfilBle(
            servicio = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"),
            tx = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
            rx = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
        )

        /** Nordic UART Service (guía §9.1). tx = Kalmi notifica, rx = la app escribe. */
        val NUS = PerfilBle(
            servicio = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"),
            tx = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"),
            rx = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"),
        )

        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
