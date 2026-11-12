package com.example.aplicativoorganolepticas.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "organo_records")
data class OrganoRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fechaRegistro: Long,
    val bloque: String,
    val numeroBin: Int,
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
    @PrimaryKey val bloque: String
)

@Entity(tableName = "cached_afectaciones")
data class CachedAfectacionEntity(
    @PrimaryKey val nombre: String
)

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

    @Insert
    suspend fun insertRecord(record: OrganoRecordEntity): Long

    @Delete
    suspend fun deleteRecord(record: OrganoRecordEntity): Int

    @Query("UPDATE organo_records SET isSynced = :synced, ultimoError = :error WHERE id = :recordId")
    suspend fun updateSyncStatus(recordId: Int, synced: Boolean, error: String)

    @Query("DELETE FROM organo_records")
    suspend fun deleteAll()

    // Cache blocks
    @Query("SELECT bloque FROM cached_blocks")
    fun getCachedBlocks(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<CachedBlockEntity>)

    @Query("DELETE FROM cached_blocks")
    suspend fun clearCachedBlocks()

    // Cache afectaciones (solo las activas en el servidor)
    @Query("SELECT nombre FROM cached_afectaciones ORDER BY nombre")
    fun getCachedAfectaciones(): Flow<List<String>>

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

@Database(
    entities = [OrganoRecordEntity::class, CachedBlockEntity::class, CachedAfectacionEntity::class, SyncLogEntity::class],
    version = 4,
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
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
