package bayern.kickner.inktap.ui.screens.ink

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.nfc.Tag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bayern.kickner.inktap.core.Content
import bayern.kickner.inktap.core.ContentError
import bayern.kickner.inktap.core.FramePacker
import bayern.kickner.inktap.core.GrayImage
import bayern.kickner.inktap.core.encodeBarcode
import bayern.kickner.inktap.core.render
import bayern.kickner.inktap.data.InkPrefs
import bayern.kickner.inktap.nfc.writeToTag
import bayern.kickner.inktap.platform.AndroidText
import bayern.kickner.inktap.platform.decodePicture
import bayern.kickner.inktap.platform.toDisplayGray
import bayern.kickner.klogger.infoLog
import bayern.kickner.klogger.warnLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotnexlib.ResultOf2
import kotnexlib.ResultOfEmpty
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the state of the ink screen. The only public entry point is [onAction]. */
class InkViewModel : ViewModel() {

    /** Current screen state, changed only through [onAction]. */
    var state by mutableStateOf(InkState())
        private set

    /** The decoded picture of the picture mode. Kept out of the state, it is neither comparable nor persisted. */
    private var picture: Bitmap? = null
    private var loaded = false
    private val gate = WriteGate()

    /** The only public entry point. Every user intent and every NFC event arrives as an [InkAction]. */
    fun onAction(action: InkAction, context: Context) {
        when (action) {
            InkAction.Load -> load(context)
            is InkAction.SelectMode -> edit(context) { copy(mode = action.mode) }
            is InkAction.EditQr -> edit(context) { copy(qr = action.value) }
            is InkAction.EditQr2 -> edit(context) { copy(qr2 = action.value) }
            is InkAction.EditNote -> edit(context) { copy(note = action.value) }
            is InkAction.EditText -> edit(context) { copy(text = action.value) }
            is InkAction.EditBarcode -> edit(context) { copy(barcode = action.value) }
            is InkAction.ShowBarcodeValue -> edit(context) { copy(showBarcodeValue = action.show) }
            is InkAction.PictureCrop -> edit(context) { copy(pictureCrop = action.crop) }
            is InkAction.PictureDither -> edit(context) { copy(pictureDither = action.dither) }
            is InkAction.Rotate180 -> rotate(action.rotate, context)
            is InkAction.PickedPicture -> pickPicture(action.uri, context)
            InkAction.ArmTestPattern -> state = state.copy(testPattern = true, nfc = state.nfc.reset()).withPreview()
            is InkAction.NfcChanged -> nfcChanged(action.status)
            is InkAction.TagFound -> write(action.tag)
        }
    }

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        state = InkPrefs.load(context).copy(nfc = state.nfc).withPreview()
    }

    /**
     * Applies a user edit, recomputes the preview and persists the inputs. An armed test pattern is dropped, so the
     * preview always shows what the user just typed.
     */
    private fun edit(context: Context, change: InkState.() -> InkState) {
        state = state.change().copy(testPattern = false, nfc = state.nfc.reset()).withPreview()
        InkPrefs.save(context, state)
    }

    /** Rotation does not change the preview and keeps an armed test pattern, the user is calibrating with it. */
    private fun rotate(rotate: Boolean, context: Context) {
        state = state.copy(rotate180 = rotate, nfc = state.nfc.reset())
        InkPrefs.save(context, state)
    }

    private fun nfcChanged(status: NfcStatus) {
        val writing = state.nfc is NfcStatus.Writing
        if (writing.not()) state = state.copy(nfc = status)
    }

    private fun pickPicture(uri: Uri?, context: Context) {
        if (uri == null) return
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val decoded = decodePicture(appContext, uri)
            withContext(Dispatchers.Main) {
                decoded.onSuccess { bitmap ->
                    picture = bitmap
                    state = state.copy(hasPicture = true, pictureFailed = false, testPattern = false, nfc = state.nfc.reset())
                        .withPreview()
                }
                decoded.onFailure {
                    warnLog { "Picture could not be decoded: ${it.javaClass.simpleName}" }
                    state = state.copy(pictureFailed = true)
                }
            }
        }
    }

    private fun write(tag: Tag) {
        // A second tag while writing is ignored, also when the content was edited in the meantime.
        val writing = state.nfc is NfcStatus.Writing
        if (writing) return
        val image = state.preview
        if (image == null) {
            state = state.copy(nfc = NfcStatus.FixContentFirst)
            return
        }
        if (gate.tryEnter().not()) return
        val frame = FramePacker.pack(image, state.rotate180)
        state = state.copy(nfc = NfcStatus.Writing(0))
        // Never log the content, a WIFI QR code contains the password.
        infoLog { "Writing mode=${state.mode} testPattern=${state.testPattern}" }
        viewModelScope.launch(Dispatchers.IO) {
            var lastPercent = 0
            val result = writeToTag(tag, frame) { progress ->
                val percent = (progress * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    viewModelScope.launch(Dispatchers.Main) {
                        val stillWriting = state.nfc is NfcStatus.Writing
                        if (stillWriting) state = state.copy(nfc = NfcStatus.Writing(percent))
                    }
                }
            }
            withContext(Dispatchers.Main) {
                state = when (result) {
                    is ResultOfEmpty.Success -> state.copy(nfc = NfcStatus.Done, testPattern = false).withPreview()
                    is ResultOfEmpty.Failure -> state.copy(nfc = NfcStatus.Failed(result.value))
                }
                if (result is ResultOfEmpty.Failure) warnLog { "Write failed at ${result.value.step}" }
                gate.leave()
            }
        }
    }

    /** Renders the preview for the current inputs, see [toContent]. Synchronous, 250 x 122 pixels take milliseconds. */
    private fun InkState.withPreview(): InkState {
        val needsPicture = mode == Mode.PICTURE && testPattern.not()
        val gray = if (needsPicture) picture?.let { toDisplayGray(it, pictureCrop) } else null
        val showsBarcode = mode == Mode.BARCODE && testPattern.not()
        val format = if (showsBarcode) (encodeBarcode(barcode) as? ResultOf2.Success)?.value?.format else null
        val content = toContent(gray)
            ?: return copy(preview = null, problem = ContentError.Empty, barcodeFormat = format)
        return when (val result = render(content, AndroidText)) {
            is ResultOf2.Success -> copy(preview = result.value, problem = null, barcodeFormat = format)
            is ResultOf2.Failure -> copy(preview = null, problem = result.value, barcodeFormat = format)
        }
    }
}

/** A finished status (done, failed, fix content) goes back to ready as soon as the user changes something. */
private fun NfcStatus.reset(): NfcStatus = when (this) {
    NfcStatus.Done, NfcStatus.FixContentFirst, is NfcStatus.Failed -> NfcStatus.Ready
    NfcStatus.Missing, NfcStatus.Off, NfcStatus.Ready, is NfcStatus.Writing -> this
}

/**
 * What the display gets for this state. Null means the picture mode has no picture yet, which the preview treats
 * like an empty input.
 */
internal fun InkState.toContent(gray: GrayImage?): Content? = when {
    testPattern -> Content.TestPattern
    else -> when (mode) {
        Mode.QR -> Content.Qr(qr)
        Mode.QR_NOTE -> Content.QrWithNote(qr, note)
        Mode.QR_CONTENT -> Content.QrWithContent(qr)
        Mode.TWO_QR -> Content.TwoQr(qr, qr2)
        Mode.TEXT -> Content.Text(text)
        Mode.BARCODE -> Content.Barcode(barcode, showBarcodeValue)
        Mode.PICTURE -> gray?.let { Content.Picture(it, pictureDither) }
    }
}

/** Lets exactly one write run at a time. A tag that arrives while another write runs is ignored. */
internal class WriteGate {
    private val busy = AtomicBoolean(false)

    /** True if the caller may write now. Must be followed by [leave]. */
    fun tryEnter(): Boolean = busy.compareAndSet(false, true)

    /** Ends the current write. */
    fun leave() = busy.set(false)
}
