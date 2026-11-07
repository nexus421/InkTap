package bayern.kickner.inktap.nfc

import android.nfc.Tag
import android.nfc.tech.NfcA
import bayern.kickner.inktap.core.EpdWriter
import bayern.kickner.inktap.core.WriteError
import kotnexlib.ResultOfEmpty

/**
 * Transceive timeout in milliseconds. The SDK uses 700, the open source NFC E-Paper Writer app raised it to
 * 1200 because the display answers slowly while it is powered only by the phone's field.
 */
private const val NFC_TIMEOUT_MS = 1200

/**
 * Connects to [tag] and writes one frame from `FramePacker.pack`. Blocking for about 5 seconds, so call it
 * from `Dispatchers.IO`, never from the main thread.
 */
fun writeToTag(tag: Tag, frame: ByteArray, onProgress: (Float) -> Unit): ResultOfEmpty<WriteError> {
    val nfcA = NfcA.get(tag) ?: return ResultOfEmpty.Failure(WriteError.ConnectionLost("no NfcA", null))
    return runCatching {
        nfcA.use {
            it.connect()
            it.timeout = NFC_TIMEOUT_MS
            EpdWriter(it::transceive).write(frame, onProgress)
        }
    }.getOrElse { ResultOfEmpty.Failure(WriteError.ConnectionLost("connect", it)) }
}
