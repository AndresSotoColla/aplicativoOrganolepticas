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
    val isSynced: Boolean = false
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

    @Query("UPDATE organo_records SET isSynced = :synced WHERE id = :recordId")
    suspend fun updateSyncStatus(recordId: Int, synced: Boolean)

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

@Database(entities = [OrganoRecordEntity::class, CachedBlockEntity::class], version = 3, exportSchema = false)
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
