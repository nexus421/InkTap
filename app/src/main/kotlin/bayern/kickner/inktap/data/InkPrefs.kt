package bayern.kickner.inktap.data

import android.content.Context
import androidx.core.content.edit
import bayern.kickner.inktap.core.Panel
import bayern.kickner.inktap.ui.screens.ink.InkState
import bayern.kickner.inktap.ui.screens.ink.Mode

/**
 * The last inputs in `SharedPreferences`. Picture, preview and NFC status are not stored. The file is excluded
 * from backups because the inputs can contain WIFI passwords.
 */
object InkPrefs {
    private const val FILE = "inktap"
    private const val PANEL = "panel"
    private const val MODE = "mode"
    private const val QR = "qr"
    private const val QR2 = "qr2"
    private const val NOTE = "note"
    private const val TEXT = "text"
    private const val BARCODE = "barcode"
    private const val SHOW_BARCODE_VALUE = "showBarcodeValue"
    private const val PICTURE_CROP = "pictureCrop"
    private const val PICTURE_DITHER = "pictureDither"
    private const val ROTATE_180 = "rotate180"

    /** Reads the stored inputs into a fresh [InkState]. Unknown modes fall back to [Mode.QR], unknown panels to 2.13 inch. */
    fun load(context: Context): InkState {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = InkState()
        val modeName = prefs.getString(MODE, null)
        return defaults.copy(
            panel = Panel.fromName(prefs.getString(PANEL, null)),
            mode = modeName?.let { name -> runCatching { Mode.valueOf(name) }.getOrNull() } ?: Mode.QR,
            qr = prefs.getString(QR, null) ?: defaults.qr,
            qr2 = prefs.getString(QR2, null) ?: defaults.qr2,
            note = prefs.getString(NOTE, null) ?: defaults.note,
            text = prefs.getString(TEXT, null) ?: defaults.text,
            barcode = prefs.getString(BARCODE, null) ?: defaults.barcode,
            showBarcodeValue = prefs.getBoolean(SHOW_BARCODE_VALUE, defaults.showBarcodeValue),
            pictureCrop = prefs.getBoolean(PICTURE_CROP, defaults.pictureCrop),
            pictureDither = prefs.getBoolean(PICTURE_DITHER, defaults.pictureDither),
            rotate180 = prefs.getBoolean(ROTATE_180, defaults.rotate180),
        )
    }

    /** Stores the inputs of [state]. Asynchronous, safe to call on every keystroke. */
    fun save(context: Context, state: InkState) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString(PANEL, state.panel.name)
            putString(MODE, state.mode.name)
            putString(QR, state.qr)
            putString(QR2, state.qr2)
            putString(NOTE, state.note)
            putString(TEXT, state.text)
            putString(BARCODE, state.barcode)
            putBoolean(SHOW_BARCODE_VALUE, state.showBarcodeValue)
            putBoolean(PICTURE_CROP, state.pictureCrop)
            putBoolean(PICTURE_DITHER, state.pictureDither)
            putBoolean(ROTATE_180, state.rotate180)
        }
    }
}
