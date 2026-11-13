package com.tomessa.miff.data.about

import android.content.res.AssetManager
import com.tomessa.miff.domain.about.LicenseInventory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Where the screen "Licences et attributions" reads from: the app's OWN files, so it works OFFLINE (criterion AP12) and sends nothing (criterion AP18).
 * A seam so that the screens are tested without the real assets.
 */
interface LicenseSource {
    /** The inventory, or null when the file cannot be read or understood (the screen then says so calmly). */
    suspend fun inventory(): LicenseInventory?

    /** The full text of a licence, or null when it cannot be read. */
    suspend fun text(file: String): String?
}

/** Reads `assets/licenses/licenses.json` and `assets/licenses/texts/<file>`. Reading happens off the main thread. */
class AssetLicenseSource(
    private val assets: AssetManager,
    /** Where the files are read: off the main thread. The tests give an immediate dispatcher, so that the screens are drawn with their content at once. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LicenseSource {
    override suspend fun inventory(): LicenseInventory? =
        withContext(dispatcher) {
            try {
                LicenseInventory.parse(read("$ROOT/licenses.json"))
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                // kotlinx.serialization: malformed or unexpected file.
                null
            }
        }

    override suspend fun text(file: String): String? {
        // The name comes from our own file, but a path is never built from a value we did not check: one plain file name, nothing else.
        if (!SAFE_FILE_NAME.matches(file)) return null
        return withContext(dispatcher) {
            try {
                read("$ROOT/texts/$file")
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun read(path: String): String = assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    companion object {
        const val ROOT = "licenses"
        val SAFE_FILE_NAME = Regex("^[a-z0-9][a-z0-9.-]*\\.txt$")
    }
}
