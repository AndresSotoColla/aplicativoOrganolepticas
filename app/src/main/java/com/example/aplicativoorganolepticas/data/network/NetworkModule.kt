package com.example.aplicativoorganolepticas.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

data class CronogramaItem(
    val bloque: String,
    val fecha_actividad: String = "",
    val observaciones: String = ""
)

data class OrganoUploadRequest(
    val fecha_registro: String,
    val bloque: String,
    val numero_bin: Int,
    val observaciones: String,
    val usuario: String,
    val fotografia: String? = null,
    // Muestras
    val m1_peso: Double, val m1_color: String, val m1_brix: Double, val m1_acidez: Double, val m1_translucidez: Int, val m1_categoria: String, val m1_afectaciones: String,
    val m2_peso: Double, val m2_color: String, val m2_brix: Double, val m2_acidez: Double, val m2_translucidez: Int, val m2_categoria: String, val m2_afectaciones: String,
    val m3_peso: Double, val m3_color: String, val m3_brix: Double, val m3_acidez: Double, val m3_translucidez: Int, val m3_categoria: String, val m3_afectaciones: String,
    val m4_peso: Double, val m4_color: String, val m4_brix: Double, val m4_acidez: Double, val m4_translucidez: Int, val m4_categoria: String, val m4_afectaciones: String,
    val m5_peso: Double, val m5_color: String, val m5_brix: Double, val m5_acidez: Double, val m5_translucidez: Int, val m5_categoria: String, val m5_afectaciones: String
)

data class UploadResponse(
    val status: String,
    val registros_insertados: Int? = null,
    val error: String? = null
)

interface OrganoApiService {
    @GET("consultor/api/cronograma_semana_organolepticas")
    suspend fun getCronograma(): List<CronogramaItem>

    @retrofit2.http.POST("consultor/cargue_json_organolepticas")
    suspend fun uploadRecord(@retrofit2.http.Body request: OrganoUploadRequest): UploadResponse
}

object NetworkModule {
    private const val BASE_URL = "https://interno.control.agricolaguapa.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val apiService: OrganoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OrganoApiService::class.java)
    }
}
