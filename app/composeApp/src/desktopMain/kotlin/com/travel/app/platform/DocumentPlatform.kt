@file:JvmName("DesktopDocumentPlatform")

package com.travel.app.platform

import androidx.compose.runtime.Composable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Desktop is a shared-code test target, not a supported document UI product.
private val desktopTestFiles: Path by lazy { Files.createTempDirectory("travel-shared-tests-") }

actual fun createLocalFiles(): LocalFiles = object : LocalFiles {
    private fun file(key: String): Path { requireLocalFileKey(key); return desktopTestFiles.resolve(key) }
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val path = file(key)
        if (!Files.exists(path)) return@withContext null
        Files.newInputStream(path).use {
            val bytes = it.readNBytes(MAX_DOCUMENT_BYTES + 1)
            check(bytes.size <= MAX_DOCUMENT_BYTES) { "저장된 파일이 허용 크기를 초과합니다." }
            bytes
        }
    }
    override suspend fun write(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "파일은 20MB 이하여야 합니다." }
        val path = file(key)
        val temporary = Files.createTempFile(desktopTestFiles, "pending-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
        Unit
    }
    override suspend fun remove(key: String) = withContext(Dispatchers.IO) { Files.deleteIfExists(file(key)); Unit }
}

actual fun documentSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

@Composable
actual fun rememberDocumentPicker(onPicked: (PickedDocument) -> Unit, onError: (String) -> Unit): () -> Unit =
    { onError("파일 첨부는 Android 또는 iOS 앱에서 사용할 수 있습니다.") }

@Composable
actual fun rememberDocumentOpener(onError: (String) -> Unit): (key: String, name: String, mediaType: String) -> Unit =
    { _, _, _ -> onError("저장된 파일 보기는 Android 또는 iOS 앱에서 사용할 수 있습니다.") }
