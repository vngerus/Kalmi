package cl.kalmi.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import cl.kalmi.app.domain.Canal
import cl.kalmi.app.domain.Emocion
import cl.kalmi.app.domain.Evento
import cl.kalmi.app.domain.Interpretacion

/**
 * Guía §11, con dos cambios a propósito:
 * - La llave es (momento, seq) y no solo seq: seq se repite si Kalmi se reinicia o se cambia de dispositivo.
 * - `emocion` es nullable: los eventos de palabras (PAL) no tienen emoción.
 * `subido` queda listo para el paso 6 (Supabase) sin necesitar migración.
 */
@Entity(tableName = "eventos", primaryKeys = ["momento", "seq"])
data class EventoEntity(
    val momento: Long,
    val seq: Int,
    val emocion: Int?,
    val canal: String,
    val atendido: Boolean,
    val atendidoEn: Long?,
    val palabras: String, // separadas por "\n", igual que las palabras personales en SharedPreferences
    val interpretacionTexto: String?,
    val interpretacionRazon: String?,
    val acerto: Boolean?,
    val subido: Boolean = false,
) {
    fun aDominio() = Evento(
        seq = seq,
        emocion = emocion?.let { c -> Emocion.entries.first { it.codigo == c } },
        momento = momento,
        canal = Canal.valueOf(canal),
        atendido = atendido,
        atendidoEn = atendidoEn,
        palabras = if (palabras.isEmpty()) emptyList() else palabras.split("\n"),
        interpretacion = interpretacionTexto?.let { Interpretacion(it, interpretacionRazon.orEmpty()) },
        acerto = acerto,
    )

    companion object {
        fun desde(e: Evento) = EventoEntity(
            momento = e.momento,
            seq = e.seq,
            emocion = e.emocion?.codigo,
            canal = e.canal.name,
            atendido = e.atendido,
            atendidoEn = e.atendidoEn,
            palabras = e.palabras.joinToString("\n"),
            interpretacionTexto = e.interpretacion?.texto,
            interpretacionRazon = e.interpretacion?.razon,
            acerto = e.acerto,
        )
    }
}

@Dao
interface EventoDao {
    @Upsert
    suspend fun guardar(e: EventoEntity)

    @Query("SELECT * FROM eventos WHERE momento >= :desde ORDER BY momento DESC")
    suspend fun desde(desde: Long): List<EventoEntity>
}

@Database(entities = [EventoEntity::class], version = 1, exportSchema = false)
abstract class HistorialDb : RoomDatabase() {
    abstract fun eventos(): EventoDao

    companion object {
        fun crear(context: Context) = Room.databaseBuilder(context, HistorialDb::class.java, "kalmi.db").build()
    }
}
