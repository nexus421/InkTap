package bayern.kickner.inktap.core

import kotnexlib.ResultOf2

/** Why a content cannot be drawn. The UI maps every case to one German sentence. */
sealed interface ContentError {
    /** A required field is empty. */
    data object Empty : ContentError

    /** The QR content does not fit with modules of at least [MIN_QR_MODULE_PX] pixels. */
    data object QrTooLong : ContentError

    /** The barcode does not fit with bars of at least one pixel. */
    data object BarcodeTooLong : ContentError

    /** Code 128 only allows printable ASCII characters. */
    data object BarcodeInvalidChars : ContentError

    /** The text does not fit even with the smallest font size. */
    data object TextTooLong : ContentError
}

/**
 * Renders text into a white box of [width] x [height] pixels, as large as it fits. Returns null if it does not
 * fit even at the smallest font size. Implemented with Android's `StaticLayout` in the app and with AWT in tests.
 */
fun interface TextRasterizer {
    /** See [TextRasterizer]. */
    fun rasterize(text: String, width: Int, height: Int): MonoImage?
}

/** Everything the user can put on the display. Each case is one mode in the UI. */
sealed interface Content {
    /** One QR code, centered. */
    data class Qr(val text: String) : Content

    /** QR code on the left, free text on the right. */
    data class QrWithNote(val text: String, val note: String) : Content

    /** QR code on the left, its own content as readable text on the right. */
    data class QrWithContent(val text: String) : Content

    /** Two QR codes next to each other. */
    data class TwoQr(val left: String, val right: String) : Content

    /** Only text, as large as possible. */
    data class Text(val text: String) : Content

    /** A barcode, optionally with its value printed below. */
    data class Barcode(val value: String, val showValue: Boolean = true) : Content

    /** A picture that is already scaled to the display size, see [fitInto]. */
    data class Picture(val image: GrayImage, val dither: Boolean = true) : Content

    /**
     * Calibration image: a 1 pixel frame on all four edges plus the word "OBEN" at the top. If one frame line is
     * missing on the real display, the visible area is offset, if the word is at the bottom, switch on rotate180.
     */
    data object TestPattern : Content
}

/** Outer margin in pixels for text and barcodes. */
private const val MARGIN = 4

/** Height of the value line below a barcode. */
private const val BARCODE_TEXT_HEIGHT = 22

/** Renders [content] into a [Display.WIDTH] x [Display.HEIGHT] image. */
fun render(content: Content, text: TextRasterizer): ResultOf2<MonoImage, ContentError> {
    val canvas = MonoImage(Display.WIDTH, Display.HEIGHT)
    val h = Display.HEIGHT
    val error: ContentError? = when (content) {
        is Content.Qr -> place(renderQr(content.text, h), canvas, (Display.WIDTH - h) / 2, 0)
        is Content.QrWithNote -> qrAndText(content.text, content.note, canvas, text)
        is Content.QrWithContent -> qrAndText(content.text, content.text, canvas, text)
        is Content.TwoQr -> {
            val half = Display.WIDTH / 2
            val size = minOf(half, h)
            place(renderQr(content.left, size), canvas, (half - size) / 2, (h - size) / 2)
                ?: place(renderQr(content.right, size), canvas, half + (half - size) / 2, (h - size) / 2)
        }
        is Content.Text -> textInto(content.text, canvas, text, MARGIN, MARGIN, Display.WIDTH - 2 * MARGIN, h - 2 * MARGIN)
        is Content.Barcode -> barcode(content, canvas, text)
        is Content.Picture -> {
            canvas.draw(content.image.toMonoImage(content.dither), 0, 0)
            null
        }
        is Content.TestPattern -> {
            canvas.fillRect(0, 0, Display.WIDTH, 1)
            canvas.fillRect(0, h - 1, Display.WIDTH, 1)
            canvas.fillRect(0, 0, 1, h)
            canvas.fillRect(Display.WIDTH - 1, 0, 1, h)
            textInto("OBEN", canvas, text, MARGIN, MARGIN, Display.WIDTH - 2 * MARGIN, 40)
        }
    }
    return if (error == null) ResultOf2.Success(canvas) else ResultOf2.Failure(error)
}

private fun place(result: ResultOf2<MonoImage, ContentError>, canvas: MonoImage, x: Int, y: Int): ContentError? =
    when (result) {
        is ResultOf2.Success -> {
            canvas.draw(result.value, x, y)
            null
        }
        is ResultOf2.Failure -> result.value
    }

private fun qrAndText(qr: String, note: String, canvas: MonoImage, text: TextRasterizer): ContentError? {
    val size = Display.HEIGHT
    val textX = size + MARGIN
    return place(renderQr(qr, size), canvas, 0, 0)
        ?: textInto(note, canvas, text, textX, MARGIN, Display.WIDTH - textX - MARGIN, size - 2 * MARGIN)
}

private fun textInto(value: String, canvas: MonoImage, text: TextRasterizer, x: Int, y: Int, w: Int, h: Int): ContentError? {
    if (value.isBlank()) return ContentError.Empty
    val image = text.rasterize(value.trim(), w, h) ?: return ContentError.TextTooLong
    canvas.draw(image, x + (w - image.width) / 2, y + (h - image.height) / 2)
    return null
}

private fun barcode(content: Content.Barcode, canvas: MonoImage, text: TextRasterizer): ContentError? {
    val modules = when (val r = encodeBarcode(content.value)) {
        is ResultOf2.Success -> r.value
        is ResultOf2.Failure -> return r.value
    }
    val total = modules.quietLeft + modules.bars.size + modules.quietRight
    val px = Display.WIDTH / total
    if (px < 1) return ContentError.BarcodeTooLong
    val barHeight = Display.HEIGHT - 2 * MARGIN - if (content.showValue) BARCODE_TEXT_HEIGHT else 0
    val left = (Display.WIDTH - total * px) / 2 + modules.quietLeft * px
    modules.bars.forEachIndexed { i, bar -> if (bar) canvas.fillRect(left + i * px, MARGIN, px, barHeight) }
    if (content.showValue.not()) return null
    val textTop = MARGIN + barHeight
    return textInto(content.value, canvas, text, MARGIN, textTop, Display.WIDTH - 2 * MARGIN, BARCODE_TEXT_HEIGHT)
}

/**
 * Largest value in [min]..[max] for which [fits] is true, found by binary search. Returns null if not even
 * [min] fits. [fits] must be monotonic (true up to some size, false above). Used to pick the font size.
 */
fun largestFitting(min: Int, max: Int, fits: (Int) -> Boolean): Int? {
    if (fits(min).not()) return null
    var low = min
    var high = max
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(mid)) low = mid else high = mid - 1
    }
    return low
}
