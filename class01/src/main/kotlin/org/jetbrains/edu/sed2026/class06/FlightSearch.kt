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
data class BadFlightSearchResult(
    val statusCode: Int,
    val body: String
) {
    fun isSuccessful(): Boolean = statusCode == 200 && body.isNotBlank()
}

/**
 * Extended interface that exposes HTTP response details.
 */
interface BadFlightSearch {
    fun searchFlights(origin: String, destination: String, departureDate: LocalDate): BadFlightSearchResult
}

/**
 * Serp implementation that returns status code along with body
 */
class BadSerpFlightSearch(
    private val httpClient: HttpClient,
    private val apiKey: String = ""
) : BadFlightSearch {

    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): BadFlightSearchResult {
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
        return BadFlightSearchResult(response.statusCode(), response.body())
    }
}

/**
 * Duffel implementation that returns status code along with body
 */
class BadDuffelFlightSearch(
    private val httpClient: HttpClient,
    private val apiKey: String = ""
) : BadFlightSearch {

    private val gson = Gson()

    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): BadFlightSearchResult {
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
        return BadFlightSearchResult(response.statusCode(), response.body())
    }
}

/**
 * Resilient flight search that tries Serp first, then falls back to Duffel
 * if the response is not satisfactory (not HTTP 200 or empty body).
 */
class BadResilientFlightSearch(
    private val primarySearch: BadFlightSearch,
    private val fallbackSearch: BadFlightSearch
) : BadFlightSearch {

    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): BadFlightSearchResult {
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
            return primaryResult ?: BadFlightSearchResult(500, "")
        }

        return fallbackResult
    }
}
