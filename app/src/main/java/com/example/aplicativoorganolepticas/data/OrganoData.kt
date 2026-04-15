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
    val mejoradores: String,               // CSV multi-select
    val finProtocoloMaduracion: String,    // "Sí" / "No"
    val avanceTranslucidez: Int,           // 10, 20, ..., 100
    val categoria: String,                 // Especial / Industria / Jugo
    val afectaciones: String,              // CSV multi-select
    // Muestra 1
    val m1PesoFruta: Double = 0.0,
    val m1ColorExterno: String = "",
    val m1FotoPath: String = "",
    val m1GradosBrix: Double = 0.0,
    val m1PruebaAcidez: Boolean = false,
    val m1Acidez: Double = 0.0,
    // Muestra 2
    val m2PesoFruta: Double = 0.0,
    val m2ColorExterno: String = "",
    val m2FotoPath: String = "",
    val m2GradosBrix: Double = 0.0,
    val m2PruebaAcidez: Boolean = false,
    val m2Acidez: Double = 0.0,
    // Muestra 3
    val m3PesoFruta: Double = 0.0,
    val m3ColorExterno: String = "",
    val m3FotoPath: String = "",
    val m3GradosBrix: Double = 0.0,
    val m3PruebaAcidez: Boolean = false,
    val m3Acidez: Double = 0.0,
    // Muestra 4
    val m4PesoFruta: Double = 0.0,
    val m4ColorExterno: String = "",
    val m4FotoPath: String = "",
    val m4GradosBrix: Double = 0.0,
    val m4PruebaAcidez: Boolean = false,
    val m4Acidez: Double = 0.0,
    // Muestra 5
    val m5PesoFruta: Double = 0.0,
    val m5ColorExterno: String = "",
    val m5FotoPath: String = "",
    val m5GradosBrix: Double = 0.0,
    val m5PruebaAcidez: Boolean = false,
    val m5Acidez: Double = 0.0
)

@Entity(tableName = "cached_blocks")
data class CachedBlockEntity(
    @PrimaryKey val bloque: String
)

@Dao
interface OrganoDao {
    @Query("SELECT * FROM organo_records ORDER BY fechaRegistro DESC")
    fun getAllRecords(): Flow<List<OrganoRecordEntity>>

    @Insert
    suspend fun insertRecord(record: OrganoRecordEntity): Long

    @Delete
    suspend fun deleteRecord(record: OrganoRecordEntity): Int

    @Query("DELETE FROM organo_records")
    suspend fun deleteAll()

    // Cache blocks
    @Query("SELECT bloque FROM cached_blocks")
    fun getCachedBlocks(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<CachedBlockEntity>)

    @Query("DELETE FROM cached_blocks")
    suspend fun clearCachedBlocks()
}

@Database(entities = [OrganoRecordEntity::class, CachedBlockEntity::class], version = 1, exportSchema = false)
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
