package com.tripmate.ai.domain.ai

import com.tripmate.ai.domain.logic.GeneratorLabels
import com.tripmate.ai.domain.logic.ItineraryGenerator
import com.tripmate.ai.domain.logic.ItineraryValidator
import com.tripmate.ai.domain.logic.ScheduleEngine
import com.tripmate.ai.domain.logic.TimeMath
import com.tripmate.ai.domain.logic.TripRequest
import com.tripmate.ai.domain.model.CatalogPlace
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.TravelSource
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.domain.model.Verification
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ---------------- Schemas the model is asked to produce ---------------- */

@Serializable
data class DraftStop(val placeId: String? = null, val title: String? = null, val durationMinutes: Int? = null, val note: String? = null)

@Serializable
data class DraftDay(val stops: List<DraftStop> = emptyList())

@Serializable
data class ItineraryDraft(val days: List<DraftDay> = emptyList(), val summary: String? = null)

@Serializable
data class AdjustIntentJson(
    val action: String? = null,
    val minutes: Int? = null,
    val stopTitle: String? = null,
    val pace: String? = null,
    val explanation: String? = null,
)

sealed interface PlanIntent {
    data class Delay(val minutes: Int) : PlanIntent
    data class Skip(val stopId: String) : PlanIntent
    data class Break(val minutes: Int) : PlanIntent
    data class ChangePace(val pace: Pace) : PlanIntent
    data object Unknown : PlanIntent
}

/**
 * Defensive parsing of model output. Anything malformed, out of range or referencing unknown
 * places is rejected or clamped; the caller falls back to the deterministic planner.
 */
object AiJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    fun extractObject(raw: String): String? {
        val cleaned = raw.replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start in 0 until end) cleaned.substring(start, end + 1) else null
    }

    fun parseDraft(raw: String): ItineraryDraft? = runCatching {
        json.decodeFromString(ItineraryDraft.serializer(), extractObject(raw) ?: return null)
    }.getOrNull()?.takeIf { d -> d.days.isNotEmpty() && d.days.any { it.stops.isNotEmpty() } }

    fun parseIntent(raw: String): AdjustIntentJson? = runCatching {
        json.decodeFromString(AdjustIntentJson.serializer(), extractObject(raw) ?: return null)
    }.getOrNull()

    /** Maps a model-proposed intent onto the deterministic engine's vocabulary, with clamping. */
    fun toPlanIntent(intent: AdjustIntentJson, day: TripDay): PlanIntent = when (intent.action?.uppercase()) {
        "DELAY" -> intent.minutes?.takeIf { it in 1..720 }?.let { PlanIntent.Delay(it) } ?: PlanIntent.Unknown
        "BREAK" -> PlanIntent.Break((intent.minutes ?: 30).coerceIn(5, 240))
        "SKIP" -> findStop(day, intent.stopTitle)?.let { PlanIntent.Skip(it.id) } ?: PlanIntent.Unknown
        "PACE" -> Pace.entries.firstOrNull { it.name == intent.pace?.uppercase() }?.let { PlanIntent.ChangePace(it) } ?: PlanIntent.Unknown
        else -> PlanIntent.Unknown
    }

    private fun findStop(day: TripDay, title: String?): TripStop? {
        if (title.isNullOrBlank()) return null
        val t = title.trim().lowercase()
        return day.stops.filter { !it.status.isFinal }.firstOrNull { it.title.lowercase() == t }
            ?: day.stops.filter { !it.status.isFinal }.firstOrNull { it.title.lowercase().contains(t) || t.contains(it.title.lowercase()) }
    }

    /**
     * Offline keyword interpreter (English + Arabic). Used when no AI route is available so that
     * "Change plan" still works deterministically for common phrasings.
     */
    fun keywordIntent(text: String, day: TripDay): PlanIntent {
        val t = text.lowercase()
        val number = Regex("(\\d{1,3})").find(normalizeDigits(t))?.value?.toIntOrNull()
        val hours = t.contains("hour") || t.contains("ساعة") || t.contains("ساعه")
        val minutes = number?.let { if (hours) it * 60 else it }
        return when {
            listOf("late", "delay", "behind", "متأخر", "تأخير", "اتأخر", "متاخر").any { t.contains(it) } ->
                PlanIntent.Delay((minutes ?: if (hours) 60 else 30).coerceIn(5, 720))
            listOf("break", "rest", "tired", "coffee", "استراحة", "راحة", "تعبان", "قهوة").any { t.contains(it) } ->
                PlanIntent.Break((minutes ?: 30).coerceIn(5, 240))
            listOf("relax", "slow", "هادئ", "ببطء", "على مهل").any { t.contains(it) } -> PlanIntent.ChangePace(Pace.RELAXED)
            listOf("faster", "packed", "more places", "أسرع", "أماكن أكثر").any { t.contains(it) } -> PlanIntent.ChangePace(Pace.PACKED)
            listOf("skip", "drop", "remove", "تخطى", "تخطي", "الغي", "ألغي", "شيل").any { t.contains(it) } ->
                day.stops.filter { !it.status.isFinal }.firstOrNull { s -> t.contains(s.title.lowercase()) }?.let { PlanIntent.Skip(it.id) }
                    ?: day.currentStop?.let { PlanIntent.Skip(it.id) } ?: PlanIntent.Unknown
            else -> PlanIntent.Unknown
        }
    }

    fun normalizeDigits(s: String): String = buildString {
        s.forEach { c ->
            append(when (c) { in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰'); else -> c })
        }
    }

    /**
     * Turns an AI draft into a validated plan built on the deterministic baseline:
     * - catalog places keep curated coordinates/metadata (labelled SAMPLE);
     * - unknown places are kept only as UNVERIFIED stops without coordinates;
     * - durations are clamped and times recomputed by [ScheduleEngine].
     * Returns null if the result fails validation.
     */
    fun applyDraft(
        draft: ItineraryDraft,
        baseline: TripPlan,
        request: TripRequest,
        catalog: List<CatalogPlace>,
        labels: GeneratorLabels,
        ar: Boolean,
        generator: ItineraryGenerator,
        idGen: () -> String,
    ): TripPlan? {
        val byId = catalog.associateBy { it.id }
        val used = HashSet<String>()
        val days = baseline.days.mapIndexed { index, baseDay ->
            val draftDay = draft.days.getOrNull(index) ?: return@mapIndexed baseDay
            val stops = draftDay.stops.take(8).mapNotNull { ds ->
                val place = ds.placeId?.let { byId[it] }?.takeIf { request.destinationId in it.destinationIds }
                val duration = (ds.durationMinutes ?: place?.typicalDurationMinutes ?: 60)
                when {
                    place != null && used.add(place.id) ->
                        generator.placeToStop(place, ar, 0, TimeMath.roundUpTo5(duration.coerceIn(place.minDurationMinutes, 300)), labels)
                            .copy(notes = ds.note.orEmpty().take(280))
                    place == null && !ds.title.isNullOrBlank() && ds.title.length <= 80 -> TripStop(
                        id = idGen(), title = ds.title.trim(), category = StopCategory.CUSTOM,
                        startMinutes = 0, durationMinutes = TimeMath.roundUpTo5(duration.coerceIn(20, 240)),
                        travelMinutesFromPrev = 20, travelSource = TravelSource.ESTIMATE, priority = 1,
                        notes = ds.note.orEmpty().take(280), verification = Verification.UNVERIFIED,
                    )
                    else -> null
                }
            }
            if (stops.isEmpty()) return@mapIndexed baseDay
            val withMeal = if (stops.none { it.category == StopCategory.MEAL }) {
                val meal = baseDay.stops.firstOrNull { it.category == StopCategory.MEAL }
                if (meal != null) {
                    val insertAt = (stops.size / 2).coerceAtLeast(1).coerceAtMost(stops.size)
                    stops.toMutableList().apply { add(insertAt, meal) }
                } else stops
            } else stops
            ItineraryGenerator.withEstimatedTravel(baseDay.copy(stops = withMeal), request.transport)
                .let { d ->
                    val fitted = ScheduleEngine.fitToDay(d.stops, d.startMinutes, d.endMinutes, null).first
                        .filter { it.status != com.tripmate.ai.domain.model.StopStatus.SKIPPED }
                    d.copy(stops = ScheduleEngine.reflow(fitted, d.startMinutes))
                }
        }
        val plan = baseline.copy(days = days, generatedBy = "cloud-ai")
        return plan.takeUnless { ItineraryValidator.hasErrors(it) }
    }
}
