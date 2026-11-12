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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
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
import com.example.aplicativoorganolepticas.ui.theme.DarkBeige
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.viewmodel.OrganoViewModel
import com.example.aplicativoorganolepticas.ui.viewmodel.SampleState
import kotlinx.coroutines.launch

private val FieldText = TextStyle(color = Color.Black, fontSize = 16.sp)
private val Verde = Color(0xFF2E7D32)

@Composable
private fun fieldColors(error: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = if (error) Color.Red else Color.Black,
    unfocusedBorderColor = if (error) Color.Red else Color.Black.copy(alpha = 0.6f),
    errorBorderColor = Color.Red
)

private fun SampleState.completa() = pesoFruta.isNotBlank() && gradosBrix.isNotBlank()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganoFormScreen(
    viewModel: OrganoViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var blockMenuExpanded by remember { mutableStateOf(false) }
    val availableBlocks by viewModel.availableBlocks.collectAsState()
    val afectacionOptions by viewModel.afectacionOptions.collectAsState()
    // ponytail: máx. 50 sugerencias en el menú; el usuario escribe para filtrar
    val filteredBlocks = remember(viewModel.bloque, availableBlocks) {
        availableBlocks.filter { it.contains(viewModel.bloque, ignoreCase = true) }.take(50)
    }
    val isBlockValid = viewModel.bloque.isNotEmpty() && availableBlocks.contains(viewModel.bloque)
    val binVal = viewModel.numeroBin.toIntOrNull()
    val isBinError = viewModel.numeroBin.isNotEmpty() && (binVal == null || binVal < 1 || binVal > 200)
    val completas = viewModel.samples.count { it.completa() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onPhotoTaken(success)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(viewModel.prepareCameraForGlobal())
        else Toast.makeText(context, "Se necesita permiso de cámara para tomar fotos", Toast.LENGTH_SHORT).show()
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(viewModel.prepareCameraForGlobal())
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ingresar Registro", color = Color.Black, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LightBeige)
            )
        },
        // Guardar siempre visible: no hay que bajar hasta el final del formulario.
        bottomBar = {
            Surface(color = LightBeige, shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        viewModel.saveRecord { errorMsg ->
                            scope.launch { snackbarHostState.showSnackbar(errorMsg ?: "Registro guardado correctamente") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("GUARDAR  ·  $completas/5 muestras", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            // ── DATOS DEL BIN: bloque + número de bin en una fila ──
            FormCard(title = "Datos del bin") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    ExposedDropdownMenuBox(
                        expanded = blockMenuExpanded && filteredBlocks.isNotEmpty(),
                        onExpandedChange = { blockMenuExpanded = it },
                        modifier = Modifier.weight(1.6f)
                    ) {
                        OutlinedTextField(
                            value = viewModel.bloque,
                            onValueChange = { viewModel.bloque = it.take(8).uppercase(); blockMenuExpanded = true },
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
                                    onClick = { viewModel.bloque = option; blockMenuExpanded = false }
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
            }

            // ── 5 MUESTRAS ──
            for (i in 0 until 5) {
                SampleCard(
                    index = i,
                    sampleState = viewModel.samples[i],
                    afectacionOptions = afectacionOptions,
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
                        if (viewModel.globalFotoUri != null) {
                            AsyncImage(
                                model = viewModel.globalFotoUri,
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
}

@Composable
private fun FormCard(
    title: String,
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
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = Color.Black, modifier = Modifier.weight(1f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleCard(
    index: Int,
    sampleState: SampleState,
    afectacionOptions: List<String>,
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

        // Afectaciones (selección múltiple, catálogo del servidor)
        ExposedDropdownMenuBox(
            expanded = afectacionMenuExpanded,
            onExpandedChange = { afectacionMenuExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = sampleState.afectaciones.ifEmpty { listOf("Ninguna") }.joinToString(", "),
                onValueChange = {},
                readOnly = true,
                label = { Text("Afectaciones") },
                supportingText = if (afectacionOptions.size <= 1) {{ Text("Sin catálogo de afectaciones: actualízalo en Inicio", color = Color.Red) }} else null,
                textStyle = FieldText,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = afectacionMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth().heightIn(min = 56.dp, max = 150.dp),
                colors = fieldColors()
            )
            ExposedDropdownMenu(
                expanded = afectacionMenuExpanded,
                onDismissRequest = { afectacionMenuExpanded = false },
                modifier = Modifier.background(DarkBeige)
            ) {
                afectacionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = sampleState.afectaciones.contains(option),
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = Color.Black)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(option, color = Color.Black)
                            }
                        },
                        onClick = { onAfectacionToggle(option) }
                    )
                }
            }
        }
    }
}
