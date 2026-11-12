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
import com.example.aplicativoorganolepticas.data.CachedAfectacionEntity
import com.example.aplicativoorganolepticas.data.CachedBlockEntity
import com.example.aplicativoorganolepticas.data.SyncLogEntity
import com.example.aplicativoorganolepticas.data.OrganoRecordEntity
import com.example.aplicativoorganolepticas.data.network.NetworkModule
import com.example.aplicativoorganolepticas.utils.CsvExporter
import androidx.room.withTransaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** Estado de envío de un registro: "Enviado", "Error" o "Pendiente". */
fun estadoDe(r: OrganoRecordEntity): String = when {
    r.isSynced -> "Enviado"
    r.ultimoError.isNotEmpty() -> "Error"
    else -> "Pendiente"
}

class OrganoViewModel(private val context: Context) : ViewModel() {

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.organoDao()

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

    // "Ninguna" es local (el servidor la ignora); el resto viene del catálogo de afectaciones activas.
    val afectacionOptions: StateFlow<List<String>> = dao.getCachedAfectaciones()
        .map { listOf("Ninguna") + it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Ninguna"))

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

    val logs: StateFlow<List<SyncLogEntity>> = dao.getLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // ─── Catálogos (bloques y afectaciones activos desde el servidor) ─────
    var isUpdatingCatalogs by mutableStateOf(false)
        private set

    fun actualizarCatalogos(onResult: (String) -> Unit) {
        if (NetworkModule.API_KEY.isBlank()) {
            onResult("Falta la clave API (ORGANO_API_KEY) en la compilación de la app")
            return
        }
        viewModelScope.launch {
            isUpdatingCatalogs = true
            try {
                val cat = NetworkModule.apiService.getCatalogos(NetworkModule.API_KEY)
                // Respuesta vacía = problema del servidor: no se borra el catálogo que ya hay en el equipo.
                if (cat.bloques.isEmpty() || cat.afectaciones.isEmpty()) {
                    onResult("El servidor no devolvió bloques o afectaciones; se conserva el catálogo actual")
                    return@launch
                }
                database.withTransaction {
                    dao.clearCachedBlocks()
                    dao.insertBlocks(cat.bloques.map { CachedBlockEntity(it) })
                    dao.clearCachedAfectaciones()
                    dao.insertAfectaciones(cat.afectaciones.map { CachedAfectacionEntity(it) })
                }
                onResult("Catálogos actualizados: ${cat.bloques.size} bloques, ${cat.afectaciones.size} afectaciones")
            } catch (e: retrofit2.HttpException) {
                onResult(
                    if (e.code() == 401 || e.code() == 503) "Clave API inválida o no configurada en el servidor (HTTP ${e.code()})"
                    else "Error HTTP ${e.code()} al actualizar catálogos"
                )
            } catch (e: Exception) {
                onResult("Sin conexión: ${e.message}")
            } finally {
                isUpdatingCatalogs = false
            }
        }
    }

    // ─── Save ──────────────────────────────────────────────────────────────
    fun saveRecord(onResult: (String?) -> Unit) {
        val binNum = numeroBin.toIntOrNull()
        val isBlockValid = availableBlocks.value.contains(bloque)
        if (!isBlockValid) { onResult("El bloque no existe en el catálogo. Actualiza catálogos desde Inicio"); return }
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

    /** estado: null = todos, o "Enviado" / "Pendiente" / "Error" (ver estadoDe). */
    fun exportToCsv(context: Context, estado: String? = null) {
        viewModelScope.launch {
            val lista = records.value.filter { estado == null || estadoDe(it) == estado }
            CsvExporter.exportAndShare(context, lista, estado ?: "Todos")
        }
    }

    // ─── Synchronization ──────────────────────────────────────────────────
    var isUploading by mutableStateOf(false)
        private set

    fun uploadRecord(record: OrganoRecordEntity, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            isUploading = true
            try { onResult(send(record)) } finally { isUploading = false }
        }
    }

    /** Envía todo lo no enviado: pendientes y los que tuvieron error. */
    fun uploadAllUnsyncedRecords(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val pending = records.value.filter { !it.isSynced }
            if (pending.isEmpty()) {
                onResult("No hay registros pendientes")
                return@launch
            }
            isUploading = true
            try {
                val errores = pending.count { send(it) != null }
                onResult("Envío terminado: ${pending.size - errores} exitosos, $errores con error")
            } finally {
                isUploading = false
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch { dao.clearLogs() }
    }

    /** Envía un registro, guarda su estado y deja log. Devuelve null si salió bien o el mensaje de error. */
    private suspend fun send(record: OrganoRecordEntity): String? {
        val error: String? = try {
            val response = NetworkModule.apiService.uploadRecord(buildRequest(record))
            if (response.status == "ok") null else (response.error ?: "Error desconocido en el servidor")
        } catch (e: retrofit2.HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (ex: Exception) { null }
            android.util.Log.e("OrganoViewModel", "HTTP Error Body: $errorBody", e)
            "Error HTTP ${e.code()}: ${errorBody?.take(300) ?: e.message()}"
        } catch (e: Exception) {
            "Error de red: ${e.message}"
        }
        dao.updateSyncStatus(record.id, error == null, error ?: "")
        dao.insertLog(
            SyncLogEntity(
                fecha = System.currentTimeMillis(),
                recordId = record.id,
                bloque = record.bloque,
                numeroBin = record.numeroBin,
                exito = error == null,
                mensaje = error ?: "Enviado correctamente"
            )
        )
        return error
    }

    private suspend fun buildRequest(record: OrganoRecordEntity): OrganoUploadRequest {
        val fotoBase64 = withContext(Dispatchers.IO) {
            record.fotoPath.takeIf { it.isNotEmpty() }?.let { getScaledBase64(it) }
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return OrganoUploadRequest(
            fecha_registro = sdf.format(Date(record.fechaRegistro)),
            bloque = record.bloque,
            numero_bin = record.numeroBin.toString(),
            observaciones = record.observaciones,
            usuario = "${Build.MANUFACTURER} ${Build.MODEL}",
            fotografia = fotoBase64,
            m1_peso = record.m1PesoFruta, m1_color = record.m1ColorExterno, m1_brix = record.m1GradosBrix, m1_acidez = record.m1Acidez, m1_translucidez = record.m1AvanceTranslucidez.toString(), m1_categoria = record.m1Categoria, m1_afectaciones = record.m1Afectaciones,
            m2_peso = record.m2PesoFruta, m2_color = record.m2ColorExterno, m2_brix = record.m2GradosBrix, m2_acidez = record.m2Acidez, m2_translucidez = record.m2AvanceTranslucidez.toString(), m2_categoria = record.m2Categoria, m2_afectaciones = record.m2Afectaciones,
            m3_peso = record.m3PesoFruta, m3_color = record.m3ColorExterno, m3_brix = record.m3GradosBrix, m3_acidez = record.m3Acidez, m3_translucidez = record.m3AvanceTranslucidez.toString(), m3_categoria = record.m3Categoria, m3_afectaciones = record.m3Afectaciones,
            m4_peso = record.m4PesoFruta, m4_color = record.m4ColorExterno, m4_brix = record.m4GradosBrix, m4_acidez = record.m4Acidez, m4_translucidez = record.m4AvanceTranslucidez.toString(), m4_categoria = record.m4Categoria, m4_afectaciones = record.m4Afectaciones,
            m5_peso = record.m5PesoFruta, m5_color = record.m5ColorExterno, m5_brix = record.m5GradosBrix, m5_acidez = record.m5Acidez, m5_translucidez = record.m5AvanceTranslucidez.toString(), m5_categoria = record.m5Categoria, m5_afectaciones = record.m5Afectaciones
        )
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
