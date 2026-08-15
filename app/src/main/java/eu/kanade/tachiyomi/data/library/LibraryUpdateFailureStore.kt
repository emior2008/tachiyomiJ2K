package eu.kanade.tachiyomi.data.library

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

@Serializable
data class LibraryUpdateFailure(
    val mangaId: Long,
    val error: String? = null,
)

@Serializable
private data class LibraryUpdateFailureList(
    val failures: List<LibraryUpdateFailure> = emptyList(),
)

/**
 * Persists unresolved failures from the most recent chapter library update.
 *
 * The file is only touched when an update records results, or when the Failed updates screen
 * explicitly reads/clears it. Constructing this store does not perform disk I/O.
 */
class LibraryUpdateFailureStore(
    context: Context,
    private val json: Json,
) {
    private val failureFile = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    suspend fun getAll(): List<LibraryUpdateFailure> =
        mutex.withLock {
            readUnlocked()
        }

    suspend fun clear() {
        mutex.withLock {
            failureFile.delete()
        }
    }

    /** Removes only the requested entries and returns the failures that remain. */
    suspend fun remove(mangaIds: Set<Long>): List<LibraryUpdateFailure> {
        if (mangaIds.isEmpty()) return getAll()
        return mutex.withLock {
            val remaining = readUnlocked().filterNot { it.mangaId in mangaIds }
            writeUnlocked(remaining)
            remaining
        }
    }

    /** Replaces the previous update's failures with failures from a new normal library update. */
    suspend fun replace(failures: Collection<LibraryUpdateFailure>) {
        mutex.withLock {
            writeUnlocked(failures)
        }
    }

    /**
     * Applies the result of a targeted retry without disturbing failures that were not selected.
     * Selected entries are removed first; entries which failed again are then written back with
     * their newest error.
     */
    suspend fun applyRetryResult(
        attemptedMangaIds: Set<Long>,
        failures: Collection<LibraryUpdateFailure>,
    ) {
        mutex.withLock {
            val untouched = readUnlocked().filterNot { it.mangaId in attemptedMangaIds }
            writeUnlocked(untouched + failures)
        }
    }

    private fun readUnlocked(): List<LibraryUpdateFailure> {
        if (!failureFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<LibraryUpdateFailureList>(failureFile.readText()).failures
        }.getOrElse { error ->
            Timber.e(error, "Failed to read saved library update failures")
            emptyList()
        }
    }

    private fun writeUnlocked(failures: Collection<LibraryUpdateFailure>) {
        val deduplicated = failures.distinctBy { it.mangaId }
        if (deduplicated.isEmpty()) {
            failureFile.delete()
            return
        }

        runCatching {
            failureFile.writeText(json.encodeToString(LibraryUpdateFailureList(deduplicated)))
        }.onFailure { error ->
            Timber.e(error, "Failed to save library update failures")
        }
    }

    private companion object {
        private const val FILE_NAME = "library_update_failures.json"
    }
}
