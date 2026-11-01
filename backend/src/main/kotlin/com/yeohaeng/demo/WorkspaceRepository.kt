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

data class StoredWorkspace(val workspace: Workspace, val receipts: Map<String, String> = emptyMap(), val schemaVersion: Int = 1)
class IdempotencyConflict : RuntimeException("같은 저장 키로 다른 요청이 전달되었어요. 입력을 확인하고 새로 저장해 주세요.")

/** Single-process local-demo adapter. Production must replace this with a transactional DB. */
@Repository
class WorkspaceRepository(private val mapper: ObjectMapper, @Value("\${travel.data-file}") dataFile: String) {
    private val path = Path.of(dataFile).toAbsolutePath()
    private var state: StoredWorkspace = load()

    private fun load(): StoredWorkspace {
        val existing = if (Files.exists(path)) {
            val tree = mapper.readTree(path.toFile())
            if (tree.has("workspace")) mapper.treeToValue(tree, StoredWorkspace::class.java)
            else StoredWorkspace(mapper.treeToValue(tree, Workspace::class.java))
        } else StoredWorkspace(javaClass.getResourceAsStream("/seed-workspace.json")!!.use { mapper.readValue<Workspace>(it) })
        require(existing.schemaVersion == 1) { "지원하지 않는 로컬 저장 형식입니다." }
        // Narrow seed correction preserves every completion state and all user-created records.
        val corrected = existing.copy(workspace = existing.workspace.copy(tasks = existing.workspace.tasks.map {
            if (it.id == "task-04" && it.title == "여행자 보험 비교하기") it.copy(title = "여행자 보험 증권 확인") else it
        }))
        if (!Files.exists(path) || corrected != existing) persist(corrected)
        return corrected
    }

    @Synchronized fun read(): Workspace = state.workspace

    @Synchronized fun update(idempotencyKey: String? = null, operation: String = "", request: Any? = null,
        transform: (Workspace) -> Workspace): Workspace {
        val fingerprint = if (idempotencyKey != null) {
            require(idempotencyKey.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "저장 키는 영문·숫자·점·밑줄·하이픈 1~128자로 입력해 주세요." }
            MessageDigest.getInstance("SHA-256").digest((operation + "\n" + mapper.writeValueAsString(request)).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } else null
        if (idempotencyKey != null && state.receipts.containsKey(idempotencyKey)) {
            if (state.receipts[idempotencyKey] != fingerprint) throw IdempotencyConflict()
            return state.workspace // Return fresh state, including changes made since the original POST.
        }
        val workspace = transform(state.workspace)
        val next = state.copy(workspace = workspace, receipts = if (idempotencyKey == null) state.receipts
            else state.receipts + (idempotencyKey to fingerprint!!))
        persist(next) // Only expose successful writes; errors leave memory unchanged.
        state = next
        return next.workspace
    }

    private fun persist(workspace: StoredWorkspace) {
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, ".workspace-", ".json")
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), workspace)
            java.nio.channels.FileChannel.open(temp, java.nio.file.StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temp, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
