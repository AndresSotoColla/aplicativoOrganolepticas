package com.example.aplicativoorganolepticas.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.example.aplicativoorganolepticas.data.AppDatabase
import com.example.aplicativoorganolepticas.data.CachedBlockEntity
import com.example.aplicativoorganolepticas.data.OrganoRecordEntity
import com.example.aplicativoorganolepticas.data.network.NetworkModule
import com.example.aplicativoorganolepticas.utils.CsvExporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import com.example.aplicativoorganolepticas.data.network.OrganoUploadRequest

// State for each of the 5 samples
data class SampleState(
    val pesoFruta: String = "",
    val colorExterno: String = "1.0",
    val gradosBrix: String = "",
    val pruebaAcidez: Boolean = false,
    val acidez: String = "",
    val avanceTranslucidez: Int = 10,
    val categoria: String = "Especial",
    val afectaciones: List<String> = listOf("Ninguna")
)

class OrganoViewModel(private val context: Context) : ViewModel() {

    private val dao = AppDatabase.getDatabase(context).organoDao()

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return OrganoViewModel(application) as T
            }
        }
    }

    // ─── Form State: Global Fields ─────────────────────────────────────────
    var bloque by mutableStateOf("")
    var numeroBin by mutableStateOf("")
    var observaciones by mutableStateOf("")
    var globalFotoPath by mutableStateOf("")
    var globalFotoUri by mutableStateOf<Uri?>(null)

    val mejoradorOptions = listOf("Dron", "Spray Boom")
    val afectacionOptions = listOf(
        "Base Café Tolerable",
        "Base Café No Tolerable",
        "Cicatriz",
        "Cochinilla Externa",
        "Cónica",
        "Corchosis",
        "Corona Grande",
        "Corona Maltratada",
        "Corona Pequeña",
        "Corona Quemada",
        "Corona Torcida Tolerable",
        "Corona Torcida No Tolerable",
        "Coronas Múltiples",
        "Cuello Tolerable",
        "Cuello No Tolerable",
        "Daño Animales",
        "Daño Insectos",
        "Daño Mecánico",
        "Deforme",
        "Fruta con Golpe",
        "Gomosis",
        "Pedúnculo Viejo",
        "Pedúnculo Humedo Acuoso",
        "Quema de Sol Tolerable",
        "Quema de Sol No Tolerable",
        "Sobremadura",
        "Off-Color Tolerable",
        "Off-Color No Tolerable",
        "Cochinilla Interna",
        "Enfermedad",
        "Golpe de Agua Leve",
        "Golpe de Agua Severo",
        "Gomosis Interna",
        "Levadura",
        "Sin Defecto",
        "Fruta Sucia"
    )

    // ─── Form State: 5 Samples ─────────────────────────────────────────────
    var samples = mutableStateListOf(
        SampleState(), SampleState(), SampleState(), SampleState(), SampleState()
    )

    // URI for pending camera capture (which sample)
    private var pendingSampleIndex by mutableIntStateOf(-1)
    var pendingSampleIndexForPermission by mutableIntStateOf(-1)  // set before requesting permission
    var pendingPhotoUri by mutableStateOf<Uri?>(null)
        private set


    // ─── Database ──────────────────────────────────────────────────────────
    private val _records = dao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val records: StateFlow<List<OrganoRecordEntity>> = _records

    val availableBlocks: StateFlow<List<String>> = dao.getCachedBlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        syncBlocks()
    }

    // ─── Sample Updates ────────────────────────────────────────────────────
    fun updateSamplePeso(index: Int, value: String) {
        samples[index] = samples[index].copy(pesoFruta = value)
    }
    fun updateSampleColor(index: Int, value: String) {
        samples[index] = samples[index].copy(colorExterno = value)
    }
    fun updateSampleBrix(index: Int, value: String) {
        samples[index] = samples[index].copy(gradosBrix = value)
    }
    fun updateSamplePruebaAcidez(index: Int, value: Boolean) {
        samples[index] = samples[index].copy(pruebaAcidez = value, acidez = if (!value) "" else samples[index].acidez)
    }
    fun updateSampleAcidez(index: Int, value: String) {
        samples[index] = samples[index].copy(acidez = value)
    }
    fun updateSampleTranslucidez(index: Int, value: Int) {
        samples[index] = samples[index].copy(avanceTranslucidez = value)
    }
    fun updateSampleCategoria(index: Int, value: String) {
        samples[index] = samples[index].copy(categoria = value)
    }
    fun updateSampleAfectacionToggle(index: Int, item: String) {
        val current = samples[index].afectaciones.toMutableList()
        if (item == "Ninguna") {
            current.clear()
            current.add("Ninguna")
        } else {
            current.remove("Ninguna")
            if (current.contains(item)) {
                current.remove(item)
                if (current.isEmpty()) current.add("Ninguna")
            } else {
                current.add(item)
            }
        }
        samples[index] = samples[index].copy(afectaciones = current)
    }

    // ─── Photo Handling ────────────────────────────────────────────────────
    fun prepareCameraForGlobal(): Uri {
        val imageDir = File(context.externalCacheDir ?: context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File.createTempFile("organo_global_", ".jpg", imageDir)
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.aplicativoorganolepticas.fileprovider",
            imageFile
        )
        pendingPhotoUri = uri
        globalFotoPath = imageFile.absolutePath
        return uri
    }

    // Deprecated for per-sample, but kept generic for permissions if needed
    fun prepareCameraForSample(index: Int): Uri = prepareCameraForGlobal()

    fun onPhotoTaken(success: Boolean) {
        if (success) {
            globalFotoUri = pendingPhotoUri
        }
        pendingPhotoUri = null
    }

    // Multi-select helpers are now handled per-sample in updateSampleAfectacionToggle

    // ─── Network ───────────────────────────────────────────────────────────
    fun syncBlocks() {
        viewModelScope.launch {
            try {
                val remoteCronograma = NetworkModule.apiService.getCronograma()
                if (remoteCronograma.isNotEmpty()) {
                    val entities = remoteCronograma.map { CachedBlockEntity(it.bloque) }
                    dao.clearCachedBlocks()
                    dao.insertBlocks(entities)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ─── Save ──────────────────────────────────────────────────────────────
    fun saveRecord(onResult: (String?) -> Unit) {
        val binNum = numeroBin.toIntOrNull()
        val isBlockValid = (bloque.startsWith("SC") || bloque.startsWith("PC")) && 
                           bloque.drop(2).all { it.isDigit() } && 
                           bloque.length == 8
        if (!isBlockValid) { onResult("El bloque debe tener exactamente 8 caracteres (Eje: PC123456)"); return }
        if (binNum == null || binNum < 1 || binNum > 200) { onResult("El número de Bin debe ser entre 1 y 200"); return }

        // Validate weight <= 5000
        samples.forEachIndexed { index, sample ->
            val peso = sample.pesoFruta.toDoubleOrNull() ?: 0.0
            if (peso > 5000) {
                onResult("El peso de la muestra ${index + 1} no puede ser mayor a 5000g")
                return
            }
        }

        val s = samples
        viewModelScope.launch {
            val record = OrganoRecordEntity(
                fechaRegistro = System.currentTimeMillis(),
                bloque = bloque,
                numeroBin = binNum,
                fotoPath = globalFotoPath,
                observaciones = observaciones,
                // Muestra 1
                m1PesoFruta = s[0].pesoFruta.toDoubleOrNull() ?: 0.0,
                m1ColorExterno = s[0].colorExterno,
                m1GradosBrix = s[0].gradosBrix.toDoubleOrNull() ?: 0.0,
                m1PruebaAcidez = s[0].pruebaAcidez,
                m1Acidez = s[0].acidez.toDoubleOrNull() ?: 0.0,
                m1AvanceTranslucidez = s[0].avanceTranslucidez,
                m1Categoria = s[0].categoria,
                m1Afectaciones = s[0].afectaciones.joinToString(", "),
                // Muestra 2
                m2PesoFruta = s[1].pesoFruta.toDoubleOrNull() ?: 0.0,
                m2ColorExterno = s[1].colorExterno,
                m2GradosBrix = s[1].gradosBrix.toDoubleOrNull() ?: 0.0,
                m2PruebaAcidez = s[1].pruebaAcidez,
                m2Acidez = s[1].acidez.toDoubleOrNull() ?: 0.0,
                m2AvanceTranslucidez = s[1].avanceTranslucidez,
                m2Categoria = s[1].categoria,
                m2Afectaciones = s[1].afectaciones.joinToString(", "),
                // Muestra 3
                m3PesoFruta = s[2].pesoFruta.toDoubleOrNull() ?: 0.0,
                m3ColorExterno = s[2].colorExterno,
                m3GradosBrix = s[2].gradosBrix.toDoubleOrNull() ?: 0.0,
                m3PruebaAcidez = s[2].pruebaAcidez,
                m3Acidez = s[2].acidez.toDoubleOrNull() ?: 0.0,
                m3AvanceTranslucidez = s[2].avanceTranslucidez,
                m3Categoria = s[2].categoria,
                m3Afectaciones = s[2].afectaciones.joinToString(", "),
                // Muestra 4
                m4PesoFruta = s[3].pesoFruta.toDoubleOrNull() ?: 0.0,
                m4ColorExterno = s[3].colorExterno,
                m4GradosBrix = s[3].gradosBrix.toDoubleOrNull() ?: 0.0,
                m4PruebaAcidez = s[3].pruebaAcidez,
                m4Acidez = s[3].acidez.toDoubleOrNull() ?: 0.0,
                m4AvanceTranslucidez = s[3].avanceTranslucidez,
                m4Categoria = s[3].categoria,
                m4Afectaciones = s[3].afectaciones.joinToString(", "),
                // Muestra 5
                m5PesoFruta = s[4].pesoFruta.toDoubleOrNull() ?: 0.0,
                m5ColorExterno = s[4].colorExterno,
                m5GradosBrix = s[4].gradosBrix.toDoubleOrNull() ?: 0.0,
                m5PruebaAcidez = s[4].pruebaAcidez,
                m5Acidez = s[4].acidez.toDoubleOrNull() ?: 0.0,
                m5AvanceTranslucidez = s[4].avanceTranslucidez,
                m5Categoria = s[4].categoria,
                m5Afectaciones = s[4].afectaciones.joinToString(", ")
            )
            dao.insertRecord(record)
            clearForm()
            onResult(null)
        }
    }

    fun deleteRecord(record: OrganoRecordEntity) {
        viewModelScope.launch { dao.deleteRecord(record) }
    }

    fun deleteAllRecords() {
        viewModelScope.launch { dao.deleteAll() }
    }

    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            CsvExporter.exportAndShare(context, records.value)
        }
    }

    // ─── Synchronization ──────────────────────────────────────────────────
    var isUploading by mutableStateOf(false)
        private set

    fun uploadRecord(record: OrganoRecordEntity, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            isUploading = true
            try {
                val fotoBase64 = record.fotoPath.takeIf { it.isNotEmpty() }?.let { getScaledBase64(it) }

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val usuario = "${Build.MANUFACTURER} ${Build.MODEL}"

                val request = OrganoUploadRequest(
                    fecha_registro = sdf.format(Date(record.fechaRegistro)),
                    bloque = record.bloque,
                    numero_bin = record.numeroBin,
                    observaciones = record.observaciones,
                    usuario = usuario,
                    fotografia = fotoBase64,
                    // M1
                    m1_peso = record.m1PesoFruta, m1_color = record.m1ColorExterno, m1_brix = record.m1GradosBrix, m1_acidez = record.m1Acidez, m1_translucidez = record.m1AvanceTranslucidez, m1_categoria = record.m1Categoria, m1_afectaciones = record.m1Afectaciones,
                    // M2
                    m2_peso = record.m2PesoFruta, m2_color = record.m2ColorExterno, m2_brix = record.m2GradosBrix, m2_acidez = record.m2Acidez, m2_translucidez = record.m2AvanceTranslucidez, m2_categoria = record.m2Categoria, m2_afectaciones = record.m2Afectaciones,
                    // M3
                    m3_peso = record.m3PesoFruta, m3_color = record.m3ColorExterno, m3_brix = record.m3GradosBrix, m3_acidez = record.m3Acidez, m3_translucidez = record.m3AvanceTranslucidez, m3_categoria = record.m3Categoria, m3_afectaciones = record.m3Afectaciones,
                    // M4
                    m4_peso = record.m4PesoFruta, m4_color = record.m4ColorExterno, m4_brix = record.m4GradosBrix, m4_acidez = record.m4Acidez, m4_translucidez = record.m4AvanceTranslucidez, m4_categoria = record.m4Categoria, m4_afectaciones = record.m4Afectaciones,
                    // M5
                    m5_peso = record.m5PesoFruta, m5_color = record.m5ColorExterno, m5_brix = record.m5GradosBrix, m5_acidez = record.m5Acidez, m5_translucidez = record.m5AvanceTranslucidez, m5_categoria = record.m5Categoria, m5_afectaciones = record.m5Afectaciones
                )

                val response = NetworkModule.apiService.uploadRecord(request)
                if (response.status == "ok") {
                    dao.updateSyncStatus(record.id, true)
                    onResult(null)
                } else {
                    onResult(response.error ?: "Error desconocido en el servidor")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("Error de red: ${e.message}")
            } finally {
                isUploading = false
            }
        }
    }

    fun uploadAllUnsyncedRecords(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val pending = records.value.filter { !it.isSynced }
            if (pending.isEmpty()) {
                onResult("No hay registros pendientes")
                return@launch
            }

            isUploading = true
            var successCount = 0
            var errorCount = 0

            pending.forEach { record ->
                try {
                    val fotoBase64 = record.fotoPath.takeIf { it.isNotEmpty() }?.let { getScaledBase64(it) }
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val usuario = "${Build.MANUFACTURER} ${Build.MODEL}"

                    val request = OrganoUploadRequest(
                        fecha_registro = sdf.format(Date(record.fechaRegistro)),
                        bloque = record.bloque,
                        numero_bin = record.numeroBin,
                        observaciones = record.observaciones,
                        usuario = usuario,
                        fotografia = fotoBase64,
                        m1_peso = record.m1PesoFruta, m1_color = record.m1ColorExterno, m1_brix = record.m1GradosBrix, m1_acidez = record.m1Acidez, m1_translucidez = record.m1AvanceTranslucidez, m1_categoria = record.m1Categoria, m1_afectaciones = record.m1Afectaciones,
                        m2_peso = record.m2PesoFruta, m2_color = record.m2ColorExterno, m2_brix = record.m2GradosBrix, m2_acidez = record.m2Acidez, m2_translucidez = record.m2AvanceTranslucidez, m2_categoria = record.m2Categoria, m2_afectaciones = record.m2Afectaciones,
                        m3_peso = record.m3PesoFruta, m3_color = record.m3ColorExterno, m3_brix = record.m3GradosBrix, m3_acidez = record.m3Acidez, m3_translucidez = record.m3AvanceTranslucidez, m3_categoria = record.m3Categoria, m3_afectaciones = record.m3Afectaciones,
                        m4_peso = record.m4PesoFruta, m4_color = record.m4ColorExterno, m4_brix = record.m4GradosBrix, m4_acidez = record.m4Acidez, m4_translucidez = record.m4AvanceTranslucidez, m4_categoria = record.m4Categoria, m4_afectaciones = record.m4Afectaciones,
                        m5_peso = record.m5PesoFruta, m5_color = record.m5ColorExterno, m5_brix = record.m5GradosBrix, m5_acidez = record.m5Acidez, m5_translucidez = record.m5AvanceTranslucidez, m5_categoria = record.m5Categoria, m5_afectaciones = record.m5Afectaciones
                    )

                    val response = NetworkModule.apiService.uploadRecord(request)
                    if (response.status == "ok") {
                        dao.updateSyncStatus(record.id, true)
                        successCount++
                    } else {
                        errorCount++
                    }
                } catch (e: Exception) {
                    errorCount++
                }
            }
            isUploading = false
            onResult("Sincronización terminada: $successCount exitosos, $errorCount errores")
        }
    }

    private fun clearForm() {
        bloque = ""
        numeroBin = ""
        observaciones = ""
        globalFotoPath = ""
        globalFotoUri = null
        for (i in 0 until 5) samples[i] = SampleState()
    }

    private fun getScaledBase64(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)

            var scale = 1
            while (options.outWidth / scale / 2 >= 1024 && options.outHeight / scale / 2 >= 1024) {
                scale *= 2
            }

            val decodeOptions = BitmapFactory.Options()
            decodeOptions.inSampleSize = scale
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
