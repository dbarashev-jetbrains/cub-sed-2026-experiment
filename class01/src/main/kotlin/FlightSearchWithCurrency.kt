package org.jetbrains.edu.sed2026.class01

import java.time.LocalDate

// THIS IS A VISITOR PATTERN IMPLEMENTATION THAT VISITS EVERY FLIGHT RESULT AND CONVERTS PRICE TO TARGET CURRENCY
class FlightSearchWithCurrency(private val flightSearch: FlightSearch, private val currencyConverter: CurrencyConverter,
                               private val targetCurrency: String): FlightSearch {
    override fun searchFlights(
        origin: String,
        destination: String,
        departureDate: LocalDate
    ): List<FlightSearchResult> {
        return flightSearch.searchFlights(origin, destination, departureDate).map { result ->
            currencyConverter.convert(result.price, result.currency, targetCurrency)?.let {
                result.copy(price = it, currency = targetCurrency)
            } ?: result
        }
    }
}