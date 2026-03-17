package org.jetbrains.edu.sed2026.class01

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

interface PlannedTripProcessor {
    fun process(flightSegment: FlightSearchResult)
    fun process(hotel: HotelSearchResult)
}

data class PlannedTrip(val flightSegments: List<FlightSearchResult>, val hotels: List<HotelSearchResult>) {
    fun accept(processor: PlannedTripProcessor) {
        flightSegments.forEach { processor.process(it) }
        hotels.forEach { processor.process(it) }
    }
}

class TripPlannerStateMachine {
    private var flightsReady = false
    private var hotelsReady = false
    private var trip = PlannedTrip(emptyList(), emptyList())

    internal val promise = CompletableFuture<PlannedTrip>()
    internal fun setFlights(flights: List<FlightSearchResult>) = synchronized(this) {
        println("Flights are ready!")
        flightsReady = true
        trip = trip.copy(flightSegments = flights)
        maybeResolvePromise()
    }
    internal fun setHotels(hotels: List<HotelSearchResult>) = synchronized(this) {
        println("Hotels are ready!")
        hotelsReady = true
        trip = trip.copy(hotels = hotels)
        maybeResolvePromise()
    }
    private fun maybeResolvePromise() = synchronized(this) {
        if (flightsReady && hotelsReady) {
            println("Resolving the promise...")
            promise.complete(trip)
        }
    }
}

class TripPlanner(private val flightSearch: FlightSearch, private val hotelSearch: HotelSearch, private val coroutineScope: CoroutineScope) {
    val stateMachine = TripPlannerStateMachine()

    fun planTrip(trip: TripPlanRequest): CompletableFuture<PlannedTrip> {
        coroutineScope.launch {
            stateMachine.setFlights(searchFlights(trip))
        }
        coroutineScope.launch {
            stateMachine.setHotels(searchHotels(trip))
        }
        return stateMachine.promise
    }

    private suspend fun searchHotels(trip: TripPlanRequest): List<HotelSearchResult> {
        delay(Random.nextLong(1000, 3000))
        val results = mutableListOf<HotelSearchResult>()
        for (segment in trip.segments) {
            var highestScoredHotel: HotelSearchResult? = null
            hotelSearch.searchHotels(segment.destination).forEach { hotel ->
                if (highestScoredHotel == null || hotel.score10 > highestScoredHotel.score10) {
                    highestScoredHotel = hotel
                }
            }
            if (highestScoredHotel != null) {
                results.add(highestScoredHotel)
            }
        }
        return results
    }

    private suspend fun searchFlights(trip: TripPlanRequest): List<FlightSearchResult> {
        delay(Random.nextLong(1000, 3000))
        val results = mutableListOf<FlightSearchResult>()

        for (segment in trip.segments) {
            println("Searching for flight from ${segment.origin} to ${segment.destination}")
            val foundFlights = flightSearch.searchFlights(segment.origin, segment.destination, segment.departureDate)
            // Find the cheapest flight for this segment
            if (foundFlights.isNotEmpty()) {
                var cheapestFlight = foundFlights[0]
                for (flight in foundFlights) {
                    if (flight.price < cheapestFlight.price) {
                        cheapestFlight = flight
                    }
                }
                results.add(cheapestFlight)
            }
        }
        return results
    }
}
