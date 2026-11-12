package com.example.aplicativoorganolepticas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.aplicativoorganolepticas.data.*
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.viewmodel.OrganoViewModel
import com.example.aplicativoorganolepticas.ui.viewmodel.estadoDe
import com.example.aplicativoorganolepticas.ui.viewmodel.numTxt
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val VerdeD = Color(0xFF2E7D32)
private val AmbarD = Color(0xFFB26A00)
private val RojoD = Color(0xFFC62828)

private fun colorEstado(e: String) = when (e) { "Enviado" -> VerdeD; "Error" -> RojoD; else -> AmbarD }
private fun dia(millis: Long, utc: Boolean = false): String =
    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
private fun fechaCorta(millis: Long, utc: Boolean = false): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
private fun f1(d: Double) = String.format(Locale.US, "%.1f", d)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: OrganoViewModel,
    onBack: () -> Unit,
    onEdit: (OrganoRecordEntity) -> Unit
) {
    val records by viewModel.records.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun avisar(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    var verResumen by rememberSaveable { mutableStateOf(true) }
    var texto by rememberSaveable { mutableStateOf("") }
    var desde by rememberSaveable { mutableStateOf<Long?>(null) }   // millis UTC del DatePicker
    var hasta by rememberSaveable { mutableStateOf<Long?>(null) }
    var estado by rememberSaveable { mutableStateOf<String?>(null) }
    var mostrarFiltros by rememberSaveable { mutableStateOf(false) }
    var pickerDe by remember { mutableStateOf<String?>(null) }      // "desde" / "hasta"
    var expandidos by remember { mutableStateOf(setOf<Int>()) }
    var aEliminar by remember { mutableStateOf<OrganoRecordEntity?>(null) }
    var pasoCache by remember { mutableIntStateOf(0) }              // 0 nada, 1 hay pendientes, 2 y 3 confirmaciones

    val filtrosActivos = listOf(desde, hasta, estado).count { it != null }
    val filtrados = remember(records, texto, desde, hasta, estado) {
        val t = texto.trim()
        records.filter { r ->
            (t.isEmpty() || r.bloque.contains(t, true) || r.numeroBin.toString() == t || r.grupo.contains(t, true)) &&
                (desde == null || dia(r.fechaRegistro) >= dia(desde!!, utc = true)) &&
                (hasta == null || dia(r.fechaRegistro) <= dia(hasta!!, utc = true)) &&
                (estado == null || estadoDe(r) == estado)
        }
    }
    val resumen = remember(filtrados) { resumir(filtrados) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Datos", color = Color.Black, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.Black) }
                },
                actions = {
                    IconButton(onClick = {
                        when {
                            records.isEmpty() -> avisar("No hay datos en el equipo")
                            records.any { !it.isSynced } -> pasoCache = 1
                            else -> pasoCache = 2
                        }
                    }) { Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar caché", tint = Color.Black) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LightBeige)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = LightBeige
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ── Resumen | Datos ──
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(selected = verResumen, onClick = { verResumen = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text("Resumen")
                }
                SegmentedButton(selected = !verResumen, onClick = { verResumen = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text("Datos (${filtrados.size})")
                }
            }

            // ── Búsqueda + filtros ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text("Buscar bloque, bin o grupo") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (texto.isNotEmpty()) IconButton(onClick = { texto = "" }) { Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda") } },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                BadgedBox(badge = { if (filtrosActivos > 0) Badge { Text("$filtrosActivos") } }) {
                    FilledTonalIconButton(onClick = { mostrarFiltros = !mostrarFiltros }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtrar")
                    }
                }
            }
            if (mostrarFiltros) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickerDe = "desde" }, modifier = Modifier.weight(1f)) {
                            Text(desde?.let { "Desde ${fechaCorta(it, true)}" } ?: "Desde", color = Color.Black)
                        }
                        OutlinedButton(onClick = { pickerDe = "hasta" }, modifier = Modifier.weight(1f)) {
                            Text(hasta?.let { "Hasta ${fechaCorta(it, true)}" } ?: "Hasta", color = Color.Black)
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null, "Pendiente", "Error", "Enviado").forEach { e ->
                            FilterChip(selected = estado == e, onClick = { estado = e }, label = { Text(e ?: "Todos") })
                        }
                        if (filtrosActivos > 0) TextButton(onClick = { desde = null; hasta = null; estado = null }) { Text("Limpiar filtros", color = RojoD) }
                    }
                }
            }
            Text(
                "Mostrando ${filtrados.size} de ${records.size} registros",
                fontSize = 12.sp, color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtrados.isEmpty()) {
                    item {
                        Text(
                            if (records.isEmpty()) "No hay registros guardados" else "Ningún registro coincide con la búsqueda o los filtros",
                            color = Color.Black.copy(alpha = 0.55f), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                } else if (verResumen) {
                    resumenItems(resumen)
                } else {
                    items(filtrados, key = { it.id }) { r ->
                        RegistroCard(
                            record = r,
                            expandido = r.id in expandidos,
                            onToggle = { expandidos = if (r.id in expandidos) expandidos - r.id else expandidos + r.id },
                            onEdit = { onEdit(r) },
                            onUpload = { viewModel.uploadRecord(r) { err -> avisar(err ?: "Registro enviado") } },
                            onDelete = { aEliminar = r },
                            subiendo = viewModel.isUploading
                        )
                    }
                }
            }
        }
    }

    // ── Selector de fecha ──
    pickerDe?.let { cual ->
        val state = rememberDatePickerState(initialSelectedDateMillis = if (cual == "desde") desde else hasta)
        DatePickerDialog(
            onDismissRequest = { pickerDe = null },
            confirmButton = {
                TextButton(onClick = {
                    if (cual == "desde") desde = state.selectedDateMillis else hasta = state.selectedDateMillis
                    pickerDe = null
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickerDe = null }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    // ── Eliminar un registro ──
    aEliminar?.let { r ->
        val n = r.muestrasConPeso().size
        val est = estadoDe(r)
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("¿Eliminar este registro?", color = Color.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bloque ${r.bloque} · Bin ${r.numeroBin}", fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(r.fechaRegistro))}", color = Color.Black)
                    Text("Grupo: ${r.grupo.ifEmpty { "—" }}", color = Color.Black)
                    Text("Muestras con peso: $n de 5", color = Color.Black)
                    Text("Foto: ${if (r.fotoPath.isEmpty()) "no" else "sí (también se borra)"}", color = Color.Black)
                    Text("Estado: $est", color = colorEstado(est), fontWeight = FontWeight.Bold)
                    if (!r.isSynced) Text("Este registro NO se ha enviado: si lo eliminas se pierde.", color = RojoD)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRecord(r); aEliminar = null; avisar("Registro eliminado") }) {
                    Text("ELIMINAR", color = RojoD, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("CANCELAR", color = Color.Black) } },
            containerColor = LightBeige
        )
    }

    // ── Borrar caché ──
    when (pasoCache) {
        1 -> {
            val sinEnviar = records.count { !it.isSynced }
            AlertDialog(
                onDismissRequest = { pasoCache = 0 },
                title = { Text("Hay datos sin enviar", color = Color.Black) },
                text = {
                    Text(
                        "Hay $sinEnviar registro(s) pendientes o con error. Antes de borrar la caché envíalos, " +
                            "o elimínalos uno a uno desde la vista Datos si no se deben cargar.",
                        color = Color.Black
                    )
                },
                confirmButton = {
                    TextButton(onClick = { pasoCache = 0; viewModel.uploadAllUnsyncedRecords { avisar(it) } }, enabled = !viewModel.isUploading) {
                        Text("ENVIAR AHORA", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                dismissButton = { TextButton(onClick = { pasoCache = 0 }) { Text("ENTENDIDO", color = Color.Black) } },
                containerColor = LightBeige
            )
        }
        2 -> AlertDialog(
            onDismissRequest = { pasoCache = 0 },
            title = { Text("Borrar caché", color = Color.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Se borrarán del equipo ${records.size} registro(s) (todos ya enviados), sus fotos y los logs de envío. " +
                            "Los catálogos se conservan.",
                        color = Color.Black
                    )
                    Text("Recomendado: descarga antes un respaldo JSON.", color = Color.Black, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { viewModel.descargarRespaldoJson(context) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text("Descargar respaldo JSON", color = Color.Black)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pasoCache = 3 }) { Text("CONTINUAR", color = RojoD, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { pasoCache = 0 }) { Text("CANCELAR", color = Color.Black) } },
            containerColor = LightBeige
        )
        3 -> AlertDialog(
            onDismissRequest = { pasoCache = 0 },
            title = { Text("Confirmación final", color = RojoD) },
            text = {
                Text(
                    "Los datos borrados NO se pueden recuperar desde la app; solo con un respaldo JSON descargado. " +
                        "¿Borrar definitivamente ${records.size} registro(s)?",
                    color = Color.Black
                )
            },
            confirmButton = {
                TextButton(onClick = { pasoCache = 0; viewModel.borrarCache { avisar(it) } }) {
                    Text("BORRAR DEFINITIVAMENTE", color = RojoD, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pasoCache = 0 }) { Text("CANCELAR", color = Color.Black) } },
            containerColor = LightBeige
        )
    }
}

// ─── Vista Resumen ──────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.resumenItems(r: Resumen) {
    item {
        ResumenCard("General") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Bines muestreados", "${r.bines}", Modifier.weight(1f))
                Tile("Muestras cargadas", "${r.muestras}", Modifier.weight(1f))
                Tile("Bloques distintos", "${r.bloques}", Modifier.weight(1f))
            }
            if (r.fechaMin != null && r.fechaMax != null) {
                Text("Periodo: ${fechaCorta(r.fechaMin)} – ${fechaCorta(r.fechaMax)}", fontSize = 13.sp, color = Color.Black)
            }
            if (r.muestrasSinPeso > 0) {
                Text("${r.muestrasSinPeso} muestra(s) sin peso: no se cuentan ni se cargan", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.6f))
            }
        }
    }
    item {
        ResumenCard("Envío") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Enviados", "${r.enviados}", Modifier.weight(1f), VerdeD)
                Tile("Pendientes", "${r.pendientes}", Modifier.weight(1f), AmbarD)
                Tile("Con error", "${r.conError}", Modifier.weight(1f), RojoD)
            }
        }
    }
    item {
        ResumenCard("Variables (promedio · mín–máx)") {
            VariableRow("Peso (g)", r.peso)
            VariableRow("Brix (°Bx)", r.brix)
            VariableRow("Translucidez (%)", r.translucidez)
            VariableRow("Color externo", r.color)
            VariableRow("Gasto NaOH (ml)", r.gastoNaOH)
            VariableRow("Acidez (%)", r.acidezPct, decimales = 2)
        }
    }
    if (r.categorias.isNotEmpty()) item {
        ResumenCard("Categorías") {
            r.categorias.forEach { (c, n) -> ConteoRow(c, n, r.muestras) }
        }
    }
    item {
        ResumenCard("Afectaciones (por muestra)") {
            ConteoRow("Sin afectación", r.muestrasSinAfectacion, r.muestras)
            r.afectaciones.forEach { (a, n) -> ConteoRow(a, n, r.muestras) }
        }
    }
    item {
        ResumenCard("Por bloque") {
            Row {
                listOf("Bloque" to 1.4f, "Bines" to 0.8f, "Mues." to 0.8f, "Brix" to 0.8f, "Peso" to 1f).forEach { (t, w) ->
                    Text(t, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black, modifier = Modifier.weight(w))
                }
            }
            HorizontalDivider(color = Color.Black.copy(alpha = 0.15f))
            r.porBloque.forEach { b ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(b.bloque, fontSize = 12.sp, color = Color.Black, modifier = Modifier.weight(1.4f))
                    Text("${b.bines}", fontSize = 12.sp, color = Color.Black, modifier = Modifier.weight(0.8f))
                    Text("${b.muestras}", fontSize = 12.sp, color = Color.Black, modifier = Modifier.weight(0.8f))
                    Text(b.brix?.let { f1(it) } ?: "—", fontSize = 12.sp, color = Color.Black, modifier = Modifier.weight(0.8f))
                    Text(b.peso?.let { String.format(Locale.US, "%.0f", it) } ?: "—", fontSize = 12.sp, color = Color.Black, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ResumenCard(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            content()
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, color: Color = Color.Black) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(LightBeige).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = Color.Black.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun VariableRow(nombre: String, e: Estadistica?, decimales: Int = 1) {
    fun f(d: Double) = String.format(Locale.US, "%.${decimales}f", d)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, fontSize = 14.sp, color = Color.Black, modifier = Modifier.weight(1f))
        if (e == null) {
            Text("sin datos", fontSize = 13.sp, color = Color.Black.copy(alpha = 0.5f))
        } else {
            Text(f(e.promedio), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("  ${f(e.min)}–${f(e.max)}  n=${e.n}", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ConteoRow(nombre: String, n: Int, total: Int) {
    val pct = if (total == 0) 0f else n.toFloat() / total
    Column {
        Row {
            Text(nombre, fontSize = 13.sp, color = Color.Black, modifier = Modifier.weight(1f))
            Text("$n  (${String.format(Locale.US, "%.0f", pct * 100)}%)", fontSize = 13.sp, color = Color.Black)
        }
        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Color.Black, trackColor = Color.Black.copy(alpha = 0.08f))
    }
}

// ─── Vista Datos ────────────────────────────────────────────────────────────

@Composable
private fun RegistroCard(
    record: OrganoRecordEntity,
    expandido: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
    subiendo: Boolean
) {
    val est = estadoDe(record)
    val ms = record.muestras()
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column {
            // Cabecera (siempre visible; toca para expandir)
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (expandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expandido) "Contraer" else "Expandir", tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bloque ${record.bloque} · Bin ${record.numeroBin}", fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(
                        "${sdf.format(Date(record.fechaRegistro))} · ${ms.count { it.tienePeso }} muestras" +
                            (if (record.grupo.isNotEmpty()) " · ${record.grupo}" else ""),
                        fontSize = 12.sp, color = Color.Black.copy(alpha = 0.6f)
                    )
                }
                Text(
                    est, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorEstado(est),
                    modifier = Modifier.border(1.dp, colorEstado(est), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            if (expandido) {
                HorizontalDivider(color = Color.Black.copy(alpha = 0.08f))
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (record.ultimoError.isNotEmpty()) Text("Último error: ${record.ultimoError}", fontSize = 12.sp, color = RojoD)
                    Row(verticalAlignment = Alignment.Top) {
                        if (record.fotoPath.isNotEmpty()) {
                            AsyncImage(
                                model = File(record.fotoPath), contentDescription = "Foto del registro", contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Grupo: ${record.grupo.ifEmpty { "—" }}", fontSize = 13.sp, color = Color.Black)
                            Text("Observaciones: ${record.observaciones.ifBlank { "—" }}", fontSize = 13.sp, color = Color.Black)
                        }
                    }
                    ms.forEachIndexed { i, m ->
                        if (!m.tienePeso) {
                            Text("M${i + 1}: sin peso (no se carga)", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.45f))
                        } else {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LightBeige).padding(8.dp)) {
                                Text(
                                    "M${i + 1}: ${numTxt(m.peso)} g · Brix ${numTxt(m.brix)}" +
                                        (if (m.pruebaAcidez) " · NaOH ${numTxt(m.acidez)} ml" else ""),
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black
                                )
                                Text("Color ${m.color} · Transl. ${m.translucidez}% · ${m.categoria}", fontSize = 12.sp, color = Color.Black)
                                Text("Afectaciones: ${m.afectaciones}", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.7f))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!record.isSynced) {
                            TextButton(onClick = onEdit) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Editar", color = Color.Black)
                            }
                            TextButton(onClick = onUpload, enabled = !subiendo) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Enviar", color = Color.Black)
                            }
                        } else {
                            Text("Enviado: no se puede editar", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.CenterVertically).weight(1f))
                        }
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = RojoD, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text("Eliminar", color = RojoD)
                        }
                    }
                }
            }
        }
    }
}
