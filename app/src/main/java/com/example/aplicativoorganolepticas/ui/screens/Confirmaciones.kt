package com.example.aplicativoorganolepticas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplicativoorganolepticas.data.OrganoRecordEntity
import com.example.aplicativoorganolepticas.data.muestras
import com.example.aplicativoorganolepticas.data.muestrasConPeso
import com.example.aplicativoorganolepticas.data.ubicacionTxt
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.viewmodel.numTxt
import java.text.SimpleDateFormat
import java.util.*

private val AvisoAmbar = Color(0xFFB26A00)

/** Todo lo que se guarda/envía de un registro, para que la persona lo revise antes de confirmar. */
@Composable
fun DetalleRegistro(r: OrganoRecordEntity) {
    val ms = r.muestras()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Bloque ${r.bloque} · Bin ${r.numeroBin}", fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Grupo: ${r.grupo.ifEmpty { "— (lo completa el servidor)" }}", fontSize = 13.sp, color = Color.Black)
        Text("Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(r.fechaRegistro))}", fontSize = 13.sp, color = Color.Black)
        Text("Ubicación: ${r.ubicacionTxt()}", fontSize = 13.sp, color = if (r.latitud == null) AvisoAmbar else Color.Black)
        Text("Foto: ${if (r.fotoPath.isEmpty()) "no" else "sí"}", fontSize = 13.sp, color = Color.Black)
        if (r.observaciones.isNotBlank()) Text("Observaciones: ${r.observaciones}", fontSize = 13.sp, color = Color.Black)
        ms.forEachIndexed { i, m ->
            if (m.tienePeso) Text(
                "• M${i + 1}: ${numTxt(m.peso)} g · Brix ${numTxt(m.brix)}" +
                    (if (m.pruebaAcidez) " · NaOH ${numTxt(m.acidez)} ml" else "") +
                    " · Color ${m.color} · Transl. ${m.translucidez}% · ${m.categoria} · ${m.afectaciones}",
                fontSize = 13.sp, color = Color.Black
            )
        }
        val sinPeso = ms.indices.filterNot { ms[it].tienePeso }.map { "M${it + 1}" }
        if (sinPeso.isNotEmpty()) Text("${sinPeso.joinToString(", ")}: sin peso, no se cargan", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.5f))
    }
}

/** Diálogo de confirmación con el detalle de uno o varios registros. */
@Composable
fun ConfirmarRegistros(
    titulo: String,
    intro: String,
    registros: List<OrganoRecordEntity>,
    textoConfirmar: String,
    textoCancelar: String = "CANCELAR",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sinGps = registros.count { it.latitud == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo, color = Color.Black) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(intro, color = Color.Black)
                if (sinGps > 0) Text(
                    if (registros.size == 1) "Sin ubicación GPS: se guarda igual, pero sin coordenadas."
                    else "$sinGps registro(s) sin ubicación GPS.",
                    color = AvisoAmbar, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                registros.forEach { r ->
                    HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                    DetalleRegistro(r)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(textoConfirmar, fontWeight = FontWeight.Bold, color = Color.Black) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(textoCancelar, color = Color.Black) } },
        containerColor = LightBeige
    )
}

/** Confirmación de envío al servidor: dice cuántos registros y muestras se cargan y con qué datos. */
@Composable
fun ConfirmarEnvio(registros: List<OrganoRecordEntity>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val muestras = registros.sumOf { it.muestrasConPeso().size }
    ConfirmarRegistros(
        titulo = if (registros.size == 1) "¿Enviar este registro?" else "¿Enviar ${registros.size} registros?",
        intro = "Se cargarán $muestras muestra(s) al servidor con estos datos:",
        registros = registros,
        textoConfirmar = "ENVIAR",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
