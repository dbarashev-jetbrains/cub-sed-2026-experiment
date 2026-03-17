package org.jetbrains.edu.sed2026.class01

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class MockFlightSearch : FlightSearch {
    private val mockResults = mutableMapOf<Triple<String, String, LocalDate>, List<FlightSearchResult>>()

    fun addMockResults(origin: String, destination: String, departureDate: LocalDate, results: List<FlightSearchResult>) {
        mockResults[Triple(origin, destination, departureDate)] = results
    }

    override fun searchFlights(origin: String, destination: String, departureDate: LocalDate): List<FlightSearchResult> {
        return mockResults[Triple(origin, destination, departureDate)] ?: emptyList()
    }
}

class TripPlannerTest {

    private fun createTripPlanner(flightSearch: FlightSearch): TripPlanner {
        val hotelSearch = DummyHotelSearch()
        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        return TripPlanner(flightSearch, hotelSearch, coroutineScope)
    }

    @Test
    fun `planTrip should return cheapest flight for each segment`() {
        val mockFlightSearch = MockFlightSearch()

        // Mock flights for Prague -> Paris
        mockFlightSearch.addMockResults(
            "PRG", "CDG", LocalDate.of(2026, 3, 20),
            listOf(
                FlightSearchResult("AF1234", 150.0, "EUR", "PRG", "CDG"),
                FlightSearchResult("OK5678", 120.0, "EUR", "PRG", "CDG"),
                FlightSearchResult("BA9012", 180.0, "EUR", "PRG", "CDG")
            )
        )

        // Mock flights for Paris -> London
        mockFlightSearch.addMockResults(
            "CDG", "LHR", LocalDate.of(2026, 3, 25),
            listOf(
                FlightSearchResult("BA3456", 90.0, "GBP", "CDG", "LHR"),
                FlightSearchResult("AF7890", 75.0, "GBP", "CDG", "LHR")
            )
        )

        val tripPlanner = createTripPlanner(mockFlightSearch)
        val trip = TripPlanRequest(
            id = 1,
            userId = 1L,
            cities = listOf("Prague", "Paris", "London"),
            segments = listOf(
                TripSegment(origin = "PRG", destination = "CDG", arrivalDate = LocalDate.of(2026, 3, 20), departureDate = LocalDate.of(2026, 3, 20)),
                TripSegment(origin = "CDG", destination = "LHR", arrivalDate = LocalDate.of(2026, 3, 25), departureDate = LocalDate.of(2026, 3, 25))
            )
        )

        val results = tripPlanner.planTrip(trip).get().flightSegments

        assertEquals(2, results.size)
        assertEquals("OK5678", results[0].flightNumber)
        assertEquals(120.0, results[0].price)
        assertEquals("AF7890", results[1].flightNumber)
        assertEquals(75.0, results[1].price)
    }

    @Test
    fun `planTrip should handle segments with no available flights`() {
        val mockFlightSearch = MockFlightSearch()

        // Only mock flights for first segment, second segment has no results
        mockFlightSearch.addMockResults(
            "PRG", "CDG", LocalDate.of(2026, 3, 20),
            listOf(FlightSearchResult("AF1234", 150.0, "EUR", "PRG", "CDG"))
        )

        val tripPlanner = createTripPlanner(mockFlightSearch)
        val trip = TripPlanRequest(
            id = 1,
            userId = 1L,
            cities = listOf("Prague", "Paris", "London"),
            segments = listOf(
                TripSegment(origin = "PRG", destination = "CDG", arrivalDate = LocalDate.of(2026, 3, 20), departureDate = LocalDate.of(2026, 3, 20)),
                TripSegment(origin = "CDG", destination = "LHR", arrivalDate = LocalDate.of(2026, 3, 25), departureDate = LocalDate.of(2026, 3, 25))
            )
        )

        val results = tripPlanner.planTrip(trip).get().flightSegments

        assertEquals(1, results.size)
        assertEquals("AF1234", results[0].flightNumber)
    }

    @Test
    fun `planTrip should return empty list when no flights are available`() {
        val mockFlightSearch = MockFlightSearch()
        val tripPlanner = createTripPlanner(mockFlightSearch)

        val trip = TripPlanRequest(
            id = 1,
            userId = 1L,
            cities = listOf("Prague", "Paris"),
            segments = listOf(
                TripSegment(origin = "PRG", destination = "CDG", arrivalDate = LocalDate.of(2026, 3, 20), departureDate = LocalDate.of(2026, 3, 20))
            )
        )

        val results = tripPlanner.planTrip(trip).get().flightSegments

        assertTrue(results.isEmpty())
    }

    @Test
    fun `planTrip should handle trip with single segment`() {
        val mockFlightSearch = MockFlightSearch()

        mockFlightSearch.addMockResults(
            "PRG", "CDG", LocalDate.of(2026, 3, 20),
            listOf(
                FlightSearchResult("AF1234", 150.0, "EUR", "PRG", "CDG"),
                FlightSearchResult("OK5678", 100.0, "EUR", "PRG", "CDG")
            )
        )

        val tripPlanner = createTripPlanner(mockFlightSearch)
        val trip = TripPlanRequest(
            id = 1,
            userId = 1L,
            cities = listOf("Prague", "Paris"),
            segments = listOf(
                TripSegment(origin = "PRG", destination = "CDG", arrivalDate = LocalDate.of(2026, 3, 20), departureDate = LocalDate.of(2026, 3, 20))
            )
        )

        val results = tripPlanner.planTrip(trip).get().flightSegments

        assertEquals(1, results.size)
        assertEquals("OK5678", results[0].flightNumber)
        assertEquals(100.0, results[0].price)
    }

    @Test
    fun `planTrip should handle multiple segments and select cheapest for each`() {
        val mockFlightSearch = MockFlightSearch()

        mockFlightSearch.addMockResults(
            "PRG", "CDG", LocalDate.of(2026, 3, 20),
            listOf(
                FlightSearchResult("AF1234", 150.0, "EUR", "PRG", "CDG"),
                FlightSearchResult("OK5678", 120.0, "EUR", "PRG", "CDG")
            )
        )

        mockFlightSearch.addMockResults(
            "CDG", "LHR", LocalDate.of(2026, 3, 25),
            listOf(
                FlightSearchResult("BA3456", 90.0, "GBP", "CDG", "LHR")
            )
        )

        mockFlightSearch.addMockResults(
            "LHR", "AMS", LocalDate.of(2026, 3, 30),
            listOf(
                FlightSearchResult("KL1111", 60.0, "EUR", "LHR", "AMS"),
                FlightSearchResult("BA2222", 55.0, "EUR", "LHR", "AMS"),
                FlightSearchResult("EZ3333", 70.0, "EUR", "LHR", "AMS")
            )
        )

        val tripPlanner = createTripPlanner(mockFlightSearch)
        val trip = TripPlanRequest(
            id = 1,
            userId = 1L,
            cities = listOf("Prague", "Paris", "London", "Amsterdam"),
            segments = listOf(
                TripSegment(origin = "PRG", destination = "CDG", arrivalDate = LocalDate.of(2026, 3, 20), departureDate = LocalDate.of(2026, 3, 20)),
                TripSegment(origin = "CDG", destination = "LHR", arrivalDate = LocalDate.of(2026, 3, 25), departureDate = LocalDate.of(2026, 3, 25)),
                TripSegment(origin = "LHR", destination = "AMS", arrivalDate = LocalDate.of(2026, 3, 30), departureDate = LocalDate.of(2026, 3, 30))
            )
        )

        val results = tripPlanner.planTrip(trip).get().flightSegments

        assertEquals(3, results.size)
        assertEquals("OK5678", results[0].flightNumber)
        assertEquals(120.0, results[0].price)
        assertEquals("BA3456", results[1].flightNumber)
        assertEquals(90.0, results[1].price)
        assertEquals("BA2222", results[2].flightNumber)
        assertEquals(55.0, results[2].price)
    }
}
