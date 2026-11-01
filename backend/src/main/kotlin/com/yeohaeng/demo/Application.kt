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
            .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
    }
}
fun main(args: Array<String>) { runApplication<Application>(*args) }

@RestController
@RequestMapping("/api/demo")
class WorkspaceController(private val service: WorkspaceService) {
    @GetMapping("/workspace") fun workspace() = service.read()
    @GetMapping("/health") fun health() = mapOf("status" to "ok", "mode" to "local-demo")
    @PatchMapping("/tasks/{id}") fun task(@PathVariable id: String, @RequestBody request: DoneRequest) = service.setDone(id, request.done)
    @PatchMapping("/favorites/{id}") fun favorite(@PathVariable id: String, @RequestBody request: SavedRequest) = service.setSaved(id, request.saved)
    @PatchMapping("/events/{id}/note") fun eventNote(@PathVariable id: String,
        @Valid @RequestBody request: EventNoteRequest) = service.setEventNote(id, request.note)
    @PostMapping("/expenses") fun expense(@Valid @RequestBody request: ExpenseRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?) = service.addExpense(request, key)
    @PostMapping("/events") fun event(@Valid @RequestBody request: EventRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?) = service.addEvent(request, key)
    @PostMapping("/journals") fun journal(@Valid @RequestBody request: JournalRequest,
        @RequestHeader("Idempotency-Key", required = false) key: String?) = service.addJournal(request, key)
}

data class ApiError(val code: String, val message: String)
@RestControllerAdvice
class ApiErrors {
    private val logger = LoggerFactory.getLogger(javaClass)
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
