package org.jetbrains.edu.sed2026

import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate

class SerpFlightSearchService(private val apiKey: String = "") {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun searchSerp(
        origin: String,
        destination: String,
        departureDate: LocalDate,
        returnDate: LocalDate? = null
    ): String? {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("serpapi.com")
            .addPathSegment("search.json")
            .addQueryParameter("stops", "1")
            .addQueryParameter("engine", "google_flights")
            .addQueryParameter("departure_id", origin)
            .addQueryParameter("arrival_id", destination)
            .addQueryParameter("outbound_date", departureDate.toString())
            .addQueryParameter("api_key", apiKey)

        if (returnDate != null) {
            urlBuilder.addQueryParameter("return_date", returnDate.toString())
            urlBuilder.addQueryParameter("type", "1") // Round trip
        } else {
            urlBuilder.addQueryParameter("type", "2") // One way
        }

        val url = urlBuilder.build()
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}
