package com.tripmate.ai.data.repo

import android.content.Context
import com.tripmate.ai.R
import com.tripmate.ai.data.db.BookmarkEntity
import com.tripmate.ai.data.db.TripEventEntity
import com.tripmate.ai.data.db.TripMateDatabase
import com.tripmate.ai.data.mapper.TripMappers
import com.tripmate.ai.data.prefs.UserPreferencesRepository
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.logic.GeneratorLabels
import com.tripmate.ai.domain.logic.ItineraryGenerator
import com.tripmate.ai.domain.logic.TripRequest
import com.tripmate.ai.domain.model.Expense
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.JournalEntry
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.TransportMode
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripEventType
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun newId(): String = UUID.randomUUID().toString()

/** Single source of truth for trips, stops, expenses, journal and bookmarks (Room, offline-first). */
@Singleton
class TripRepository @Inject constructor(
    private val db: TripMateDatabase,
    private val prefs: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
) {
    private val trips = db.tripDao()

    fun observeTrips(): Flow<List<TripPlan>> = trips.observeAll().map { rows -> rows.map(TripMappers::toDomain) }
    fun observeTrip(id: String): Flow<TripPlan?> = trips.observe(id).map { it?.let(TripMappers::toDomain) }
    suspend fun getTrip(id: String): TripPlan? = trips.get(id)?.let(TripMappers::toDomain)
    suspend fun allTrips(): List<TripPlan> = trips.getAll().map(TripMappers::toDomain)

    suspend fun save(plan: TripPlan) {
        val stamped = plan.copy(updatedAt = System.currentTimeMillis(), createdAt = plan.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis())
        val e = TripMappers.toEntities(stamped)
        trips.saveFull(e.trip, e.days, e.stops)
    }

    suspend fun updateDay(tripId: String, day: TripDay) {
        val plan = getTrip(tripId) ?: return
        save(plan.copy(days = plan.days.map { if (it.dayIndex == day.dayIndex) day else it }))
    }

    suspend fun setStatus(tripId: String, status: TripStatus) {
        val plan = getTrip(tripId) ?: return
        if (status == TripStatus.ACTIVE) trips.deactivateOthers(tripId)
        save(plan.copy(status = status))
        logEvent(tripId, if (status == TripStatus.ACTIVE) TripEventType.TRIP_STARTED else if (status == TripStatus.COMPLETED) TripEventType.TRIP_COMPLETED else TripEventType.PLAN_CHANGED)
    }

    suspend fun delete(tripId: String) {
        db.journalDao().getAll().filter { it.tripId == tripId }.forEach { j -> j.photoPath?.let { File(it).delete() } }
        trips.delete(tripId)
    }

    suspend fun logEvent(tripId: String, type: TripEventType, stopId: String? = null, metadata: String = "") =
        db.eventDao().insert(TripEventEntity(tripId = tripId, timestamp = System.currentTimeMillis(), type = type.name, stopId = stopId, metadata = metadata))

    /* --------------- Expenses & journal --------------- */

    fun observeExpenses(tripId: String): Flow<List<Expense>> = db.expenseDao().observe(tripId).map { l -> l.map(TripMappers::toDomain) }
    suspend fun saveExpense(e: Expense) = db.expenseDao().upsert(TripMappers.toEntity(e))
    suspend fun deleteExpense(id: String) = db.expenseDao().delete(id)

    fun observeJournal(tripId: String): Flow<List<JournalEntry>> = db.journalDao().observe(tripId).map { l -> l.map(TripMappers::toDomain) }
    suspend fun saveJournal(j: JournalEntry) = db.journalDao().upsert(TripMappers.toEntity(j))
    suspend fun deleteJournal(j: JournalEntry) { j.photoPath?.let { File(it).delete() }; db.journalDao().delete(j.id) }

    /* --------------- Bookmarks --------------- */

    fun observeBookmarks() = db.bookmarkDao().observe()
    suspend fun toggleBookmark(key: String, source: String, title: String, lat: Double?, lng: Double?, address: String?, bookmarked: Boolean) {
        if (bookmarked) db.bookmarkDao().delete(key)
        else db.bookmarkDao().upsert(BookmarkEntity(key, source, title, lat, lng, address, System.currentTimeMillis()))
    }

    /* --------------- Sample data --------------- */

    fun generatorLabels() = GeneratorLabels(
        lunch = context.getString(R.string.stop_lunch_break),
        rest = context.getString(R.string.stop_rest_break),
        checklistWater = context.getString(R.string.checklist_water),
        checklistTicket = context.getString(R.string.checklist_ticket),
        checklistModest = context.getString(R.string.checklist_modest),
    )

    fun isArabic(): Boolean = context.resources.configuration.locales[0].language == "ar"

    /** Seeds a clearly-labelled 3-day Cairo sample trip on first launch (works with no keys). */
    suspend fun seedSampleIfNeeded() {
        val p = prefs.current()
        if (p.sampleSeeded) return
        if (trips.count() == 0) save(buildSampleTrip())
        prefs.markSampleSeeded()
    }

    fun buildSampleTrip(): TripPlan {
        val ar = isArabic()
        val today = LocalDate.now()
        val request = TripRequest(
            destinationId = "cairo", destinationName = PlaceCatalog.destination("cairo")!!.name(ar),
            startDate = today, endDate = today.plusDays(2), currency = "EGP", budget = 15000.0,
            interests = setOf(Interest.HISTORY, Interest.MUSEUMS, Interest.PHOTOGRAPHY, Interest.FOOD),
            pace = Pace.BALANCED, groupSize = 2, transport = TransportMode.TAXI, locale = if (ar) "ar" else "en",
        )
        val plan = ItineraryGenerator(::newId).generate(request, PlaceCatalog.places, generatorLabels(), ar, System.currentTimeMillis())
        return plan.copy(title = context.getString(R.string.sample_trip_title), isSample = true, generatedBy = "sample")
    }

    suspend fun clearAll() {
        db.journalDao().getAll().forEach { j -> j.photoPath?.let { File(it).delete() } }
        withContext(Dispatchers.IO) { db.clearAllTables() }
        File(context.filesDir, "journal").deleteRecursively()
    }
}
