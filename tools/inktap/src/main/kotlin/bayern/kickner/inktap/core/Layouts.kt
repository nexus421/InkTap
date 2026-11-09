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

    /** A picture that is already scaled to the panel size, see [fitInto]. */
    data class Picture(val image: GrayImage, val dither: Boolean = true) : Content

    /**
     * Calibration image: a 1 pixel frame on all four edges plus the word "OBEN" at the top. If one frame line is
     * missing on the real display, the visible area is offset, if the word is at the bottom, switch on rotate180.
     */
    data object TestPattern : Content
}

/**
 * Sizes that grow with the display. For the 2.13 inch panel they are exactly the values the layouts were
 * designed and tested with: margin 4, value line 22, test pattern text 40, QR with note uses the full height.
 */
private class Metrics(val panel: Panel) {
    val w = panel.width
    val h = panel.height

    /** Outer margin for text and barcodes. */
    val margin = h / 30

    /** Height of the value line below a barcode. */
    val barcodeText = Math.round(h * 0.18f)

    /** Height of the word at the top of the test pattern. */
    val testPatternText = h * 40 / 122

    /** Side of the QR code next to a text. Wide panels give it the full height, 4:3 panels keep room for the text. */
    val qrNextToText = minOf(h, w * 55 / 100)
}

/** Renders [content] into a [Panel.width] x [Panel.height] image for [panel]. */
fun render(content: Content, text: TextRasterizer, panel: Panel): ResultOf2<MonoImage, ContentError> {
    val m = Metrics(panel)
    val canvas = MonoImage(m.w, m.h)
    val error: ContentError? = when (content) {
        is Content.Qr -> {
            val size = m.h
            place(renderQr(content.text, size), canvas, (m.w - size) / 2, 0)
        }
        is Content.QrWithNote -> qrAndText(content.text, content.note, canvas, text, m)
        is Content.QrWithContent -> qrAndText(content.text, content.text, canvas, text, m)
        is Content.TwoQr -> {
            val half = m.w / 2
            val size = minOf(half, m.h)
            place(renderQr(content.left, size), canvas, (half - size) / 2, (m.h - size) / 2)
                ?: place(renderQr(content.right, size), canvas, half + (half - size) / 2, (m.h - size) / 2)
        }
        is Content.Text -> textInto(content.text, canvas, text, m.margin, m.margin, m.w - 2 * m.margin, m.h - 2 * m.margin)
        is Content.Barcode -> barcode(content, canvas, text, m)
        is Content.Picture -> {
            canvas.draw(content.image.toMonoImage(content.dither), 0, 0)
            null
        }
        is Content.TestPattern -> {
            canvas.fillRect(0, 0, m.w, 1)
            canvas.fillRect(0, m.h - 1, m.w, 1)
            canvas.fillRect(0, 0, 1, m.h)
            canvas.fillRect(m.w - 1, 0, 1, m.h)
            textInto("OBEN", canvas, text, m.margin, m.margin, m.w - 2 * m.margin, m.testPatternText)
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

private fun qrAndText(qr: String, note: String, canvas: MonoImage, text: TextRasterizer, m: Metrics): ContentError? {
    val size = m.qrNextToText
    val textX = size + m.margin
    return place(renderQr(qr, size), canvas, 0, (m.h - size) / 2)
        ?: textInto(note, canvas, text, textX, m.margin, m.w - textX - m.margin, m.h - 2 * m.margin)
}

private fun textInto(value: String, canvas: MonoImage, text: TextRasterizer, x: Int, y: Int, w: Int, h: Int): ContentError? {
    if (value.isBlank()) return ContentError.Empty
    val image = text.rasterize(value.trim(), w, h) ?: return ContentError.TextTooLong
    canvas.draw(image, x + (w - image.width) / 2, y + (h - image.height) / 2)
    return null
}

private fun barcode(content: Content.Barcode, canvas: MonoImage, text: TextRasterizer, m: Metrics): ContentError? {
    val modules = when (val r = encodeBarcode(content.value)) {
        is ResultOf2.Success -> r.value
        is ResultOf2.Failure -> return r.value
    }
    val total = modules.quietLeft + modules.bars.size + modules.quietRight
    val px = m.w / total
    if (px < 1) return ContentError.BarcodeTooLong
    val barHeight = m.h - 2 * m.margin - if (content.showValue) m.barcodeText else 0
    val left = (m.w - total * px) / 2 + modules.quietLeft * px
    modules.bars.forEachIndexed { i, bar -> if (bar) canvas.fillRect(left + i * px, m.margin, px, barHeight) }
    if (content.showValue.not()) return null
    val textTop = m.margin + barHeight
    return textInto(content.value, canvas, text, m.margin, textTop, m.w - 2 * m.margin, m.barcodeText)
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
