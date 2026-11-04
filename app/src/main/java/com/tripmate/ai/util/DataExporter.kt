package com.tripmate.ai.util

import com.tripmate.ai.data.db.TripMateDatabase
import com.tripmate.ai.data.repo.TripRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/** Exports all local user data as JSON (photos are referenced by file name only). */
@Singleton
class DataExporter @Inject constructor(private val repo: TripRepository, private val db: TripMateDatabase) {
    suspend fun exportJson(): String {
        val trips = repo.allTrips()
        val expenses = db.expenseDao().getAll()
        val journal = db.journalDao().getAll()
        return buildJsonObject {
            put("app", "TripMate AI")
            put("exportedAt", System.currentTimeMillis())
            putJsonArray("trips") {
                trips.forEach { t ->
                    add(buildJsonObject {
                        put("id", t.id); put("title", t.title); put("destination", t.destinationName)
                        put("start", t.startDate.toString()); put("end", t.endDate.toString())
                        put("currency", t.currency); put("budget", t.budgetTotal); put("status", t.status.name)
                        put("days", JsonArray(t.days.map { d ->
                            buildJsonObject {
                                put("date", d.date.toString())
                                put("stops", buildJsonArray {
                                    d.stops.forEach { s ->
                                        add(buildJsonObject {
                                            put("title", s.title); put("start", TimeMathAdapter.hhmm(s.startMinutes)); put("durationMinutes", s.durationMinutes)
                                            put("status", s.status.name); put("notes", s.notes); put("verification", s.verification.name)
                                            s.lat?.let { put("lat", it) }; s.lng?.let { put("lng", it) }
                                        })
                                    }
                                })
                            }
                        }))
                    })
                }
            }
            putJsonArray("expenses") {
                expenses.forEach { e -> add(buildJsonObject { put("tripId", e.tripId); put("amount", e.amount); put("currency", e.currency); put("category", e.category); put("note", e.note) }) }
            }
            putJsonArray("journal") {
                journal.forEach { j -> add(buildJsonObject { put("tripId", j.tripId); put("createdAt", j.createdAt); put("text", j.text); put("photo", JsonPrimitive(j.photoPath?.substringAfterLast('/'))) }) }
            }
        }.toString()
    }
}

private object TimeMathAdapter { fun hhmm(m: Int) = com.tripmate.ai.domain.logic.TimeMath.hhmm(m) }
