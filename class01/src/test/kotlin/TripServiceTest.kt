package org.jetbrains.edu.sed2026.class01

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class InMemoryStorage : Storage {
    private val trips = mutableListOf<TripPlanRequest>()
    private var nextId = 1
    private val snapshots = mutableMapOf<Long, AddTripDialogSnapshot>()

    override fun getTrips(userId: Long): List<TripPlanRequest> {
        return trips.filter { it.userId == userId }
    }

    override fun addTrip(userId: Long, cities: List<String>, segments: List<TripSegment>): Int {
        val tripId = nextId++
        val tripSegments = segments.map { it.copy(tripId = tripId) }
        trips.add(TripPlanRequest(tripId, userId, cities, tripSegments))
        return tripId
    }

    override fun saveSnapshot(snapshot: AddTripDialogSnapshot) {
        snapshots[snapshot.userId] = snapshot
    }

    override fun getSnapshot(userId: Long): AddTripDialogSnapshot? {
        return snapshots[userId]
    }

    override fun removeSnapshot(userId: Long) {
        snapshots.remove(userId)
    }
}

class TripServiceTest {

    @Test
    fun `addTrip should return Success when no overlapping trips exist`() {
        val storage = InMemoryStorage()
        val tripService = TripService(storage)

        val cities = listOf("Paris", "London")
        val travelDate = LocalDate.of(2026, 3, 10)
        val segments = listOf(
            TripSegment(origin = "Paris", destination = "London", arrivalDate = travelDate, departureDate = travelDate)
        )

        val result = tripService.addTrip(1L, cities, segments)

        assertTrue(result is TripAddResult.Success)
        assertEquals(cities, (result as TripAddResult.Success).trip.cities)
    }

    @Test
    fun `addTrip should return Warning when trips overlap`() {
        val storage = InMemoryStorage()
        val tripService = TripService(storage)

        // Add first trip: Paris to London on March 10
        val date1 = LocalDate.of(2026, 3, 10)
        tripService.addTrip(
            userId = 1L,
            cities = listOf("Paris", "London"),
            segments = listOf(TripSegment(origin = "Paris", destination = "London", arrivalDate = date1, departureDate = date1))
        )

        // Add overlapping trip: Berlin to Amsterdam on March 10 (same date)
        val result = tripService.addTrip(
            userId = 1L,
            cities = listOf("Berlin", "Amsterdam"),
            segments = listOf(TripSegment(origin = "Berlin", destination = "Amsterdam", arrivalDate = date1, departureDate = date1))
        )

        assertTrue(result is TripAddResult.Warning)
        val warning = result as TripAddResult.Warning
        assertTrue(warning.message.contains("Paris"))
        assertTrue(warning.message.contains("London"))
    }

    @Test
    fun `addTrip should return Warning when new trip completely contains existing trip`() {
        val storage = InMemoryStorage()
        val tripService = TripService(storage)

        tripService.addTrip(
            userId = 1L,
            cities = listOf("Paris"),
            segments = listOf(TripSegment(origin = "Paris", destination = "Paris", arrivalDate = LocalDate.of(2026, 3, 15), departureDate = LocalDate.of(2026, 3, 20)))
        )

        val result = tripService.addTrip(
            userId = 1L,
            cities = listOf("London"),
            segments = listOf(TripSegment(origin = "London", destination = "London", arrivalDate = LocalDate.of(2026, 3, 10), departureDate = LocalDate.of(2026, 3, 25)))
        )

        assertTrue(result is TripAddResult.Warning)
    }

    @Test
    fun `addTrip should return Success when trips do not overlap`() {
        val storage = InMemoryStorage()
        val tripService = TripService(storage)

        tripService.addTrip(
            userId = 1L,
            cities = listOf("Paris"),
            segments = listOf(TripSegment(origin = "Paris", destination = "Paris", arrivalDate = LocalDate.of(2026, 3, 10), departureDate = LocalDate.of(2026, 3, 15)))
        )

        val result = tripService.addTrip(
            userId = 1L,
            cities = listOf("London"),
            segments = listOf(TripSegment(origin = "London", destination = "London", arrivalDate = LocalDate.of(2026, 3, 16), departureDate = LocalDate.of(2026, 3, 25)))
        )

        assertTrue(result is TripAddResult.Success)
    }

    @Test
    fun `addTrip should only check trips for the same user`() {
        val storage = InMemoryStorage()
        val tripService = TripService(storage)

        tripService.addTrip(
            userId = 1L,
            cities = listOf("Paris"),
            segments = listOf(TripSegment(origin = "Paris", destination = "Paris", arrivalDate = LocalDate.of(2026, 3, 10), departureDate = LocalDate.of(2026, 3, 20)))
        )

        val result = tripService.addTrip(
            userId = 2L,
            cities = listOf("London"),
            segments = listOf(TripSegment(origin = "London", destination = "London", arrivalDate = LocalDate.of(2026, 3, 15), departureDate = LocalDate.of(2026, 3, 25)))
        )

        assertTrue(result is TripAddResult.Success)
    }

    @Test
    fun `addTrip should warn about multiple overlapping trips`() {
        val storage = InMemoryStorage()
        val tripService = TripService(storage)

        tripService.addTrip(
            userId = 1L,
            cities = listOf("Paris"),
            segments = listOf(TripSegment(origin = "Paris", destination = "Paris", arrivalDate = LocalDate.of(2026, 3, 10), departureDate = LocalDate.of(2026, 3, 20)))
        )

        tripService.addTrip(
            userId = 1L,
            cities = listOf("London"),
            segments = listOf(TripSegment(origin = "London", destination = "London", arrivalDate = LocalDate.of(2026, 3, 15), departureDate = LocalDate.of(2026, 3, 25)))
        )

        val result = tripService.addTrip(
            userId = 1L,
            cities = listOf("Berlin"),
            segments = listOf(TripSegment(origin = "Berlin", destination = "Berlin", arrivalDate = LocalDate.of(2026, 3, 12), departureDate = LocalDate.of(2026, 3, 22)))
        )

        assertTrue(result is TripAddResult.Warning)
        val warning = result as TripAddResult.Warning
        assertTrue(warning.message.contains("Paris"))
        assertTrue(warning.message.contains("London"))
    }
}
