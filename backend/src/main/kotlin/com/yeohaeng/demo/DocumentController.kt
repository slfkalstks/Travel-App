package com.yeohaeng.demo

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartHttpServletRequest

@RestController
@RequestMapping("/api/demo/documents")
class DocumentController(private val service: DocumentService) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(request: HttpServletRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?,
        @RequestHeader("Idempotency-Key", required = false) key: String?): Workspace {
        val multipart = request as? MultipartHttpServletRequest ?: throw IllegalArgumentException("파일을 선택해 주세요.")
        require(multipart.multiFileMap.keys == setOf("file") && multipart.getFiles("file").size == 1) { "파일 하나를 선택해 주세요." }
        require(multipart.parameterMap.keys == setOf("name", "reservationId") &&
            multipart.parameterMap.values.all { it.size == 1 }) { "문서 이름과 예약 연결을 확인해 주세요." }
        return service.upload(multipart.getFile("file")!!, multipart.getParameter("name")!!,
            multipart.getParameter("reservationId")!!, key, tripId)
    }

    @GetMapping("/{id}/content")
    fun content(@PathVariable id: String, @RequestHeader("X-Trip-Id", required = false) tripId: String?): ResponseEntity<InputStreamResource> {
        val content = service.content(id, tripId)
        return try {
            ResponseEntity.ok().contentType(MediaType.parseMediaType(content.record.mediaType))
                .contentLength(content.record.sizeBytes)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.record.name, Charsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(InputStreamResource(content.stream))
        } catch (failure: Exception) { content.stream.close(); throw failure }
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: String, @Valid @RequestBody request: DocumentUpdateRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.update(id, request, tripId)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.delete(id, tripId)
}
