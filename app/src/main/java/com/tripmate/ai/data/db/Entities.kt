package com.tripmate.ai.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val destinationId: String,
    val destinationName: String,
    val locale: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val currency: String,
    val budget: Double,
    val pace: String,
    val transport: String,
    val groupSize: Int,
    val interests: String,
    val walking: String,
    val food: String,
    val status: String,
    val isSample: Boolean,
    val generatedBy: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "trip_days",
    primaryKeys = ["tripId", "dayIndex"],
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
)
data class DayEntity(
    val tripId: String,
    val dayIndex: Int,
    val epochDay: Long,
    val startMinutes: Int,
    val endMinutes: Int,
)

@Entity(
    tableName = "trip_stops",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class StopEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val dayIndex: Int,
    val position: Int,
    val placeId: String?,
    val title: String,
    val category: String,
    val lat: Double?,
    val lng: Double?,
    val startMinutes: Int,
    val durationMinutes: Int,
    val minDurationMinutes: Int,
    val travelMinutes: Int,
    val travelSource: String,
    val status: String,
    val priority: Int,
    val notes: String,
    val priceAmount: Double?,
    val priceCurrency: String?,
    val verification: String,
    val opensMinutes: Int?,
    val closesMinutes: Int?,
    val checklistJson: String,
    val actualArrivalMinutes: Int?,
    val actualEndMinutes: Int?,
)

data class TripWithChildren(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val days: List<DayEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId") val stops: List<StopEntity>,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val epochDay: Long,
    val amount: Double,
    val currency: String,
    val category: String,
    val note: String,
    val stopId: String?,
)

@Entity(
    tableName = "journal",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class JournalEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val createdAt: Long,
    val text: String,
    val photoPath: String?,
    val stopId: String?,
)

@Entity(
    tableName = "trip_events",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TripEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val timestamp: Long,
    val type: String,
    val stopId: String?,
    val metadata: String,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val key: String,
    val source: String,
    val title: String,
    val lat: Double?,
    val lng: Double?,
    val address: String?,
    val createdAt: Long,
)

@Entity(tableName = "guide_cache")
data class GuideCacheEntity(
    @PrimaryKey val key: String,
    val text: String,
    val route: String,
    val createdAt: Long,
)
