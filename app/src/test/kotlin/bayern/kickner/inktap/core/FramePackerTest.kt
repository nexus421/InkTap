package bayern.kickner.inktap.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramePackerTest {

    /**
     * Literal re-implementation of the official SDK (`NFC.jar` 2.13 inch branch, read from its bytecode):
     * a 250 x 128 ARGB bitmap is rotated by 270 degrees with `Matrix.setRotate(270f)`, which maps (x, y) to
     * (y, 249 - x), then every row of 128 pixels is packed MSB first with `(pixel and 0xFF) > 128` meaning 1 (white).
     */
    private fun sdkPipeline(image: MonoImage): ByteArray {
        val w = 250
        val h = 128
        val landscape = IntArray(w * h) { i ->
            val black = image[i % w, i / w]
            if (black) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val portrait = IntArray(h * w)
        for (y in 0 until h) for (x in 0 until w) portrait[(w - 1 - x) * h + y] = landscape[y * w + x]
        val out = ByteArray(4000)
        for (row in 0 until 250) for (b in 0 until 16) {
            var data = 0
            for (bit in 0 until 8) {
                data = (data shl 1) and 0xFF
                val pixel = portrait[(b * 8 + bit) + row * 128] and 0xFF
                if (pixel > 128) data = data or 1
            }
            out[row * 16 + b] = data.toByte()
        }
        return out
    }

    private fun randomImage(seed: Int): MonoImage {
        val random = Random(seed)
        val image = MonoImage(Display.WIDTH, Display.HEIGHT)
        for (y in 0 until Display.HEIGHT) for (x in 0 until Display.WIDTH) image[x, y] = random.nextBoolean()
        return image
    }

    @Test
    fun matchesTheWaveshareSdkPipeline() {
        repeat(20) { seed ->
            val image = randomImage(seed)
            assertContentEquals(sdkPipeline(image), FramePacker.pack(image))
        }
    }

    @Test
    fun whiteImageIsAllFF() {
        val frame = FramePacker.pack(MonoImage(Display.WIDTH, Display.HEIGHT))
        assertEquals(4000, frame.size)
        assertTrue(frame.all { it == 0xFF.toByte() })
    }

    @Test
    fun topLeftPixelIsLastRowFirstBit() {
        val image = MonoImage(Display.WIDTH, Display.HEIGHT)
        image[0, 0] = true
        val frame = FramePacker.pack(image)
        assertEquals(0x7F.toByte(), frame[249 * 16])
        assertEquals(1, frame.count { it != 0xFF.toByte() })
    }

    @Test
    fun displayModelIsTheExactInverse() {
        repeat(5) { seed ->
            val image = randomImage(100 + seed)
            val shown = displayedImage(FramePacker.pack(image))
            for (y in 0 until Display.HEIGHT) for (x in 0 until Display.WIDTH) assertEquals(image[x, y], shown[x, y])
        }
    }

    @Test
    fun rotate180TurnsThePictureUpsideDown() {
        val image = MonoImage(Display.WIDTH, Display.HEIGHT)
        image[0, 0] = true
        val shown = displayedImage(FramePacker.pack(image, rotate180 = true))
        assertTrue(shown[249, 121])
        assertEquals(1, shown.blackCount())
    }
}
