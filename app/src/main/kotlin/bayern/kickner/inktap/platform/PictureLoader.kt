package bayern.kickner.inktap.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import bayern.kickner.inktap.core.GrayImage
import bayern.kickner.inktap.core.Panel
import bayern.kickner.inktap.core.fitInto
import bayern.kickner.inktap.core.lumaOf

/** Longer edge of the decoded picture. Enough for every [Panel] and keeps a 50 megapixel photo out of memory. */
private const val DECODE_EDGE_PX = 1000

/**
 * Decodes the picture behind [uri] (from the Photo Picker) into a small software bitmap. Blocking, call it
 * from `Dispatchers.IO`. Returns a failed [Result] for broken or unsupported files.
 */
fun decodePicture(context: Context, uri: Uri): Result<Bitmap> = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Software memory, otherwise getPixels fails on hardware bitmaps.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        decoder.setTargetSampleSize(maxOf(1, longest / DECODE_EDGE_PX))
    }
}

/** Scales [picture] onto a white canvas of the size of [panel], see [fitInto] for [crop], and returns its brightness. */
fun toDisplayGray(picture: Bitmap, crop: Boolean, panel: Panel): GrayImage {
    val w = panel.width
    val h = panel.height
    val target = fitInto(picture.width, picture.height, crop, w, h)
    val canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBitmap)
    canvas.drawColor(Color.WHITE)
    val dst = Rect(target.x, target.y, target.x + target.w, target.y + target.h)
    canvas.drawBitmap(picture, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    val argb = IntArray(w * h)
    canvasBitmap.getPixels(argb, 0, w, 0, 0, w, h)
    return GrayImage(w, h, IntArray(argb.size) { lumaOf(argb[it]) })
}
