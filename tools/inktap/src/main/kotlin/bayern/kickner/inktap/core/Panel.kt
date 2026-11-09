package bayern.kickner.inktap.core

/**
 * The supported Waveshare passive NFC e-paper models. Values come from the official Waveshare Android SDK
 * (`NFC.jar`, tables `d`, `e`, `f`, `g`, `h`) and match Proxmark3 (`client/src/cmdhfwaveshare.c`).
 *
 * All models answer with the same UID, so the app cannot tell them apart. The user picks the model.
 *
 * @property width Visible width in pixels as the user sees the display (landscape). Every layout uses it.
 * @property height Visible height in pixels (landscape).
 * @property panelId Argument of the "select panel" command `CD 00 <id>`.
 * @property packetBytes Image bytes per `CD 08` data command.
 * @property portraitMemory True if the panel memory is portrait (the SDK rotates the image by 270 degrees).
 * @property refreshTimeoutMs How long to wait for the display to report that the refresh finished.
 * @property inches Diagonal for the UI, for example "2.13".
 */
enum class Panel(
    val width: Int,
    val height: Int,
    val panelId: Byte,
    val packetBytes: Int,
    val portraitMemory: Boolean,
    val refreshTimeoutMs: Long,
    val inches: String,
) {
    /** 2.13 inch, 250 x 122. Portrait memory of 250 rows with 128 columns, of which 122 are visible. */
    EPD_2IN13(width = 250, height = 122, panelId = 4, packetBytes = 16, portraitMemory = true, refreshTimeoutMs = 10_000, inches = "2.13"),

    /** 4.2 inch, 400 x 300. Landscape memory, row by row, no rotation. Bigger panel, slower refresh. */
    EPD_4IN2(width = 400, height = 300, panelId = 10, packetBytes = 100, portraitMemory = false, refreshTimeoutMs = 20_000, inches = "4.2");

    /** Bytes per memory row. Portrait panels have 128 columns (16 bytes), landscape panels one bit per pixel. */
    val rowBytes: Int get() = if (portraitMemory) PORTRAIT_ROW_BYTES else width / 8

    /** Number of memory rows. */
    val rows: Int get() = if (portraitMemory) width else height

    /** Size of a complete frame in bytes. */
    val frameBytes: Int get() = rowBytes * rows

    /** Number of `CD 08` data commands per frame. */
    val packets: Int get() = frameBytes / packetBytes

    /** Helpers for parsing stored values. */
    companion object {
        private const val PORTRAIT_ROW_BYTES = 16

        /** The model stored under [name], or [EPD_2IN13] if the name is unknown (older or newer app version). */
        fun fromName(name: String?): Panel = entries.firstOrNull { it.name == name } ?: EPD_2IN13
    }
}
