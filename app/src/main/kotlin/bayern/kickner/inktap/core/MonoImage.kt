package bayern.kickner.inktap.core

/**
 * Size of the Waveshare 2.13 inch NFC e-paper as the user sees it: landscape, 250 x 122 pixels.
 * Every layout is drawn in this orientation. [FramePacker] turns it into the panel's own memory layout.
 */
object Display {
    /** Visible width in pixels (landscape). */
    const val WIDTH = 250

    /** Visible height in pixels (landscape). */
    const val HEIGHT = 122
}

/**
 * A 1-bit image. `true` means black, `false` means white. New images are completely white.
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 */
class MonoImage(val width: Int, val height: Int) {

    private val pixels = BooleanArray(width * height)

    init {
        require(width > 0 && height > 0) { "Image size must be positive, was ${width}x$height" }
    }

    /** True if ([x], [y]) is black. Coordinates outside the image are white. */
    operator fun get(x: Int, y: Int): Boolean = inside(x, y) && pixels[y * width + x]

    /** Sets ([x], [y]) to black ([black] = true) or white. Coordinates outside the image are ignored. */
    operator fun set(x: Int, y: Int, black: Boolean) {
        if (inside(x, y)) pixels[y * width + x] = black
    }

    /** Fills the rectangle with its top left corner at ([x], [y]) in black. Clipped at the image border. */
    fun fillRect(x: Int, y: Int, w: Int, h: Int) {
        for (yy in y until y + h) for (xx in x until x + w) this[xx, yy] = true
    }

    /** Copies every black pixel of [source] into this image, with the source's top left corner at ([x], [y]). */
    fun draw(source: MonoImage, x: Int, y: Int) {
        for (yy in 0 until source.height) for (xx in 0 until source.width) {
            if (source[xx, yy]) this[x + xx, y + yy] = true
        }
    }

    /**
     * ARGB pixels for an on-screen preview, row by row. Defaults look like e-paper: dark gray ink on warm white.
     */
    fun toArgb(ink: Int = 0xFF222222.toInt(), paper: Int = 0xFFF4F4F0.toInt()): IntArray =
        IntArray(width * height) { if (pixels[it]) ink else paper }

    /** Number of black pixels, handy for tests and for detecting an empty result. */
    fun blackCount(): Int = pixels.count { it }

    private fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height
}

/**
 * An 8-bit grayscale image, `0` is black and `255` is white. Used as the input for [toMonoImage].
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 * @property luma One value per pixel, row by row.
 */
class GrayImage(val width: Int, val height: Int, val luma: IntArray) {
    init {
        require(luma.size == width * height) { "Expected ${width * height} values, got ${luma.size}" }
    }
}
