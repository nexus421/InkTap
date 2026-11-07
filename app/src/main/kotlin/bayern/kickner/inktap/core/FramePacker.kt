package bayern.kickner.inktap.core

/**
 * Converts a landscape [MonoImage] of [Display.WIDTH] x [Display.HEIGHT] into the 4000 byte frame the
 * 2.13 inch panel expects.
 *
 * The panel memory is portrait: 250 rows of 16 bytes (128 pixel columns, of which the first 122 are visible).
 * A bit of `1` is white, `0` is black, the most significant bit is the leftmost pixel. This is exactly what the
 * official Waveshare SDK produces when it rotates a 250 x 128 landscape bitmap by 270 degrees and packs it,
 * see `FramePackerTest.matchesTheWaveshareSdkPipeline`.
 */
object FramePacker {

    /** Bytes per portrait row (128 columns). */
    const val ROW_BYTES = 16

    /** Number of portrait rows, equals [Display.WIDTH]. */
    const val ROWS = Display.WIDTH

    /** Size of a complete frame in bytes. */
    const val FRAME_BYTES = ROW_BYTES * ROWS

    /**
     * Packs [image] into a frame.
     *
     * @param rotate180 Turns the picture upside down, for users who hold the display the other way round.
     */
    fun pack(image: MonoImage, rotate180: Boolean = false): ByteArray {
        require(image.width == Display.WIDTH && image.height == Display.HEIGHT) {
            "Expected ${Display.WIDTH}x${Display.HEIGHT}, got ${image.width}x${image.height}"
        }
        // All white, which also fills the 6 invisible padding columns.
        val frame = ByteArray(FRAME_BYTES) { 0xFF.toByte() }
        for (row in 0 until ROWS) for (col in 0 until Display.HEIGHT) {
            val x = Display.WIDTH - 1 - row
            val y = col
            val black = if (rotate180) image[Display.WIDTH - 1 - x, Display.HEIGHT - 1 - y] else image[x, y]
            if (black) {
                val index = row * ROW_BYTES + col / 8
                frame[index] = (frame[index].toInt() and (0x80 ushr (col % 8)).inv()).toByte()
            }
        }
        return frame
    }
}
