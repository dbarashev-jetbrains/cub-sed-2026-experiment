package org.jetbrains.edu.sed2026.class06

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jetbrains.edu.sed2026.class06.FlightSearch2 as FlightSearch
import org.jetbrains.edu.sed2026.class01.FlightSearchResult
import org.jetbrains.edu.sed2026.class01.HotelSearch
import org.jetbrains.edu.sed2026.class01.HotelSearchResult
import org.jetbrains.edu.sed2026.class01.PlannedTrip
import org.jetbrains.edu.sed2026.class01.ResponseFactory
import org.jetbrains.edu.sed2026.class01.AddTripDialogSnapshot
import org.jetbrains.edu.sed2026.class01.CachingCurrencyConverter
import org.jetbrains.edu.sed2026.class01.DummyHotelSearch
import org.jetbrains.edu.sed2026.class01.HotelSearchWithCurrency
import org.jetbrains.edu.sed2026.class01.TripPlanRequest
import java.sql.DriverManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.use

/**
 * This is a very simple interface that defines the contract for a trip planner.
 * It is designed to be flexible enough to support different search engines and
 * different types of trip planning algorithms.
 */
interface TripPlanner {
    var flightSearchFactory: ()-> FlightSearch
    var hotelSearchFactory: ()-> HotelSearch

    /** Starts the trip planning process.*/
    fun execute(tripRequest: TripPlanRequest): CompletableFuture<PlannedTrip>

    /** Terminates the trip planning process.*/
    fun stop()
}

/**
 * This is an implementation of the TripPlanner interface that uses
 * a single thread executor to run the searches in sequence: flight search first, then hotel search.
 */
open class TripPlannerImpl: TripPlanner {
    var dbPath = "jdbc:sqlite:tripplanner.db"
    private val gson = Gson()
    // This executor is used to run the searches in sequence.
    private val executor = Executors.newSingleThreadExecutor()

    // A factory that creates a new instance of the flight search engine.
    override var flightSearchFactory: () -> FlightSearch = { TODO("Initialize me") }
    // This is called when the flight search is done.
    var onFlightSearchDone: (List<FlightSearchResult>)->Unit = {}

    // A factory that creates a new instance of the hotel search engine.
    override var hotelSearchFactory: () -> HotelSearch = { TODO("Initialize me") }
    // This is called when the hotel search is done.
    var onHotelSearchDone: (List<HotelSearchResult>)->Unit = {}

    // This is the future that will be completed when the trip planning is done.
    val resultFuture = CompletableFuture<PlannedTrip>()

    // This is the actual trip that will be returned when the trip planning is done. We will update it
    // as the searches are done.
    var trip = PlannedTrip(emptyList(), emptyList())

    /**
     * This is the main method that is called when the user wants to plan a trip.
     * It should be called when an add trip dialog is completed, and we have a dialog data snapshot in the database.
     */
    fun planTrip(userId: Long, responseFactory: ResponseFactory) {
        getDialogSnapshot(userId)?.restore()?.let { dialogSnapshot ->
            val tripRequest = TripPlanRequest(0, dialogSnapshot.userId, dialogSnapshot.cities, dialogSnapshot.segments)
            execute(tripRequest).thenAccept { plannedTrip ->
                process(plannedTrip, responseFactory)
            }
        }
    }

    override fun execute(tripRequest: TripPlanRequest): CompletableFuture<PlannedTrip> {
        executor.submit {
            val flightSearch = flightSearchFactory()
            val flights = searchFlights(flightSearch, tripRequest)
            onFlightSearchDone(flights)
            trip = trip.copy(flightSegments = flights)
        }
        executor.submit {
            val hotelSearch = hotelSearchFactory()
            val hotels = searchHotels(hotelSearch, tripRequest)
            onHotelSearchDone(hotels)
            trip = trip.copy(hotels = hotels)
        }
        executor.submit {
            resultFuture.complete(trip)
        }
        return resultFuture
    }

    override fun stop() {
        executor.shutdown()
    }

    fun searchFlights(flightSearch: FlightSearch, trip: TripPlanRequest): List<FlightSearchResult> {
        val results = mutableListOf<FlightSearchResult>()

        for (segment in trip.segments) {
            println("Searching for flight from ${segment.origin} to ${segment.destination}")
            val rawJson = flightSearch.searchFlights(segment.origin, segment.destination, segment.departureDate).body

            val json = gson.fromJson(rawJson, JsonObject::class.java)

            val foundFlights = when {
                json.has("best_flights") -> parseSerpResponse(rawJson, segment.origin, segment.destination)
                json.has("data") -> parseDuffelResponse(rawJson, segment.origin, segment.destination)
                else -> emptyList()
            }

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

    fun searchHotels(hotelSearch: HotelSearch, trip: TripPlanRequest): List<HotelSearchResult> {
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

    /**
     * Serp-specific JSON parsing - embedded in TripPlanner (OCP violation)
     */
    private fun parseSerpResponse(json: String, origin: String, destination: String): List<FlightSearchResult> {
        val results = mutableListOf<FlightSearchResult>()
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    /**
     * Duffel-specific JSON parsing - embedded in TripPlanner (OCP violation)
     */
    private fun parseDuffelResponse(json: String, origin: String, destination: String): List<FlightSearchResult> {
        val results = mutableListOf<FlightSearchResult>()
        try {
            val root = gson.fromJson(json, JsonObject::class.java)
            val data = root.getAsJsonObject("data")
            if (data != null) {
                val offers = data.getAsJsonArray("offers")
                if (offers != null) {
                    for (offerElement in offers) {
                        val offerObj = offerElement.asJsonObject
                        val totalAmount = if (offerObj.has("total_amount")) offerObj.get("total_amount").asDouble else 0.0
                        val currency = if (offerObj.has("total_currency")) offerObj.get("total_currency").asString else "USD"

                        val slices = offerObj.getAsJsonArray("slices")
                        val flightNumber = if (slices != null && slices.size() > 0) {
                            val segments = slices.get(0).asJsonObject.getAsJsonArray("segments")
                            if (segments != null && segments.size() > 0) {
                                segments.get(0).asJsonObject.get("operating_carrier_flight_number").asString
                            } else "Unknown"
                        } else "Unknown"

                        results.add(FlightSearchResult(flightNumber, totalAmount, currency, origin, destination))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    fun getDialogSnapshot(userId: Long): AddTripDialogSnapshot? {
        DriverManager.getConnection(dbPath).use { conn ->
            val stmt = conn.prepareStatement("SELECT json FROM snapshots WHERE user_id = ?")
            stmt.setLong(1, userId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return AddTripDialogSnapshot(userId, rs.getString("json"))
            }
        }
        return null
    }

    private fun process(plannedTrip: PlannedTrip, responseFactory: ResponseFactory) {
        val sb = StringBuilder()
        var totalCost: Double = 0.0
        plannedTrip.flightSegments.forEach { flightSegment ->
            sb.append("### Flight: ${flightSegment.flightNumber}\n")
            sb.append("- From: ${flightSegment.origin}\n")
            sb.append("- To: ${flightSegment.destination}\n")
            sb.append("- Price: ${flightSegment.price} ${flightSegment.currency}\n\n")
            totalCost += flightSegment.price
        }
        plannedTrip.hotels.forEach { hotel ->
            sb.append("### Hotel: ${hotel.hotelName}\n")
            sb.append("- Location: ${hotel.location}\n")
            sb.append("- Daily Price: ${hotel.dailyPrice} ${hotel.currency}\n")
            sb.append("- Score: ${hotel.score10}/10\n\n")
            totalCost += hotel.dailyPrice
        }
        val message = "Trip added successfully! 🎉\n\n$sb\nTotal estimated cost: $totalCost"
        responseFactory.createTextResponse().copy(message = message).execute()
    }
}

/**
 * This is an improved version of the TripPlanner that uses coroutines to
 * run the searches in parallel.
 *
 * We reuse code from the previous implementation.
 */
class TripPlannerImproved(private val coroutineScope: CoroutineScope): TripPlannerImpl() {
    private var flightsReady = false
    private var hotelsReady = false

    override fun execute(tripRequest: TripPlanRequest): CompletableFuture<PlannedTrip> {
        coroutineScope.launch {
            setFlights(searchFlights(flightSearchFactory(), tripRequest))
        }
        coroutineScope.launch {
            setHotels(searchHotels(hotelSearchFactory(), tripRequest))
        }
        return resultFuture
    }

    override fun stop() {
        coroutineScope.cancel()
    }

    internal fun setFlights(flights: List<FlightSearchResult>) {
        onFlightSearchDone(flights)
        flightsReady = true
        trip = trip.copy(flightSegments = flights)
        maybeResolvePromise()
    }
    internal fun setHotels(hotels: List<HotelSearchResult>) {
        onHotelSearchDone(hotels)
        hotelsReady = true
        trip = trip.copy(hotels = hotels)
        maybeResolvePromise()
    }

    private fun maybeResolvePromise() = synchronized(this) {
        if (flightsReady && hotelsReady) {
            println("Resolving the promise...")
            resultFuture.complete(trip)
        }
    }
}

/**
 * Creates a new instance of the TripPlanner.
 */
fun createTripPlanner(): TripPlannerImpl {
    val httpClient = OkHttpClientAdapter(OkHttpClient())
    val currencyConverter = CachingCurrencyConverter()
    val hotelSearch = HotelSearchWithCurrency(DummyHotelSearch(), currencyConverter, "CZK")

    // TODO: we have an improved TripPlanner that uses coroutines, but we don't want to use it yet'
    return TripPlannerImpl().apply {
        flightSearchFactory = { ResilientFlightSearch2(
            primarySearch = SerpFlightSearch2(httpClient),
            fallbackSearch = DuffelFlightSearch2(httpClient)
        )}
        hotelSearchFactory = {
            hotelSearch
        }
        onFlightSearchDone = { flights ->
            println("Flights are ready: $flights")
            if (flights.isEmpty()) {
                this@apply.stop()
            }
        }
        onHotelSearchDone = { hotels ->
            println("Hotels are ready: $hotels")
        }
    }
}