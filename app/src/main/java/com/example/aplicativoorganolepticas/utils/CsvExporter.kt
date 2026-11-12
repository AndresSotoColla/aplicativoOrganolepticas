package com.example.aplicativoorganolepticas.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.aplicativoorganolepticas.data.OrganoRecordEntity
import com.example.aplicativoorganolepticas.data.SyncLogEntity
import com.example.aplicativoorganolepticas.data.muestras
import com.example.aplicativoorganolepticas.ui.viewmodel.estadoDe
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.*

/** Contenido del respaldo JSON: todo lo que hay en el equipo. */
data class RespaldoOrganolepticas(
    val generado: String,
    val dispositivo: String,
    val registros: List<OrganoRecordEntity>,
    val logs: List<SyncLogEntity>
)

object CsvExporter {
    private val deviceUser get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun exportAndShare(context: Context, records: List<OrganoRecordEntity>, etiqueta: String = "Todos") {
        if (records.isEmpty()) {
            Toast.makeText(context, "No hay registros ($etiqueta) para descargar", Toast.LENGTH_SHORT).show()
            return
        }
        val sdfShort = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        fun q(s: String) = "\"${s.replace('"', '\'')}\""

        val header = "fecha_registro,bloque,grupo,numero_bin,observaciones," +
                (1..5).joinToString(",") { "m${it}_peso,m${it}_color,m${it}_brix,m${it}_prueba_acidez,m${it}_acidez,m${it}_translucidez,m${it}_categoria,m${it}_afectaciones" } +
                ",usuario,estado,ultimo_error,latitud,longitud,precision_gps_m\n"

        val sb = StringBuilder()
        records.forEach { r ->
            sb.append("${sdfShort.format(Date(r.fechaRegistro))},${r.bloque},${r.grupo},${r.numeroBin},${q(r.observaciones)},")
            r.muestras().forEach { m ->
                sb.append("${m.peso},${m.color},${m.brix},${if (m.pruebaAcidez) "Sí" else "No"},${m.acidez},${m.translucidez},${m.categoria},${q(m.afectaciones)},")
            }
            sb.append("$deviceUser,${estadoDe(r)},${q(r.ultimoError)},${r.latitud ?: ""},${r.longitud ?: ""},${r.precisionGps ?: ""}\n")
        }
        guardarEnDescargas(context, "Organolepticas_${etiqueta}_${System.currentTimeMillis()}.csv", "text/csv",
            "﻿" + header + sb)
    }

    fun exportarRespaldoJson(context: Context, registros: List<OrganoRecordEntity>, logs: List<SyncLogEntity>) {
        if (registros.isEmpty() && logs.isEmpty()) {
            Toast.makeText(context, "No hay datos en el equipo para respaldar", Toast.LENGTH_SHORT).show()
            return
        }
        val respaldo = RespaldoOrganolepticas(
            generado = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            dispositivo = deviceUser,
            registros = registros,
            logs = logs
        )
        val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(RespaldoOrganolepticas::class.java).indent("  ").toJson(respaldo)
        guardarEnDescargas(context, "Organolepticas_respaldo_${System.currentTimeMillis()}.json", "application/json", json)
    }

    private fun guardarEnDescargas(context: Context, fileName: String, mime: String, content: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (uri == null) {
                    Toast.makeText(context, "Error al crear el archivo", Toast.LENGTH_SHORT).show()
                    return
                }
                resolver.openOutputStream(uri).use { out -> out?.write(content.toByteArray(Charsets.UTF_8)) }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                java.io.File(dir, fileName).writeText(content, Charsets.UTF_8)
            }
            Toast.makeText(context, "Guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
