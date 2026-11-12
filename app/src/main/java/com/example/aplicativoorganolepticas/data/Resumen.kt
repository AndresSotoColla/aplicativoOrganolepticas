package com.example.aplicativoorganolepticas.data

/** Estadística (promedio, mínimo, máximo) de una variable; null si no hay datos. */
data class Estadistica(val n: Int, val promedio: Double, val min: Double, val max: Double)

fun List<Double>.estadistica(): Estadistica? =
    if (isEmpty()) null else Estadistica(size, average(), min(), max())

data class ResumenBloque(val bloque: String, val bines: Int, val muestras: Int, val brix: Double?, val peso: Double?)

data class Resumen(
    val bines: Int,
    val bloques: Int,
    val muestras: Int,
    val muestrasSinPeso: Int,
    val peso: Estadistica?,
    val brix: Estadistica?,
    val translucidez: Estadistica?,
    val color: Estadistica?,
    val gastoNaOH: Estadistica?,      // ml, solo muestras con prueba de acidez
    val acidezPct: Estadistica?,      // % = ml * 0.1 * 0.064 / 5 * 100 (misma fórmula del servidor)
    val categorias: List<Pair<String, Int>>,
    val afectaciones: List<Pair<String, Int>>,   // por muestra, sin "Ninguna", de mayor a menor
    val muestrasSinAfectacion: Int,
    val enviados: Int,
    val pendientes: Int,
    val conError: Int,
    val fechaMin: Long?,
    val fechaMax: Long?,
    val porBloque: List<ResumenBloque>
)

/** Resumen de los registros dados. Solo cuentan las muestras con peso (las demás no se cargan). */
fun resumir(registros: List<OrganoRecordEntity>): Resumen {
    val ms = registros.flatMap { it.muestrasConPeso() }
    val conAcidez = ms.filter { it.pruebaAcidez && it.acidez > 0.0 }
    val afect = ms.flatMap { m -> m.afectaciones.split(",").map { it.trim() }.filter { it.isNotEmpty() && !it.equals("Ninguna", true) } }
    return Resumen(
        bines = registros.size,
        bloques = registros.map { it.bloque }.distinct().size,
        muestras = ms.size,
        muestrasSinPeso = registros.size * 5 - ms.size,
        peso = ms.map { it.peso }.estadistica(),
        brix = ms.map { it.brix }.filter { it > 0.0 }.estadistica(),
        translucidez = ms.map { it.translucidez.toDouble() }.estadistica(),
        color = ms.mapNotNull { it.color.replace("≥", "").toDoubleOrNull() }.estadistica(),
        gastoNaOH = conAcidez.map { it.acidez }.estadistica(),
        acidezPct = conAcidez.map { it.acidez * 0.1 * 0.064 / 5 * 100 }.estadistica(),
        categorias = ms.groupingBy { it.categoria }.eachCount().toList().sortedByDescending { it.second },
        afectaciones = afect.groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
        muestrasSinAfectacion = ms.count { m -> m.afectaciones.split(",").all { it.trim().isEmpty() || it.trim().equals("Ninguna", true) } },
        enviados = registros.count { it.isSynced },
        pendientes = registros.count { !it.isSynced && it.ultimoError.isEmpty() },
        conError = registros.count { !it.isSynced && it.ultimoError.isNotEmpty() },
        fechaMin = registros.minOfOrNull { it.fechaRegistro },
        fechaMax = registros.maxOfOrNull { it.fechaRegistro },
        porBloque = registros.groupBy { it.bloque }.map { (b, rs) ->
            val mb = rs.flatMap { it.muestrasConPeso() }
            ResumenBloque(
                bloque = b,
                bines = rs.size,
                muestras = mb.size,
                brix = mb.map { it.brix }.filter { it > 0.0 }.takeIf { it.isNotEmpty() }?.average(),
                peso = mb.map { it.peso }.takeIf { it.isNotEmpty() }?.average()
            )
        }.sortedBy { it.bloque }
    )
}
