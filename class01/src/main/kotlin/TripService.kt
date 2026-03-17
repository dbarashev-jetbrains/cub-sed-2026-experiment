package org.jetbrains.edu.sed2026.class01

import java.time.LocalDate

sealed class TripAddResult {
    data class Success(val trip: TripPlanRequest) : TripAddResult()
    data class Warning(val message: String, val trip: TripPlanRequest) : TripAddResult()
}

class TripService(private val storage: Storage) {
    fun addTrip(userId: Long, cities: List<String>, segments: List<TripSegment>): TripAddResult {
        val existingTrips = storage.getTrips(userId)

        val startDate = segments.first().arrivalDate
        val endDate = segments.last().departureDate

        val overlappingTrips = existingTrips.filter { trip ->
            val tripStart = trip.segments.firstOrNull()?.arrivalDate ?: LocalDate.MIN
            val tripEnd = trip.segments.lastOrNull()?.departureDate ?: LocalDate.MAX
            datesOverlap(tripStart, tripEnd, startDate, endDate)
        }

        val tripId = storage.addTrip(userId, cities, segments)
        val addedTrip = storage.getTrips(userId).find { it.id == tripId } ?: storage.getTrips(userId).last()

        return if (overlappingTrips.isNotEmpty()) {
            val overlappingCities = overlappingTrips.joinToString(", ") { it.cities.joinToString(" -> ") }
            TripAddResult.Warning(
                "Warning: This trip overlaps with existing trip(s): $overlappingCities",
                addedTrip
            )
        } else {
            TripAddResult.Success(addedTrip)
        }
    }

    private fun datesOverlap(start1: LocalDate, end1: LocalDate, start2: LocalDate, end2: LocalDate): Boolean {
        return !(end1.isBefore(start2) || end2.isBefore(start1))
    }
}
