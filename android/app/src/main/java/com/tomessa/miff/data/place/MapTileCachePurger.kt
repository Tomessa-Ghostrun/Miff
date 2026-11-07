package com.tomessa.miff.data.place

import android.content.Context
import com.tomessa.miff.data.session.SessionTeardown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager
import java.io.File
import kotlin.coroutines.resume

/*
 * SECURITY, for this file: the map keeps the tiles it downloaded in a small database of the app (`mbgl-offline.db`, in the private
 * files of the app, excluded from every backup: data_extraction_rules.xml). The tiles shown are the area around places (a home,
 * a school): they are the picture of where the person goes, so they are erased with the session, like the local Firestore cache.
 * Nothing about a tile is logged.
 */

/**
 * Erases the tiles of the map when the session ends (sign-out, loss of session, and the deletion of the account, which goes through
 * the same teardown). Behind functions so that the logic is tested without the map library.
 */
class MapTileCachePurger(
    private val hasCache: () -> Boolean,
    private val clear: suspend () -> Unit,
    private val timeoutMillis: Long = TIMEOUT_MS,
) : SessionTeardown {
    override suspend fun stop() {
        // Nothing to erase if the map was never shown: the map library is not even started for that.
        if (!hasCache()) return
        try {
            withTimeoutOrNull(timeoutMillis) { clear() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A tile cache that cannot be erased must never block a sign-out; the next session erases it again.
        }
    }

    companion object {
        const val TIMEOUT_MS = 5_000L

        /** The file of the tile cache of MapLibre (private files of the app, unless the app asks for the external storage: it does not). */
        const val DATABASE_NAME = "mbgl-offline.db"

        fun forContext(context: Context): MapTileCachePurger {
            val app = context.applicationContext
            return MapTileCachePurger(
                hasCache = { File(app.filesDir, DATABASE_NAME).exists() },
                clear = {
                    withContext(Dispatchers.Main) {
                        MapLibre.getInstance(app)
                        suspendCancellableCoroutine { continuation ->
                            OfflineManager.getInstance(app).clearAmbientCache(
                                object : OfflineManager.FileSourceCallback {
                                    override fun onSuccess() {
                                        if (continuation.isActive) continuation.resume(Unit)
                                    }

                                    override fun onError(message: String) {
                                        if (continuation.isActive) continuation.resume(Unit)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}
