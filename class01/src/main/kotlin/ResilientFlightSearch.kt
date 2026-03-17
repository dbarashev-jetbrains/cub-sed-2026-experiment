package org.jetbrains.edu.sed2026.class01

import java.time.LocalDate

// THIS IS A COMPOSITE FLIGHT SEARCH
class ResilientFlightSearch(private val primaryFlightSearch: FlightSearch, private val secondaryFlightSearch: FlightSearch): FlightSearch {
    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): List<FlightSearchResult> {
        // 1. Try Primary Flight Search first.
        var foundFlights = runSearch(primaryFlightSearch, origin, destination, departureDate)

        // 2. Fallback to the secondary if primary fails or returns no results
        if (foundFlights.isEmpty()) {
            println("Primary Search failed or no results, trying Fallback...")
            foundFlights = runSearch(secondaryFlightSearch, origin, destination, departureDate)
        }

        return foundFlights
    }

    private fun runSearch(search: FlightSearch, origin: String, destination: String, departureDate: LocalDate): List<FlightSearchResult> {
        try {
            return search.searchFlights(origin, destination, departureDate)
        } catch (e: Exception) {
            println("Error calling ${search.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        return emptyList()
    }
}