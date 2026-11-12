package com.example.aplicativoorganolepticas.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.aplicativoorganolepticas.data.CachedAfectacionEntity
import com.example.aplicativoorganolepticas.data.ubicacionTxt
import com.example.aplicativoorganolepticas.ui.theme.DarkBeige
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.viewmodel.OrganoViewModel
import com.example.aplicativoorganolepticas.ui.viewmodel.SampleState
import kotlinx.coroutines.launch
import java.io.File

private val FieldText = TextStyle(color = Color.Black, fontSize = 16.sp)
private val Verde = Color(0xFF2E7D32)
private val Rojo = Color(0xFFC62828)

// Colores para diferenciar afectaciones externas e internas
private val ExternaFondo = Color(0xFFFFE0B2)
private val ExternaTexto = Color(0xFF8A4B00)
private val InternaFondo = Color(0xFFD6E8FA)
private val InternaTexto = Color(0xFF0B4F8A)

@Composable
private fun fieldColors(error: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = if (error) Color.Red else Color.Black,
    unfocusedBorderColor = if (error) Color.Red else Color.Black.copy(alpha = 0.6f),
    errorBorderColor = Color.Red
)

private fun SampleState.completa() = tienePeso && gradosBrix.isNotBlank()

/** Grupos del catálogo de afectaciones en el orden de la lista: externas, internas y luego otras. */
private fun agrupar(catalogo: List<CachedAfectacionEntity>): List<Pair<String, List<String>>> {
    val ext = catalogo.filter { it.tipo.contains("extern", true) }.map { it.nombre }
    val int = catalogo.filter { it.tipo.contains("intern", true) }.map { it.nombre }
    val otras = catalogo.filter { !it.tipo.contains("extern", true) && !it.tipo.contains("intern", true) }.map { it.nombre }
    return listOf("Afectación externa" to ext, "Afectación interna" to int, "Otras" to otras).filter { it.second.isNotEmpty() }
}

private fun coloresGrupo(titulo: String): Pair<Color, Color> = when {
    titulo.contains("extern", true) -> ExternaFondo to ExternaTexto
    titulo.contains("intern", true) -> InternaFondo to InternaTexto
    else -> Color(0xFFEEEEEE) to Color.DarkGray
}

/** Lista de cambios para los diálogos de confirmación (con scroll si es larga). */
@Composable
fun ListaCambios(intro: String, cambios: List<String>) {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
        Text(intro, color = Color.Black, modifier = Modifier.padding(bottom = 8.dp))
        cambios.forEach { Text("• $it", color = Color.Black, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganoFormScreen(
    viewModel: OrganoViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val editando = viewModel.isEditing

    var blockMenuExpanded by remember { mutableStateOf(false) }
    var confirmarGuardar by remember { mutableStateOf(false) }
    var confirmarCancelar by remember { mutableStateOf(false) }
    val availableBlocks by viewModel.availableBlocks.collectAsState()
    val catalogo by viewModel.afectacionesCatalogo.collectAsState()
    val ninguna by viewModel.opcionNinguna.collectAsState()
    val grupos = remember(catalogo) { agrupar(catalogo) }
    // ponytail: máx. 50 sugerencias en el menú; el usuario escribe para filtrar
    val filteredBlocks = remember(viewModel.bloque, availableBlocks) {
        availableBlocks.filter { it.contains(viewModel.bloque, ignoreCase = true) }.take(50)
    }
    val isBlockValid = viewModel.bloque.isNotEmpty() && availableBlocks.contains(viewModel.bloque)
    val binVal = viewModel.numeroBin.toIntOrNull()
    val isBinError = viewModel.numeroBin.isNotEmpty() && (binVal == null || binVal < 1 || binVal > 200)
    val conPeso = viewModel.samples.count { it.tienePeso }
    val cambios = if (editando) viewModel.cambiosPendientes() else emptyList()

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onPhotoTaken(success)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(viewModel.prepareCameraForGlobal())
        else Toast.makeText(context, "Se necesita permiso de cámara para tomar fotos", Toast.LENGTH_SHORT).show()
    }
    // GPS: solo en registro nuevo (al editar se conserva la ubicación original)
    var permisoGps by remember { mutableStateOf(viewModel.tienePermisoGps()) }
    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permisoGps = viewModel.tienePermisoGps()
    }
    if (!editando) {
        DisposableEffect(permisoGps) {
            viewModel.iniciarGps()
            onDispose { viewModel.detenerGps() }
        }
    }
    var previa by remember { mutableStateOf<com.example.aplicativoorganolepticas.data.OrganoRecordEntity?>(null) }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(viewModel.prepareCameraForGlobal())
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Salir: en edición, si hay cambios se pide confirmar (con el resumen); si no, sale sin preguntar.
    fun salir() {
        if (editando && cambios.isNotEmpty()) confirmarCancelar = true
        else { if (editando) viewModel.cancelEdit(); onBack() }
    }
    BackHandler { salir() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (editando) "Editar registro" else "Ingresar Registro", color = Color.Black, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { salir() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LightBeige)
            )
        },
        // Botones siempre visibles: no hay que bajar hasta el final del formulario.
        bottomBar = {
            Surface(color = LightBeige, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editando) {
                        OutlinedButton(onClick = { salir() }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text("Cancelar edición", color = Color.Black)
                        }
                        Button(
                            onClick = { confirmarGuardar = true },
                            enabled = cambios.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text(if (cambios.isEmpty()) "Sin cambios" else "Guardar cambios (${cambios.size})",
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                val err = viewModel.validar()
                                if (err != null) scope.launch { snackbarHostState.showSnackbar(err) }
                                else previa = viewModel.vistaPrevia()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text("GUARDAR  ·  $conPeso muestra(s) con peso", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = LightBeige
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── DATOS DEL BIN: bloque + número de bin en una fila, y el grupo del bloque ──
            FormCard(title = "Datos del bin") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    ExposedDropdownMenuBox(
                        expanded = blockMenuExpanded && filteredBlocks.isNotEmpty(),
                        onExpandedChange = { blockMenuExpanded = it },
                        modifier = Modifier.weight(1.6f)
                    ) {
                        OutlinedTextField(
                            value = viewModel.bloque,
                            onValueChange = { viewModel.onBloqueChange(it.take(8).uppercase()); blockMenuExpanded = true },
                            label = { Text("Bloque") },
                            textStyle = FieldText,
                            singleLine = true,
                            isError = viewModel.bloque.isNotEmpty() && !isBlockValid,
                            supportingText = {
                                when {
                                    availableBlocks.isEmpty() -> Text("Sin catálogo: actualízalo en Inicio", color = Color.Red)
                                    viewModel.bloque.isNotEmpty() && !isBlockValid -> Text("No está en el catálogo", color = Color.Red)
                                }
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = fieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = blockMenuExpanded && filteredBlocks.isNotEmpty(),
                            onDismissRequest = { blockMenuExpanded = false },
                            modifier = Modifier.background(DarkBeige)
                        ) {
                            filteredBlocks.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = Color.Black) },
                                    onClick = { viewModel.onBloqueChange(option); blockMenuExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = viewModel.numeroBin,
                        onValueChange = { if (it.length <= 3) viewModel.numeroBin = it.filter { c -> c.isDigit() } },
                        label = { Text("Bin") },
                        placeholder = { Text("1–200") },
                        textStyle = FieldText,
                        singleLine = true,
                        isError = isBinError,
                        supportingText = { if (isBinError) Text("Entre 1 y 200", color = Color.Red) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = fieldColors(isBinError)
                    )
                }
                if (isBlockValid) GrupoSelector(viewModel)
                if (!editando) {
                    EstadoGps(viewModel, permisoGps) {
                        gpsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                } else {
                    viewModel.editOriginal?.let { Text("Ubicación: ${it.ubicacionTxt()} (no cambia al editar)", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.6f)) }
                }
            }

            // ── 5 MUESTRAS ──
            for (i in 0 until 5) {
                SampleCard(
                    index = i,
                    sampleState = viewModel.samples[i],
                    grupos = grupos,
                    ninguna = ninguna,
                    onPesoChange = { viewModel.updateSamplePeso(i, it) },
                    onColorChange = { viewModel.updateSampleColor(i, it) },
                    onBrixChange = { viewModel.updateSampleBrix(i, it) },
                    onPruebaAcidezChange = { viewModel.updateSamplePruebaAcidez(i, it) },
                    onAcidezChange = { viewModel.updateSampleAcidez(i, it) },
                    onTranslucidezChange = { viewModel.updateSampleTranslucidez(i, it) },
                    onCategoriaChange = { viewModel.updateSampleCategoria(i, it) },
                    onAfectacionToggle = { viewModel.updateSampleAfectacionToggle(i, it) }
                )
            }

            // ── FOTO + OBSERVACIONES lado a lado ──
            FormCard(title = "Foto y observaciones") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .clickable { launchCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (viewModel.globalFotoPath.isNotEmpty()) {
                            AsyncImage(
                                model = File(viewModel.globalFotoPath),
                                contentDescription = "Foto del registro (tocar para repetir)",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(36.dp))
                                Text("Tomar foto", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = viewModel.observaciones,
                        onValueChange = { viewModel.observaciones = it },
                        label = { Text("Observaciones (opcional)") },
                        textStyle = FieldText,
                        modifier = Modifier.weight(1f).height(120.dp),
                        colors = fieldColors()
                    )
                }
            }
        }
    }

    if (confirmarGuardar) {
        AlertDialog(
            onDismissRequest = { confirmarGuardar = false },
            title = { Text("¿Guardar ${cambios.size} cambio(s)?", color = Color.Black) },
            text = { ListaCambios("Bloque ${viewModel.editOriginal?.bloque} · Bin ${viewModel.editOriginal?.numeroBin}", cambios) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarGuardar = false
                    viewModel.saveEdit { err ->
                        if (err == null) {
                            Toast.makeText(context, "Cambios guardados", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                }) { Text("GUARDAR", fontWeight = FontWeight.Bold, color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { confirmarGuardar = false }) { Text("SEGUIR EDITANDO", color = Color.Black) } },
            containerColor = LightBeige
        )
    }

    previa?.let { r ->
        ConfirmarRegistros(
            titulo = "¿Guardar este registro?",
            intro = "Revisa los datos que quedan guardados en el equipo (se cargan al servidor cuando los envíes):",
            registros = listOf(r),
            textoConfirmar = "GUARDAR",
            textoCancelar = "REVISAR",
            onConfirm = {
                previa = null
                viewModel.saveRecord { errorMsg ->
                    scope.launch { snackbarHostState.showSnackbar(errorMsg ?: "Registro guardado correctamente") }
                }
            },
            onDismiss = { previa = null }
        )
    }

    if (confirmarCancelar) {
        AlertDialog(
            onDismissRequest = { confirmarCancelar = false },
            title = { Text("¿Descartar los cambios?", color = Color.Black) },
            text = { ListaCambios("Se perderán estos cambios:", cambios) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarCancelar = false
                    viewModel.cancelEdit()
                    onBack()
                }) { Text("DESCARTAR", fontWeight = FontWeight.Bold, color = Rojo) }
            },
            dismissButton = { TextButton(onClick = { confirmarCancelar = false }) { Text("SEGUIR EDITANDO", color = Color.Black) } },
            containerColor = LightBeige
        )
    }
}

/** Estado del GPS para el registro nuevo: precisión, buscando, ubicación apagada o sin permiso (con botón para darlo). */
@Composable
private fun EstadoGps(viewModel: OrganoViewModel, permiso: Boolean, pedirPermiso: () -> Unit) {
    val u = viewModel.ubicacion
    val (texto, color) = when {
        !permiso -> "Sin permiso de ubicación: el registro se guarda sin GPS" to Rojo
        u != null -> "Ubicación GPS lista (±${u.accuracy.toInt()} m)" to Verde
        !viewModel.ubicacionActivada() -> "La ubicación del teléfono está apagada: actívala para guardar con GPS" to Rojo
        else -> "Buscando ubicación GPS… (si no la encuentra, se guarda sin GPS)" to Color(0xFF8A4B00)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(texto, fontSize = 13.sp, color = color, modifier = Modifier.weight(1f))
        if (!permiso) TextButton(onClick = pedirPermiso) { Text("Dar permiso", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

/** Grupo del bloque: muestra GF y grupo siembra, el usuario elige cuál queda y puede editarlo. */
@Composable
private fun GrupoSelector(viewModel: OrganoViewModel) {
    val info = viewModel.infoBloque(viewModel.bloque) ?: return
    val opciones = listOf("Grupo forza" to info.grupoForza, "Grupo siembra" to info.grupoSiembra).filter { it.second.isNotEmpty() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F3E8)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (info.grupoForza.isEmpty()) {
            Text(
                "Este bloque no tiene grupo forza: se propone el grupo siembra. Elige cuál dejar o edítalo.",
                fontSize = 13.sp, color = Color(0xFF8A4B00)
            )
        }
        opciones.forEach { (etiqueta, valor) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = viewModel.grupo == valor, onClick = { viewModel.grupo = valor }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = viewModel.grupo == valor, onClick = { viewModel.grupo = valor },
                    colors = RadioButtonDefaults.colors(selectedColor = Color.Black))
                Text("$etiqueta: ", color = Color.Black, fontSize = 14.sp)
                Text(valor, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedTextField(
            value = viewModel.grupo,
            onValueChange = { viewModel.grupo = it.uppercase().take(12) },
            label = { Text("Grupo que queda en el registro (editable)") },
            textStyle = FieldText,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
    }
}

@Composable
private fun FormCard(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Color.Black.copy(alpha = 0.55f))
                }
                trailing()
            }
            content()
        }
    }
}

/** Selector de una opción (solo lectura) para usar en filas de 2 columnas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            textStyle = FieldText,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = fieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DarkBeige)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option, color = Color.Black) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun OpcionAfectacion(nombre: String, marcada: Boolean, fondo: Color, texto: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = marcada, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = texto))
                Spacer(modifier = Modifier.width(8.dp))
                Text(nombre, color = Color.Black, fontWeight = if (marcada) FontWeight.Bold else FontWeight.Normal)
            }
        },
        onClick = onClick,
        modifier = Modifier.background(fondo)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleCard(
    index: Int,
    sampleState: SampleState,
    grupos: List<Pair<String, List<String>>>,
    ninguna: String,
    onPesoChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onBrixChange: (String) -> Unit,
    onPruebaAcidezChange: (Boolean) -> Unit,
    onAcidezChange: (String) -> Unit,
    onTranslucidezChange: (Int) -> Unit,
    onCategoriaChange: (String) -> Unit,
    onAfectacionToggle: (String) -> Unit
) {
    var afectacionMenuExpanded by remember { mutableStateOf(false) }
    val isPesoError = (sampleState.pesoFruta.toDoubleOrNull() ?: 0.0) > 5000
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    FormCard(
        title = "Muestra ${index + 1}",
        subtitle = if (!sampleState.tienePeso) "Sin peso: esta muestra no se carga" else null,
        trailing = {
            if (sampleState.completa()) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Muestra completa", tint = Verde, modifier = Modifier.size(22.dp))
            }
        }
    ) {
        // Fila 1: Peso | Brix
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = sampleState.pesoFruta,
                onValueChange = { onPesoChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Peso (g)") },
                textStyle = FieldText,
                singleLine = true,
                isError = isPesoError,
                supportingText = if (isPesoError) {{ Text("Máx. 5000 g", color = Color.Red) }} else null,
                keyboardOptions = decimal,
                modifier = Modifier.weight(1f),
                colors = fieldColors(isPesoError)
            )
            OutlinedTextField(
                value = sampleState.gradosBrix,
                onValueChange = { onBrixChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Brix (°Bx)") },
                textStyle = FieldText,
                singleLine = true,
                keyboardOptions = decimal,
                modifier = Modifier.weight(1f),
                colors = fieldColors()
            )
        }

        // Fila 2: Color externo | % Translucidez
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                label = "Color externo",
                value = sampleState.colorExterno,
                options = listOf("0.5", "1.0", "1.5", "2.0", "2.5", "≥3.0"),
                onSelect = onColorChange,
                modifier = Modifier.weight(1f)
            )
            DropdownField(
                label = "% Translucidez",
                value = "${sampleState.avanceTranslucidez}%",
                options = (1..10).map { "${it * 10}%" },
                onSelect = { onTranslucidezChange(it.removeSuffix("%").toInt()) },
                modifier = Modifier.weight(1f)
            )
        }

        // Fila 3: Categoría | Prueba de acidez
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DropdownField(
                label = "Categoría",
                value = sampleState.categoria,
                options = listOf("Especial", "Industria", "Jugos"),
                onSelect = onCategoriaChange,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier.weight(1f).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Prueba acidez", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Switch(
                    checked = sampleState.pruebaAcidez,
                    onCheckedChange = onPruebaAcidezChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Black,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    )
                )
            }
        }

        if (sampleState.pruebaAcidez) {
            OutlinedTextField(
                value = sampleState.acidez,
                onValueChange = { onAcidezChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Gasto hidróxido de sodio (ml)") },
                textStyle = FieldText,
                singleLine = true,
                keyboardOptions = decimal,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
        }

        // Afectaciones: "sin afectación" (Sin defecto) primero, luego externas e internas con colores distintos
        ExposedDropdownMenuBox(
            expanded = afectacionMenuExpanded,
            onExpandedChange = { afectacionMenuExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = sampleState.afectaciones.ifEmpty { listOf(ninguna) }.joinToString(", "),
                onValueChange = {},
                readOnly = true,
                label = { Text("Afectaciones") },
                supportingText = if (grupos.isEmpty()) {{ Text("Sin catálogo de afectaciones: actualízalo en Inicio", color = Color.Red) }} else null,
                textStyle = FieldText,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = afectacionMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth().heightIn(min = 56.dp, max = 150.dp),
                colors = fieldColors()
            )
            ExposedDropdownMenu(
                expanded = afectacionMenuExpanded,
                onDismissRequest = { afectacionMenuExpanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                OpcionAfectacion(ninguna, sampleState.afectaciones.isEmpty(), Color.White, Color.Black) {
                    onAfectacionToggle(ninguna)
                }
                grupos.forEach { (titulo, nombres) ->
                    val (fondo, texto) = coloresGrupo(titulo)
                    Text(
                        titulo.uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().background(texto).padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    nombres.forEach { n ->
                        OpcionAfectacion(n, sampleState.afectaciones.contains(n), fondo, texto) { onAfectacionToggle(n) }
                    }
                }
            }
        }
        // Lo marcado, separado por tipo y con el mismo color de la lista
        grupos.forEach { (titulo, nombres) ->
            val marcadas = sampleState.afectaciones.filter { it in nombres }
            if (marcadas.isNotEmpty()) {
                val (fondo, texto) = coloresGrupo(titulo)
                Text(
                    "$titulo: ${marcadas.joinToString(", ")}",
                    color = texto, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(fondo).padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
