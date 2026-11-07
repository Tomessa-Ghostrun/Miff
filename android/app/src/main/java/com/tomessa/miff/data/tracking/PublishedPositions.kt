package com.tomessa.miff.data.tracking

/*
 * PRIVACY, for this file: what the phone remembers of the positions it published, and nothing more (a yes or a no per Miff, never a coordinate: the local
 * state of the detection holds the coordinates, this only says whether one was written). Nothing is read from the system, nothing is logged.
 */

/**
 * Has this phone PUBLISHED its own last position in this Miff? (contract of backend/data-model.md for the photo of an update: "j'ai publié ma position
 * dans cette Miff", the formal reciprocity of the reading of the photos: without it the person could not read the photos of the others.)
 * The detection writes the position when the app opens, once the person agreed and gave the location.
 */
fun interface PublishedPositions {
    fun isPublished(miffId: String): Boolean
}

/** The answer of the local state of the detection: a position was written to this Miff. */
class LocalPublishedPositions(
    private val local: TrackingLocalStore,
) : PublishedPositions {
    override fun isPublished(miffId: String): Boolean =
        local
            .load()
            ?.miffs
            ?.get(miffId)
            ?.positionAtMillis != null
}
