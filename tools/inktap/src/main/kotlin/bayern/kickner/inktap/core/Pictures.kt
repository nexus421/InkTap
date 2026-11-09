package bayern.kickner.inktap.core

/**
 * A rectangle in pixels.
 *
 * @property x Left edge.
 * @property y Top edge.
 * @property w Width.
 * @property h Height.
 */
data class PixelRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Where a picture of [srcW] x [srcH] goes on a display of [dstW] x [dstH], keeping its aspect ratio.
 *
 * @param crop `true` fills the whole display and cuts off what sticks out (good for photos), `false` shows the
 * whole picture with white bars (good for logos and screenshots).
 * @return The target rectangle on the display. With [crop] it may start at negative coordinates.
 */
fun fitInto(srcW: Int, srcH: Int, crop: Boolean, dstW: Int, dstH: Int): PixelRect {
    require(srcW > 0 && srcH > 0) { "Picture size must be positive" }
    val scaleX = dstW.toDouble() / srcW
    val scaleY = dstH.toDouble() / srcH
    val scale = if (crop) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
    val w = Math.round(srcW * scale).toInt().coerceAtLeast(1)
    val h = Math.round(srcH * scale).toInt().coerceAtLeast(1)
    return PixelRect((dstW - w) / 2, (dstH - h) / 2, w, h)
}

/**
 * Converts to black and white.
 *
 * @param dither `true` uses Floyd-Steinberg error diffusion, so photos keep their shades. `false` is a plain
 * threshold at the middle gray, which keeps logos and text crisp.
 */
fun GrayImage.toMonoImage(dither: Boolean): MonoImage {
    val out = MonoImage(width, height)
    val work = luma.copyOf()
    for (y in 0 until height) for (x in 0 until width) {
        val old = work[y * width + x].coerceIn(0, 255)
        val black = old < 128
        out[x, y] = black
        if (dither.not()) continue
        val err = old - if (black) 0 else 255
        spread(work, x + 1, y, err * 7 / 16)
        spread(work, x - 1, y + 1, err * 3 / 16)
        spread(work, x, y + 1, err * 5 / 16)
        spread(work, x + 1, y + 1, err * 1 / 16)
    }
    return out
}

private fun GrayImage.spread(work: IntArray, x: Int, y: Int, amount: Int) {
    if (x in 0 until width && y in 0 until height) work[y * width + x] += amount
}

/** Perceived brightness (0 to 255) of an ARGB pixel, ITU-R BT.601 weights. Transparent counts as white. */
fun lumaOf(argb: Int): Int {
    val alpha = argb ushr 24 and 0xFF
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    val luma = (299 * r + 587 * g + 114 * b) / 1000
    return (luma * alpha + 255 * (255 - alpha)) / 255
}
