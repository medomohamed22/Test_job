package com.tripmate.ai.ai

import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.logic.TimeMath
import com.tripmate.ai.domain.logic.TripRequest
import com.tripmate.ai.domain.model.CatalogPlace
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripPlan

enum class GuideStyle { SHORT, DETAILED, KIDS }
enum class GuideQuestion { LOOK_FOR, HISTORY, ARCHITECTURE, CUSTOMS, TIPS }

/**
 * Builds minimal prompts. Only the destination, the selected stop and the user's own question
 * are included — no account data, no precise location, no unrelated history or notes.
 */
object AiPrompts {
    private fun lang(ar: Boolean) = if (ar) "Arabic (Modern Standard, friendly)" else "English"

    private const val GROUNDING =
        "Rules: be accurate and modest. If you are not sure about a fact, say so. Do NOT state opening hours, " +
            "ticket prices, bookings, live traffic or guaranteed accessibility as facts; tell the traveller to check official sources. " +
            "No markdown headings; plain short paragraphs."

    fun guide(placeName: String, destination: String, style: GuideStyle, ar: Boolean): String {
        val shape = when (style) {
            GuideStyle.SHORT -> "a concise 3–4 sentence introduction"
            GuideStyle.DETAILED -> "a detailed but readable explanation (about 200 words) covering history and what makes it special"
            GuideStyle.KIDS -> "a fun, simple explanation for a 9-year-old (about 80 words)"
        }
        return "You are TripMate, a careful local tour guide. Write $shape about \"$placeName\" in $destination. " +
            "Answer in ${lang(ar)}. $GROUNDING"
    }

    fun question(placeName: String, destination: String, q: GuideQuestion, ar: Boolean): String {
        val ask = when (q) {
            GuideQuestion.LOOK_FOR -> "List 4 specific things a visitor should look for, one short line each"
            GuideQuestion.HISTORY -> "Explain the key history in under 150 words"
            GuideQuestion.ARCHITECTURE -> "Explain the notable architecture or design features in under 150 words"
            GuideQuestion.CUSTOMS -> "Explain respectful local customs and etiquette relevant to visiting, in under 120 words"
            GuideQuestion.TIPS -> "Give 4 practical visiting tips, one short line each"
        }
        return "You are TripMate, a careful local tour guide. About \"$placeName\" in $destination: $ask. Answer in ${lang(ar)}. $GROUNDING"
    }

    fun freeQuestion(placeName: String, destination: String, userQuestion: String, ar: Boolean) =
        "You are TripMate, a careful local tour guide. The traveller is at \"$placeName\" in $destination and asks: " +
            "\"${userQuestion.take(400)}\". Answer in ${lang(ar)} in under 150 words. $GROUNDING"

    fun photo(placeName: String?, ar: Boolean) =
        "You are TripMate, a careful tour guide. Describe what is visible in this travel photo" +
            (placeName?.let { " taken around \"$it\"" } ?: "") +
            ". Identify architecture or objects only if you are reasonably confident and say when you are unsure. " +
            "Answer in ${lang(ar)}, under 120 words. Do not identify people. $GROUNDING"

    fun summarize(passage: String, ar: Boolean) =
        "Summarize this travel guide passage in 2 short sentences in ${lang(ar)}. Use only facts from the passage.\n\nPassage:\n${passage.take(2000)}"

    fun paraphraseNote(note: String, ar: Boolean) =
        "Rewrite this personal travel note clearly in ${lang(ar)}, keeping its meaning, under 80 words. Do not add facts.\n\nNote:\n${note.take(800)}"

    fun checklist(placeName: String, startTime: String, ar: Boolean) =
        "Write a brief preparation checklist (max 5 bullet lines starting with '- ') for visiting \"$placeName\" at $startTime. " +
            "Answer in ${lang(ar)}. Generic, practical items only; no prices or opening hours."

    fun tripChat(trip: TripPlan, day: TripDay?, userMessage: String, ar: Boolean): String {
        val schedule = day?.stops?.joinToString("; ") { "${TimeMath.hhmm(it.startMinutes)} ${it.title} (${it.status.name.lowercase()})" }.orEmpty()
        return "You are TripMate, a travel companion. Trip: ${trip.destinationName}, ${trip.dayCount} days, pace ${trip.pace.name.lowercase()}. " +
            "Today's plan: $schedule. The traveller says: \"${userMessage.take(500)}\". Reply helpfully in ${lang(ar)} in under 120 words. " +
            "If they want to change the schedule, explain the trade-offs and tell them to use the Change plan button to review a before/after proposal. $GROUNDING"
    }

    fun adjustIntent(day: TripDay, userMessage: String): String {
        val stops = day.stops.filter { !it.status.isFinal }.joinToString(", ") { "\"${it.title}\"" }
        return """
            Classify the traveller's request into ONE schedule action. Remaining stops: [$stops].
            Request: "${userMessage.take(400)}"
            Respond ONLY with JSON: {"action":"DELAY|SKIP|BREAK|PACE|NONE","minutes":number|null,"stopTitle":string|null,"pace":"RELAXED|BALANCED|PACKED"|null,"explanation":"one sentence in the request's language explaining the trade-off"}
        """.trimIndent()
    }

    fun itinerary(request: TripRequest, places: List<CatalogPlace>, ar: Boolean): String {
        val catalog = places.joinToString("\n") { "- id=${it.id}; ${it.nameEn}; area=${it.area}; typical=${it.typicalDurationMinutes}min; interests=${it.interests.joinToString(",") { i -> i.name.lowercase() }}" }
        val days = java.time.temporal.ChronoUnit.DAYS.between(request.startDate, request.endDate) + 1
        return """
            Plan a $days-day itinerary for ${request.destinationName}. Pace: ${request.pace.name.lowercase()}; ${request.hoursPerDay} hours per day;
            interests: ${request.interests.joinToString { it.name.lowercase() }}; group of ${request.groupSize}; transport: ${request.transport.name.lowercase()};
            walking: ${request.walking.name.lowercase()}; food preference: ${request.food.name.lowercase()}.
            Prefer these verified catalog places (use their exact id). Group nearby areas on the same day.
            $catalog
            You may add at most one extra place per day ONLY by title (no id) if clearly relevant.
            Respond ONLY with JSON: {"days":[{"stops":[{"placeId":string|null,"title":string|null,"durationMinutes":number,"note":"one short tip in ${lang(ar)}"}]}],"summary":"one sentence in ${lang(ar)}"}
        """.trimIndent()
    }

    fun placesFor(destinationId: String) = PlaceCatalog.forDestination(destinationId)
}
