package com.yeohaeng.demo

import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.format.DateTimeParseException

@SpringBootApplication
class Application : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/demo/**")
            .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:8080", "http://127.0.0.1:8080")
            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
    }
}
fun main(args: Array<String>) { runApplication<Application>(*args) }

@RestController
@RequestMapping("/api/demo")
class WorkspaceController(private val service: WorkspaceService) {
    @GetMapping("/workspace") fun workspace(@RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.read(tripId)
    @GetMapping("/health") fun health() = mapOf("status" to "ok", "mode" to "local-demo")
    @GetMapping("/trips") fun trips() = service.trips()
    @PostMapping("/trips") fun createTrip(@Valid @RequestBody request: TripRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?) = service.createTrip(request, key)
    @PatchMapping("/trips/{id}") fun editTrip(@PathVariable id: String, @Valid @RequestBody request: TripRequest) = service.editTrip(id, request)
    @PatchMapping("/trips/{id}/archive") fun archiveTrip(@PathVariable id: String, @RequestBody request: ArchiveRequest) = service.archiveTrip(id, request.archived)
    @DeleteMapping("/trips/{id}") fun deleteTrip(@PathVariable id: String) = service.deleteTrip(id)

    @PatchMapping("/tasks/{id}") fun task(@PathVariable id: String, @RequestBody request: DoneRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.setDone(id, request.done, tripId)
    @PostMapping("/tasks") fun addTask(@Valid @RequestBody request: TaskRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.addTask(request, key, tripId)
    @PatchMapping("/tasks/{id}/details") fun editTask(@PathVariable id: String, @Valid @RequestBody request: TaskRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.editTask(id, request, tripId)
    @DeleteMapping("/tasks/{id}") fun deleteTask(@PathVariable id: String,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.deleteTask(id, tripId)
    @PatchMapping("/favorites/{id}") fun favorite(@PathVariable id: String, @RequestBody request: SavedRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.setSaved(id, request.saved, tripId)
    @PatchMapping("/events/{id}/note") fun eventNote(@PathVariable id: String, @Valid @RequestBody request: EventNoteRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.setEventNote(id, request.note, tripId)
    @PatchMapping("/events/{id}") fun editEvent(@PathVariable id: String, @Valid @RequestBody request: EventEditRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.editEvent(id, request, tripId)
    @DeleteMapping("/events/{id}") fun deleteEvent(@PathVariable id: String,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.deleteEvent(id, tripId)
    @PostMapping("/reservations") fun addReservation(@Valid @RequestBody request: ReservationRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.addReservation(request, key, tripId)
    @PatchMapping("/reservations/{id}") fun editReservation(@PathVariable id: String, @Valid @RequestBody request: ReservationRequest,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.editReservation(id, request, tripId)
    @DeleteMapping("/reservations/{id}") fun deleteReservation(@PathVariable id: String,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.deleteReservation(id, tripId)
    @PostMapping("/expenses") fun expense(@Valid @RequestBody request: ExpenseRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.addExpense(request, key, tripId)
    @PostMapping("/events") fun event(@Valid @RequestBody request: EventRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.addEvent(request, key, tripId)
    @PostMapping("/journals") fun journal(@Valid @RequestBody request: JournalRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?,
        @RequestHeader("X-Trip-Id", required = false) tripId: String?) = service.addJournal(request, key, tripId)
}

data class ApiError(val code: String, val message: String)
@RestControllerAdvice
class ApiErrors {
    private val logger = LoggerFactory.getLogger(javaClass)
    @ExceptionHandler(DocumentTooLarge::class, org.springframework.web.multipart.MaxUploadSizeExceededException::class)
    fun oversized() = ResponseEntity.status(413).body(ApiError("FILE_TOO_LARGE", "파일은 20MB까지 올릴 수 있어요."))
    @ExceptionHandler(org.springframework.web.multipart.MultipartException::class)
    fun malformedMultipart() = ResponseEntity.badRequest().body(ApiError("INVALID_INPUT", "파일과 문서 이름을 확인해 주세요."))
    @ExceptionHandler(IdempotencyConflict::class)
    fun conflict(ex: IdempotencyConflict) = ResponseEntity.status(409).body(ApiError("IDEMPOTENCY_CONFLICT", ex.message!!))
    @ExceptionHandler(MissingItem::class)
    fun missing() = ResponseEntity.status(404).body(ApiError("NOT_FOUND", "항목을 찾을 수 없어요."))
    @ExceptionHandler(IllegalArgumentException::class, DateTimeParseException::class)
    fun invalid(ex: Exception) = ResponseEntity.badRequest().body(ApiError("INVALID_INPUT",
        if (ex is DateTimeParseException) "올바른 날짜 또는 시간을 입력해 주세요." else ex.message ?: "입력값을 확인해 주세요."))
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException) = ResponseEntity.badRequest().body(ApiError("VALIDATION_ERROR",
        "입력값을 확인해 주세요: " + ex.bindingResult.fieldErrors.map { it.field }.distinct().joinToString(", ")))
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformed() = ResponseEntity.badRequest().body(ApiError("INVALID_JSON", "필수 입력값과 형식을 확인해 주세요."))
    @ExceptionHandler(java.io.IOException::class)
    fun storage(ex: java.io.IOException): ResponseEntity<ApiError> {
        logger.error("Local demo storage failed", ex)
        return ResponseEntity.internalServerError().body(ApiError("STORAGE_ERROR", "저장하지 못했어요. 다시 시도해 주세요."))
    }
}
