package com.example.aplicativoorganolepticas.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "organo_records")
data class OrganoRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fechaRegistro: Long,
    val bloque: String,
    val numeroBin: Int,
    // Grupo que queda en el registro: GF del bloque, o grupo siembra si no tiene GF (el usuario elige y puede editar)
    val grupo: String = "",
    // Campos globales
    val fotoPath: String = "",
    val observaciones: String = "",
    // Muestra 1
    val m1PesoFruta: Double = 0.0,
    val m1ColorExterno: String = "",
    val m1GradosBrix: Double = 0.0,
    val m1PruebaAcidez: Boolean = false,
    val m1Acidez: Double = 0.0,
    val m1AvanceTranslucidez: Int = 10,
    val m1Categoria: String = "Especial",
    val m1Afectaciones: String = "Ninguna",
    // Muestra 2
    val m2PesoFruta: Double = 0.0,
    val m2ColorExterno: String = "",
    val m2GradosBrix: Double = 0.0,
    val m2PruebaAcidez: Boolean = false,
    val m2Acidez: Double = 0.0,
    val m2AvanceTranslucidez: Int = 10,
    val m2Categoria: String = "Especial",
    val m2Afectaciones: String = "Ninguna",
    // Muestra 3
    val m3PesoFruta: Double = 0.0,
    val m3ColorExterno: String = "",
    val m3GradosBrix: Double = 0.0,
    val m3PruebaAcidez: Boolean = false,
    val m3Acidez: Double = 0.0,
    val m3AvanceTranslucidez: Int = 10,
    val m3Categoria: String = "Especial",
    val m3Afectaciones: String = "Ninguna",
    // Muestra 4
    val m4PesoFruta: Double = 0.0,
    val m4ColorExterno: String = "",
    val m4GradosBrix: Double = 0.0,
    val m4PruebaAcidez: Boolean = false,
    val m4Acidez: Double = 0.0,
    val m4AvanceTranslucidez: Int = 10,
    val m4Categoria: String = "Especial",
    val m4Afectaciones: String = "Ninguna",
    // Muestra 5
    val m5PesoFruta: Double = 0.0,
    val m5ColorExterno: String = "",
    val m5GradosBrix: Double = 0.0,
    val m5PruebaAcidez: Boolean = false,
    val m5Acidez: Double = 0.0,
    val m5AvanceTranslucidez: Int = 10,
    val m5Categoria: String = "Especial",
    val m5Afectaciones: String = "Ninguna",
    val isSynced: Boolean = false,
    // Último error de envío ("" = sin error). Pendiente = !isSynced && ultimoError vacío.
    val ultimoError: String = ""
)

@Entity(tableName = "cached_blocks")
data class CachedBlockEntity(
    @PrimaryKey val bloque: String,
    val grupoForza: String = "",
    val grupoSiembra: String = ""
)

/** tipo: "Afectación externa", "Afectación interna" o "Ninguno" (tipo_afectacion del servidor). */
@Entity(tableName = "cached_afectaciones")
data class CachedAfectacionEntity(
    @PrimaryKey val nombre: String,
    val tipo: String = ""
)

/** Una muestra del bin. peso <= 0 = muestra vacía: no se envía al servidor ni cuenta en el resumen. */
data class Muestra(
    val peso: Double,
    val color: String,
    val brix: Double,
    val pruebaAcidez: Boolean,
    val acidez: Double,
    val translucidez: Int,
    val categoria: String,
    val afectaciones: String
) {
    val tienePeso get() = peso > 0.0
}

fun OrganoRecordEntity.muestras(): List<Muestra> = listOf(
    Muestra(m1PesoFruta, m1ColorExterno, m1GradosBrix, m1PruebaAcidez, m1Acidez, m1AvanceTranslucidez, m1Categoria, m1Afectaciones),
    Muestra(m2PesoFruta, m2ColorExterno, m2GradosBrix, m2PruebaAcidez, m2Acidez, m2AvanceTranslucidez, m2Categoria, m2Afectaciones),
    Muestra(m3PesoFruta, m3ColorExterno, m3GradosBrix, m3PruebaAcidez, m3Acidez, m3AvanceTranslucidez, m3Categoria, m3Afectaciones),
    Muestra(m4PesoFruta, m4ColorExterno, m4GradosBrix, m4PruebaAcidez, m4Acidez, m4AvanceTranslucidez, m4Categoria, m4Afectaciones),
    Muestra(m5PesoFruta, m5ColorExterno, m5GradosBrix, m5PruebaAcidez, m5Acidez, m5AvanceTranslucidez, m5Categoria, m5Afectaciones)
)

fun OrganoRecordEntity.conMuestras(m: List<Muestra>): OrganoRecordEntity = copy(
    m1PesoFruta = m[0].peso, m1ColorExterno = m[0].color, m1GradosBrix = m[0].brix, m1PruebaAcidez = m[0].pruebaAcidez, m1Acidez = m[0].acidez, m1AvanceTranslucidez = m[0].translucidez, m1Categoria = m[0].categoria, m1Afectaciones = m[0].afectaciones,
    m2PesoFruta = m[1].peso, m2ColorExterno = m[1].color, m2GradosBrix = m[1].brix, m2PruebaAcidez = m[1].pruebaAcidez, m2Acidez = m[1].acidez, m2AvanceTranslucidez = m[1].translucidez, m2Categoria = m[1].categoria, m2Afectaciones = m[1].afectaciones,
    m3PesoFruta = m[2].peso, m3ColorExterno = m[2].color, m3GradosBrix = m[2].brix, m3PruebaAcidez = m[2].pruebaAcidez, m3Acidez = m[2].acidez, m3AvanceTranslucidez = m[2].translucidez, m3Categoria = m[2].categoria, m3Afectaciones = m[2].afectaciones,
    m4PesoFruta = m[3].peso, m4ColorExterno = m[3].color, m4GradosBrix = m[3].brix, m4PruebaAcidez = m[3].pruebaAcidez, m4Acidez = m[3].acidez, m4AvanceTranslucidez = m[3].translucidez, m4Categoria = m[3].categoria, m4Afectaciones = m[3].afectaciones,
    m5PesoFruta = m[4].peso, m5ColorExterno = m[4].color, m5GradosBrix = m[4].brix, m5PruebaAcidez = m[4].pruebaAcidez, m5Acidez = m[4].acidez, m5AvanceTranslucidez = m[4].translucidez, m5Categoria = m[4].categoria, m5Afectaciones = m[4].afectaciones
)

/** Muestras que se cargan (las que tienen peso). */
fun OrganoRecordEntity.muestrasConPeso(): List<Muestra> = muestras().filter { it.tienePeso }

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fecha: Long,
    val recordId: Int,
    val bloque: String,
    val numeroBin: Int,
    val exito: Boolean,
    val mensaje: String
)

@Dao
interface OrganoDao {
    @Query("SELECT * FROM organo_records ORDER BY fechaRegistro DESC")
    fun getAllRecords(): Flow<List<OrganoRecordEntity>>

    @Query("SELECT * FROM organo_records ORDER BY fechaRegistro DESC")
    suspend fun getAllRecordsOnce(): List<OrganoRecordEntity>

    @Query("SELECT * FROM sync_logs ORDER BY fecha DESC")
    suspend fun getAllLogsOnce(): List<SyncLogEntity>

    @Insert
    suspend fun insertRecord(record: OrganoRecordEntity): Long

    @Delete
    suspend fun deleteRecord(record: OrganoRecordEntity): Int

    @Update
    suspend fun updateRecord(record: OrganoRecordEntity)

    @Query("UPDATE organo_records SET isSynced = :synced, ultimoError = :error WHERE id = :recordId")
    suspend fun updateSyncStatus(recordId: Int, synced: Boolean, error: String)

    @Query("DELETE FROM organo_records")
    suspend fun deleteAll()

    // Cache blocks
    @Query("SELECT * FROM cached_blocks ORDER BY bloque")
    fun getCachedBlocks(): Flow<List<CachedBlockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<CachedBlockEntity>)

    @Query("DELETE FROM cached_blocks")
    suspend fun clearCachedBlocks()

    // Cache afectaciones (solo las activas en el servidor)
    @Query("SELECT * FROM cached_afectaciones ORDER BY tipo, nombre")
    fun getCachedAfectaciones(): Flow<List<CachedAfectacionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAfectaciones(items: List<CachedAfectacionEntity>)

    @Query("DELETE FROM cached_afectaciones")
    suspend fun clearCachedAfectaciones()

    // Logs de envío
    @Insert
    suspend fun insertLog(log: SyncLogEntity)

    // ponytail: tope fijo de 300 en pantalla, paginar si hace falta ver más
    @Query("SELECT * FROM sync_logs ORDER BY fecha DESC LIMIT 300")
    fun getLogs(): Flow<List<SyncLogEntity>>

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()
}

// v3 -> v4: catálogo de afectaciones, logs de envío y último error por registro, SIN borrar registros capturados.
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organo_records ADD COLUMN ultimoError TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS cached_afectaciones (nombre TEXT NOT NULL, PRIMARY KEY(nombre))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fecha INTEGER NOT NULL, " +
                "recordId INTEGER NOT NULL, bloque TEXT NOT NULL, numeroBin INTEGER NOT NULL, exito INTEGER NOT NULL, mensaje TEXT NOT NULL)"
        )
    }
}

// v4 -> v5: grupo (GF/GS) en el registro y en el catálogo de bloques, tipo de afectación. Conserva todo lo capturado
// y el catálogo ya descargado (las columnas nuevas quedan vacías hasta "Actualizar Catálogos").
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE organo_records ADD COLUMN grupo TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cached_blocks ADD COLUMN grupoForza TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cached_blocks ADD COLUMN grupoSiembra TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cached_afectaciones ADD COLUMN tipo TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [OrganoRecordEntity::class, CachedBlockEntity::class, CachedAfectacionEntity::class, SyncLogEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun organoDao(): OrganoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "organo_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
