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
        val image = MonoImage(Panel.EPD_2IN13.width, Panel.EPD_2IN13.height)
        for (y in 0 until Panel.EPD_2IN13.height) for (x in 0 until Panel.EPD_2IN13.width) image[x, y] = random.nextBoolean()
        return image
    }

    @Test
    fun matchesTheWaveshareSdkPipeline() {
        repeat(20) { seed ->
            val image = randomImage(seed)
            assertContentEquals(sdkPipeline(image), FramePacker.pack(image, Panel.EPD_2IN13))
        }
    }

    @Test
    fun whiteImageIsAllFF() {
        val frame = FramePacker.pack(MonoImage(Panel.EPD_2IN13.width, Panel.EPD_2IN13.height), Panel.EPD_2IN13)
        assertEquals(4000, frame.size)
        assertTrue(frame.all { it == 0xFF.toByte() })
    }

    @Test
    fun topLeftPixelIsLastRowFirstBit() {
        val image = MonoImage(Panel.EPD_2IN13.width, Panel.EPD_2IN13.height)
        image[0, 0] = true
        val frame = FramePacker.pack(image, Panel.EPD_2IN13)
        assertEquals(0x7F.toByte(), frame[249 * 16])
        assertEquals(1, frame.count { it != 0xFF.toByte() })
    }

    @Test
    fun displayModelIsTheExactInverse() {
        repeat(5) { seed ->
            val image = randomImage(100 + seed)
            val shown = displayedImage(FramePacker.pack(image, Panel.EPD_2IN13))
            for (y in 0 until Panel.EPD_2IN13.height) for (x in 0 until Panel.EPD_2IN13.width) assertEquals(image[x, y], shown[x, y])
        }
    }

    @Test
    fun rotate180TurnsThePictureUpsideDown() {
        val image = MonoImage(Panel.EPD_2IN13.width, Panel.EPD_2IN13.height)
        image[0, 0] = true
        val shown = displayedImage(FramePacker.pack(image, Panel.EPD_2IN13, rotate180 = true))
        assertTrue(shown[249, 121])
        assertEquals(1, shown.blackCount())
    }

    /**
     * The SDK branch for the 4.2 inch (type 3 in `NFC.jar`): no rotation, the 400 x 300 ARGB pixels are packed
     * in their natural order, 8 per byte, MSB first, `(pixel and 0xFF) > 128` meaning 1 (white).
     */
    private fun sdkFlatPipeline(image: MonoImage): ByteArray {
        val pixels = IntArray(image.width * image.height) { i ->
            if (image[i % image.width, i / image.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val out = ByteArray(pixels.size / 8)
        for (k in out.indices) {
            var data = 0
            for (bit in 0 until 8) {
                data = (data shl 1) and 0xFF
                if (pixels[k * 8 + bit] and 0xFF > 128) data = data or 1
            }
            out[k] = data.toByte()
        }
        return out
    }

    private fun randomImage(seed: Int, panel: Panel): MonoImage {
        val random = Random(seed)
        val image = MonoImage(panel.width, panel.height)
        for (y in 0 until panel.height) for (x in 0 until panel.width) image[x, y] = random.nextBoolean()
        return image
    }

    @Test
    fun matchesTheSdkPipelineFor4in2() {
        repeat(5) { seed ->
            val image = randomImage(seed, Panel.EPD_4IN2)
            assertContentEquals(sdkFlatPipeline(image), FramePacker.pack(image, Panel.EPD_4IN2))
        }
    }

    @Test
    fun fourTwoFrameHasFifteenThousandBytesAndTopLeftIsFirstBit() {
        val image = MonoImage(400, 300)
        image[0, 0] = true
        val frame = FramePacker.pack(image, Panel.EPD_4IN2)
        assertEquals(15_000, frame.size)
        assertEquals(0x7F.toByte(), frame[0])
        assertEquals(1, frame.count { it != 0xFF.toByte() })
    }

    @Test
    fun fourTwoDisplayModelIsTheExactInverseAlsoRotated() {
        val image = randomImage(7, Panel.EPD_4IN2)
        val shown = displayedImage(FramePacker.pack(image, Panel.EPD_4IN2, rotate180 = true), Panel.EPD_4IN2)
        for (y in 0 until 300) for (x in 0 until 400) assertEquals(image[399 - x, 299 - y], shown[x, y])
    }

    @Test
    fun imageOfTheWrongSizeIsRejected() {
        val error = runCatching { FramePacker.pack(MonoImage(250, 122), Panel.EPD_4IN2) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
