package com.example.aplicativoorganolepticas.ui.screens

import android.Manifest
import android.net.Uri
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
import android.content.pm.PackageManager
import coil.compose.AsyncImage
import com.example.aplicativoorganolepticas.ui.theme.DarkBeige
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.viewmodel.OrganoViewModel
import com.example.aplicativoorganolepticas.ui.viewmodel.SampleState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganoFormScreen(
    viewModel: OrganoViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Global dropdowns expanded states
    var blockMenuExpanded by remember { mutableStateOf(false) }
    var translucidezMenuExpanded by remember { mutableStateOf(false) }
    var categoriaMenuExpanded by remember { mutableStateOf(false) }
    var afectacionMenuExpanded by remember { mutableStateOf(false) }

    val availableBlocks by viewModel.availableBlocks.collectAsState()
    val filteredBlocks = remember(viewModel.bloque, availableBlocks) {
        if (viewModel.bloque.isEmpty()) availableBlocks
        else availableBlocks.filter { it.contains(viewModel.bloque, ignoreCase = true) }
    }
    val isBlockValid = (viewModel.bloque.startsWith("SC") || viewModel.bloque.startsWith("PC")) && 
                        viewModel.bloque.drop(2).all { it.isDigit() } &&
                        viewModel.bloque.length == 8 &&
                        (availableBlocks.isEmpty() || availableBlocks.contains(viewModel.bloque))

    val blackTextStyle = TextStyle(color = Color.Black, fontSize = 16.sp)
    val translucidezOptions = (1..10).map { it * 10 }

    val context = LocalContext.current

    // Camera launcher — takes picture and reports to ViewModel
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onPhotoTaken(success)
    }

    // Permission launcher — once granted, immediately opens the camera
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = viewModel.prepareCameraForSample(viewModel.pendingSampleIndexForPermission)
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Se necesita permiso de cámara para tomar fotos", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to request camera permission or launch directly
    fun launchCamera() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            val uri = viewModel.prepareCameraForGlobal()
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(permission)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = LightBeige
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── BLOQUE ───────────────────────────────────────────────────
            SectionLabel("Bloque")
            ExposedDropdownMenuBox(
                expanded = blockMenuExpanded,
                onExpandedChange = { blockMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.bloque,
                    onValueChange = { newValue ->
                        val processed = newValue.take(8).uppercase()
                        viewModel.bloque = processed
                    },
                    label = { Text("Bloque (Eje: PC123456)", color = if (isBlockValid) Color.Black else Color.Red) },
                    textStyle = blackTextStyle,
                    isError = viewModel.bloque.isNotEmpty() && !isBlockValid,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockMenuExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color.Black,
                        errorBorderColor = Color.Red
                    )
                )
                if (filteredBlocks.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = blockMenuExpanded,
                        onDismissRequest = { blockMenuExpanded = false },
                        modifier = Modifier.background(DarkBeige)
                    ) {
                        filteredBlocks.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.Black) },
                                onClick = { viewModel.bloque = option; blockMenuExpanded = false },
                                modifier = Modifier.background(DarkBeige)
                            )
                        }
                    }
                }
            }

            // ── NÚMERO BIN ───────────────────────────────────────────────
            val binVal = viewModel.numeroBin.toIntOrNull()
            val isBinError = viewModel.numeroBin.isNotEmpty() && (binVal == null || binVal < 1 || binVal > 200)
            SectionLabel("Número de Bin")
            OutlinedTextField(
                value = viewModel.numeroBin,
                onValueChange = { if (it.length <= 3) viewModel.numeroBin = it.filter { c -> c.isDigit() } },
                label = { Text("1 – 200", color = if (isBinError) Color.Red else Color.Black) },
                textStyle = blackTextStyle,
                isError = isBinError,
                supportingText = { if (isBinError) Text("Debe ser un número entre 1 y 200", color = Color.Red) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isBinError) Color.Red else Color.Black,
                    unfocusedBorderColor = if (isBinError) Color.Red else Color.Black
                )
            )

            // ── 5 MUESTRAS ───────────────────────────────────────────────
            for (i in 0 until 5) {
                SampleCard(
                    index = i,
                    sampleState = viewModel.samples[i],
                    afectacionOptions = viewModel.afectacionOptions,
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

            // ── TOMAR FOTO ──────────────────────────────────────────────
            SectionLabel("Foto del Registro")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(2.dp, Color.Black, RoundedCornerShape(16.dp))
                    .clickable { launchCamera() },
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.globalFotoUri != null) {
                    AsyncImage(
                        model = viewModel.globalFotoUri,
                        contentDescription = "Foto global",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("TOCAR PARA TOMAR FOTO", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }

            // ── OBSERVACIONES ────────────────────────────────────────────
            SectionLabel("Observaciones")
            OutlinedTextField(
                value = viewModel.observaciones,
                onValueChange = { viewModel.observaciones = it },
                label = { Text("Opcional", color = Color.Black) },
                textStyle = blackTextStyle,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── GUARDAR ──────────────────────────────────────────────────
            Button(
                onClick = {
                    viewModel.saveRecord { errorMsg ->
                        scope.launch {
                            if (errorMsg != null) {
                                snackbarHostState.showSnackbar(errorMsg)
                            } else {
                                snackbarHostState.showSnackbar("Registro guardado correctamente")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("GUARDAR", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
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
    var colorMenuExpanded by remember { mutableStateOf(false) }
    var translucidezMenuExpanded by remember { mutableStateOf(false) }
    var categoriaMenuExpanded by remember { mutableStateOf(false) }
    var afectacionMenuExpanded by remember { mutableStateOf(false) }

    val blackTextStyle = TextStyle(color = Color.Black, fontSize = 16.sp)
    val colorOptions = listOf("0.5", "1.0", "1.5", "2.0", "2.5", "≥3.0")
    val translucidezOptions = (1..10).map { it * 10 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            // Header
            Text(
                text = "Muestra ${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            val pesoValue = sampleState.pesoFruta.toDoubleOrNull() ?: 0.0
            val isPesoError = pesoValue > 5000
            OutlinedTextField(
                value = sampleState.pesoFruta,
                onValueChange = { onPesoChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Peso Fruta (g)", color = if (isPesoError) Color.Red else Color.Black) },
                textStyle = blackTextStyle,
                isError = isPesoError,
                supportingText = { if (isPesoError) Text("El peso no puede ser > 5000g", color = Color.Red) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isPesoError) Color.Red else Color.Black,
                    unfocusedBorderColor = if (isPesoError) Color.Red else Color.Black
                )
            )

            // Color Externo
            Text("Color Externo", fontWeight = FontWeight.Medium, color = Color.Black, fontSize = 14.sp)
            ExposedDropdownMenuBox(
                expanded = colorMenuExpanded,
                onExpandedChange = { colorMenuExpanded = !colorMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = sampleState.colorExterno,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = blackTextStyle,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color.Black
                    )
                )
                ExposedDropdownMenu(
                    expanded = colorMenuExpanded,
                    onDismissRequest = { colorMenuExpanded = false },
                    modifier = Modifier.background(DarkBeige)
                ) {
                    colorOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.Black) },
                            onClick = { onColorChange(option); colorMenuExpanded = false },
                            modifier = Modifier.background(DarkBeige)
                        )
                    }
                }
            }

            // Grados Brix
            OutlinedTextField(
                value = sampleState.gradosBrix,
                onValueChange = { onBrixChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Grados Brix (°Bx)", color = Color.Black) },
                textStyle = blackTextStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Black
                )
            )

            // ── % AVANCE TRANSLUCIDEZ ──
            Text("% Avance Translucidez", fontWeight = FontWeight.Medium, color = Color.Black, fontSize = 14.sp)
            ExposedDropdownMenuBox(
                expanded = translucidezMenuExpanded,
                onExpandedChange = { translucidezMenuExpanded = !translucidezMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = "${sampleState.avanceTranslucidez}%",
                    onValueChange = {},
                    readOnly = true,
                    textStyle = blackTextStyle,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = translucidezMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black)
                )
                ExposedDropdownMenu(
                    expanded = translucidezMenuExpanded,
                    onDismissRequest = { translucidezMenuExpanded = false },
                    modifier = Modifier.background(DarkBeige)
                ) {
                    translucidezOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("$option%", color = Color.Black) },
                            onClick = { onTranslucidezChange(option); translucidezMenuExpanded = false },
                            modifier = Modifier.background(DarkBeige)
                        )
                    }
                }
            }

            // ── CATEGORÍA ──
            Text("Categoría", fontWeight = FontWeight.Medium, color = Color.Black, fontSize = 14.sp)
            ExposedDropdownMenuBox(
                expanded = categoriaMenuExpanded,
                onExpandedChange = { categoriaMenuExpanded = !categoriaMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = sampleState.categoria,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = blackTextStyle,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoriaMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black)
                )
                ExposedDropdownMenu(
                    expanded = categoriaMenuExpanded,
                    onDismissRequest = { categoriaMenuExpanded = false },
                    modifier = Modifier.background(DarkBeige)
                ) {
                    listOf("Especial", "Industria", "Jugos").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.Black) },
                            onClick = { onCategoriaChange(option); categoriaMenuExpanded = false },
                            modifier = Modifier.background(DarkBeige)
                        )
                    }
                }
            }

            // ── AFECTACIONES (multi) ──
            Text("Afectaciones", fontWeight = FontWeight.Medium, color = Color.Black, fontSize = 14.sp)
            ExposedDropdownMenuBox(
                expanded = afectacionMenuExpanded,
                onExpandedChange = { afectacionMenuExpanded = !afectacionMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (sampleState.afectaciones.isEmpty()) "Ninguna" else sampleState.afectaciones.joinToString(", "),
                    onValueChange = {},
                    readOnly = true,
                    textStyle = blackTextStyle,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = afectacionMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().heightIn(min = 56.dp, max = 150.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black),
                    singleLine = false
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
                            onClick = { onAfectacionToggle(option) },
                            modifier = Modifier.background(DarkBeige)
                        )
                    }
                }
            }

            // Prueba Acidez toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Prueba Acidez", fontWeight = FontWeight.Medium, color = Color.Black, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (sampleState.pruebaAcidez) "Sí" else "No",
                        color = Color.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(
                        checked = sampleState.pruebaAcidez,
                        onCheckedChange = { onPruebaAcidezChange(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Black,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray
                        )
                    )
                }
            }

            // Acidez value (only shown when Prueba Acidez = Sí)
            if (sampleState.pruebaAcidez) {
                OutlinedTextField(
                    value = sampleState.acidez,
                    onValueChange = { onAcidezChange(it.filter { c -> c.isDigit() || c == '.' }) },
                    label = { Text("Gasto Hidróxido de Sodio (ml)", color = Color.Black) },
                    textStyle = blackTextStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color.Black
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        fontSize = 15.sp
    )
}
