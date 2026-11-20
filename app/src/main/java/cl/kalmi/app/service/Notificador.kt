package cl.kalmi.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.kalmi.app.MainActivity
import cl.kalmi.app.R
import cl.kalmi.app.domain.Avisos
import cl.kalmi.app.domain.CanalAviso
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.ui.theme.KalmiColors
import androidx.compose.ui.graphics.toArgb

/**
 * Notificaciones locales cuando Kalmi avisa algo (guía §13). Hoy llegan por BLE/simulador;
 * con FCM (paso 7) el mismo texto sale de [Avisos] en el servidor.
 */
class Notificador(private val context: Context) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(CanalAviso.EMOCIONES.id, "Emociones", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(CanalAviso.ATENCION.id, "Necesita atención", NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel(CanalAviso.AYUDA.id, "Pide ayuda", NotificationManager.IMPORTANCE_HIGH).apply {
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 250, 150, 250) // suave, sin insistir
                    },
                ),
            )
        }
    }

    fun mostrar(e: Evento) {
        val permitido = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permitido) return

        val aviso = Avisos.texto(e)
        val abrir = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PESTANA, MainActivity.PESTANA_ACOMPANAR)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, aviso.canal.id)
            .setSmallIcon(R.drawable.ic_kalmi_notif)
            .setColor((e.emocion?.let { colorDe(it.codigo) } ?: KalmiColors.CalmaTinta).toArgb())
            .setContentTitle(aviso.titulo)
            .setContentText(aviso.cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.cuerpo))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setPriority(if (aviso.canal == CanalAviso.EMOCIONES) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission") // verificado arriba
        NotificationManagerCompat.from(context).notify(ID, n)
    }

    private fun colorDe(codigo: Int) = when (codigo) {
        1 -> KalmiColors.BienTinta
        2 -> KalmiColors.TristeTinta
        3, 6 -> KalmiColors.FrustradoTinta
        4 -> KalmiColors.MuyEnojadoTinta
        else -> KalmiColors.CalmaTinta
    }

    private val CanalAviso.id get() = "kalmi_" + name.lowercase()

    private companion object {
        const val ID = 1 // un solo aviso visible: el más reciente reemplaza al anterior
    }
}
