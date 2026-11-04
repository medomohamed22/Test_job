package com.tripmate.ai.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.ondevice.DownloadStatus
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.tripmate.ai.BuildConfig
import com.tripmate.ai.domain.model.AiExecutionInfo
import com.tripmate.ai.domain.model.AiResult
import com.tripmate.ai.domain.model.AiRoute
import com.tripmate.ai.domain.model.OnDeviceAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** What each AI task needs; decides the Firebase [InferenceMode]. */
enum class AiTask(val preferOnDevice: Boolean, val cloudOnly: Boolean, val onDeviceOnly: Boolean = false) {
    GUIDE_NARRATIVE(false, false),
    GUIDE_QUESTION(false, false),
    TRIP_CHAT(false, false),
    SUMMARIZE_CACHED(true, false),
    PARAPHRASE_NOTE(true, false),
    NEXT_STOP_CHECKLIST(true, false),
    ITINERARY_JSON(false, true),
    ADJUST_INTENT_JSON(false, true),
    PHOTO_EXPLAIN(false, true),
    GUIDE_LOCAL_ONLY(true, false, onDeviceOnly = true),
    ON_DEVICE_SELF_TEST(true, false, onDeviceOnly = true),
}

data class AiStatus(
    val firebaseConfigured: Boolean,
    val online: Boolean,
    val onDevice: OnDeviceAvailability,
    val cloudModel: String,
    val downloadProgressBytes: Long? = null,
    val lastError: String? = null,
)

/**
 * Hybrid inference router built on Firebase AI Logic (firebase-ai + firebase-ai-ondevice).
 *
 * Guarantees:
 * - The provenance shown to the user ([AiRoute]) is taken from the SDK response's
 *   `inferenceSource` — never assumed.
 * - With no Firebase config the app runs in DEMO mode and AI calls return curated content.
 * - Offline without an on-device model, calls fail fast and fall back to saved content.
 * - Prompts/locations are never logged; only task name, route, latency and error class.
 */
@OptIn(PublicPreviewAPI::class)
@Singleton
class AiInferenceCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val network: NetworkMonitor,
) {
    private val firebaseConfigured: Boolean
        get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val _status = MutableStateFlow(
        AiStatus(firebaseConfigured, network.onlineNow(), if (firebaseConfigured) OnDeviceAvailability.UNKNOWN else OnDeviceAvailability.NOT_CONFIGURED, BuildConfig.GEMINI_CLOUD_MODEL),
    )
    val status: StateFlow<AiStatus> = _status.asStateFlow()

    private val _history = MutableStateFlow<List<AiExecutionInfo>>(emptyList())
    val history: StateFlow<List<AiExecutionInfo>> = _history.asStateFlow()

    private val models = HashMap<Pair<InferenceMode, Boolean>, GenerativeModel>()

    /** Checks (but never silently downloads) the on-device model via AICore. */
    suspend fun refreshOnDeviceStatus(): OnDeviceAvailability {
        val availability = if (!firebaseConfigured) OnDeviceAvailability.NOT_CONFIGURED else try {
            when (FirebaseAIOnDevice.checkStatus()) {
                OnDeviceModelStatus.AVAILABLE -> OnDeviceAvailability.AVAILABLE
                OnDeviceModelStatus.DOWNLOADABLE -> OnDeviceAvailability.DOWNLOADABLE
                OnDeviceModelStatus.DOWNLOADING -> OnDeviceAvailability.DOWNLOADING
                OnDeviceModelStatus.UNAVAILABLE -> OnDeviceAvailability.UNAVAILABLE
                else -> OnDeviceAvailability.UNKNOWN
            }
        } catch (c: CancellationException) { throw c } catch (t: Throwable) {
            _status.update { it.copy(lastError = t.javaClass.simpleName) }
            OnDeviceAvailability.ERROR
        }
        _status.update { it.copy(firebaseConfigured = firebaseConfigured, online = network.onlineNow(), onDevice = availability) }
        return availability
    }

    /** User-initiated model download (Diagnostics screen). */
    suspend fun downloadOnDeviceModel() {
        if (!firebaseConfigured) return
        try {
            FirebaseAIOnDevice.download().collect { s ->
                when (s) {
                    is DownloadStatus.DownloadStarted -> _status.update { it.copy(onDevice = OnDeviceAvailability.DOWNLOADING, downloadProgressBytes = 0) }
                    is DownloadStatus.DownloadInProgress -> _status.update { it.copy(onDevice = OnDeviceAvailability.DOWNLOADING, downloadProgressBytes = s.totalBytesDownloaded) }
                    is DownloadStatus.DownloadCompleted -> _status.update { it.copy(onDevice = OnDeviceAvailability.AVAILABLE, downloadProgressBytes = null) }
                    is DownloadStatus.DownloadFailed -> _status.update { it.copy(onDevice = OnDeviceAvailability.ERROR, lastError = "DownloadFailed") }
                    else -> Unit
                }
            }
        } catch (c: CancellationException) { throw c } catch (t: Throwable) {
            _status.update { it.copy(onDevice = OnDeviceAvailability.ERROR, lastError = t.javaClass.simpleName) }
        }
    }

    private fun model(mode: InferenceMode, json: Boolean): GenerativeModel = models.getOrPut(mode to json) {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = BuildConfig.GEMINI_CLOUD_MODEL,
            generationConfig = generationConfig {
                temperature = if (json) 0.2f else 0.5f
                if (json) responseMimeType = "application/json"
            },
            onDeviceConfig = OnDeviceConfig(mode = mode),
        )
    }

    /**
     * Runs [prompt] on the best allowed route. If AI is unavailable or fails, returns
     * [fallback] (curated/deterministic content) with an honest provenance label, or null text.
     */
    suspend fun generate(
        task: AiTask,
        prompt: String,
        image: Bitmap? = null,
        json: Boolean = false,
        fallback: (() -> String?)? = null,
    ): AiResult {
        val online = network.onlineNow()
        val onDevice = _status.value.onDevice.let { if (it == OnDeviceAvailability.UNKNOWN && firebaseConfigured) refreshOnDeviceStatus() else it }
        _status.update { it.copy(online = online) }

        fun fallbackResult(route: AiRoute, reason: String, error: String? = null, ms: Long? = null): AiResult {
            val text = fallback?.invoke()
            val info = AiExecutionInfo(task.name, if (text != null) route else AiRoute.UNAVAILABLE, ms, onDevice, reason, error)
            record(info)
            return AiResult(text.orEmpty(), info)
        }

        if (!firebaseConfigured) return fallbackResult(AiRoute.DEMO, "firebase_not_configured")
        val localReady = onDevice == OnDeviceAvailability.AVAILABLE && image == null
        if (task.onDeviceOnly && !localReady) return fallbackResult(AiRoute.OFFLINE_CONTENT, "on_device_model_${onDevice.name.lowercase()}")
        if (!online && (task.cloudOnly || !localReady)) return fallbackResult(AiRoute.OFFLINE_CONTENT, "offline_no_local_model")

        val mode = when {
            task.onDeviceOnly -> InferenceMode.ONLY_ON_DEVICE
            task.cloudOnly || image != null -> InferenceMode.ONLY_IN_CLOUD
            !online -> InferenceMode.ONLY_ON_DEVICE // avoid waiting on a cloud timeout
            task.preferOnDevice && localReady -> InferenceMode.PREFER_ON_DEVICE
            localReady -> InferenceMode.PREFER_IN_CLOUD
            else -> InferenceMode.ONLY_IN_CLOUD
        }

        val started = SystemClock.elapsedRealtime()
        return try {
            val response = withTimeout(45_000) {
                if (image != null) model(mode, json).generateContent(content { image(image); text(prompt) })
                else model(mode, json).generateContent(prompt)
            }
            val ms = SystemClock.elapsedRealtime() - started
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) return fallbackResult(AiRoute.OFFLINE_CONTENT, "empty_response", ms = ms)
            val route = when (response.inferenceSource) {
                InferenceSource.ON_DEVICE -> AiRoute.ON_DEVICE
                InferenceSource.IN_CLOUD -> AiRoute.CLOUD
                else -> if (mode == InferenceMode.ONLY_ON_DEVICE) AiRoute.ON_DEVICE else AiRoute.CLOUD
            }
            val info = AiExecutionInfo(task.name, route, ms, onDevice, fallbackReason = if (route == AiRoute.CLOUD && localReady && task.preferOnDevice) "sdk_fell_back_to_cloud" else null)
            record(info)
            AiResult(text, info)
        } catch (c: CancellationException) {
            if (c is kotlinx.coroutines.TimeoutCancellationException) fallbackResult(AiRoute.OFFLINE_CONTENT, "timeout", "Timeout", SystemClock.elapsedRealtime() - started)
            else throw c
        } catch (t: Throwable) {
            _status.update { it.copy(lastError = t.javaClass.simpleName) }
            fallbackResult(AiRoute.OFFLINE_CONTENT, "ai_error", t.javaClass.simpleName, SystemClock.elapsedRealtime() - started)
        }
    }

    private fun record(info: AiExecutionInfo) = _history.update { (listOf(info) + it).take(30) }
}
