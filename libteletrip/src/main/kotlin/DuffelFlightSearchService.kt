package org.jetbrains.edu.sed2026

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

class DuffelFlightSearchService(private val apiKey: String = "") {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://api.duffel.com"
    
    fun searchDuffel(
        origin: String,
        destination: String,
        departureDate: LocalDate,
        returnDate: LocalDate
    ): String? {
        val requestBody = mapOf(
            "data" to mapOf(
                "slices" to listOf(
                    mapOf(
                        "origin" to origin,
                        "destination" to destination,
                        "departure_date" to departureDate.toString()
                    ),
                ),
                "passengers" to listOf(
                    mapOf("type" to "adult")
                ),
                "max_connections" to "0",
                "cabin_class" to "economy"
            )
        )
        
        val jsonBody = gson.toJson(requestBody)
        val mediaType = "application/json".toMediaType()
        
        val request = Request.Builder()
            .url("$baseUrl/air/offer_requests")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Duffel-Version", "v2")
            .addHeader("Accept", "application/json")
            .post(jsonBody.toRequestBody(mediaType))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("Error: ${response.code} - ${response.body?.string()}")
                return null
            }
            return response.body?.string()
        }
    }
}
