package com.travel.app.data

import com.travel.app.platform.*
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CachedDocument(val tripId: String, val id: String, val sha256: String, val key: String)
@Serializable
private data class DocumentIndex(val version: Int = 1, val entries: List<CachedDocument> = emptyList())
data class DocumentFilesState(val statuses: Map<String, DocumentCacheStatus> = emptyMap(), val busy: Boolean = false, val error: String? = null)

/** Cache identity includes trip and immutable content digest. Never trust a filename as a path. */
class DocumentCache(
    private val scope: CoroutineScope,
    private val files: LocalFiles,
    private val client: HttpClient = createHttpClient(),
    private val baseUrl: String = demoBaseUrl(),
) {
    private val mutable = MutableStateFlow(DocumentFilesState())
    val state = mutable.asStateFlow()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var activeTrip: String? = null
    private val seenDocuments = mutableMapOf<Pair<String,String>, DocumentRecord>()
    private class FileFailure(message: String) : Exception(message)
    private fun fileCheck(condition: Boolean, message: () -> String) { if(!condition) throw FileFailure(message()) }
    fun close() = client.close()
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun reportError(message: String) { mutable.update { it.copy(error = message) } }
    private fun key(tripId: String, document: DocumentRecord) = "document-" + documentSha256("$tripId\u0000${document.id}\u0000${document.sha256}".encodeToByteArray()) + "." + documentExtension(document.mediaType)
    private suspend fun index(): DocumentIndex {
        val bytes = files.read("document-index-v1.json") ?: return DocumentIndex()
        try { return json.decodeFromString<DocumentIndex>(bytes.decodeToString()).also { require(it.version == 1) } }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) {
            if(files.read("document-index-unreadable-backup.json") == null) files.write("document-index-unreadable-backup.json", bytes)
            val snapshot = try { files.read("travel-snapshot-v1.json")?.let { json.decodeFromString<TravelSnapshot>(it.decodeToString()) } }
                catch(e: CancellationException) { throw e } catch(_: Exception) { null }
            val candidates = snapshot?.takeIf { it.version == 1 }?.workspaces.orEmpty().values.flatMap { workspace ->
                workspace.documents.map { (workspace.trip.id to it.id) to it }
            }.toMap() + seenDocuments
            val entries = candidates.mapNotNull { (identity, document) ->
                val cacheKey = key(identity.first, document)
                if(files.read(cacheKey) == null) null else CachedDocument(identity.first, document.id, document.sha256, cacheKey)
            }
            return DocumentIndex(entries = entries).also { saveIndex(it) }
        }
    }
    /** Recover discovery metadata before a network refresh can replace the last downloaded snapshot. */
    suspend fun initialize() = lock.withLock { index(); Unit }
    private suspend fun saveIndex(value: DocumentIndex) = files.write("document-index-v1.json", json.encodeToString(value).encodeToByteArray())
    private suspend fun verified(tripId: String, document: DocumentRecord): Boolean {
        val bytes = files.read(key(tripId, document)) ?: return false
        return bytes.size.toLong() == document.sizeBytes && withContext(Dispatchers.Default) { documentSha256(bytes) == document.sha256 }
    }
    suspend fun reconcile(workspace: Workspace, authoritative: Boolean) = lock.withLock {
        val tripId = workspace.trip.id
        activeTrip = tripId
        workspace.documents.forEach { seenDocuments[tripId to it.id] = it }
        val statuses = mutableMapOf<String, DocumentCacheStatus>()
        workspace.documents.forEach { document ->
            statuses[document.id] = if (verified(tripId, document)) DocumentCacheStatus.READY else DocumentCacheStatus.NOT_DOWNLOADED
        }
        if (authoritative) {
            val before = index()
            val ids = workspace.documents.map { it.id }.toSet()
            val stale = before.entries.filter { it.tripId == tripId && it.id !in ids }
            // Keep the index entry until removal succeeds so a failed purge can be retried.
            stale.forEach { files.remove(it.key) }
            if (stale.isNotEmpty()) saveIndex(before.copy(entries = before.entries - stale.toSet()))
        }
        mutable.update { it.copy(statuses = statuses) }
    }
    private suspend fun store(tripId: String, document: DocumentRecord, bytes: ByteArray) {
        fileCheck(bytes.size.toLong() == document.sizeBytes && bytes.size in 1..MAX_DOCUMENT_BYTES) { "파일 크기가 저장된 정보와 달라요. 다시 내려받아 주세요." }
        fileCheck(withContext(Dispatchers.Default) { documentSha256(bytes) } == document.sha256) { "파일이 손상됐어요. 다시 내려받아 주세요." }
        val cacheKey = key(tripId, document)
        seenDocuments[tripId to document.id] = document
        val before = index()
        val entry = CachedDocument(tripId, document.id, document.sha256, cacheKey)
        // Register before creating content: even an interrupted download remains discoverable for
        // purge/retry. An index entry alone is never treated as a completed download.
        saveIndex(before.copy(entries = before.entries.filterNot { it.tripId == tripId && it.id == document.id } + entry))
        files.write(cacheKey, bytes)
        if (activeTrip == tripId) mutable.update { it.copy(statuses = it.statuses + (document.id to DocumentCacheStatus.READY)) }
    }
    suspend fun rememberUpload(tripId: String, document: DocumentRecord, bytes: ByteArray) = lock.withLock { store(tripId, document, bytes) }
    suspend fun remove(tripId: String, document: DocumentRecord) = lock.withLock {
        val before = index()
        files.remove(key(tripId, document))
        saveIndex(before.copy(entries = before.entries.filterNot { it.tripId == tripId && it.id == document.id }))
        if (activeTrip == tripId) mutable.update { it.copy(statuses = it.statuses - document.id) }
    }
    suspend fun retainTrips(ids: Set<String>) = lock.withLock {
        val before = index()
        val stale = before.entries.filter { it.tripId !in ids }
        stale.forEach { files.remove(it.key) }
        if(stale.isNotEmpty()) saveIndex(before.copy(entries = before.entries - stale.toSet()))
    }
    fun open(tripId: String, document: DocumentRecord, offline: Boolean, onReady: (String) -> Unit) = fetch(tripId, document, offline, onReady)
    fun download(tripId: String, document: DocumentRecord) = fetch(tripId, document, false) {}
    private fun fetch(tripId: String, document: DocumentRecord, offline: Boolean, onReady: (String) -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                lock.withLock {
                    if (!verified(tripId, document)) {
                        fileCheck(!offline) { "이 파일은 기기에 저장되지 않았어요. 인터넷 연결 후 내려받아 주세요." }
                        if (activeTrip == tripId) mutable.update { it.copy(statuses = it.statuses + (document.id to DocumentCacheStatus.DOWNLOADING)) }
                        val bytes = withTimeout(60_000) {
                            client.prepareGet("$baseUrl/api/demo/documents/${document.id}/content") { header("X-Trip-Id", tripId) }.execute { response ->
                                fileCheck(response.status.isSuccess()) { "파일을 내려받지 못했어요. 연결과 파일 상태를 확인해 주세요." }
                                fileCheck(response.contentType()?.withoutParameters()?.toString() == document.mediaType) { "파일 형식을 확인하지 못했어요." }
                                response.contentLength()?.let { fileCheck(it == document.sizeBytes && it in 1..MAX_DOCUMENT_BYTES.toLong()) { "파일 크기를 확인하지 못했어요." } }
                                val channel = response.bodyAsChannel()
                                try { channel.readBuffer(MAX_DOCUMENT_BYTES.toLong() + 1).readByteArray() } finally { channel.cancel() }
                            }
                        }
                        store(tripId, document, bytes)
                    }
                    if (activeTrip == tripId) mutable.update { it.copy(statuses = it.statuses + (document.id to DocumentCacheStatus.READY)) }
                    onReady(key(tripId, document))
                }
                mutable.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                mutable.update { it.copy(busy = false) }
                if (e !is TimeoutCancellationException) throw e
                fail(document, "파일을 내려받는 시간이 길어졌어요. 다시 시도해 주세요.")
            } catch (e: Exception) { fail(document, if(e is FileFailure) e.message!! else "파일을 내려받거나 기기에 보관하지 못했어요. 연결과 저장 공간을 확인하고 다시 시도해 주세요.") }
        }
    }
    private fun fail(document: DocumentRecord, message: String) {
        mutable.update { it.copy(busy = false, error = message, statuses = it.statuses + (document.id to DocumentCacheStatus.ERROR)) }
    }
}
