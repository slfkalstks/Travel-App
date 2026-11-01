package com.travel.app.data

import com.travel.app.platform.LocalFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TravelSnapshot(
    val version: Int = 1,
    val trips: List<Trip> = emptyList(),
    val workspaces: Map<String, Workspace> = emptyMap(),
    val lastTripId: String? = null,
)

/** Downloaded server snapshots only. Unsynced edits never enter this cache. */
class TravelCache(private val files: LocalFiles) {
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private suspend fun readUnlocked(): TravelSnapshot {
        val bytes = files.read("travel-snapshot-v1.json") ?: return TravelSnapshot()
        return json.decodeFromString<TravelSnapshot>(bytes.decodeToString()).also { snapshot ->
            require(snapshot.version == 1)
            require(snapshot.workspaces.all { (id, workspace) -> id == workspace.trip.id })
        }
    }
    suspend fun read(): TravelSnapshot = lock.withLock { readUnlocked() }
    suspend fun save(trips: List<Trip>, workspace: Workspace?) = lock.withLock {
        val before = try { readUnlocked() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            // Keep unreadable bytes before replacing this downloaded replica.
            if(files.read("travel-snapshot-unreadable-backup.json") == null) {
                files.read("travel-snapshot-v1.json")?.let { files.write("travel-snapshot-unreadable-backup.json", it) }
            }
            TravelSnapshot()
        }
        val ids = trips.map { it.id }.toSet()
        val workspaces = before.workspaces.filterKeys { it in ids }.toMutableMap()
        if (workspace != null && workspace.trip.id in ids) workspaces[workspace.trip.id] = workspace
        val selected = workspace?.trip?.id ?: before.lastTripId?.takeIf { it in ids }
        files.write("travel-snapshot-v1.json", json.encodeToString(TravelSnapshot(trips = trips, workspaces = workspaces, lastTripId = selected)).encodeToByteArray())
    }
}
