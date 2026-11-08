package bayern.kickner.inktap.core

import kotnexlib.ResultOf2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every layout goes through the full chain (layout, frame, panel model) and is then read back with ZXing,
 * an independent decoder. If ZXing reads it from 250 x 122 pixels, a phone camera reads it from the panel.
 */
class LayoutTest {

    private val url = "https://www.kickner.bayern/inktap?id=42"

    @Test
    fun singleQr() = assertEquals(url, decode(renderToPanel(Content.Qr(url))))

    @Test
    fun singleQrWithUmlautsAndEmoji() {
        val text = "Grüße aus Bayern! ÄÖÜß 😀"
        assertEquals(text, decode(renderToPanel(Content.Qr(text))))
    }

    @Test
    fun wifiQr() {
        // The WIFI payload format itself is defined with semicolons.
        val wifi = "WIFI:T:WPA;S:Gast;P:sehr-geheim-123;;"
        assertEquals(wifi, decode(renderToPanel(Content.Qr(wifi))))
    }

    @Test
    fun qrWithNoteHasTextOnTheRight() {
        val panel = renderToPanel(Content.QrWithNote(url, "WLAN\nGast"))
        assertEquals(url, decode(panel, 0, 0, 122, 122))
        assertTrue(blackIn(panel, 126, 0, 124, 122) > 200)
    }

    @Test
    fun qrWithContentShowsTheContent() {
        val panel = renderToPanel(Content.QrWithContent("ABC-123"))
        assertEquals("ABC-123", decode(panel, 0, 0, 122, 122))
        assertTrue(blackIn(panel, 126, 0, 124, 122) > 100)
    }

    @Test
    fun twoQrCodesAreBothReadable() {
        val panel = renderToPanel(Content.TwoQr("links", url))
        assertEquals("links", decode(panel, 0, 0, 125, 122))
        assertEquals(url, decode(panel, 125, 0, 125, 122))
    }

    @Test
    fun longestQrThatStillFits() {
        val text = "x".repeat(213)
        assertEquals(text, decode(renderToPanel(Content.Qr(text))))
        assertEquals(ContentError.QrTooLong, failure(Content.Qr("x".repeat(214))))
    }

    @Test
    fun twoQrCodesHaveTheSameLimit() {
        val text = "y".repeat(213)
        val panel = renderToPanel(Content.TwoQr(text, text))
        assertEquals(text, decode(panel, 0, 0, 125, 122))
    }

    @Test
    fun emptyInputIsAnError() {
        assertEquals(ContentError.Empty, failure(Content.Qr("")))
        assertEquals(ContentError.Empty, failure(Content.QrWithNote(url, "   ")))
        assertEquals(ContentError.Empty, failure(Content.Text("")))
        assertEquals(ContentError.Empty, failure(Content.Barcode("")))
    }

    @Test
    fun textIsDrawnLargeAndCentered() {
        val panel = renderToPanel(Content.Text("Hallo"))
        assertTrue(panel.blackCount() > 2000)
        assertEquals(ContentError.TextTooLong, failure(Content.Text("Wort ".repeat(200))))
    }

    @Test
    fun code128Text() = assertEquals("Kunde-4711", decode(renderToPanel(Content.Barcode("Kunde-4711"))))

    @Test
    fun code128EvenAndOddDigits() {
        assertEquals("12345678", decode(renderToPanel(Content.Barcode("12345678", showValue = false))))
        assertEquals("1234567", decode(renderToPanel(Content.Barcode("1234567", showValue = false))))
    }

    @Test
    fun ean13IsDetectedAndReadable() {
        val ean = "4006381333931"
        assertEquals(BarcodeFormat.EAN_13, assertIs<ResultOf2.Success<BarcodeModules>>(encodeBarcode(ean)).value.format)
        assertEquals(ean, decode(renderToPanel(Content.Barcode(ean))))
    }

    @Test
    fun thirteenDigitsWithWrongCheckDigitBecomeCode128() {
        val value = "4006381333932"
        assertEquals(BarcodeFormat.CODE_128, assertIs<ResultOf2.Success<BarcodeModules>>(encodeBarcode(value)).value.format)
        assertEquals(value, decode(renderToPanel(Content.Barcode(value))))
    }

    @Test
    fun barcodeLimits() {
        assertEquals(ContentError.BarcodeInvalidChars, failure(Content.Barcode("Grüße")))
        val longest = "A".repeat(17)
        assertEquals(longest, decode(renderToPanel(Content.Barcode(longest))))
        assertEquals(ContentError.BarcodeTooLong, failure(Content.Barcode(longest + "A")))
        val digits = "1234567890".repeat(3) + "1234"
        assertEquals(digits, decode(renderToPanel(Content.Barcode(digits))))
        assertEquals(ContentError.BarcodeTooLong, failure(Content.Barcode(digits + "56")))
    }

    @Test
    fun code128PatternsMatchZxing() {
        val field = Class.forName("com.google.zxing.oned.Code128Reader").getDeclaredField("CODE_PATTERNS")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val zxing = (field.get(null) as Array<IntArray>).map { it.joinToString("") }
        assertEquals(zxing.take(106), CODE128)
        assertEquals(CODE128_STOP, zxing[106])
    }

    @Test
    fun rotate180IsStillReadable() = assertEquals(url, decode(renderToPanel(Content.Qr(url), rotate180 = true)))

    @Test
    fun largestFittingFindsTheBoundary() {
        assertEquals(37, largestFitting(10, 80) { it <= 37 })
        assertEquals(80, largestFitting(10, 80) { true })
        assertNull(largestFitting(10, 80) { false })
        assertNotNull(largestFitting(10, 10) { true })
    }

    private fun failure(content: Content, panel: Panel = Panel.EPD_2IN13): ContentError =
        assertIs<ResultOf2.Failure<ContentError>>(render(content, AwtText, panel)).value

    private fun blackIn(img: MonoImage, x: Int, y: Int, w: Int, h: Int): Int {
        var n = 0
        for (yy in y until y + h) for (xx in x until x + w) if (img[xx, yy]) n++
        return n
    }
}
