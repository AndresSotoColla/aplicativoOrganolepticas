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
        "Daño mecánico",
        "Pudrición",
        "Sobremadurez",
        "Mancha",
        "Deshidratación",
        "Quemadura solar",
        "Ninguna"
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
        selectedAfectaciones.add("Ninguna")
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

    // ─── Multi-select helpers ──────────────────────────────────────────────
    fun onMejoradorToggle(item: String) {
        if (selectedMejoradores.contains(item)) selectedMejoradores.remove(item)
        else selectedMejoradores.add(item)
    }

    fun onAfectacionToggle(item: String) {
        if (item == "Ninguna") {
            selectedAfectaciones.clear()
            selectedAfectaciones.add("Ninguna")
        } else {
            selectedAfectaciones.remove("Ninguna")
            if (selectedAfectaciones.contains(item)) {
                selectedAfectaciones.remove(item)
                if (selectedAfectaciones.isEmpty()) selectedAfectaciones.add("Ninguna")
            } else {
                selectedAfectaciones.add(item)
            }
        }
    }

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
        if (bloque.isBlank()) { onResult("Ingrese un bloque válido"); return }
        if (binNum == null || binNum < 1 || binNum > 200) { onResult("El número de Bin debe ser entre 1 y 200"); return }

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

    private fun clearForm() {
        bloque = ""
        numeroBin = ""
        observaciones = ""
        globalFotoPath = ""
        globalFotoUri = null
        for (i in 0 until 5) samples[i] = SampleState()
    }
}
