package com.tripmate.ai

import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.ai.AiJson
import com.tripmate.ai.domain.ai.AdjustIntentJson
import com.tripmate.ai.domain.ai.PlanIntent
import com.tripmate.ai.domain.logic.ItineraryGenerator
import com.tripmate.ai.domain.logic.ItineraryValidator
import com.tripmate.ai.domain.logic.TripRequest
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.Verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonAndGeneratorTest {
    private val request = TripRequest(
        destinationId = "cairo", destinationName = "Cairo", startDate = TestData.date, endDate = TestData.date.plusDays(2),
        currency = "EGP", budget = 15000.0, interests = setOf(Interest.HISTORY, Interest.MUSEUMS), pace = Pace.BALANCED, groupSize = 2,
    )
    private val generator = ItineraryGenerator(TestData::id)

    @Test fun `deterministic 3-day Cairo plan is valid, unique and fits each day`() {
        val plan = generator.generate(request, PlaceCatalog.places, TestData.labels, false, 0)
        assertEquals(3, plan.dayCount)
        assertFalse(ItineraryValidator.hasErrors(plan))
        val placeIds = plan.allStops.mapNotNull { it.placeId }
        assertEquals(placeIds.size, placeIds.toSet().size)
        plan.days.forEach { d ->
            assertTrue(d.stops.any { it.category == StopCategory.MEAL })
            assertTrue(d.stops.all { it.endMinutes <= d.endMinutes + 60 })
        }
        assertTrue("must-see pyramids included", "giza_pyramids" in placeIds)
        assertTrue(plan.allStops.filter { it.placeId != null }.all { it.verification == Verification.SAMPLE })
    }

    @Test fun `parses fenced JSON and rejects malformed output`() {
        val raw = "Sure!\n```json\n{\"days\":[{\"stops\":[{\"placeId\":\"gem\",\"durationMinutes\":150}]}]}\n```"
        assertNotNull(AiJson.parseDraft(raw))
        assertNull(AiJson.parseDraft("not json at all"))
        assertNull(AiJson.parseDraft("{\"days\":[]}"))
        assertNull(AiJson.parseDraft("{\"days\":[{\"stops\":[{\"placeId\":"))
    }

    @Test fun `applyDraft keeps catalog data, marks unknown places unverified and clamps durations`() {
        val baseline = generator.generate(request, PlaceCatalog.places, TestData.labels, false, 0)
        val raw = """{"days":[{"stops":[
            {"placeId":"giza_pyramids","durationMinutes":9999,"note":"Go early"},
            {"placeId":"giza_pyramids","durationMinutes":60},
            {"placeId":"alexandria_only_fake","title":"Secret Rooftop","durationMinutes":5},
            {"placeId":"bibliotheca","durationMinutes":60}
        ]}]}"""
        val draft = AiJson.parseDraft(raw)!!
        val plan = AiJson.applyDraft(draft, baseline, request, PlaceCatalog.places, TestData.labels, false, generator, TestData::id)!!
        val day0 = plan.days[0].stops
        assertEquals(1, day0.count { it.placeId == "giza_pyramids" })               // duplicate removed
        assertTrue(day0.first { it.placeId == "giza_pyramids" }.durationMinutes <= 300) // clamped
        assertTrue(day0.none { it.placeId == "bibliotheca" })                          // wrong city rejected
        val custom = day0.first { it.title == "Secret Rooftop" }
        assertEquals(Verification.UNVERIFIED, custom.verification)
        assertNull(custom.lat)
        assertTrue(custom.durationMinutes >= 20)
        assertFalse(ItineraryValidator.hasErrors(plan))
    }

    @Test fun `intent mapping clamps and resolves stop titles`() {
        val day = TestData.day()
        assertEquals(PlanIntent.Delay(45), AiJson.toPlanIntent(AdjustIntentJson("delay", 45), day))
        assertEquals(PlanIntent.Unknown, AiJson.toPlanIntent(AdjustIntentJson("DELAY", 99999), day))
        assertEquals(PlanIntent.Break(240), AiJson.toPlanIntent(AdjustIntentJson("BREAK", 1000), day))
        assertEquals(PlanIntent.Skip("C"), AiJson.toPlanIntent(AdjustIntentJson("SKIP", stopTitle = "stop c"), day))
        assertEquals(PlanIntent.Unknown, AiJson.toPlanIntent(AdjustIntentJson("rm -rf"), day))
    }

    @Test fun `offline keyword intents work in English and Arabic`() {
        val day = TestData.day()
        assertEquals(PlanIntent.Delay(90), AiJson.keywordIntent("we're running late, about 90 minutes", day))
        assertEquals(PlanIntent.Delay(45), AiJson.keywordIntent("متأخرين ٤٥ دقيقة", day))
        assertEquals(PlanIntent.Delay(60), AiJson.keywordIntent("تأخير ساعة", day))
        assertEquals(PlanIntent.Break(20), AiJson.keywordIntent("need a coffee break 20 min", day))
        assertEquals(PlanIntent.ChangePace(Pace.RELAXED), AiJson.keywordIntent("let's slow down", day))
        assertEquals(PlanIntent.Unknown, AiJson.keywordIntent("hello", day))
    }
}
