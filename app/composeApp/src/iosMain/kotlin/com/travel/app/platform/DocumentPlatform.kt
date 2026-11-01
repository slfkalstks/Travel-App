@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.travel.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.getBytes
import platform.Foundation.writeToFile
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewControllerDelegateProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode.UIDocumentPickerModeOpen
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.presentationController
import platform.darwin.NSObject

private val safeDocumentMessages = setOf(
    "올바르지 않은 로컬 파일 식별자입니다.", "PDF, PNG, JPEG 파일만 열 수 있습니다.",
    "빈 파일은 첨부할 수 없습니다.", "파일은 20MB 이하여야 합니다.",
    "PDF, PNG, JPEG 파일만 첨부할 수 있습니다.", "파일 이름에 사용할 수 없는 문자가 있거나 이름이 너무 깁니다.",
    "이 기기에 저장된 파일이 없습니다. 온라인에서 다시 내려받아 주세요.", "이 파일을 미리 볼 수 없습니다.",
    "다른 화면을 닫은 뒤 다시 시도해 주세요.",
)

private fun safeDocumentMessage(error: Exception, fallback: String): String =
    error.message?.takeIf { it in safeDocumentMessages } ?: fallback

private inline fun <T> documentIo(fallback: String, operation: () -> T): T = try { operation() }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) { throw IllegalStateException(safeDocumentMessage(error, fallback)) }

private fun localDirectory(): String {
    val manager = NSFileManager.defaultManager
    val applicationSupport = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
    val directory = applicationSupport.URLByAppendingPathComponent("travel-local-files", isDirectory = true)!!
    check(manager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
        "로컬 저장소를 만들 수 없습니다."
    }
    return directory.path!!
}

private fun localPath(key: String): String {
    requireLocalFileKey(key)
    return "${localDirectory()}/$key"
}

actual fun createLocalFiles(): LocalFiles = object : LocalFiles {
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.Default) {
        documentIo("저장된 파일을 읽을 수 없습니다. 다시 내려받아 주세요.") {
            val path = localPath(key)
            if (!NSFileManager.defaultManager.fileExistsAtPath(path)) null else readBoundedFile(NSURL.fileURLWithPath(path))
        }
    }

    override suspend fun write(key: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        documentIo("파일을 기기에 저장할 수 없습니다.") {
            require(bytes.size <= MAX_DOCUMENT_BYTES) { "파일은 20MB 이하여야 합니다." }
            // Foundation atomically writes a sibling temporary file and replaces the destination.
            check(bytes.toNSData().writeToFile(localPath(key), atomically = true)) { "파일을 기기에 저장할 수 없습니다." }
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.Default) {
        documentIo("로컬 파일을 삭제할 수 없습니다.") {
            val path = localPath(key)
            val manager = NSFileManager.defaultManager
            check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, error = null)) { "로컬 파일을 삭제할 수 없습니다." }
            listOf("pdf", "png", "jpg").forEach { extension ->
                val preview = "${NSTemporaryDirectory()}document-previews/$key.$extension"
                check(!manager.fileExistsAtPath(preview) || manager.removeItemAtPath(preview, error = null)) { "파일 미리보기를 삭제할 수 없습니다." }
            }
        }
    }
}

private fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    require(length <= MAX_DOCUMENT_BYTES.toULong()) { "파일은 20MB 이하여야 합니다." }
    return ByteArray(length.toInt()).also { bytes ->
        if (bytes.isNotEmpty()) bytes.usePinned { getBytes(it.addressOf(0), length) }
    }
}

private fun readBoundedFile(url: NSURL): ByteArray {
    val handle = NSFileHandle.fileHandleForReadingFromURL(url, error = null) ?: error("파일을 읽을 수 없습니다.")
    return try {
        val data = handle.readDataUpToLength((MAX_DOCUMENT_BYTES + 1).toULong(), error = null)
            ?: error("파일을 읽을 수 없습니다.")
        data.toByteArray()
    } finally { handle.closeAndReturnError(error = null) }
}

actual fun documentSha256(bytes: ByteArray): String {
    val digest = ByteArray(32)
    digest.usePinned { output ->
        if (bytes.isEmpty()) CC_SHA256(null, 0u, output.addressOf(0).reinterpret())
        else bytes.usePinned { input -> CC_SHA256(input.addressOf(0), bytes.size.toUInt(), output.addressOf(0).reinterpret()) }
    }
    return digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}

private class DocumentPickerDelegate(
    private val scope: CoroutineScope,
    private val onPicked: (PickedDocument) -> Unit,
    private val onError: (String) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    var active = false
    private var reading = false

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        if (reading) return
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) { active = false; return }
        reading = true
        // Start access immediately while the delegate URL is valid; always balance it on cancellation.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val access = url.startAccessingSecurityScopedResource()
            try {
                val document = withContext(Dispatchers.Default) {
                    var result: ByteArray? = null
                    var failure: Exception? = null
                    NSFileCoordinator().coordinateReadingItemAtURL(url, options = 0u, error = null) { coordinated ->
                        try { result = readBoundedFile(coordinated!!) }
                        catch (error: Exception) { failure = error }
                    }
                    failure?.let { throw it }
                    pickedDocument(url.lastPathComponent ?: "", result ?: error("선택한 파일을 읽을 수 없습니다."))
                }
                onPicked(document)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(safeDocumentMessage(error, "파일을 가져올 수 없습니다. 다시 선택해 주세요."))
            } finally {
                if (access) url.stopAccessingSecurityScopedResource()
                reading = false
                active = false
            }
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        if (!reading) active = false
    }
}

@Composable
actual fun rememberDocumentPicker(onPicked: (PickedDocument) -> Unit, onError: (String) -> Unit): () -> Unit {
    val parent = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val picked = rememberUpdatedState(onPicked)
    val failed = rememberUpdatedState(onError)
    // UIDocumentPickerViewController keeps a weak delegate; Compose retains this bridge.
    val delegate = remember(scope) { DocumentPickerDelegate(scope, { picked.value(it) }, { failed.value(it) }) }
    return {
        if (!delegate.active) {
            try {
                check(parent.presentedViewController == null && parent.view.window != null) { "다른 화면을 닫은 뒤 다시 시도해 주세요." }
                val picker = UIDocumentPickerViewController(documentTypes = listOf("com.adobe.pdf", "public.png", "public.jpeg"), inMode = UIDocumentPickerModeOpen)
                picker.allowsMultipleSelection = false
                picker.delegate = delegate
                delegate.active = true
                parent.presentViewController(picker, animated = true, completion = null)
                check(picker.presentingViewController != null) { "파일 선택기를 열 수 없습니다." }
            } catch (error: Exception) {
                delegate.active = false
                failed.value(safeDocumentMessage(error, "파일 선택기를 열 수 없습니다. 다시 시도해 주세요."))
            }
        }
    }
}

private class PreviewItem(private val url: NSURL, private val title: String) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL = url
    override fun previewItemTitle(): String = title
}

private class PreviewSession(
    private val item: PreviewItem,
    private val onDismiss: () -> Unit,
) : NSObject(), QLPreviewControllerDataSourceProtocol, QLPreviewControllerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
    private var dismissed = false
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long = 1
    override fun previewController(controller: QLPreviewController, previewItemAtIndex: Long): QLPreviewItemProtocol = item
    override fun previewControllerDidDismiss(controller: QLPreviewController) = finish()
    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) = finish()
    fun finish() {
        if (!dismissed) { dismissed = true; onDismiss() }
    }
}

/** Quick Look has weak delegates; each controller retains its own immutable session. */
private class RetainedPreviewController(private val session: PreviewSession) : QLPreviewController(nibName = null, bundle = null) {
    init { dataSource = session; delegate = session }
}

private class PreviewState {
    var active = false
    var session: PreviewSession? = null
}

@Composable
actual fun rememberDocumentOpener(onError: (String) -> Unit): (key: String, name: String, mediaType: String) -> Unit {
    val parent = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val failed = rememberUpdatedState(onError)
    val state = remember { PreviewState() }
    return { key, name, mediaType ->
        if (!state.active) {
            // Acquire before dispatching file IO so a second tap cannot race this preparation.
            state.active = true
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var presented = false
            try {
                val url = withContext(Dispatchers.Default) {
                    requireLocalFileKey(key)
                    val extension = documentExtension(mediaType)
                    val bytes = createLocalFiles().read(key) ?: error("이 기기에 저장된 파일이 없습니다. 온라인에서 다시 내려받아 주세요.")
                    val directory = "${NSTemporaryDirectory()}document-previews"
                    check(NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
                        "미리보기를 준비할 수 없습니다."
                    }
                    val path = "$directory/$key.$extension"
                    check(bytes.toNSData().writeToFile(path, atomically = true)) { "미리보기를 준비할 수 없습니다." }
                    NSURL.fileURLWithPath(path)
                }
                val item = PreviewItem(url, name)
                check(QLPreviewController.canPreviewItem(item)) { "이 파일을 미리 볼 수 없습니다." }
                check(parent.presentedViewController == null && parent.view.window != null) { "다른 화면을 닫은 뒤 다시 시도해 주세요." }
                val session = PreviewSession(item) { state.active = false; state.session = null }
                state.session = session
                val preview = RetainedPreviewController(session)
                preview.presentationController?.delegate = session
                parent.presentViewController(preview, animated = true, completion = null)
                check(preview.presentingViewController != null) { "파일을 열 수 없습니다." }
                presented = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed.value(safeDocumentMessage(error, "파일을 열 수 없습니다. 다시 시도해 주세요."))
            } finally {
                if (!presented) { state.active = false; state.session = null }
            }
            }
        }
    }
}
