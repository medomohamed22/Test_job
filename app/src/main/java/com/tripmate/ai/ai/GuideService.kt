package com.tripmate.ai.ai

import android.graphics.Bitmap
import com.tripmate.ai.data.db.GuideCacheEntity
import com.tripmate.ai.data.db.TripMateDatabase
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.model.AiExecutionInfo
import com.tripmate.ai.domain.model.AiResult
import com.tripmate.ai.domain.model.AiRoute
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tour-guide content with honest provenance:
 * 1. Gemini (on-device or cloud) when available and allowed;
 * 2. previously generated AI text cached locally ("Saved offline content");
 * 3. curated bilingual catalog text ("Saved offline content");
 * 4. otherwise an explicit "not available" state.
 */
@Singleton
class GuideService @Inject constructor(
    private val ai: AiInferenceCoordinator,
    db: TripMateDatabase,
) {
    private val cache = db.guideCacheDao()

    private fun key(placeKey: String, kind: String, ar: Boolean) = "$placeKey|$kind|${if (ar) "ar" else "en"}"

    suspend fun guide(placeId: String?, title: String, destination: String, style: GuideStyle, ar: Boolean, cloudAllowed: Boolean): AiResult {
        val place = PlaceCatalog.place(placeId)
        val curated: () -> String? = {
            place?.guide(ar)?.let { g -> when (style) { GuideStyle.SHORT -> g.short; GuideStyle.DETAILED -> g.detailed; GuideStyle.KIDS -> g.kids } }
        }
        return run(AiTask.GUIDE_NARRATIVE, key(placeId ?: title, "guide_${style.name}", ar), AiPrompts.guide(title, destination, style, ar), cloudAllowed, curated)
    }

    suspend fun question(placeId: String?, title: String, destination: String, q: GuideQuestion, ar: Boolean, cloudAllowed: Boolean): AiResult {
        val place = PlaceCatalog.place(placeId)
        val curated: () -> String? = {
            val g = place?.guide(ar)
            when (q) {
                GuideQuestion.LOOK_FOR -> g?.lookFor?.joinToString("\n") { "• $it" }
                GuideQuestion.HISTORY -> g?.detailed
                GuideQuestion.ARCHITECTURE -> g?.lookFor?.joinToString("\n") { "• $it" }
                GuideQuestion.CUSTOMS -> PlaceCatalog.customs(ar)
                GuideQuestion.TIPS -> g?.tips
            }
        }
        return run(AiTask.GUIDE_QUESTION, key(placeId ?: title, "q_${q.name}", ar), AiPrompts.question(title, destination, q, ar), cloudAllowed, curated)
    }

    suspend fun ask(title: String, destination: String, question: String, ar: Boolean, cloudAllowed: Boolean): AiResult =
        run(AiTask.GUIDE_QUESTION, null, AiPrompts.freeQuestion(title, destination, question, ar), cloudAllowed) { null }

    suspend fun summarize(passage: String, ar: Boolean): AiResult =
        ai.generate(AiTask.SUMMARIZE_CACHED, AiPrompts.summarize(passage, ar))

    suspend fun explainPhoto(bitmap: Bitmap, title: String?, ar: Boolean): AiResult =
        ai.generate(AiTask.PHOTO_EXPLAIN, AiPrompts.photo(title, ar), image = bitmap)

    /**
     * [cloudAllowed] = user consented to cloud AI. Without consent only on-device inference
     * (if the model is available) and offline content are used.
     */
    private suspend fun run(task: AiTask, cacheKey: String?, prompt: String, cloudAllowed: Boolean, curated: () -> String?): AiResult {
        val cached = cacheKey?.let { cache.get(it) }
        val offline: () -> String? = { cached?.text ?: curated() }
        val effectiveTask = if (cloudAllowed) task else AiTask.GUIDE_LOCAL_ONLY
        val result = ai.generate(effectiveTask, prompt, fallback = offline)
        if (cacheKey != null && (result.info.route == AiRoute.CLOUD || result.info.route == AiRoute.ON_DEVICE) && result.text.isNotBlank()) {
            cache.upsert(GuideCacheEntity(cacheKey, result.text, result.info.route.name, System.currentTimeMillis()))
        }
        if (!cloudAllowed && result.info.route != AiRoute.ON_DEVICE && result.text.isNotBlank()) {
            return result.copy(info = result.info.copy(route = if (result.info.route == AiRoute.DEMO) AiRoute.DEMO else AiRoute.OFFLINE_CONTENT, fallbackReason = "cloud_consent_not_given"))
        }
        return result
    }

    fun unavailable(task: String) = AiResult("", AiExecutionInfo(task, AiRoute.UNAVAILABLE))
}
