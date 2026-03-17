package org.jetbrains.edu.sed2026.class01

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.collections.set

@Serializable
data class TripBuilder(
    val userId: Long = 0,
    val cities: List<String> = mutableListOf(),
    val segments: List<TripSegment> = mutableListOf(),
    val state: String = ""
) {
    val segmentsCompleted: Boolean get() = segments.size == cities.size - 1

    fun toSnapshot(): TripBuilderSnapshot =
        TripBuilderSnapshot(userId, Json.encodeToString(this))

}

data class TripBuilderSnapshot(val userId: Long, val json: String) {
    fun restore(): TripBuilder {
        return Json.decodeFromString(json)
    }
}

class AddTripDialog(private val storage: Storage, private val tripService: TripService, private val tripPlanner: TripPlanner) {
    var sendMainMenu: (Long)->Unit = {}

    fun processRequest(request: TelegramRequest, responseFactory: ResponseFactory): Result<Unit> {
        val userId = request.userId
        val text = request.message
        val tripBuilder = storage.getSnapshot(userId)?.restore()
        when {
            text == "Add TripPlanRequest" -> startAddTrip(userId, responseFactory)
            tripBuilder?.state == "awaiting_cities" -> receiveCities(userId, text, tripBuilder, responseFactory)
            tripBuilder?.state == "awaiting_round_trip" -> receiveRoundTripResponse(userId, text, tripBuilder, responseFactory)
            tripBuilder?.state == "awaiting_travel_date" -> receiveTravelDate(userId, text, tripBuilder, responseFactory)
            else -> return Result.failure(Exception("Invalid state for user $userId"))
        }
        return Result.success(Unit)
    }


    private fun startAddTrip(userId: Long, responseFactory: ResponseFactory) {
        val tripBuilder = TripBuilder(userId = userId, state = "awaiting_cities")
        storage.saveSnapshot(tripBuilder.toSnapshot())

        responseFactory.createTextResponse().run {
            copy(chatId = userId.toString(), message = "Enter the cities you want to visit (comma-separated):")
        }.execute()
    }

    private fun receiveCities(
        userId: Long,
        text: String,
        curTripBuilder: TripBuilder,
        responseFactory: ResponseFactory
    ) {
        val cities = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (cities.size < 2) {
            responseFactory.createTextResponse().run {
                copy(chatId = userId.toString(), message = "Please enter at least two cities.")
            }.execute()
            return
        }

        val tripBuilder = curTripBuilder.copy(cities = cities)

        if (cities.first().lowercase() != cities.last().lowercase()) {
            storage.saveSnapshot(tripBuilder.copy(state = "awaiting_round_trip").toSnapshot())
            responseFactory.createTextResponse().run {
                copy(chatId = userId.toString(),
                    message = "Is this a round-trip?",
                    buttons = listOf(listOf("Yes", "No"))
                )
            }.execute()
        } else {
            storage.saveSnapshot(tripBuilder.toSnapshot())
            askTravelDate(userId, responseFactory)
        }
    }

    private fun receiveRoundTripResponse(
        userId: Long,
        text: String,
        tripBuilder: TripBuilder,
        responseFactory: ResponseFactory
    ) {
        var currentTripBuilder = tripBuilder
        if (text.lowercase() == "yes") {
            currentTripBuilder = tripBuilder.copy(cities = tripBuilder.cities + tripBuilder.cities.first())
            storage.saveSnapshot(currentTripBuilder.toSnapshot())
        }
        askTravelDate(userId, responseFactory)
    }

    private fun askTravelDate(userId: Long, responseFactory: ResponseFactory) {
        val tripBuilder = storage.getSnapshot(userId)!!.restore()
        val currentCityIndex = tripBuilder.segments.size
        val origin = tripBuilder.cities[currentCityIndex]
        val destination = tripBuilder.cities[currentCityIndex + 1]
        storage.saveSnapshot(tripBuilder.copy(state = "awaiting_travel_date").toSnapshot())
        responseFactory.createTextResponse().run {
            copy(chatId = userId.toString(), message = "When do you travel from $origin to $destination? (YYYY-MM-DD)")
        }.execute()
    }

    private fun receiveTravelDate(
        userId: Long,
        text: String,
        tripBuilder: TripBuilder,
        responseFactory: ResponseFactory
    ) {
        try {
            val date = LocalDate.parse(text)

            if (tripBuilder.segments.isNotEmpty() && date.isBefore(tripBuilder.segments.last().departureDate)) {
                responseFactory.createTextResponse().run {
                    copy(chatId = userId.toString(), message = "Travel date cannot be before previous travel date (${tripBuilder.segments.last().departureDate}). Try again:")
                }.execute()
                return
            }

            val currentCityIndex = tripBuilder.segments.size
            val origin = tripBuilder.cities[currentCityIndex]
            val destination = tripBuilder.cities[currentCityIndex + 1]

            val newSegment = TripSegment(
                origin = origin,
                destination = destination,
                arrivalDate = date,
                departureDate = date
            )
            val newValue = tripBuilder.copy(segments = tripBuilder.segments + newSegment)

            storage.saveSnapshot(newValue.toSnapshot())
            if (newValue.segmentsCompleted) {
                completeTripCreation(userId, responseFactory)
            } else {
                askTravelDate(userId, responseFactory)
            }
        } catch (e: Exception) {
            responseFactory.createTextResponse().run {
                copy(chatId = userId.toString(), message = "Invalid date format. Please use YYYY-MM-DD:")
            }.execute()
        }
    }

    private fun completeTripCreation(userId: Long, responseFactory: ResponseFactory) {
        val tripBuilder = storage.getSnapshot(userId)!!.restore()
        val result = tripService.addTrip(userId, tripBuilder.cities, tripBuilder.segments)

        val planningResponse = responseFactory.createTextResponse()
        val createdResponse = planningResponse.copy(chatId = userId.toString(), message = "Planning your trip...")
        createdResponse.execute()

        val tripRequest = when (result) {
            is TripAddResult.Success -> result.trip
            is TripAddResult.Warning -> result.trip
        }

        tripPlanner.planTrip(tripRequest).thenAccept { plannedTrip ->
            val markdownProcessor = TripMarkdownProcessor()
            plannedTrip.accept(markdownProcessor)
            val costProcessor = TripCostProcessor()
            plannedTrip.accept(costProcessor)

            val flightsText = markdownProcessor.getMarkdown()
            val totalCost = costProcessor.totalCost

            val messageText = when (result) {
                is TripAddResult.Success -> "Trip added successfully! 🎉\n\n$flightsText\nTotal estimated cost: $totalCost"
                is TripAddResult.Warning -> "Trip added successfully! 🎉\n\n⚠️ ${result.message}\n\n$flightsText\nTotal estimated cost: $totalCost"
            }

            createdResponse.update(messageText)
            sendMainMenu(userId)
        }
    }

    fun reset(userId: Long) {
        storage.removeSnapshot(userId)
    }

}
