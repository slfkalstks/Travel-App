package com.yeohaeng.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID

// schemaVersion remains readable on nested entries to preserve schema-1 receipts verbatim.
data class StoredWorkspace(val workspace: Workspace, val receipts: Map<String, String> = emptyMap(), val schemaVersion: Int = 1)
data class TripCreationReceipt(val tripId: String, val fingerprint: String)
data class StoredCollection(val workspaces: Map<String, StoredWorkspace>,
    val creationReceipts: Map<String, TripCreationReceipt> = emptyMap(), val schemaVersion: Int = 2)
class IdempotencyConflict(message: String = "같은 저장 키로 다른 요청이 전달되었어요. 입력을 확인하고 새로 저장해 주세요.") : RuntimeException(message)

/** Single-process local adapter. Trip selection is request-local, never mutable global state. */
@Repository
class WorkspaceRepository(private val mapper: ObjectMapper, @Value("\${travel.data-file}") dataFile: String) {
    private val path = Path.of(dataFile).toAbsolutePath()
    private var state: StoredCollection = load()

    private fun load(): StoredCollection {
        if (!Files.exists(path)) {
            val seed = javaClass.getResourceAsStream("/seed-workspace.json")!!.use { mapper.readValue<Workspace>(it) }
            return StoredCollection(linkedMapOf(seed.trip.id to StoredWorkspace(seed))).also { persist(it) }
        }
        val tree = mapper.readTree(path.toFile())
        require(tree.isObject) { "올바르지 않은 로컬 저장 형식입니다." }
        val declaredVersion = tree.get("schemaVersion")
        if (declaredVersion != null) require(declaredVersion.isIntegralNumber && declaredVersion.canConvertToInt()) { "올바르지 않은 로컬 저장 버전입니다." }
        if (declaredVersion?.intValue() == 2) {
            val collection = mapper.treeToValue(tree, StoredCollection::class.java)
            require(collection.workspaces.all { (id, entry) -> id == entry.workspace.trip.id && entry.schemaVersion == 1 }) {
                "여행 ID 또는 저장 버전이 일치하지 않습니다."
            }
            return collection // Empty collections remain empty on restart.
        }
        val legacy = when {
            declaredVersion != null -> {
                require(declaredVersion.intValue() == 1) { "지원하지 않는 로컬 저장 형식입니다." }
                mapper.treeToValue(tree, StoredWorkspace::class.java)
            }
            tree.has("workspace") -> mapper.treeToValue(tree, StoredWorkspace::class.java)
            else -> StoredWorkspace(mapper.treeToValue(tree, Workspace::class.java))
        }
        require(legacy.schemaVersion == 1) { "지원하지 않는 로컬 저장 형식입니다." }
        val migrated = StoredCollection(linkedMapOf(legacy.workspace.trip.id to legacy))
        // Backup original bytes before replacing the file. Failed conversion never overwrites source.
        val backup = path.resolveSibling(path.fileName.toString() + ".pre-schema2-" + UUID.randomUUID() + ".bak")
        Files.copy(path, backup)
        java.nio.channels.FileChannel.open(backup, java.nio.file.StandardOpenOption.WRITE).use { it.force(true) }
        persist(migrated)
        return migrated
    }

    private fun resolveTripId(tripId: String?): String = tripId ?: state.workspaces.keys.firstOrNull { it == "trip-osaka-2026" }
        ?: state.workspaces.keys.firstOrNull() ?: throw MissingItem()

    @Synchronized fun read(tripId: String? = null): Workspace = state.workspaces[resolveTripId(tripId)]?.workspace ?: throw MissingItem()
    @Synchronized fun trips(): List<Trip> = state.workspaces.values.map { it.workspace.trip }

    @Synchronized fun update(idempotencyKey: String? = null, operation: String = "", request: Any? = null,
        tripId: String? = null, transform: (Workspace) -> Workspace): Workspace {
        val id = resolveTripId(tripId)
        val entry = state.workspaces[id] ?: throw MissingItem()
        val fingerprint = fingerprint(idempotencyKey, operation, request)
        if (idempotencyKey != null && entry.receipts.containsKey(idempotencyKey)) {
            if (entry.receipts[idempotencyKey] != fingerprint) throw IdempotencyConflict()
            return entry.workspace
        }
        val workspace = transform(entry.workspace)
        require(workspace.trip.id == id) { "여행 ID는 변경할 수 없습니다." }
        val updated = entry.copy(workspace = workspace, receipts = if (idempotencyKey == null) entry.receipts
            else entry.receipts + (idempotencyKey to fingerprint!!))
        commit(state.copy(workspaces = state.workspaces + (id to updated)))
        return workspace
    }

    @Synchronized fun createTrip(request: TripRequest, key: String?, create: () -> Workspace): Workspace {
        val fingerprint = fingerprint(key, "POST /trips", request)
        if (key != null && state.creationReceipts.containsKey(key)) {
            val receipt = state.creationReceipts.getValue(key)
            if (receipt.fingerprint != fingerprint) throw IdempotencyConflict()
            return state.workspaces[receipt.tripId]?.workspace
                ?: throw IdempotencyConflict("이미 삭제한 여행의 생성 요청입니다. 새 여행을 만들려면 새 요청 키를 사용해 주세요.")
        }
        val workspace = create()
        val receipts = if (key == null) state.creationReceipts else state.creationReceipts +
            (key to TripCreationReceipt(workspace.trip.id, fingerprint!!))
        commit(state.copy(workspaces = state.workspaces + (workspace.trip.id to StoredWorkspace(workspace)), creationReceipts = receipts))
        return workspace
    }

    @Synchronized fun deleteTrip(id: String): List<Trip> {
        if (state.workspaces.containsKey(id)) commit(state.copy(workspaces = state.workspaces - id))
        return trips() // Keep creation receipts so a delayed POST cannot resurrect deleted trips.
    }

    private fun fingerprint(key: String?, operation: String, request: Any?): String? {
        if (key == null) return null
        require(key.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "저장 키는 영문·숫자·점·밑줄·하이픈 1~128자로 입력해 주세요." }
        return MessageDigest.getInstance("SHA-256").digest((operation + "\n" + mapper.writeValueAsString(request)).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    private fun commit(next: StoredCollection) {
        persist(next)
        state = next
    }
    private fun persist(collection: StoredCollection) {
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, ".workspace-", ".json")
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), collection)
            java.nio.channels.FileChannel.open(temp, java.nio.file.StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temp, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
