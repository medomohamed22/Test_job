package com.tripmate.ai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TripDao {
    @Transaction
    @Query("SELECT * FROM trips ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PLANNED' THEN 1 ELSE 2 END, updatedAt DESC")
    abstract fun observeAll(): Flow<List<TripWithChildren>>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract fun observe(id: String): Flow<TripWithChildren?>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract suspend fun get(id: String): TripWithChildren?

    @Transaction
    @Query("SELECT * FROM trips")
    abstract suspend fun getAll(): List<TripWithChildren>

    @Query("SELECT COUNT(*) FROM trips")
    abstract suspend fun count(): Int

    @Upsert abstract suspend fun upsertTrip(trip: TripEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertDays(days: List<DayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertStops(stops: List<StopEntity>)
    @Query("DELETE FROM trip_days WHERE tripId = :tripId") abstract suspend fun deleteDays(tripId: String)
    @Query("DELETE FROM trip_stops WHERE tripId = :tripId") abstract suspend fun deleteStops(tripId: String)
    @Query("DELETE FROM trips WHERE id = :id") abstract suspend fun delete(id: String)
    @Query("UPDATE trips SET status = 'PLANNED' WHERE status = 'ACTIVE' AND id != :exceptId") abstract suspend fun deactivateOthers(exceptId: String)

    @Transaction
    open suspend fun saveFull(trip: TripEntity, days: List<DayEntity>, stops: List<StopEntity>) {
        upsertTrip(trip)
        deleteStops(trip.id)
        deleteDays(trip.id)
        insertDays(days)
        insertStops(stops)
    }
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY epochDay DESC, rowid DESC")
    fun observe(tripId: String): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses") suspend fun getAll(): List<ExpenseEntity>
    @Upsert suspend fun upsert(e: ExpenseEntity)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observe(tripId: String): Flow<List<JournalEntity>>
    @Query("SELECT * FROM journal") suspend fun getAll(): List<JournalEntity>
    @Upsert suspend fun upsert(e: JournalEntity)
    @Query("DELETE FROM journal WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface TripEventDao {
    @Insert suspend fun insert(e: TripEventEntity)
    @Query("SELECT * FROM trip_events WHERE tripId = :tripId ORDER BY timestamp DESC")
    fun observe(tripId: String): Flow<List<TripEventEntity>>
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") fun observe(): Flow<List<BookmarkEntity>>
    @Upsert suspend fun upsert(b: BookmarkEntity)
    @Query("DELETE FROM bookmarks WHERE `key` = :key") suspend fun delete(key: String)
}

@Dao
interface GuideCacheDao {
    @Query("SELECT * FROM guide_cache WHERE `key` = :key") suspend fun get(key: String): GuideCacheEntity?
    @Upsert suspend fun upsert(e: GuideCacheEntity)
}
