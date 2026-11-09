package bayern.kickner.inktap.core

import kotnexlib.ResultOf2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The same chain as [LayoutTest] (layout, frame, panel model, ZXing) for the 4.2 inch panel. */
class LayoutTest4in2 {

    private val panel = Panel.EPD_4IN2
    private val url = "https://www.kickner.bayern/inktap?id=42"

    private fun shown(content: Content, rotate180: Boolean = false) = renderToPanel(content, rotate180, panel)

    @Test
    fun singleQrIsReadableAndUsesTheFullHeight() {
        val image = shown(Content.Qr(url))
        assertEquals(url, decode(image))
        assertEquals(0, blackIn(image, 0, 0, 50, 300))
        assertEquals(0, blackIn(image, 350, 0, 50, 300))
    }

    @Test
    fun qrWithNoteKeepsRoomForTheText() {
        val image = shown(Content.QrWithNote(url, "Gäste-WLAN scannen"))
        assertEquals(url, decode(image, 0, 40, 220, 220))
        assertTrue(blackIn(image, 230, 0, 170, 300) > 500)
    }

    @Test
    fun twoQrCodesAreBothReadable() {
        val image = shown(Content.TwoQr("links", url))
        assertEquals("links", decode(image, 0, 0, 200, 300))
        assertEquals(url, decode(image, 200, 0, 200, 300))
    }

    @Test
    fun muchLongerQrContentFits() {
        // 214 characters are already too long for the 2.13 inch panel. qrcode-kotlin jumps to version 40 at
        // 1000 characters, so 900 is the round number that still fits with 2 pixel modules.
        val text = "x".repeat(900)
        assertEquals(text, decode(shown(Content.Qr(text))))
    }

    @Test
    fun barcodesAreReadable() {
        assertEquals("4006381333931", decode(shown(Content.Barcode("4006381333931"))))
        val longer = "A".repeat(25)
        assertEquals(longer, decode(shown(Content.Barcode(longer))))
    }

    @Test
    fun rotate180IsStillReadable() = assertEquals(url, decode(shown(Content.Qr(url), rotate180 = true)))

    @Test
    fun testPatternHasAllFourEdgesAndTextAtTheTop() {
        val image = shown(Content.TestPattern)
        for (x in 0 until 400) assertTrue(image[x, 0] && image[x, 299])
        for (y in 0 until 300) assertTrue(image[0, y] && image[399, y])
        assertTrue(blackIn(image, 2, 2, 396, 108) > 1000)
        assertEquals(0, blackIn(image, 2, 115, 396, 183))
    }

    @Test
    fun pictureFillsTheWholePanel() {
        val gray = GrayImage(400, 300, IntArray(400 * 300) { 0 })
        assertEquals(400 * 300, shown(Content.Picture(gray, dither = false)).blackCount())
    }

    @Test
    fun cropAndFitUseThePanelSize() {
        assertEquals(PixelRect(0, 0, 400, 300), fitInto(4000, 3000, crop = true, dstW = 400, dstH = 300))
        assertEquals(PixelRect(100, 0, 200, 300), fitInto(3000, 4500, crop = false, dstW = 400, dstH = 300))
    }

    @Test
    fun twoThirteenLayoutsAreUnchanged() {
        // The metrics must reproduce the original 2.13 inch constants, so this is the old image pixel by pixel.
        val image = assertIs<ResultOf2.Success<MonoImage>>(render(Content.Barcode("Kunde-4711"), AwtText, Panel.EPD_2IN13)).value
        assertEquals(250, image.width)
        assertEquals(0, blackIn(image, 0, 0, 250, 4))
        assertTrue(blackIn(image, 0, 4, 250, 92) > 0)
    }

    @Test
    fun unknownStoredPanelFallsBackTo2in13() {
        assertEquals(Panel.EPD_4IN2, Panel.fromName("EPD_4IN2"))
        assertEquals(Panel.EPD_2IN13, Panel.fromName("EPD_7IN5"))
        assertEquals(Panel.EPD_2IN13, Panel.fromName(null))
    }

    private fun blackIn(img: MonoImage, x: Int, y: Int, w: Int, h: Int): Int {
        var n = 0
        for (yy in y until y + h) for (xx in x until x + w) if (img[xx, yy]) n++
        return n
    }
}
