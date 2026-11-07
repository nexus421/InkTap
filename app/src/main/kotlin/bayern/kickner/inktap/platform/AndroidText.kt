package bayern.kickner.inktap.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import bayern.kickner.inktap.core.MonoImage
import bayern.kickner.inktap.core.TextRasterizer
import bayern.kickner.inktap.core.largestFitting
import bayern.kickner.inktap.core.lumaOf

/** Smallest font size in pixels that is still readable on the panel. */
private const val MIN_FONT_PX = 10

/** Largest font size in pixels, a single short word fills the display at about this size. */
private const val MAX_FONT_PX = 96

/**
 * [TextRasterizer] on top of `StaticLayout`: bold, centered, word wrap, as large as it fits. Anti-aliasing is
 * off, because the panel only knows black and white and grey edge pixels would turn into ragged dots.
 */
object AndroidText : TextRasterizer {

    override fun rasterize(text: String, width: Int, height: Int): MonoImage? {
        val paint = TextPaint().apply {
            isAntiAlias = false
            color = Color.BLACK
            typeface = Typeface.DEFAULT_BOLD
        }
        // Whole words first. Long URLs have no spaces, they may be broken anywhere as a fallback.
        val size = fittingSize(text, paint, width, height, wholeWords = true)
            ?: fittingSize(text, paint, width, height, wholeWords = false)
            ?: return null

        paint.textSize = size.toFloat()
        val layout = layout(text, paint, width)
        val bitmap = Bitmap.createBitmap(width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        layout.draw(canvas)
        val argb = IntArray(width * layout.height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, layout.height)
        val image = MonoImage(width, layout.height)
        argb.forEachIndexed { i, pixel -> image[i % width, i / width] = lumaOf(pixel) < 128 }
        return image
    }

    private fun fittingSize(text: String, paint: TextPaint, width: Int, height: Int, wholeWords: Boolean): Int? {
        val words = text.split(' ', '\n').filter { it.isNotEmpty() }
        return largestFitting(MIN_FONT_PX, MAX_FONT_PX) { px ->
            paint.textSize = px.toFloat()
            val wordsFit = wholeWords.not() || words.all { paint.measureText(it) <= width }
            wordsFit && layout(text, paint, width).height <= height
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
}
