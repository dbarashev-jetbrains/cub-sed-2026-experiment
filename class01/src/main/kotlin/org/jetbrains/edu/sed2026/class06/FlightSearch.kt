package org.jetbrains.edu.sed2026.class06

import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate


/**
 * Result of a flight search that includes HTTP status information.
 * This allows the ResilientFlightSearch to determine if the response is satisfactory.
 */
data class FlightSearchResult2(
    val statusCode: Int,
    val body: String
) {
    fun isSuccessful(): Boolean = statusCode == 200 && body.isNotBlank()
}

/**
 * Extended interface that exposes HTTP response details.
 */
interface FlightSearch2 {
    fun searchFlights(origin: String, destination: String, departureDate: LocalDate): FlightSearchResult2
}

/**
 * Serp implementation that returns status code along with body
 */
class SerpFlightSearch2(
    private val httpClient: HttpClient,
    private val apiKey: String = ""
) : FlightSearch2 {

    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): FlightSearchResult2 {
        val url = "https://serpapi.com/search.json?" +
                "stops=1&" +
                "engine=google_flights&" +
                "departure_id=$origin&" +
                "arrival_id=$destination&" +
                "outbound_date=$departureDate&" +
                "type=2&" +
                "api_key=$apiKey"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return FlightSearchResult2(response.statusCode(), response.body())
    }
}

/**
 * Duffel implementation that returns status code along with body
 */
class DuffelFlightSearch2(
    private val httpClient: HttpClient,
    private val apiKey: String = ""
) : FlightSearch2 {

    private val gson = Gson()

    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): FlightSearchResult2 {
        val requestBody = mapOf(
            "data" to mapOf(
                "slices" to listOf(
                    mapOf(
                        "origin" to origin,
                        "destination" to destination,
                        "departure_date" to departureDate.toString()
                    )
                ),
                "passengers" to listOf(
                    mapOf("type" to "adult")
                ),
                "max_connections" to "0",
                "cabin_class" to "economy"
            )
        )

        val jsonBody = gson.toJson(requestBody)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.duffel.com/air/offer_requests"))
            .header("Authorization", "Bearer $apiKey")
            .header("Duffel-Version", "v2")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return FlightSearchResult2(response.statusCode(), response.body())
    }
}

/**
 * Resilient flight search that tries Serp first, then falls back to Duffel
 * if the response is not satisfactory (not HTTP 200 or empty body).
 */
class ResilientFlightSearch2(
    private val primarySearch: FlightSearch2,
    private val fallbackSearch: FlightSearch2
) : FlightSearch2 {

    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): FlightSearchResult2 {
        // Try primary search first
        val primaryResult = try {
            primarySearch.searchFlights(origin, destination, departureDate)
        } catch (e: Exception) {
            println("Primary search failed with exception: ${e.message}")
            null
        }

        // Check if primary result is satisfactory
        if (primaryResult != null && primaryResult.isSuccessful()) {
            println("Primary search successful (status=${primaryResult.statusCode})")
            return primaryResult
        }

        // Primary failed or not satisfactory, try fallback
        println("Primary search not satisfactory (status=${primaryResult?.statusCode}, body empty=${primaryResult?.body?.isBlank()}), trying fallback...")

        val fallbackResult = try {
            fallbackSearch.searchFlights(origin, destination, departureDate)
        } catch (e: Exception) {
            println("Fallback search failed with exception: ${e.message}")
            // Return whatever we got from primary, or an error result
            return primaryResult ?: FlightSearchResult2(500, "")
        }

        return fallbackResult
    }
}
