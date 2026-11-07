package bayern.kickner.inktap.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import bayern.kickner.inktap.core.Display
import bayern.kickner.inktap.core.GrayImage
import bayern.kickner.inktap.core.fitInto
import bayern.kickner.inktap.core.lumaOf

/** Longer edge of the decoded picture. Enough for 250 x 122 and keeps a 50 megapixel photo out of memory. */
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

/** Scales [picture] onto a white 250 x 122 canvas, see [fitInto] for [crop], and returns its brightness. */
fun toDisplayGray(picture: Bitmap, crop: Boolean): GrayImage {
    val target = fitInto(picture.width, picture.height, crop)
    val canvasBitmap = Bitmap.createBitmap(Display.WIDTH, Display.HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBitmap)
    canvas.drawColor(Color.WHITE)
    val dst = Rect(target.x, target.y, target.x + target.w, target.y + target.h)
    canvas.drawBitmap(picture, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    val argb = IntArray(Display.WIDTH * Display.HEIGHT)
    canvasBitmap.getPixels(argb, 0, Display.WIDTH, 0, 0, Display.WIDTH, Display.HEIGHT)
    return GrayImage(Display.WIDTH, Display.HEIGHT, IntArray(argb.size) { lumaOf(argb[it]) })
}
