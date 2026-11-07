package bayern.kickner.inktap.core

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotnexlib.ResultOf2
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.test.fail

/**
 * What the panel shows for a frame: the inverse of [FramePacker.pack], derived independently from the SDK
 * memory layout (portrait row r, column c is landscape pixel x = 249 - r, y = c, columns 122 to 127 invisible).
 */
fun displayedImage(frame: ByteArray): MonoImage {
    val image = MonoImage(Display.WIDTH, Display.HEIGHT)
    for (row in 0 until 250) for (col in 0 until 122) {
        val bit = frame[row * 16 + col / 8].toInt() and (0x80 ushr (col % 8))
        image[249 - row, col] = bit == 0
    }
    return image
}

/**
 * Decodes the first barcode or QR code ZXing finds, like a phone camera would: the image is scaled 3x and gets a
 * white border (the display case). Returns null if nothing is found.
 */
fun decode(image: MonoImage, x: Int = 0, y: Int = 0, w: Int = image.width, h: Int = image.height): String? {
    val scale = 3
    val border = 20
    val outW = w * scale + 2 * border
    val outH = h * scale + 2 * border
    val argb = IntArray(outW * outH) { 0xFFFFFFFF.toInt() }
    for (yy in 0 until h * scale) for (xx in 0 until w * scale) {
        if (image[x + xx / scale, y + yy / scale]) argb[(yy + border) * outW + xx + border] = 0xFF000000.toInt()
    }
    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(outW, outH, argb)))
    val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")
    return runCatching { MultiFormatReader().decode(bitmap, hints).text }.getOrNull()
}

/** The whole chain the app uses: layout, frame, what the panel shows. */
fun renderToPanel(content: Content, rotate180: Boolean = false): MonoImage {
    val image = when (val r = render(content, AwtText)) {
        is ResultOf2.Success -> r.value
        is ResultOf2.Failure -> fail("Render failed: ${r.value}")
    }
    return displayedImage(FramePacker.pack(image, rotate180))
}

/** Test stand-in for the Android `StaticLayout` rasterizer: AWT, bold sans serif, largest size that fits. */
object AwtText : TextRasterizer {
    override fun rasterize(text: String, width: Int, height: Int): MonoImage? {
        val lines = text.lines()
        val probe = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY).createGraphics()
        val size = largestFitting(10, 80) { px ->
            val metrics = probe.getFontMetrics(Font(Font.SANS_SERIF, Font.BOLD, px))
            lines.maxOf { metrics.stringWidth(it) } <= width && metrics.height * lines.size <= height
        } ?: return null
        val img = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        val g = img.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = java.awt.Color.BLACK
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, size)
        val m = g.fontMetrics
        val top = (height - m.height * lines.size) / 2
        lines.forEachIndexed { i, line -> g.drawString(line, (width - m.stringWidth(line)) / 2, top + i * m.height + m.ascent) }
        val out = MonoImage(width, height)
        for (yy in 0 until height) for (xx in 0 until width) out[xx, yy] = img.getRGB(xx, yy) and 0xFFFFFF == 0
        return out
    }
}
