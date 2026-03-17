package org.jetbrains.edu.sed2026.class01

import java.time.LocalDate

typealias Money = Double

data class FlightSearchResult(
    val flightNumber: String,
    val price: Money,
    val currency: String,
    val origin: String,
    val destination: String
)


interface FlightSearch {
    fun searchFlights(origin: String, destination: String, departureDate: LocalDate): List<FlightSearchResult>
}
