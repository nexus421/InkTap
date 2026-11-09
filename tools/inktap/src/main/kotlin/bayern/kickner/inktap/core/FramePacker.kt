package bayern.kickner.inktap.core

/**
 * Converts a landscape [MonoImage] into the frame a [Panel] expects.
 *
 * A bit of `1` is white, `0` is black, the most significant bit is the leftmost pixel of a memory row.
 * Portrait panels (2.13 inch) store 250 rows of 16 bytes (128 columns, the first 122 visible). The official
 * Waveshare SDK gets there by rotating a 250 x 128 landscape bitmap by 270 degrees. Landscape panels (4.2 inch)
 * store the image row by row without rotation. See `FramePackerTest` for both SDK pipelines.
 */
object FramePacker {

    /**
     * Packs [image] into a frame of [Panel.frameBytes] bytes.
     *
     * @param rotate180 Turns the picture upside down, for users who hold the display the other way round.
     */
    fun pack(image: MonoImage, panel: Panel, rotate180: Boolean = false): ByteArray {
        require(image.width == panel.width && image.height == panel.height) {
            "Expected ${panel.width}x${panel.height}, got ${image.width}x${image.height}"
        }
        // All white, which also fills invisible padding columns.
        val frame = ByteArray(panel.frameBytes) { 0xFF.toByte() }
        for (y in 0 until panel.height) for (x in 0 until panel.width) {
            val black = if (rotate180) image[panel.width - 1 - x, panel.height - 1 - y] else image[x, y]
            if (black.not()) continue
            val row = if (panel.portraitMemory) panel.width - 1 - x else y
            val col = if (panel.portraitMemory) y else x
            val index = row * panel.rowBytes + col / 8
            frame[index] = (frame[index].toInt() and (0x80 ushr (col % 8)).inv()).toByte()
        }
        return frame
    }
}
