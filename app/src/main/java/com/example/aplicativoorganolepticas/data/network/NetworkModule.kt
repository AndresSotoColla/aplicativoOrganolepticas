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

interface OrganoApiService {
    @GET("consultor/api/cronograma_semana_organolepticas")
    suspend fun getCronograma(): List<CronogramaItem>
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
