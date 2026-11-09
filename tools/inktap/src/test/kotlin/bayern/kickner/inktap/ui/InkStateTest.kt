package bayern.kickner.inktap.ui

import bayern.kickner.inktap.core.Content
import bayern.kickner.inktap.ui.screens.ink.InkState
import bayern.kickner.inktap.ui.screens.ink.Mode
import bayern.kickner.inktap.ui.screens.ink.WriteGate
import bayern.kickner.inktap.ui.screens.ink.toContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InkStateTest {

    @Test
    fun qrModeMapsToQr() = assertEquals(Content.Qr("a"), InkState(mode = Mode.QR, qr = "a").toContent(null))

    @Test
    fun everyModeMapsToItsContent() {
        val state = InkState(qr = "q", qr2 = "r", note = "n", text = "t", barcode = "b", showBarcodeValue = false)
        assertEquals(Content.QrWithNote("q", "n"), state.copy(mode = Mode.QR_NOTE).toContent(null))
        assertEquals(Content.QrWithContent("q"), state.copy(mode = Mode.QR_CONTENT).toContent(null))
        assertEquals(Content.TwoQr("q", "r"), state.copy(mode = Mode.TWO_QR).toContent(null))
        assertEquals(Content.Text("t"), state.copy(mode = Mode.TEXT).toContent(null))
        assertEquals(Content.Barcode("b", showValue = false), state.copy(mode = Mode.BARCODE).toContent(null))
    }

    @Test
    fun pictureWithoutBitmapIsNull() = assertNull(InkState(mode = Mode.PICTURE).toContent(null))

    @Test
    fun testPatternWins() =
        assertEquals(Content.TestPattern, InkState(mode = Mode.TEXT, testPattern = true).toContent(null))

    @Test
    fun secondTagWhileWritingIsIgnored() {
        val gate = WriteGate()
        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        gate.leave()
        assertTrue(gate.tryEnter())
    }
}
