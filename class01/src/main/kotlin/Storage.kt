package org.jetbrains.edu.sed2026.class01


import java.sql.DriverManager
import java.time.LocalDate
import kotlin.use

interface Storage {
    fun getTrips(userId: Long): List<TripPlanRequest>
    fun addTrip(userId: Long, cities: List<String>, segments: List<TripSegment>): Int

    fun saveSnapshot(snapshot: AddTripDialogSnapshot)
    fun getSnapshot(userId: Long): AddTripDialogSnapshot?
    fun removeSnapshot(userId: Long)
}

class SQLiteStorage(private val dbPath: String) : Storage {
    init {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS trips (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    cities TEXT NOT NULL
                )
            """)
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS flights (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    flight_number TEXT NOT NULL,
                    departure_time TEXT NOT NULL,
                    arrival_time TEXT NOT NULL
                )
            """)
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS trip_segments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trip_id INTEGER NOT NULL,
                    origin TEXT NOT NULL,
                    destination TEXT NOT NULL,
                    arrival_date TEXT NOT NULL,
                    departure_date TEXT NOT NULL,
                    flight_id INTEGER,
                    FOREIGN KEY (trip_id) REFERENCES trips(id),
                    FOREIGN KEY (flight_id) REFERENCES flights(id)
                )
            """)
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS snapshots (
                    user_id INTEGER PRIMARY KEY,
                    json TEXT NOT NULL
                )
            """)
        }
    }

    override fun getTrips(userId: Long): List<TripPlanRequest> {
        val trips = mutableListOf<TripPlanRequest>()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            val tripStmt = conn.prepareStatement("SELECT id, user_id, cities FROM trips WHERE user_id = ?")
            tripStmt.setLong(1, userId)
            val tripRs = tripStmt.executeQuery()
            while (tripRs.next()) {
                val tripId = tripRs.getInt("id")
                val cities = tripRs.getString("cities").split(",")

                val segments = mutableListOf<TripSegment>()
                val segmentStmt = conn.prepareStatement("SELECT id, origin, destination, arrival_date, departure_date, flight_id FROM trip_segments WHERE trip_id = ?")
                segmentStmt.setInt(1, tripId)
                val segmentRs = segmentStmt.executeQuery()
                while (segmentRs.next()) {
                    segments.add(TripSegment(
                        segmentRs.getInt("id"),
                        tripId,
                        segmentRs.getString("origin"),
                        segmentRs.getString("destination"),
                        LocalDate.parse(segmentRs.getString("arrival_date")),
                        LocalDate.parse(segmentRs.getString("departure_date")),
                        segmentRs.getObject("flight_id") as Int?
                    ))
                }

                trips.add(TripPlanRequest(tripId, userId, cities, segments))
            }
        }
        return trips
    }

    override fun addTrip(userId: Long, cities: List<String>, segments: List<TripSegment>): Int {
        var tripId = -1
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            conn.autoCommit = false
            try {
                val tripStmt = conn.prepareStatement("INSERT INTO trips (user_id, cities) VALUES (?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)
                tripStmt.setLong(1, userId)
                tripStmt.setString(2, cities.joinToString(","))
                tripStmt.executeUpdate()

                val rs = tripStmt.generatedKeys
                if (rs.next()) {
                    tripId = rs.getInt(1)
                }

                val segmentStmt = conn.prepareStatement("INSERT INTO trip_segments (trip_id, origin, destination, arrival_date, departure_date, flight_id) VALUES (?, ?, ?, ?, ?, ?)")
                for (segment in segments) {
                    segmentStmt.setInt(1, tripId)
                    segmentStmt.setString(2, segment.origin)
                    segmentStmt.setString(3, segment.destination)
                    segmentStmt.setString(4, segment.arrivalDate.toString())
                    segmentStmt.setString(5, segment.departureDate.toString())
                    if (segment.flightId != null) segmentStmt.setInt(6, segment.flightId) else segmentStmt.setNull(6, java.sql.Types.INTEGER)
                    segmentStmt.addBatch()
                }
                segmentStmt.executeBatch()
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        return tripId
    }

    override fun saveSnapshot(snapshot: AddTripDialogSnapshot) {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            val stmt = conn.prepareStatement("INSERT OR REPLACE INTO snapshots (user_id, json) VALUES (?, ?)")
            stmt.setLong(1, snapshot.userId)
            stmt.setString(2, snapshot.json)
            stmt.executeUpdate()
        }
    }

    override fun getSnapshot(userId: Long): AddTripDialogSnapshot? {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            val stmt = conn.prepareStatement("SELECT json FROM snapshots WHERE user_id = ?")
            stmt.setLong(1, userId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return AddTripDialogSnapshot(userId, rs.getString("json"))
            }
        }
        return null
    }

    override fun removeSnapshot(userId: Long) {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            val stmt = conn.prepareStatement("DELETE FROM snapshots WHERE user_id = ?")
            stmt.setLong(1, userId)
            stmt.executeUpdate()
        }
    }
}
