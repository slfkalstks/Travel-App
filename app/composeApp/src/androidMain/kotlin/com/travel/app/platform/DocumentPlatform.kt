@file:JvmName("AndroidDocumentPlatform")

package com.travel.app.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private lateinit var documentApplicationContext: Context

class DocumentFileProvider : FileProvider()

private val safeDocumentMessages = setOf(
    "올바르지 않은 로컬 파일 식별자입니다.", "PDF, PNG, JPEG 파일만 열 수 있습니다.",
    "빈 파일은 첨부할 수 없습니다.", "파일은 20MB 이하여야 합니다.",
    "PDF, PNG, JPEG 파일만 첨부할 수 있습니다.", "파일 이름에 사용할 수 없는 문자가 있거나 이름이 너무 깁니다.",
    "이 기기에 저장된 파일이 없습니다. 온라인에서 다시 내려받아 주세요.",
)

private fun safeDocumentMessage(error: Exception, fallback: String): String =
    error.message?.takeIf { it in safeDocumentMessages } ?: fallback

private inline fun <T> documentIo(fallback: String, operation: () -> T): T = try { operation() }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) { throw IllegalStateException(safeDocumentMessage(error, fallback)) }

/** Call from Application/Activity before creating the shared store. */
fun initializeDocumentPlatform(context: Context) {
    documentApplicationContext = context.applicationContext
}

private fun localDirectory(): File = File(documentApplicationContext.filesDir, "travel-local-files").apply {
    check(isDirectory || mkdirs()) { "로컬 저장소를 만들 수 없습니다." }
}

actual fun createLocalFiles(): LocalFiles = AndroidLocalFiles()

private class AndroidLocalFiles : LocalFiles {
    private fun file(key: String): File {
        requireLocalFileKey(key)
        return File(localDirectory(), key)
    }

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        documentIo("저장된 파일을 읽을 수 없습니다. 다시 내려받아 주세요.") {
            val target = file(key)
            if (!target.exists()) null else target.inputStream().use { input -> input.readDocumentBytes() }
        }
    }

    override suspend fun write(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        documentIo("파일을 기기에 저장할 수 없습니다.") {
            require(bytes.size <= MAX_DOCUMENT_BYTES) { "파일은 20MB 이하여야 합니다." }
            val target = file(key)
            val temporary = File.createTempFile("pending-", ".tmp", target.parentFile)
            try {
                temporary.outputStream().use { it.write(bytes); it.fd.sync() }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
            Unit
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        documentIo("로컬 파일을 삭제할 수 없습니다.") {
            val target = file(key)
            check(!target.exists() || target.delete()) { "로컬 파일을 삭제할 수 없습니다." }
            listOf("pdf", "png", "jpg").forEach { extension ->
                val preview = File(documentApplicationContext.cacheDir, "document-previews/$key.$extension")
                check(!preview.exists() || preview.delete()) { "파일 미리보기를 삭제할 수 없습니다." }
            }
        }
    }
}

actual fun documentSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

private fun InputStream.readDocumentBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_DOCUMENT_BYTES) { "파일은 20MB 이하여야 합니다." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

@Composable
actual fun rememberDocumentPicker(onPicked: (PickedDocument) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picked = rememberUpdatedState(onPicked)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val selecting = rememberSaveable { mutableStateOf(false) }
    val reading = remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selecting.value = false
        if (uri != null) {
            reading.value = true
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val document = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    var name = ""
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                    }
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        input.readDocumentBytes()
                    } ?: error("선택한 파일을 읽을 수 없습니다.")
                    pickedDocument(name, bytes)
                }
                picked.value(document)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed.value(safeDocumentMessage(error, "파일을 가져올 수 없습니다. 다시 선택해 주세요."))
            } finally {
                reading.value = false
            }
            }
        }
    }
    return {
        if (!selecting.value && !reading.value) {
            selecting.value = true
            try { launcher.launch(arrayOf("application/pdf", "image/png", "image/jpeg")) }
            catch (error: Exception) {
                selecting.value = false
                failed.value("파일 선택기를 열 수 없습니다. 다시 시도해 주세요.")
            }
        }
    }
}

@Composable
actual fun rememberDocumentOpener(onError: (String) -> Unit): (key: String, name: String, mediaType: String) -> Unit {
    val context = LocalContext.current
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val preparing = remember { mutableStateOf(false) }
    val viewing = rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewing.value = false
    }
    return { key, name, mediaType ->
        if (!preparing.value && !viewing.value) {
            preparing.value = true
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val preview = withContext(Dispatchers.IO) {
                    requireLocalFileKey(key)
                    val extension = documentExtension(mediaType)
                    val bytes = createLocalFiles().read(key) ?: error("이 기기에 저장된 파일이 없습니다. 온라인에서 다시 내려받아 주세요.")
                    val directory = File(context.cacheDir, "document-previews").apply {
                        check(isDirectory || mkdirs()) { "미리보기를 준비할 수 없습니다." }
                    }
                    File(directory, "$key.$extension").apply { writeBytes(bytes) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", preview, name)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mediaType)
                    clipData = ClipData.newRawUri(name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                viewing.value = true
                launcher.launch(intent)
            } catch (cancelled: CancellationException) {
                viewing.value = false
                throw cancelled
            } catch (error: Exception) {
                viewing.value = false
                failed.value(if (error is android.content.ActivityNotFoundException) "이 파일을 열 수 있는 앱을 설치해 주세요." else safeDocumentMessage(error, "파일을 열 수 없습니다. 다시 시도해 주세요."))
            } finally {
                preparing.value = false
            }
            }
        }
    }
}
