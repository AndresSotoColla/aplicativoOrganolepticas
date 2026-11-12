package com.example.aplicativoorganolepticas.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.withTransaction
import com.example.aplicativoorganolepticas.data.*
import com.example.aplicativoorganolepticas.data.network.NetworkModule
import com.example.aplicativoorganolepticas.data.network.OrganoUploadRequest
import com.example.aplicativoorganolepticas.utils.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Estado de cada una de las 5 muestras en el formulario
data class SampleState(
    val pesoFruta: String = "",
    val colorExterno: String = "1.0",
    val gradosBrix: String = "",
    val pruebaAcidez: Boolean = false,
    val acidez: String = "",
    val avanceTranslucidez: Int = 10,
    val categoria: String = "Especial",
    val afectaciones: List<String> = emptyList()   // vacía = sin afectación
) {
    val tienePeso get() = (pesoFruta.toDoubleOrNull() ?: 0.0) > 0.0
}

private fun esTipoNinguno(a: CachedAfectacionEntity) = a.tipo.contains("ningun", ignoreCase = true) || esSinAfectacion(a.nombre)

/** Estado de envío de un registro: "Enviado", "Error" o "Pendiente". */
fun estadoDe(r: OrganoRecordEntity): String = when {
    r.isSynced -> "Enviado"
    r.ultimoError.isNotEmpty() -> "Error"
    else -> "Pendiente"
}

/** Número legible: 0 = vacío ("—"), sin ".0" si es entero. */
fun numTxt(d: Double): String = when {
    d == 0.0 -> "—"
    d % 1.0 == 0.0 -> d.toLong().toString()
    else -> d.toString()
}

/** Campos comparables de un registro (etiqueta, valor legible), en el orden del formulario. */
fun camposDe(r: OrganoRecordEntity): List<Pair<String, String>> =
    listOf(
        "Bloque" to r.bloque,
        "Grupo" to r.grupo.ifEmpty { "—" },
        "Bin" to "${r.numeroBin}",
        "Observaciones" to r.observaciones.ifEmpty { "—" },
        "Foto" to if (r.fotoPath.isEmpty()) "Sin foto" else "Foto ${File(r.fotoPath).name}"
    ) + r.muestras().flatMapIndexed { i, m ->
        val p = "M${i + 1}"
        listOf(
            "$p peso (g)" to numTxt(m.peso),
            "$p color externo" to m.color,
            "$p brix" to numTxt(m.brix),
            "$p prueba acidez" to if (m.pruebaAcidez) "Sí" else "No",
            "$p gasto NaOH (ml)" to numTxt(m.acidez),
            "$p translucidez" to "${m.translucidez}%",
            "$p categoría" to m.categoria,
            "$p afectaciones" to m.afectaciones
        )
    }

/** Lista "Campo: antes → después" de lo que cambió entre dos versiones de un registro. */
fun cambiosEntre(antes: OrganoRecordEntity, despues: OrganoRecordEntity): List<String> =
    camposDe(antes).zip(camposDe(despues))
        .filter { (a, d) -> a.second != d.second }
        .map { (a, d) -> "${a.first}: ${a.second} → ${d.second}" }

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

    // ─── Formulario ────────────────────────────────────────────────────────
    var bloque by mutableStateOf("")
        private set
    var grupo by mutableStateOf("")
    var numeroBin by mutableStateOf("")
    var observaciones by mutableStateOf("")
    var globalFotoPath by mutableStateOf("")
        private set
    val samples = mutableStateListOf(SampleState(), SampleState(), SampleState(), SampleState(), SampleState())

    private var pendingPhotoPath: String? = null

    // ─── Base de datos ─────────────────────────────────────────────────────
    val records: StateFlow<List<OrganoRecordEntity>> = dao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blocksInfo: StateFlow<List<CachedBlockEntity>> = dao.getCachedBlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableBlocks: StateFlow<List<String>> = blocksInfo.map { l -> l.map { it.bloque } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Catálogo de afectaciones externas/internas (sin la opción "sin afectación", que va aparte y de primera). */
    val afectacionesCatalogo: StateFlow<List<CachedAfectacionEntity>> = dao.getCachedAfectaciones()
        .map { l -> l.filterNot { esTipoNinguno(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nombre de la opción "sin afectación": la de tipo Ninguno del catálogo ("Sin defecto"). */
    val opcionNinguna: StateFlow<String> = dao.getCachedAfectaciones()
        .map { l -> l.firstOrNull { it.tipo.contains("ningun", ignoreCase = true) }?.nombre ?: "Sin defecto" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Sin defecto")

    val logs: StateFlow<List<SyncLogEntity>> = dao.getLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun infoBloque(b: String): CachedBlockEntity? = blocksInfo.value.firstOrNull { it.bloque == b }

    /** Al cambiar el bloque se propone su grupo: el GF y, si no tiene, el grupo siembra. El usuario puede cambiarlo. */
    fun onBloqueChange(v: String) {
        bloque = v
        grupo = infoBloque(v)?.let { it.grupoForza.ifEmpty { it.grupoSiembra } } ?: ""
    }

    // ─── Muestras ──────────────────────────────────────────────────────────
    fun updateSamplePeso(index: Int, value: String) { samples[index] = samples[index].copy(pesoFruta = value) }
    fun updateSampleColor(index: Int, value: String) { samples[index] = samples[index].copy(colorExterno = value) }
    fun updateSampleBrix(index: Int, value: String) { samples[index] = samples[index].copy(gradosBrix = value) }
    fun updateSamplePruebaAcidez(index: Int, value: Boolean) {
        samples[index] = samples[index].copy(pruebaAcidez = value, acidez = if (!value) "" else samples[index].acidez)
    }
    fun updateSampleAcidez(index: Int, value: String) { samples[index] = samples[index].copy(acidez = value) }
    fun updateSampleTranslucidez(index: Int, value: Int) { samples[index] = samples[index].copy(avanceTranslucidez = value) }
    fun updateSampleCategoria(index: Int, value: String) { samples[index] = samples[index].copy(categoria = value) }

    /** "Sin afectación" excluye a las demás: marcarla limpia la lista; marcar una afectación la desmarca. */
    fun updateSampleAfectacionToggle(index: Int, item: String) {
        val current = samples[index].afectaciones.filterNot { esSinAfectacion(it) }.toMutableList()
        if (esSinAfectacion(item) || item == opcionNinguna.value) current.clear()
        else if (!current.remove(item)) current.add(item)
        samples[index] = samples[index].copy(afectaciones = current)
    }

    // ─── Foto ──────────────────────────────────────────────────────────────
    fun prepareCameraForGlobal(): Uri {
        val imageDir = File(context.externalCacheDir ?: context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File.createTempFile("organo_global_", ".jpg", imageDir)
        pendingPhotoPath = imageFile.absolutePath
        return FileProvider.getUriForFile(context, "com.example.aplicativoorganolepticas.fileprovider", imageFile)
    }

    fun onPhotoTaken(success: Boolean) {
        val path = pendingPhotoPath ?: return
        if (success) globalFotoPath = path else File(path).delete()
        pendingPhotoPath = null
    }

    // ─── Formulario <-> registro ───────────────────────────────────────────
    private fun formToEntity(base: OrganoRecordEntity?): OrganoRecordEntity {
        val antes = base?.muestras()
        val ms = samples.mapIndexed { i, it ->
            Muestra(
                peso = it.pesoFruta.toDoubleOrNull() ?: 0.0,
                color = it.colorExterno,
                brix = it.gradosBrix.toDoubleOrNull() ?: 0.0,
                pruebaAcidez = it.pruebaAcidez,
                acidez = it.acidez.toDoubleOrNull() ?: 0.0,
                translucidez = it.avanceTranslucidez,
                categoria = it.categoria,
                // Sin afectación: se guarda el nombre vigente; al editar se conserva el que ya tenía ("Ninguna" en datos viejos)
                afectaciones = if (it.afectaciones.isNotEmpty()) it.afectaciones.joinToString(", ")
                               else antes?.get(i)?.afectaciones?.takeIf { a -> esSinAfectacion(a) } ?: opcionNinguna.value
            )
        }
        return (base ?: OrganoRecordEntity(fechaRegistro = System.currentTimeMillis(), bloque = "", numeroBin = 0))
            .copy(
                bloque = bloque,
                grupo = grupo.trim().uppercase(),
                numeroBin = numeroBin.toIntOrNull() ?: 0,
                fotoPath = globalFotoPath,
                observaciones = observaciones
            )
            .conMuestras(ms)
    }

    private fun loadIntoForm(e: OrganoRecordEntity) {
        fun txt(d: Double) = if (d == 0.0) "" else numTxt(d)
        bloque = e.bloque
        grupo = e.grupo
        numeroBin = if (e.numeroBin == 0) "" else e.numeroBin.toString()
        observaciones = e.observaciones
        globalFotoPath = e.fotoPath
        e.muestras().forEachIndexed { i, m ->
            samples[i] = SampleState(
                pesoFruta = txt(m.peso),
                colorExterno = m.color.ifEmpty { "1.0" },
                gradosBrix = txt(m.brix),
                pruebaAcidez = m.pruebaAcidez,
                acidez = txt(m.acidez),
                avanceTranslucidez = m.translucidez,
                categoria = m.categoria,
                afectaciones = m.afectaciones.split(",").map { it.trim() }.filterNot { esSinAfectacion(it) }
            )
        }
    }

    private fun clearForm() {
        bloque = ""; grupo = ""; numeroBin = ""; observaciones = ""; globalFotoPath = ""
        for (i in 0 until 5) samples[i] = SampleState()
    }

    /** null si el formulario es válido; si no, el mensaje para el usuario. */
    private fun validar(): String? {
        val binNum = numeroBin.toIntOrNull()
        if (!availableBlocks.value.contains(bloque)) return "El bloque no existe en el catálogo. Actualiza catálogos desde Inicio"
        if (binNum == null || binNum < 1 || binNum > 200) return "El número de Bin debe ser entre 1 y 200"
        if (samples.none { it.tienePeso }) return "Ingresa el peso de al menos una muestra (las muestras sin peso no se cargan)"
        samples.forEachIndexed { i, s ->
            if ((s.pesoFruta.toDoubleOrNull() ?: 0.0) > 5000) return "El peso de la muestra ${i + 1} no puede ser mayor a 5000 g"
        }
        return null
    }

    fun saveRecord(onResult: (String?) -> Unit) {
        validar()?.let { onResult(it); return }
        val record = formToEntity(null).copy(fechaRegistro = System.currentTimeMillis())
        viewModelScope.launch {
            dao.insertRecord(record)
            clearForm()
            onResult(null)
        }
    }

    // ─── Edición (solo registros no enviados) ──────────────────────────────
    var editOriginal by mutableStateOf<OrganoRecordEntity?>(null)
        private set
    val isEditing get() = editOriginal != null

    // Formulario nuevo que estaba a medias al entrar a editar: se restaura al salir de la edición.
    private var borrador: OrganoRecordEntity? = null

    fun startEdit(r: OrganoRecordEntity) {
        if (editOriginal == null) borrador = formToEntity(null)
        editOriginal = r
        loadIntoForm(r)
    }

    /** Cambios del formulario respecto al registro original ("Campo: antes → después"). */
    fun cambiosPendientes(): List<String> = editOriginal?.let { cambiosEntre(it, formToEntity(it)) } ?: emptyList()

    fun cancelEdit() {
        editOriginal = null
        borrador?.let { loadIntoForm(it) } ?: clearForm()
        borrador = null
    }

    fun saveEdit(onResult: (String?) -> Unit) {
        val orig = editOriginal ?: return
        validar()?.let { onResult(it); return }
        if (records.value.firstOrNull { it.id == orig.id }?.isSynced == true) {
            onResult("Este registro ya se envió; no se puede editar"); return
        }
        // Editado = vuelve a quedar pendiente (se borra el último error).
        val nuevo = formToEntity(orig).copy(ultimoError = "")
        viewModelScope.launch {
            dao.updateRecord(nuevo)
            cancelEdit()
            onResult(null)
        }
    }

    // ─── Borrado ───────────────────────────────────────────────────────────
    fun deleteRecord(record: OrganoRecordEntity) {
        viewModelScope.launch {
            dao.deleteRecord(record)
            withContext(Dispatchers.IO) { if (record.fotoPath.isNotEmpty()) File(record.fotoPath).delete() }
        }
    }

    /** Borra registros (todos enviados), sus fotos y los logs. Se niega si queda algo sin enviar. */
    fun borrarCache(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val todos = dao.getAllRecordsOnce()
            val sinEnviar = todos.count { !it.isSynced }
            if (sinEnviar > 0) {
                onResult("Hay $sinEnviar registro(s) sin enviar: envíalos o elimínalos antes de borrar la caché")
                return@launch
            }
            database.withTransaction {
                dao.deleteAll()
                dao.clearLogs()
            }
            withContext(Dispatchers.IO) { todos.forEach { if (it.fotoPath.isNotEmpty()) File(it.fotoPath).delete() } }
            onResult("Caché borrada: ${todos.size} registro(s) eliminados del equipo")
        }
    }

    // ─── Descargas ─────────────────────────────────────────────────────────
    /** estado: null = todos, o "Enviado" / "Pendiente" / "Error" (ver estadoDe). */
    fun exportToCsv(context: Context, estado: String? = null) {
        viewModelScope.launch {
            val lista = records.value.filter { estado == null || estadoDe(it) == estado }
            CsvExporter.exportAndShare(context, lista, estado ?: "Todos")
        }
    }

    /** Respaldo completo (registros + logs) en JSON, en Descargas. */
    fun descargarRespaldoJson(context: Context) {
        viewModelScope.launch {
            val regs = dao.getAllRecordsOnce()
            val logsTodos = dao.getAllLogsOnce()
            CsvExporter.exportarRespaldoJson(context, regs, logsTodos)
        }
    }

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
                val bloques = cat.bloques_info?.map { CachedBlockEntity(it.bloque, it.grupo_forza, it.grupo_siembra) }
                    ?: cat.bloques.map { CachedBlockEntity(it) }
                val afects = cat.afectaciones_info?.map { CachedAfectacionEntity(it.nombre, it.tipo) }
                    ?: cat.afectaciones.map { CachedAfectacionEntity(it) }
                // Respuesta vacía = problema del servidor: no se borra el catálogo que ya hay en el equipo.
                if (bloques.isEmpty() || afects.isEmpty()) {
                    onResult("El servidor no devolvió bloques o afectaciones; se conserva el catálogo actual")
                    return@launch
                }
                database.withTransaction {
                    dao.clearCachedBlocks()
                    dao.insertBlocks(bloques)
                    dao.clearCachedAfectaciones()
                    dao.insertAfectaciones(afects)
                }
                val nAfect = afects.count { !esTipoNinguno(it) }
                onResult("Catálogos actualizados: ${bloques.size} bloques, $nAfect afectaciones")
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

    // ─── Envío ─────────────────────────────────────────────────────────────
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
            val pending = dao.getAllRecordsOnce().filter { !it.isSynced }
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
        val error: String? = if (record.muestrasConPeso().isEmpty()) {
            "Sin muestras con peso: no hay nada que cargar"
        } else try {
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
                mensaje = error ?: "Enviado correctamente (${record.muestrasConPeso().size} muestras)"
            )
        )
        return error
    }

    private suspend fun buildRequest(record: OrganoRecordEntity): OrganoUploadRequest {
        val fotoBase64 = withContext(Dispatchers.IO) {
            record.fotoPath.takeIf { it.isNotEmpty() }?.let { getScaledBase64(it) }
        }
        val m = record.muestras()
        // Peso null = muestra sin peso: no viaja en el JSON y el servidor no la carga.
        fun peso(i: Int) = m[i].peso.takeIf { it > 0.0 }
        return OrganoUploadRequest(
            fecha_registro = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.fechaRegistro)),
            bloque = record.bloque,
            numero_bin = record.numeroBin.toString(),
            observaciones = record.observaciones,
            usuario = "${Build.MANUFACTURER} ${Build.MODEL}",
            fotografia = fotoBase64,
            grupo_forza = record.grupo.ifBlank { null },
            m1_peso = peso(0), m1_color = m[0].color, m1_brix = m[0].brix, m1_acidez = m[0].acidez, m1_translucidez = m[0].translucidez.toString(), m1_categoria = m[0].categoria, m1_afectaciones = m[0].afectaciones,
            m2_peso = peso(1), m2_color = m[1].color, m2_brix = m[1].brix, m2_acidez = m[1].acidez, m2_translucidez = m[1].translucidez.toString(), m2_categoria = m[1].categoria, m2_afectaciones = m[1].afectaciones,
            m3_peso = peso(2), m3_color = m[2].color, m3_brix = m[2].brix, m3_acidez = m[2].acidez, m3_translucidez = m[2].translucidez.toString(), m3_categoria = m[2].categoria, m3_afectaciones = m[2].afectaciones,
            m4_peso = peso(3), m4_color = m[3].color, m4_brix = m[3].brix, m4_acidez = m[3].acidez, m4_translucidez = m[3].translucidez.toString(), m4_categoria = m[3].categoria, m4_afectaciones = m[3].afectaciones,
            m5_peso = peso(4), m5_color = m[4].color, m5_brix = m[4].brix, m5_acidez = m[4].acidez, m5_translucidez = m[4].translucidez.toString(), m5_categoria = m[4].categoria, m5_afectaciones = m[4].afectaciones
        )
    }

    private fun getScaledBase64(path: String): String? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            var scale = 1
            while (options.outWidth / scale / 2 >= 1024 && options.outHeight / scale / 2 >= 1024) scale *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = scale }) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
