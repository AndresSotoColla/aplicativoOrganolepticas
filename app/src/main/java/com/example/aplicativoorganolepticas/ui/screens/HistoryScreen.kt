package com.example.aplicativoorganolepticas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplicativoorganolepticas.data.OrganoRecordEntity
import com.example.aplicativoorganolepticas.ui.theme.DarkBeige
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.viewmodel.OrganoViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: OrganoViewModel,
    onBack: () -> Unit
) {
    val records by viewModel.records.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<OrganoRecordEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Historial de Registros", color = Color.Black, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.Black)
                    }
                },
                actions = {
                    if (records.any { !it.isSynced }) {
                        IconButton(
                            onClick = {
                                viewModel.uploadAllUnsyncedRecords { msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            enabled = !viewModel.isUploading
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Sincronizar todo", tint = Color.Black)
                        }
                    }
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar todo", tint = Color.Black)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LightBeige)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = LightBeige
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (viewModel.isUploading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Color.Black,
                    trackColor = Color.Black.copy(alpha = 0.1f)
                )
            }
            if (records.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Black.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No hay registros guardados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(records) { record ->
                        OrganoRecordItem(
                            record = record,
                            onDelete = { recordToDelete = record },
                            onUpload = {
                                viewModel.uploadRecord(record) { error ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(error ?: "Registro sincronizado")
                                    }
                                }
                            },
                            isUploading = viewModel.isUploading
                        )
                    }
                }
            }

            // Individual delete dialog
            if (recordToDelete != null) {
                AlertDialog(
                    onDismissRequest = { recordToDelete = null },
                    title = { Text("Borrar registro", color = Color.Black) },
                    text = { Text("¿Deseas eliminar el registro del bloque ${recordToDelete?.bloque} – Bin ${recordToDelete?.numeroBin}?", color = Color.Black) },
                    confirmButton = {
                        TextButton(onClick = {
                            recordToDelete?.let { viewModel.deleteRecord(it) }
                            recordToDelete = null
                        }) { Text("BORRAR", color = Color.Red, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { recordToDelete = null }) { Text("CANCELAR", color = Color.Black) }
                    },
                    containerColor = LightBeige
                )
            }

            // Bulk delete dialog
            if (showDeleteAllDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllDialog = false },
                    title = { Text("Confirmar borrado", color = Color.Black) },
                    text = { Text("¿Borrar todos los registros? Esta acción no se puede deshacer.", color = Color.Black) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteAllRecords()
                            showDeleteAllDialog = false
                        }) { Text("BORRAR TODO", color = Color.Red, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAllDialog = false }) { Text("CANCELAR", color = Color.Black) }
                    },
                    containerColor = LightBeige
                )
            }
        }
    }
}

@Composable
fun OrganoRecordItem(
    record: OrganoRecordEntity,
    onDelete: () -> Unit,
    onUpload: () -> Unit,
    isUploading: Boolean
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bloque: ${record.bloque}  •  Bin: ${record.numeroBin}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = sdf.format(Date(record.fechaRegistro)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Red.copy(alpha = 0.7f))
                }
            }

            // Sync Status & Upload Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.isSynced) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Sincronizado",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sincronizado", color = Color(0xFF4CAF50), fontSize = 12.sp)
                } else {
                    TextButton(
                        onClick = onUpload,
                        enabled = !isUploading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Subir a API", color = Color.Black, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.Black.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            // Global Photo & Observations
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (record.fotoPath.isNotEmpty()) {
                    AsyncImage(
                        model = record.fotoPath,
                        contentDescription = "Foto registro",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Observaciones:", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
                    Text(
                        text = if (record.observaciones.isBlank()) "Sin observaciones" else record.observaciones,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.Black.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            // Samples summary
            Text("Muestras:", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))

            val sampleData = listOf(
                Triple(record.m1PesoFruta, record.m1ColorExterno, record.m1GradosBrix),
                Triple(record.m2PesoFruta, record.m2ColorExterno, record.m2GradosBrix),
                Triple(record.m3PesoFruta, record.m3ColorExterno, record.m3GradosBrix),
                Triple(record.m4PesoFruta, record.m4ColorExterno, record.m4GradosBrix),
                Triple(record.m5PesoFruta, record.m5ColorExterno, record.m5GradosBrix)
            )
            val acidezData = listOf(
                Pair(record.m1PruebaAcidez, record.m1Acidez),
                Pair(record.m2PruebaAcidez, record.m2Acidez),
                Pair(record.m3PruebaAcidez, record.m3Acidez),
                Pair(record.m4PruebaAcidez, record.m4Acidez),
                Pair(record.m5PruebaAcidez, record.m5Acidez)
            )
            val translucidezData = listOf(record.m1AvanceTranslucidez, record.m2AvanceTranslucidez, record.m3AvanceTranslucidez, record.m4AvanceTranslucidez, record.m5AvanceTranslucidez)
            val categoriaData = listOf(record.m1Categoria, record.m2Categoria, record.m3Categoria, record.m4Categoria, record.m5Categoria)
            val afectacionesData = listOf(record.m1Afectaciones, record.m2Afectaciones, record.m3Afectaciones, record.m4Afectaciones, record.m5Afectaciones)

            sampleData.forEachIndexed { i, (peso, color, brix) ->
                val (pruebaAcidez, acidez) = acidezData[i]
                val acidezStr = if (pruebaAcidez) " | Acidez: $acidez%" else ""
                val t = translucidezData[i]
                val cat = categoriaData[i]
                val af = afectacionesData[i]

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "M${i + 1}: ${peso}g | Brix: $brix°$acidezStr",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Color: $color | Trans.: $t% | Cat: $cat",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black
                    )
                    Text(
                        text = "Afectaciones: $af",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black
        )
    }
}
