package com.yeohaeng.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DocumentApiTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var repository: WorkspaceRepository
    @Autowired lateinit var documents: DocumentService
    @Autowired lateinit var service: WorkspaceService
    @LocalServerPort var port: Int = 0
    private lateinit var trip: Workspace
    private val pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF\n".toByteArray()
    companion object {
        val directory: Path = Files.createTempDirectory("yeohaeng-documents-test-")
        val dataPath: Path = directory.resolve("workspace.json")
        val blobRoot: Path = directory.resolve("documents")
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("travel.data-file") { dataPath.toString() }
            registry.add("travel.document-root") { blobRoot.toString() }
        }
    }
    @BeforeEach fun freshTrip() {
        repository.trips().forEach { documents.deleteTrip(it.id) }
        trip = newTrip()
    }
    private fun newTrip() = service.createTrip(TripRequest("문서 여행", "도쿄", "일본", "2026-11-12", "2026-11-15", 1, 0))
    private fun file(bytes: ByteArray = pdf, filename: String = "document.pdf", type: String = "application/pdf") =
        MockMultipartFile("file", filename, type, bytes)
    private fun upload(bytes: ByteArray = pdf, key: String = UUID.randomUUID().toString(), tripId: String = trip.trip.id,
        name: String = "예약 확인서", reservationId: String = "", filename: String = "document.pdf", type: String = "application/pdf"): Workspace =
        mapper.readValue(mvc.perform(multipart("/api/demo/documents").file(file(bytes, filename, type))
            .param("name", name).param("reservationId", reservationId).header("X-Trip-Id", tripId).header("Idempotency-Key", key))
            .andExpect(status().isOk).andReturn().response.contentAsString)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun blobs(): List<Path> = if (!Files.exists(blobRoot)) emptyList() else Files.list(blobRoot).use { it.toList() }

    @Test fun `all three supported formats download exact original bytes with metadata and safe attachment headers`() {
        val fixtures = listOf(Triple(pdf, "Document.PDF", "application/pdf"),
            Triple(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0), "document.png", "image/png"),
            Triple(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0, 0, 0xff.toByte(), 0xd9.toByte()), "document.jpeg", "image/jpeg"))
        fixtures.forEach { (bytes, filename, type) ->
            val record = upload(bytes, filename = filename, type = type, name = "  예약 확인서  ").documents.last()
            assertEquals("예약 확인서", record.name); assertEquals(type, record.mediaType)
            assertEquals(bytes.size.toLong(), record.sizeBytes); assertEquals(hash(bytes), record.sha256)
            assertEquals("", record.reservationId); assertNotNull(UUID.fromString(record.id)); assertNotNull(Instant.parse(record.createdAt))
            val response = mvc.perform(get("/api/demo/documents/${record.id}/content").header("X-Trip-Id", trip.trip.id))
                .andExpect(status().isOk).andExpect(content().contentType(type))
                .andExpect(header().longValue("Content-Length", bytes.size.toLong()))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn().response
            assertArrayEquals(bytes, response.contentAsByteArray); assertEquals(record.sha256, hash(response.contentAsByteArray))
            assertTrue(response.getHeader("Content-Disposition")!!.contains("attachment"))
            assertTrue(response.getHeader("Content-Disposition")!!.contains("filename*="))
            assertFalse(response.contentAsString.contains(blobRoot.toString()))
        }
        assertEquals(3, blobs().size)
    }

    @Test fun `idempotency survives restart different bytes conflict and deleted upload never resurrects`() {
        val key = "same-document"
        val first = upload(key = key)
        assertEquals(first, upload(key = key, filename = "another.pdf"))
        assertEquals(1, blobs().size)
        mvc.perform(multipart("/api/demo/documents").file(file(pdf + 1.toByte())).param("name", "예약 확인서").param("reservationId", "")
            .header("X-Trip-Id", trip.trip.id).header("Idempotency-Key", key))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
        val reloadedRepository = WorkspaceRepository(mapper, dataPath.toString())
        val reloaded = DocumentService(reloadedRepository, dataPath.toString(), blobRoot.toString())
        assertEquals(first, reloaded.upload(file(), "예약 확인서", "", key, trip.trip.id))
        val id = first.documents.single().id
        repeat(2) { mvc.perform(delete("/api/demo/documents/$id").header("X-Trip-Id", trip.trip.id)).andExpect(status().isOk) }
        assertTrue(upload(key = key).documents.isEmpty()); assertTrue(blobs().isEmpty())
        assertTrue(repository.read(trip.trip.id).documents.isEmpty())
    }

    @Test fun `trip scope isolates binary metadata deletion and idempotency receipts`() {
        val first = upload(key = "shared")
        val other = newTrip()
        val otherSaved = upload(key = "shared", tripId = other.trip.id)
        val id = first.documents.single().id
        mvc.perform(get("/api/demo/documents/$id/content").header("X-Trip-Id", other.trip.id)).andExpect(status().isNotFound)
        mvc.perform(patch("/api/demo/documents/$id").header("X-Trip-Id", other.trip.id).contentType("application/json")
            .content("""{"name":"다른 여행","reservationId":""}""")).andExpect(status().isNotFound)
        mvc.perform(delete("/api/demo/documents/$id").header("X-Trip-Id", other.trip.id)).andExpect(status().isOk)
        assertEquals(first, repository.read(trip.trip.id)); assertEquals(otherSaved, repository.read(other.trip.id))
        listOf("", "missing").forEach { missing ->
            mvc.perform(get("/api/demo/documents/$id/content").header("X-Trip-Id", missing)).andExpect(status().isNotFound)
            mvc.perform(delete("/api/demo/documents/$id").header("X-Trip-Id", missing)).andExpect(status().isNotFound)
        }
        mvc.perform(get("/api/demo/documents/$id/content")).andExpect(status().isOk)
    }

    @Test fun `rename and reservation link changes preserve bytes and deleting reservation only unlinks`() {
        val reservation = service.addReservation(ReservationRequest("activity", "체험", "2026-11-12"), tripId = trip.trip.id).reservations.single()
        val record = upload(key = "linked-upload", reservationId = reservation.id).documents.single()
        val body = mapper.writeValueAsString(DocumentUpdateRequest("이름 바꿈", ""))
        val edited = mapper.readValue<Workspace>(mvc.perform(patch("/api/demo/documents/${record.id}").header("X-Trip-Id", trip.trip.id)
            .contentType("application/json").content(body)).andExpect(status().isOk).andReturn().response.contentAsString).documents.single()
        assertEquals(record.copy(name = "이름 바꿈", reservationId = ""), edited)
        documents.update(record.id, DocumentUpdateRequest("다시 연결", reservation.id), trip.trip.id)
        mvc.perform(delete("/api/demo/reservations/${reservation.id}").header("X-Trip-Id", trip.trip.id)).andExpect(status().isOk)
        assertEquals("", repository.read(trip.trip.id).documents.single().reservationId)
        assertEquals(repository.read(trip.trip.id), upload(key = "linked-upload", reservationId = reservation.id))
        assertArrayEquals(pdf, Files.readAllBytes(blobRoot.resolve(record.id)))
        mvc.perform(get("/api/demo/documents/${record.id}/content").header("X-Trip-Id", trip.trip.id)).andExpect(status().isOk)
    }

    @Test fun `Spring injected trip deletion removes only selected trip blobs and keeps other trip intact`() {
        val own = upload().documents.single()
        val other = newTrip(); val otherRecord = upload(tripId = other.trip.id).documents.single()
        repeat(2) { mvc.perform(delete("/api/demo/trips/${trip.trip.id}")).andExpect(status().isOk) }
        assertFalse(Files.exists(blobRoot.resolve(own.id)))
        assertArrayEquals(pdf, Files.readAllBytes(blobRoot.resolve(otherRecord.id)))
        assertEquals(listOf(otherRecord), repository.read(other.trip.id).documents)
    }

    @Test fun `bad file type signature size name link and multipart shape fail without state or receipt changes`() {
        val before = Files.readAllBytes(dataPath)
        val badFiles = listOf(file(byteArrayOf()), file("hello".toByteArray()), file(filename = "../document.pdf"),
            file(filename = "a\\document.pdf"), file(filename = "document.pdf\n"), file(filename = "document.exe"),
            file(filename = "document.png", type = "image/png"), file(type = "image/jpeg"), file(type = "text/html"))
        badFiles.forEach { invalid -> mvc.perform(multipart("/api/demo/documents").file(invalid).param("name", "문서").param("reservationId", "")
            .header("X-Trip-Id", trip.trip.id).header("Idempotency-Key", "reusable-invalid"))
            .andExpect(status().isBadRequest) }
        listOf("", " ", ".", "..", " . ", "a/b", "a\\b", "a\u0000b", "a\u0085b", "a\u2028b", "x".repeat(201)).forEach { name ->
            mvc.perform(multipart("/api/demo/documents").file(file()).param("name", name).param("reservationId", "")
                .header("X-Trip-Id", trip.trip.id)).andExpect(status().isBadRequest)
        }
        mvc.perform(multipart("/api/demo/documents").file(file()).param("name", "문서").param("reservationId", "missing")
            .header("X-Trip-Id", trip.trip.id)).andExpect(status().isBadRequest)
        val requests = listOf(multipart("/api/demo/documents").file(file()).param("name", "문서"),
            multipart("/api/demo/documents").file(file()).param("reservationId", ""),
            multipart("/api/demo/documents").file(file()).file(file()).param("name", "문서").param("reservationId", ""),
            multipart("/api/demo/documents").file(file()).param("name", "문서", "중복").param("reservationId", ""),
            multipart("/api/demo/documents").file(file()).param("name", "문서").param("reservationId", "").param("extra", "x"))
        requests.forEach { request -> mvc.perform(request.header("X-Trip-Id", trip.trip.id)).andExpect(status().isBadRequest) }
        assertArrayEquals(before, Files.readAllBytes(dataPath)); assertTrue(blobs().isEmpty())
        assertEquals(1, upload(key = "reusable-invalid", name = "문서").documents.size)
    }

    @Test fun `cross trip reservation links and every nonstring unknown or missing patch field are rejected atomically`() {
        val record = upload().documents.single()
        val other = newTrip()
        val reservation = service.addReservation(ReservationRequest("activity", "다른 여행", "2026-11-12"), tripId = other.trip.id).reservations.single()
        val before = Files.readAllBytes(dataPath)
        val bodies = mutableListOf("""{"name":"문서","reservationId":"${reservation.id}"}""",
            """{"reservationId":""}""", """{"name":"문서"}""")
        for (field in listOf("name", "reservationId")) for (token in listOf("null", "3", "true", "[]", "{}")) {
            val node = mapper.createObjectNode().put("name", "문서").put("reservationId", "")
            node.set<ObjectNode>(field, mapper.readTree(token)); bodies += mapper.writeValueAsString(node)
        }
        for (field in listOf("id", "sizeBytes", "sha256", "mediaType", "createdAt", "extra")) {
            bodies += mapper.writeValueAsString(mapper.createObjectNode().put("name", "문서").put("reservationId", "").put(field, "forbidden"))
        }
        bodies.forEach { body -> mvc.perform(patch("/api/demo/documents/${record.id}").header("X-Trip-Id", trip.trip.id)
            .contentType("application/json").content(body)).andExpect(status().isBadRequest) }
        assertArrayEquals(before, Files.readAllBytes(dataPath)); assertArrayEquals(pdf, Files.readAllBytes(blobRoot.resolve(record.id)))
    }

    @Test fun `20 MiB inclusive is accepted and application limit rejects the next byte before changes`() {
        val limit = ByteArray(DocumentService.MAX_BYTES.toInt()).also { pdf.copyInto(it) }
        val record = upload(limit).documents.single()
        assertEquals(DocumentService.MAX_BYTES, record.sizeBytes)
        val before = Files.readAllBytes(dataPath)
        mvc.perform(multipart("/api/demo/documents").file(file(limit + 0.toByte())).param("name", "문서").param("reservationId", "")
            .header("X-Trip-Id", trip.trip.id)).andExpect(status().isPayloadTooLarge).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
        assertArrayEquals(before, Files.readAllBytes(dataPath)); assertEquals(1, blobs().size)
    }

    @Test fun `real multipart parser returns 413 for an oversized request`() {
        val boundary = "document-limit-test"
        val prefix = ("--$boundary\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n문서\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"reservationId\"\r\n\r\n\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").toByteArray()
        val before = Files.readAllBytes(dataPath)
        // Exercise both the file limit and the independent total request limit.
        for (size in listOf(DocumentService.MAX_BYTES.toInt() + 1, 22 * 1024 * 1024)) {
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/demo/documents")).version(HttpClient.Version.HTTP_1_1)
                .header("X-Trip-Id", trip.trip.id).header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                    HttpRequest.BodyPublishers.ofByteArray(ByteArray(size)),
                    HttpRequest.BodyPublishers.ofString("\r\n--$boundary--\r\n"))).build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(413, response.statusCode()); assertEquals("FILE_TOO_LARGE", mapper.readTree(response.body())["code"].asText())
        }
        assertArrayEquals(before, Files.readAllBytes(dataPath)); assertTrue(blobs().isEmpty())
    }

    @Test fun `real HTTP multipart text parts are accepted with binary bytes preserved`() {
        val boundary = "document-real-upload"
        val prefix = ("--$boundary\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n예약 확인서\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"reservationId\"\r\n\r\n\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").toByteArray()
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/demo/documents")).version(HttpClient.Version.HTTP_1_1)
            .header("X-Trip-Id", trip.trip.id).header("Idempotency-Key", "real-http-upload")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                HttpRequest.BodyPublishers.ofByteArray(pdf), HttpRequest.BodyPublishers.ofString("\r\n--$boundary--\r\n"))).build()
        val client = HttpClient.newHttpClient()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        val record = mapper.readValue<Workspace>(response.body()).documents.single()
        assertEquals("예약 확인서", record.name); assertEquals(hash(pdf), record.sha256)
        val get = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/demo/documents/${record.id}/content"))
            .header("X-Trip-Id", trip.trip.id).GET().build()
        val downloaded = client.send(get, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, downloaded.statusCode()); assertArrayEquals(pdf, downloaded.body())
    }

    @Test fun `failed metadata commit removes only newly allocated blob and permits safe retry`() {
        val existing = upload().documents.single()
        val bytes = Files.readAllBytes(dataPath)
        Files.delete(dataPath); Files.createDirectory(dataPath)
        try {
            mvc.perform(multipart("/api/demo/documents").file(file()).param("name", "文서").param("reservationId", "")
                .header("X-Trip-Id", trip.trip.id).header("Idempotency-Key", "commit-failure"))
                .andExpect(status().isInternalServerError).andExpect(jsonPath("$.code").value("STORAGE_ERROR"))
            assertEquals(listOf(existing), repository.read(trip.trip.id).documents)
            assertEquals(listOf(blobRoot.resolve(existing.id)), blobs())
            assertArrayEquals(pdf, Files.readAllBytes(blobRoot.resolve(existing.id)))
            mvc.perform(delete("/api/demo/documents/${existing.id}").header("X-Trip-Id", trip.trip.id))
                .andExpect(status().isInternalServerError)
            assertArrayEquals(pdf, Files.readAllBytes(blobRoot.resolve(existing.id)))
        } finally { Files.delete(dataPath); Files.write(dataPath, bytes) }
        assertEquals(2, upload(key = "commit-failure", name = "文서").documents.size)
    }

    @Test fun `symlink storage root rejects upload without modifying any external files`() {
        val outside = Files.createTempDirectory("document-original-directory-")
        val original = outside.resolve("document.pdf"); Files.write(original, pdf)
        if (Files.exists(blobRoot)) Files.delete(blobRoot)
        Files.createSymbolicLink(blobRoot, outside)
        val before = Files.readAllBytes(dataPath)
        try {
            mvc.perform(multipart("/api/demo/documents").file(file()).param("name", "문서").param("reservationId", "")
                .header("X-Trip-Id", trip.trip.id)).andExpect(status().isInternalServerError)
            assertArrayEquals(before, Files.readAllBytes(dataPath)); assertArrayEquals(pdf, Files.readAllBytes(original))
            assertEquals(1, Files.list(outside).use { it.count() })
        } finally { Files.delete(blobRoot) }
    }

    @Test fun `symlink blob cannot expose or delete its external target and missing or truncated originals fail closed`() {
        val record = upload().documents.single(); val path = blobRoot.resolve(record.id)
        val outside = directory.resolve("original-user-file.pdf"); Files.write(outside, pdf)
        Files.delete(path); Files.createSymbolicLink(path, outside)
        mvc.perform(get("/api/demo/documents/${record.id}/content").header("X-Trip-Id", trip.trip.id)).andExpect(status().isNotFound)
        mvc.perform(delete("/api/demo/documents/${record.id}").header("X-Trip-Id", trip.trip.id)).andExpect(status().isOk)
        assertArrayEquals(pdf, Files.readAllBytes(outside)); assertTrue(Files.isSymbolicLink(path))
        Files.delete(path) // The reconciler deliberately leaves symlink entries untouched.
        val next = upload().documents.single(); Files.write(blobRoot.resolve(next.id), byteArrayOf(1))
        mvc.perform(get("/api/demo/documents/${next.id}/content").header("X-Trip-Id", trip.trip.id)).andExpect(status().isInternalServerError)
        Files.delete(blobRoot.resolve(next.id))
        mvc.perform(get("/api/demo/documents/${next.id}/content").header("X-Trip-Id", trip.trip.id)).andExpect(status().isNotFound)
    }

    @Test fun `parallel duplicate uploads create one immutable blob and no temporary files`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(List(4) { Callable { documents.upload(file(), "문서", "", "concurrent", trip.trip.id) } }).map { it.get() }
            assertTrue(results.all { it == results.first() }); assertEquals(1, results.first().documents.size)
            assertEquals(1, blobs().size); assertArrayEquals(pdf, Files.readAllBytes(blobs().single()))
        } finally { pool.shutdownNow() }
    }

    @Test fun `legacy workspace defaults documents empty and preserves original collections and receipts on document writes`() {
        val legacyPath = Files.createTempDirectory("yeohaeng-document-legacy-").resolve("workspace.json")
        val tree = javaClass.getResourceAsStream("/seed-workspace.json")!!.use { mapper.readTree(it) as ObjectNode }
        tree.remove("documents"); val original = mapper.writeValueAsBytes(tree); Files.write(legacyPath, original)
        val legacyRepository = WorkspaceRepository(mapper, legacyPath.toString())
        val before = legacyRepository.read(); assertTrue(before.documents.isEmpty())
        legacyRepository.update("existing-receipt", "legacy", "value") { it }
        val legacyDocuments = DocumentService(legacyRepository, legacyPath.toString())
        val uploaded = legacyDocuments.upload(file(), "기존 여행 문서", "", "new-receipt", null)
        assertEquals(before, uploaded.copy(documents = emptyList()))
        val loaded = WorkspaceRepository(mapper, legacyPath.toString())
        assertEquals(uploaded, loaded.read())
        assertEquals(uploaded, loaded.update("existing-receipt", "legacy", "value") { error("receipt should skip transform") })
        assertEquals(2, mapper.readTree(legacyPath.toFile())["schemaVersion"].intValue())
        val backup = Files.list(legacyPath.parent).use { paths -> paths.filter { it.fileName.toString().contains(".pre-schema2-") }.findFirst().orElseThrow() }
        assertArrayEquals(original, Files.readAllBytes(backup))
    }

    @Test fun `deferred blob deletion is retried by idempotent delete while every trip live blob is protected`() {
        val localPath = Files.createTempDirectory("document-deferred-delete-").resolve("workspace.json")
        val localRepository = WorkspaceRepository(mapper, localPath.toString())
        val localDocuments = DocumentService(localRepository, localPath.toString())
        val localService = WorkspaceService(localRepository, localDocuments)
        val own = localService.createTrip(TripRequest("첫 여행", "도쿄", "일본", "2026-11-12", "2026-11-15", 1, 0))
        val other = localService.createTrip(TripRequest("다른 여행", "도쿄", "일본", "2026-11-12", "2026-11-15", 1, 0))
        val record = localDocuments.upload(file(), "문서", "", "own", own.trip.id).documents.single()
        val retained = localDocuments.upload(file(), "문서", "", "other", other.trip.id).documents.single()
        val root = localPath.parent.resolve("documents")
        val abandonedTemp = root.resolve(".upload-123456.tmp"); Files.write(abandonedTemp, pdf)
        Files.setPosixFilePermissions(root, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"))
        try {
            assertTrue(localDocuments.delete(record.id, own.trip.id).documents.isEmpty())
            assertTrue(Files.exists(root.resolve(record.id))) // Real AccessDenied leaves an orphan.
            assertArrayEquals(pdf, Files.readAllBytes(root.resolve(retained.id)))
        } finally { Files.setPosixFilePermissions(root, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")) }
        localDocuments.delete(record.id, own.trip.id)
        assertFalse(Files.exists(root.resolve(record.id)))
        assertArrayEquals(pdf, Files.readAllBytes(root.resolve(retained.id)))
        assertTrue(Files.exists(abandonedTemp)) // Running delete never collects upload temps.
        localDocuments.deleteTrip(own.trip.id)
        localDocuments.deleteTrip(own.trip.id)
        assertArrayEquals(pdf, Files.readAllBytes(root.resolve(retained.id)))
    }

    @Test fun `startup retries orphan and abandoned temp cleanup without touching arbitrary entries or malformed legacy ids`() {
        val localPath = Files.createTempDirectory("document-startup-cleanup-").resolve("workspace.json")
        val localRepository = WorkspaceRepository(mapper, localPath.toString())
        val localDocuments = DocumentService(localRepository, localPath.toString())
        val live = localDocuments.upload(file(), "문서", "", "live", null).documents.single()
        val root = localPath.parent.resolve("documents")
        val orphan = root.resolve(UUID.randomUUID().toString()); Files.write(orphan, pdf)
        val abandoned = root.resolve(".upload-987654321.tmp"); Files.write(abandoned, pdf)
        val arbitrary = root.resolve("original.pdf"); Files.write(arbitrary, pdf)
        val malformed = root.resolve("legacy-not-a-uuid"); Files.write(malformed, pdf)
        val unrelatedTemp = root.resolve(".upload-user-notes.tmp"); Files.write(unrelatedTemp, pdf)
        val directory = Files.createDirectory(root.resolve(UUID.randomUUID().toString()))
        val nested = directory.resolve("keep.pdf"); Files.write(nested, pdf)
        val outside = localPath.parent.resolve("selected-original.pdf"); Files.write(outside, pdf)
        val link = root.resolve(UUID.randomUUID().toString()); Files.createSymbolicLink(link, outside)
        localRepository.update { it.copy(documents = it.documents + live.copy(id = "legacy-not-a-uuid")) }
        val before = Files.readAllBytes(localPath)
        Files.setPosixFilePermissions(root, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"))
        try {
            DocumentService(localRepository, localPath.toString())
            assertTrue(Files.exists(orphan)); assertTrue(Files.exists(abandoned))
        } finally { Files.setPosixFilePermissions(root, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")) }
        val reloaded = WorkspaceRepository(mapper, localPath.toString())
        DocumentService(reloaded, localPath.toString())
        assertFalse(Files.exists(orphan)); assertFalse(Files.exists(abandoned))
        for (retained in listOf(root.resolve(live.id), arbitrary, malformed, unrelatedTemp, nested, outside)) {
            assertArrayEquals(pdf, Files.readAllBytes(retained))
        }
        assertTrue(Files.isSymbolicLink(link)); assertTrue(Files.isDirectory(directory))
        assertArrayEquals(before, Files.readAllBytes(localPath))
    }
}
