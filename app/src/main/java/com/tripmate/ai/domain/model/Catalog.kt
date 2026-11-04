package com.tripmate.ai.domain.model

/** Visual motif used for the original, bundled destination illustrations (no stock photos). */
enum class ArtMotif { PYRAMIDS, SPHINX, MUSEUM, MOSQUE, BAZAAR, GARDEN, NILE, CHURCH, TOWER, SEA, FORT, LIBRARY, MEAL, BREAK, CITY }

data class Destination(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val countryEn: String,
    val countryAr: String,
    val lat: Double,
    val lng: Double,
    val motif: ArtMotif,
    val seeded: Boolean,
) {
    fun name(ar: Boolean) = if (ar) nameAr else nameEn
}

/** Curated, bilingual offline guide text. Shown with the "Saved offline content" provenance. */
data class LocalizedGuide(
    val short: String,
    val detailed: String,
    val kids: String,
    val lookFor: List<String>,
    val tips: String,
)

data class CatalogPlace(
    val id: String,
    val destinationIds: Set<String>,
    val nameEn: String,
    val nameAr: String,
    val area: String,
    val lat: Double,
    val lng: Double,
    val interests: Set<Interest>,
    val category: StopCategory,
    val typicalDurationMinutes: Int,
    val minDurationMinutes: Int,
    val priority: Int,
    /** Typical opening window — illustrative and NOT verified. */
    val typicalOpens: Int?,
    val typicalCloses: Int?,
    /** Illustrative per-person estimate in [priceCurrency]; NOT live pricing. */
    val priceEstimate: Double?,
    val priceCurrency: String = "EGP",
    val motif: ArtMotif,
    val walkingIntensity: WalkingLevel,
    val guideEn: LocalizedGuide,
    val guideAr: LocalizedGuide,
    val attribution: String = "TripMate curated sample content",
) {
    fun name(ar: Boolean) = if (ar) nameAr else nameEn
    fun guide(ar: Boolean) = if (ar) guideAr else guideEn
}
