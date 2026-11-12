package com.example.aplicativoorganolepticas

import com.example.aplicativoorganolepticas.data.OrganoRecordEntity
import com.example.aplicativoorganolepticas.data.muestrasConPeso
import com.example.aplicativoorganolepticas.data.resumir
import com.example.aplicativoorganolepticas.ui.viewmodel.cambiosEntre
import org.junit.Assert.assertEquals
import org.junit.Test

class OrganoLogicaTest {
    private val base = OrganoRecordEntity(
        fechaRegistro = 0L, bloque = "PC440124", numeroBin = 7, grupo = "GF1915",
        m1PesoFruta = 1500.0, m1GradosBrix = 14.0, m1PruebaAcidez = true, m1Acidez = 5.0, m1Afectaciones = "Cuello, Off Color",
        m2PesoFruta = 1700.0, m2GradosBrix = 16.0, m2Afectaciones = "Ninguna"
        // M3..M5 sin peso: no se cargan
    )

    @Test
    fun cambios_listan_solo_lo_modificado() {
        val editado = base.copy(numeroBin = 8, m2GradosBrix = 15.5)
        assertEquals(listOf("Bin: 7 → 8", "M2 brix: 16 → 15.5"), cambiosEntre(base, editado))
        assertEquals(emptyList<String>(), cambiosEntre(base, base.copy()))
    }

    @Test
    fun resumen_ignora_muestras_sin_peso() {
        assertEquals(2, base.muestrasConPeso().size)
        val r = resumir(listOf(base, base.copy(id = 2, bloque = "PC010123")))
        assertEquals(2, r.bines)
        assertEquals(2, r.bloques)
        assertEquals(4, r.muestras)
        assertEquals(6, r.muestrasSinPeso)
        assertEquals(15.0, r.brix!!.promedio, 1e-9)
        assertEquals(0.64, r.acidezPct!!.promedio, 1e-9)   // 5 ml * 0.1 * 0.064 / 5 * 100
        assertEquals(listOf("Cuello" to 2, "Off Color" to 2), r.afectaciones)
        assertEquals(2, r.muestrasSinAfectacion)
    }
}
