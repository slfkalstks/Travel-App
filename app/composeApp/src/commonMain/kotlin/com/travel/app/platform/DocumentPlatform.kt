package com.travel.app.platform

import androidx.compose.runtime.Composable

/** App-private, persistent storage. Keys are opaque identifiers, never display filenames. */
interface LocalFiles {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, bytes: ByteArray)
    suspend fun remove(key: String)
}

expect fun createLocalFiles(): LocalFiles
expect fun documentSha256(bytes: ByteArray): String

data class PickedDocument(val name: String, val mediaType: String, val bytes: ByteArray)

@Composable
expect fun rememberDocumentPicker(
    onPicked: (PickedDocument) -> Unit,
    onError: (String) -> Unit,
): () -> Unit

@Composable
expect fun rememberDocumentOpener(
    onError: (String) -> Unit,
): (key: String, name: String, mediaType: String) -> Unit

const val MAX_DOCUMENT_BYTES: Int = 20 * 1024 * 1024

internal fun requireLocalFileKey(key: String) {
    require(key.length in 1..180 && key != "." && key != ".." && key.all {
        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-'
    }) { "올바르지 않은 로컬 파일 식별자입니다." }
}

internal fun documentExtension(mediaType: String): String = when (mediaType) {
    "application/pdf" -> "pdf"
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    else -> error("PDF, PNG, JPEG 파일만 열 수 있습니다.")
}

/** A picker MIME filter is advisory; inspect the selected bytes as well. */
internal fun pickedDocument(name: String, bytes: ByteArray): PickedDocument {
    require(bytes.isNotEmpty()) { "빈 파일은 첨부할 수 없습니다." }
    require(bytes.size <= MAX_DOCUMENT_BYTES) { "파일은 20MB 이하여야 합니다." }
    val type = when {
        bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-" -> "application/pdf"
        bytes.size >= 8 && bytes.take(8).map { it.toInt() and 255 } == listOf(137, 80, 78, 71, 13, 10, 26, 10) -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
        else -> error("PDF, PNG, JPEG 파일만 첨부할 수 있습니다.")
    }
    val displayName = name.ifBlank { "document.${documentExtension(type)}" }
    require(displayName.length <= 200 && displayName != "." && displayName != ".." && displayName.none {
        it == '/' || it == '\\' || it.code < 32 || it.code == 127
    }) { "파일 이름에 사용할 수 없는 문자가 있거나 이름이 너무 깁니다." }
    return PickedDocument(displayName, type, bytes)
}
