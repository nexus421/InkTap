package bayern.kickner.inktap.ui.screens.ink

import android.net.Uri
import android.nfc.Tag
import bayern.kickner.inktap.core.BarcodeFormat
import bayern.kickner.inktap.core.ContentError
import bayern.kickner.inktap.core.MonoImage
import bayern.kickner.inktap.core.Panel
import bayern.kickner.inktap.core.WriteError

/** The seven modes, one chip each. */
enum class Mode { QR, QR_NOTE, QR_CONTENT, TWO_QR, TEXT, BARCODE, PICTURE }

/** What the status area below the preview shows. */
sealed interface NfcStatus {
    /** The phone has no NFC hardware. The preview still works. */
    data object Missing : NfcStatus

    /** NFC is switched off in the system settings. */
    data object Off : NfcStatus

    /** Waiting for the display. */
    data object Ready : NfcStatus

    /** A frame is being written, [percent] from 0 to 100. */
    data class Writing(val percent: Int) : NfcStatus

    /** The last write succeeded. */
    data object Done : NfcStatus

    /** A display was touched while the content had a problem. */
    data object FixContentFirst : NfcStatus

    /** The last write failed with [error]. */
    data class Failed(val error: WriteError) : NfcStatus
}

/** State of the only screen. [preview] is null exactly when [problem] is not null. */
data class InkState(
    val panel: Panel = Panel.EPD_2IN13,
    val mode: Mode = Mode.QR,
    val qr: String = "",
    val qr2: String = "",
    val note: String = "",
    val text: String = "",
    val barcode: String = "",
    val showBarcodeValue: Boolean = true,
    val barcodeFormat: BarcodeFormat? = null,
    val hasPicture: Boolean = false,
    val pictureCrop: Boolean = true,
    val pictureDither: Boolean = true,
    val rotate180: Boolean = false,
    val testPattern: Boolean = false,
    val preview: MonoImage? = null,
    val problem: ContentError? = ContentError.Empty,
    val pictureFailed: Boolean = false,
    val nfc: NfcStatus = NfcStatus.Ready,
)

/** Everything the user (or the NFC callback) can do. */
sealed interface InkAction {
    /** Restores the last inputs, only the first call has an effect. */
    data object Load : InkAction

    /** Switches the display model. The preview is rendered again for its size. */
    data class SelectPanel(val panel: Panel) : InkAction

    /** Switches the layout. */
    data class SelectMode(val mode: Mode) : InkAction

    /** Changes the (left) QR content. */
    data class EditQr(val value: String) : InkAction

    /** Changes the right QR content of the two QR layout. */
    data class EditQr2(val value: String) : InkAction

    /** Changes the note next to the QR code. */
    data class EditNote(val value: String) : InkAction

    /** Changes the text of the text layout. */
    data class EditText(val value: String) : InkAction

    /** Changes the barcode value. */
    data class EditBarcode(val value: String) : InkAction

    /** Shows or hides the value below the barcode. */
    data class ShowBarcodeValue(val show: Boolean) : InkAction

    /** Result of the Photo Picker, null if the user cancelled. */
    data class PickedPicture(val uri: Uri?) : InkAction

    /** Fill the display and cut off the edges, or show the whole picture. */
    data class PictureCrop(val crop: Boolean) : InkAction

    /** Dither grayscale (photos) or plain threshold (logos). */
    data class PictureDither(val dither: Boolean) : InkAction

    /** Turns the output upside down. */
    data class Rotate180(val rotate: Boolean) : InkAction

    /** Shows the calibration image until it was written once. */
    data object ArmTestPattern : InkAction

    /** NFC availability changed, reported by the activity in onResume. */
    data class NfcChanged(val status: NfcStatus) : InkAction

    /** A tag came into the field. */
    data class TagFound(val tag: Tag) : InkAction
}
