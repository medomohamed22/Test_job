package com.tripmate.ai.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class, DayEntity::class, StopEntity::class, ExpenseEntity::class,
        JournalEntity::class, TripEventEntity::class, BookmarkEntity::class, GuideCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TripMateDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun journalDao(): JournalDao
    abstract fun eventDao(): TripEventDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun guideCacheDao(): GuideCacheDao
}
