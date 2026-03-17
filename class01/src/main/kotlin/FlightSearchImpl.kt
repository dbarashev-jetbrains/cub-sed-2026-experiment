package org.jetbrains.edu.sed2026.class01

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.jetbrains.edu.sed2026.DuffelFlightSearchService
import org.jetbrains.edu.sed2026.SerpFlightSearchService
import java.time.LocalDate

class SerpFlightSearchImpl(private val serpSearch: SerpFlightSearchService) : FlightSearch {
    // THIS IS A SINGLETON OBJECT
    private val gson = Gson()

    override fun searchFlights(
        origin: String,
        destination: String,
        departureDate: LocalDate
    ): List<FlightSearchResult> {
        try {
            val responseJson = serpSearch.searchSerp(origin, destination, departureDate, null) ?: "[]"
            return parseResponse(responseJson, origin, destination)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun parseResponse(json: String, origin: String, destination: String): List<FlightSearchResult> {
        val results = mutableListOf<FlightSearchResult>()
        val root = gson.fromJson(json, JsonObject::class.java)
        val bestFlights = root.getAsJsonArray("best_flights")
        if (bestFlights != null) {
            for (flightElement in bestFlights) {
                val flightObj = flightElement.asJsonObject
                val price = if (flightObj.has("price")) flightObj.get("price").asDouble else 0.0
                val currency = if (flightObj.has("currency")) flightObj.get("currency").asString else "USD"

                val flightsArray = flightObj.getAsJsonArray("flights")
                val flightNumber = if (flightsArray != null && flightsArray.size() > 0) {
                    flightsArray.get(0).asJsonObject.get("flight_number").asString
                } else "Unknown"

                results.add(FlightSearchResult(flightNumber, price, currency, origin, destination))
            }
        }
        return results
    }
}

class DuffelFlightSearchImpl(private val duffelSearch: DuffelFlightSearchService) : FlightSearch {
    // THIS IS A SINGLETON OBJECT
    private val gson = Gson()

    override fun searchFlights(
        origin: String,
        destination: String,
        departureDate: LocalDate
    ): List<FlightSearchResult> {
        try {
            val responseJson = duffelSearch.searchDuffel(origin, destination, departureDate, departureDate) ?: "[]"
            return parseResponse(responseJson, origin, destination)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun parseResponse(json: String, origin: String, destination: String): List<FlightSearchResult> {
        val results = mutableListOf<FlightSearchResult>()
        val root = gson.fromJson(json, JsonObject::class.java)
        val data = root.getAsJsonObject("data")
        if (data != null) {
            val offers = data.getAsJsonArray("offers")
            if (offers != null) {
                for (offerElement in offers) {
                    val offerObj = offerElement.asJsonObject
                    println("Next offer: $offerObj")
                    val totalAmount = if (offerObj.has("total_amount")) offerObj.get("total_amount").asDouble else 0.0
                    val currency = if (offerObj.has("total_currency")) offerObj.get("total_currency").asString else "USD"

                    val slices = offerObj.getAsJsonArray("slices")
                    val flightNumber = if (slices != null && slices.size() > 0) {
                        val segments = slices.get(0).asJsonObject.getAsJsonArray("segments")
                        if (segments != null && segments.size() > 0) {
                            segments.get(0).asJsonObject.get("operating_carrier_flight_number").asString
                        } else "Unknown"
                    } else "Unknown"

                    results.add(FlightSearchResult(flightNumber, totalAmount, currency,  origin, destination))
                }
            }
        }
        return results
    }
}
