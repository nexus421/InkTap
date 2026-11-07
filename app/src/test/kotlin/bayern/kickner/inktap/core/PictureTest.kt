package bayern.kickner.inktap.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PictureTest {

    @Test
    fun cropFillsTheDisplay() {
        // A 4:3 photo is wider than tall but less wide than the display, so it is cut at top and bottom.
        assertEquals(PixelRect(0, -33, 250, 188), fitInto(4000, 3000, crop = true))
    }

    @Test
    fun fitShowsEverything() = assertEquals(PixelRect(84, 0, 81, 122), fitInto(3000, 4500, crop = false))

    @Test
    fun ditherKeepsMiddleGray() {
        val gray = GrayImage(Display.WIDTH, Display.HEIGHT, IntArray(Display.WIDTH * Display.HEIGHT) { 128 })
        val share = gray.toMonoImage(dither = true).blackCount().toDouble() / (Display.WIDTH * Display.HEIGHT)
        assertTrue(share in 0.45..0.55, "black share was $share")
    }

    @Test
    fun thresholdIsCrisp() {
        val luma = IntArray(Display.WIDTH * Display.HEIGHT) { if (it % Display.WIDTH < 125) 30 else 220 }
        val mono = GrayImage(Display.WIDTH, Display.HEIGHT, luma).toMonoImage(dither = false)
        assertEquals(125 * Display.HEIGHT, mono.blackCount())
    }

    @Test
    fun lumaOfKnownColors() {
        assertEquals(0, lumaOf(0xFF000000.toInt()))
        assertEquals(255, lumaOf(0xFFFFFFFF.toInt()))
        assertEquals(76, lumaOf(0xFFFF0000.toInt()))
        assertEquals(255, lumaOf(0x00000000))
    }

    @Test
    fun previewColors() {
        val image = MonoImage(2, 1)
        image[0, 0] = true
        val argb = image.toArgb(ink = 1, paper = 2)
        assertEquals(listOf(1, 2), argb.toList())
    }

    @Test
    fun testPatternHasAllFourEdgesAndTextAtTheTop() {
        val panel = renderToPanel(Content.TestPattern)
        for (x in 0 until Display.WIDTH) assertTrue(panel[x, 0] && panel[x, Display.HEIGHT - 1])
        for (y in 0 until Display.HEIGHT) assertTrue(panel[0, y] && panel[Display.WIDTH - 1, y])
        var top = 0
        var bottom = 0
        for (y in 2 until 60) for (x in 2 until Display.WIDTH - 2) if (panel[x, y]) top++
        for (y in 62 until Display.HEIGHT - 2) for (x in 2 until Display.WIDTH - 2) if (panel[x, y]) bottom++
        assertTrue(top > 300 && bottom == 0, "top=$top bottom=$bottom")
    }

    @Test
    fun pictureLayoutUsesTheWholeDisplay() {
        val luma = IntArray(Display.WIDTH * Display.HEIGHT) { 0 }
        val panel = renderToPanel(Content.Picture(GrayImage(Display.WIDTH, Display.HEIGHT, luma), dither = false))
        assertEquals(Display.WIDTH * Display.HEIGHT, panel.blackCount())
    }
}
