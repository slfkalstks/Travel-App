package com.yeohaeng.demo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class DocumentTooLarge : RuntimeException("파일은 20MB까지 올릴 수 있어요.")
data class DocumentContent(val record: DocumentRecord, val stream: InputStream)
private data class DocumentFingerprint(val name: String, val reservationId: String, val mediaType: String,
    val sizeBytes: Long, val sha256: String)

/** Single-process adapter: service monitor -> repository monitor, never the reverse. */
@Service
class DocumentService(private val repository: WorkspaceRepository,
    @Value("\${travel.data-file}") dataFile: String,
    @Value("\${travel.document-root:}") documentRoot: String = "") {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val configuredRoot = if (documentRoot.isBlank()) Path.of(dataFile).toAbsolutePath().parent.resolve("documents")
        else Path.of(documentRoot).toAbsolutePath()
    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val UPLOAD_TEMP = Regex("\\.upload-[0-9]+\\.tmp")
        private val BAD_CHARACTER = Regex("[/\\\\\\u0000-\\u001f\\u007f-\\u009f\\u2028\\u2029]")
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }

    init {
        // One service owns this single-process store. Startup precedes incoming
        // uploads, so only this pass may collect abandoned upload temporary files.
        synchronized(this) { reconcile(includeUploadTemps = true) }
    }

    private fun root(create: Boolean): Path {
        val normalized = configuredRoot.normalize()
        // Configuration is trusted; canonicalize its parent, but never follow a blob-root link.
        val parent = normalized.parent.toRealPath()
        val path = parent.resolve(normalized.fileName)
        if (create && !Files.exists(path, NOFOLLOW_LINKS)) {
            try { Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))) }
            catch (_: UnsupportedOperationException) { Files.createDirectory(path) }
        }
        if (Files.exists(path, NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, NOFOLLOW_LINKS)) throw java.io.IOException("Invalid document storage directory")
        } else if (!create) throw MissingItem()
        return path
    }

    private fun blob(id: String, create: Boolean = false): Path {
        if (!ID.matches(id)) throw MissingItem()
        return root(create).resolve(id)
    }

    private fun displayName(raw: String): String {
        require(raw.length <= 200 && !BAD_CHARACTER.containsMatchIn(raw)) { "문서 이름은 경로·제어문자 없이 200자까지 입력해 주세요." }
        return raw.trim().also { require(it.isNotBlank() && it != "." && it != "..") { "문서 이름을 입력해 주세요." } }
    }

    private fun reservation(state: Workspace, id: String) {
        require(id.length <= 200 && !BAD_CHARACTER.containsMatchIn(id)) { "올바른 예약을 선택해 주세요." }
        require(id.isEmpty() || state.reservations.any { it.id == id }) { "이 여행에 있는 예약을 선택해 주세요." }
    }

    private fun mediaType(file: MultipartFile): String {
        val name = file.originalFilename ?: throw IllegalArgumentException("파일 형식을 확인해 주세요.")
        require(name.isNotBlank() && !BAD_CHARACTER.containsMatchIn(name)) { "파일 이름에 경로·제어문자를 사용할 수 없어요." }
        val expected = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
            else -> throw IllegalArgumentException("PDF, JPEG, PNG 파일만 올릴 수 있어요.")
        }
        require(file.contentType?.lowercase() == expected) { "파일 확장자와 형식이 일치하지 않아요." }
        return expected
    }

    private fun matches(prefix: ByteArray, type: String): Boolean = when (type) {
        "application/pdf" -> prefix.size >= 5 && prefix.take(5).toByteArray().contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
        "image/png" -> prefix.size >= 8 && prefix.take(8).toByteArray().contentEquals(PNG)
        "image/jpeg" -> prefix.size >= 3 && prefix[0] == 0xff.toByte() && prefix[1] == 0xd8.toByte() && prefix[2] == 0xff.toByte()
        else -> false
    }

    @Synchronized fun upload(file: MultipartFile, rawName: String, reservationId: String, key: String?, tripId: String?): Workspace {
        val name = displayName(rawName)
        val type = mediaType(file)
        if (file.size > MAX_BYTES) throw DocumentTooLarge()
        require(file.size > 0) { "빈 파일은 올릴 수 없어요." }
        // Check links only inside the transform: a successful retry must return current
        // state even if its formerly linked reservation was deleted in the meantime.
        repository.read(tripId)
        val directory = root(true)
        val temp = try { Files.createTempFile(directory, ".upload-", ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) }
            catch (_: UnsupportedOperationException) { Files.createTempFile(directory, ".upload-", ".tmp") }
        var newBlob: Path? = null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var prefix = byteArrayOf()
            Files.newByteChannel(temp, setOf(WRITE, NOFOLLOW_LINKS)).use { output ->
                file.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (read == 0) continue
                        total += read
                        if (total > MAX_BYTES) throw DocumentTooLarge()
                        if (prefix.size < 8) prefix += buffer.copyOfRange(0, minOf(read, 8 - prefix.size))
                        digest.update(buffer, 0, read)
                        val bytes = java.nio.ByteBuffer.wrap(buffer, 0, read)
                        while (bytes.hasRemaining()) output.write(bytes)
                    }
                }
            }
            require(total > 0 && matches(prefix, type)) { "파일 내용과 형식이 일치하지 않아요." }
            java.nio.channels.FileChannel.open(temp, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val fingerprint = DocumentFingerprint(name, reservationId, type, total, sha)
            try {
                return repository.update(key, "POST /documents", fingerprint, tripId) { state ->
                    reservation(state, reservationId)
                    val id = UUID.randomUUID().toString()
                    val target = blob(id, true)
                    if (Files.exists(target, NOFOLLOW_LINKS)) throw java.io.IOException("Document identity collision")
                    Files.move(temp, target, ATOMIC_MOVE)
                    newBlob = target
                    state.copy(documents = state.documents + DocumentRecord(id, name, type, total, sha, Instant.now().toString(), reservationId))
                }
            } catch (failure: Exception) {
                // Only this transform's newly allocated UUID may be cleaned on failed metadata commit.
                newBlob?.let { cleanup(it) }
                throw failure
            }
        } finally { cleanup(temp) }
    }

    @Synchronized fun update(id: String, request: DocumentUpdateRequest, tripId: String?): Workspace {
        val name = displayName(request.name)
        return repository.update(tripId = tripId) { state ->
            if (state.documents.none { it.id == id }) throw MissingItem()
            reservation(state, request.reservationId)
            state.copy(documents = state.documents.map { if (it.id == id) it.copy(name = name, reservationId = request.reservationId) else it })
        }
    }

    @Synchronized fun content(id: String, tripId: String?): DocumentContent {
        val record = repository.read(tripId).documents.find { it.id == id } ?: throw MissingItem()
        val path = blob(record.id)
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw MissingItem()
        val channel = Files.newByteChannel(path, setOf(READ, NOFOLLOW_LINKS))
        if (channel.size() != record.sizeBytes) {
            channel.close()
            throw java.io.IOException("Document length mismatch")
        }
        return DocumentContent(record, Channels.newInputStream(channel))
    }

    @Synchronized fun delete(id: String, tripId: String?): Workspace {
        val workspace = repository.update(tripId = tripId) { state ->
            state.copy(documents = state.documents.filterNot { it.id == id })
        }
        // Also retries deferred cleanup when this ID was already deleted.
        reconcile(includeUploadTemps = false)
        return workspace
    }

    @Synchronized fun deleteTrip(id: String): List<Trip> {
        val trips = repository.deleteTrip(id)
        reconcile(includeUploadTemps = false)
        return trips
    }

    /** Caller holds service monitor; all metadata document writers use that monitor. */
    private fun reconcile(includeUploadTemps: Boolean) {
        // Take one coherent snapshot, including every trip, before considering any
        // filename. Metadata is authoritative even when two legacy rows share an ID.
        val liveIds = synchronized(repository) {
            repository.trips().flatMap { repository.read(it.id).documents.map(DocumentRecord::id) }.toSet()
        }
        try {
            Files.list(root(create = false)).use { paths ->
                paths.forEach { path ->
                    val name = path.fileName.toString()
                    val orphan = ID.matches(name) && name !in liveIds
                    val abandonedUpload = includeUploadTemps && UPLOAD_TEMP.matches(name)
                    // Never recurse, follow links, infer paths from malformed IDs,
                    // or collect arbitrary names/directories in the storage root.
                    if ((orphan || abandonedUpload) && Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
                        cleanup(path)
                    }
                }
            }
        }
        catch (_: MissingItem) { /* Absent directory is already clean. */ }
        catch (failure: java.io.IOException) { logger.warn("Document reconciliation deferred until next delete or startup", failure) }
        catch (failure: java.io.UncheckedIOException) { logger.warn("Document reconciliation deferred until next delete or startup", failure) }
    }
    private fun cleanup(path: Path) {
        try { Files.deleteIfExists(path) }
        catch (failure: java.io.IOException) { logger.warn("Document file cleanup deferred until next delete or startup", failure) }
    }
}
