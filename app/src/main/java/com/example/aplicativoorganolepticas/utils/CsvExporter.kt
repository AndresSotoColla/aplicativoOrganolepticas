package com.example.aplicativoorganolepticas.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.aplicativoorganolepticas.data.OrganoRecordEntity
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    fun exportAndShare(context: Context, records: List<OrganoRecordEntity>) {
        if (records.isEmpty()) {
            Toast.makeText(context, "No hay registros para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val bom = "\uFEFF"
        val sdfFull = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sdfShort = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val deviceUser = "${Build.MANUFACTURER} ${Build.MODEL}"

        val header = "fecha_registro,bloque,numero_bin,observaciones," +
                "m1_peso,m1_color,m1_brix,m1_prueba_acidez,m1_acidez,m1_translucidez,m1_categoria,m1_afectaciones," +
                "m2_peso,m2_color,m2_brix,m2_prueba_acidez,m2_acidez,m2_translucidez,m2_categoria,m2_afectaciones," +
                "m3_peso,m3_color,m3_brix,m3_prueba_acidez,m3_acidez,m3_translucidez,m3_categoria,m3_afectaciones," +
                "m4_peso,m4_color,m4_brix,m4_prueba_acidez,m4_acidez,m4_translucidez,m4_categoria,m4_afectaciones," +
                "m5_peso,m5_color,m5_brix,m5_prueba_acidez,m5_acidez,m5_translucidez,m5_categoria,m5_afectaciones,usuario\n"

        val sb = StringBuilder()
        records.forEach { r ->
            sb.append("${sdfShort.format(Date(r.fechaRegistro))},")
            sb.append("${r.bloque},")
            sb.append("${r.numeroBin},")
            sb.append("\"${r.observaciones}\",")
            // Muestra 1
            sb.append("${r.m1PesoFruta},${r.m1ColorExterno},${r.m1GradosBrix},${if (r.m1PruebaAcidez) "Sí" else "No"},${r.m1Acidez},${r.m1AvanceTranslucidez},${r.m1Categoria},\"${r.m1Afectaciones}\",")
            // Muestra 2
            sb.append("${r.m2PesoFruta},${r.m2ColorExterno},${r.m2GradosBrix},${if (r.m2PruebaAcidez) "Sí" else "No"},${r.m2Acidez},${r.m2AvanceTranslucidez},${r.m2Categoria},\"${r.m2Afectaciones}\",")
            // Muestra 3
            sb.append("${r.m3PesoFruta},${r.m3ColorExterno},${r.m3GradosBrix},${if (r.m3PruebaAcidez) "Sí" else "No"},${r.m3Acidez},${r.m3AvanceTranslucidez},${r.m3Categoria},\"${r.m3Afectaciones}\",")
            // Muestra 4
            sb.append("${r.m4PesoFruta},${r.m4ColorExterno},${r.m4GradosBrix},${if (r.m4PruebaAcidez) "Sí" else "No"},${r.m4Acidez},${r.m4AvanceTranslucidez},${r.m4Categoria},\"${r.m4Afectaciones}\",")
            // Muestra 5
            sb.append("${r.m5PesoFruta},${r.m5ColorExterno},${r.m5GradosBrix},${if (r.m5PruebaAcidez) "Sí" else "No"},${r.m5Acidez},${r.m5AvanceTranslucidez},${r.m5Categoria},\"${r.m5Afectaciones}\",")
            sb.append("$deviceUser\n")
        }

        val fileName = "Organolepticas_${System.currentTimeMillis()}.csv"
        val content = bom + header + sb.toString()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                uri?.let {
                    resolver.openOutputStream(it).use { out ->
                        out?.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
                } ?: Toast.makeText(context, "Error al crear el archivo", Toast.LENGTH_SHORT).show()
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(dir, fileName)
                file.writeText(content, Charsets.UTF_8)
                Toast.makeText(context, "Guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
