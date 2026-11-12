package com.example.aplicativoorganolepticas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.viewmodel.OrganoViewModel
import com.example.aplicativoorganolepticas.ui.viewmodel.estadoDe
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val Verde = Color(0xFF2E7D32)
private val Ambar = Color(0xFFB26A00)
private val Rojo = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(viewModel: OrganoViewModel, onBack: () -> Unit) {
    val records by viewModel.records.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClearLogs by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    val enviados = records.count { estadoDe(it) == "Enviado" }
    val pendientes = records.count { estadoDe(it) == "Pendiente" }
    val conError = records.filter { estadoDe(it) == "Error" }
    val porEnviar = pendientes + conError.size

    fun avisar(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cargar y Descargar Datos", color = Color.Black, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LightBeige)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = LightBeige
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Resumen ──
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatBox("Total", records.size, Color.Black, Modifier.weight(1f))
                    StatBox("Enviados", enviados, Verde, Modifier.weight(1f))
                    StatBox("Pendientes", pendientes, Ambar, Modifier.weight(1f))
                    StatBox("Con error", conError.size, Rojo, Modifier.weight(1f))
                }
            }

            // ── Cargar (enviar al servidor) ──
            item {
                SectionCard("Cargar al servidor") {
                    Text(
                        if (porEnviar == 0) "Todo está enviado." else "$porEnviar registro(s) por enviar (pendientes y con error).",
                        color = Color.Black, fontSize = 14.sp
                    )
                    if (viewModel.isUploading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.Black)
                    }
                    Button(
                        onClick = { viewModel.uploadAllUnsyncedRecords { avisar(it) } },
                        enabled = porEnviar > 0 && !viewModel.isUploading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(if (viewModel.isUploading) "ENVIANDO..." else "ENVIAR DATOS", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Descargar (CSV en Descargas del equipo) ──
            item {
                SectionCard("Descargar datos del dispositivo") {
                    Text("Genera un CSV en la carpeta Descargas.", color = Color.Black.copy(alpha = 0.7f), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownloadButton("Todos (${records.size})", Modifier.weight(1f)) { viewModel.exportToCsv(context) }
                        DownloadButton("Enviados ($enviados)", Modifier.weight(1f)) { viewModel.exportToCsv(context, "Enviado") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownloadButton("Pendientes ($pendientes)", Modifier.weight(1f)) { viewModel.exportToCsv(context, "Pendiente") }
                        DownloadButton("Con error (${conError.size})", Modifier.weight(1f)) { viewModel.exportToCsv(context, "Error") }
                    }
                    Text("Respaldo completo de la app (registros y logs) para recuperar datos:",
                        color = Color.Black.copy(alpha = 0.7f), fontSize = 13.sp)
                    DownloadButton("Respaldo JSON", Modifier.fillMaxWidth()) { viewModel.descargarRespaldoJson(context) }
                }
            }

            // ── Registros con error ──
            if (conError.isNotEmpty()) {
                item { SectionTitle("Registros con error") }
                items(conError, key = { "err_${it.id}" }) { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Bloque ${r.bloque} · Bin ${r.numeroBin}", fontWeight = FontWeight.Bold, color = Color.Black)
                                Text(sdf.format(Date(r.fechaRegistro)), fontSize = 12.sp, color = Color.Black.copy(alpha = 0.6f))
                                Text(r.ultimoError, fontSize = 12.sp, color = Rojo)
                            }
                            TextButton(
                                onClick = { viewModel.uploadRecord(r) { err -> avisar(err ?: "Registro enviado") } },
                                enabled = !viewModel.isUploading
                            ) { Text("Reintentar", color = Color.Black) }
                        }
                    }
                }
            }

            // ── Logs de envío ──
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Logs de envío", Modifier.weight(1f))
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = { confirmClearLogs = true }) { Text("Limpiar", color = Rojo) }
                    }
                }
            }
            if (logs.isEmpty()) {
                item {
                    Text("Aún no hay envíos registrados.", color = Color.Black.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            items(logs, key = { "log_${it.id}" }) { log ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (log.exito) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = if (log.exito) "Éxito" else "Error",
                        tint = if (log.exito) Verde else Rojo,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${sdf.format(Date(log.fecha))} · Bloque ${log.bloque} · Bin ${log.numeroBin}",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                        Text(log.mensaje, fontSize = 12.sp, color = if (log.exito) Color.Black.copy(alpha = 0.6f) else Rojo)
                    }
                }
                HorizontalDivider(color = Color.Black.copy(alpha = 0.08f))
            }
        }

        if (confirmClearLogs) {
            AlertDialog(
                onDismissRequest = { confirmClearLogs = false },
                title = { Text("Limpiar logs", color = Color.Black) },
                text = { Text("Se borra solo el historial de envíos; los registros capturados no se tocan.", color = Color.Black) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearLogs(); confirmClearLogs = false }) {
                        Text("LIMPIAR", color = Rojo, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClearLogs = false }) { Text("CANCELAR", color = Color.Black) } },
                containerColor = LightBeige
            )
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = Color.Black.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black, modifier = modifier)
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
private fun DownloadButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.Black, fontSize = 13.sp, maxLines = 1)
    }
}
