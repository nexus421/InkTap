package bayern.kickner.inktap.core

import kotnexlib.ResultOfEmpty
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A simulated display. It answers `00 00` to everything, reports "refresh done" (`FF 00`) on the
 * [busyPolls]+1-th `CD 0A` and stores every row it receives, so the frame can be compared afterwards.
 */
private class FakeDisplay(
    val busyPolls: Int = 3,
    frameBytes: Int = Panel.EPD_2IN13.frameBytes,
    val failOn: (ByteArray, Int) -> ByteArray? = { _, _ -> null },
) {
    val sent = mutableListOf<ByteArray>()
    val received = ByteArray(frameBytes)
    private var offset = 0
    private var polls = 0

    fun transceive(cmd: ByteArray): ByteArray {
        sent += cmd
        failOn(cmd, sent.size)?.let { return it }
        if (cmd[1] == 0x08.toByte()) {
            cmd.copyInto(received, offset, 3, cmd.size)
            offset += cmd.size - 3
        }
        if (cmd[1] == 0x0A.toByte()) {
            polls++
            return if (polls > busyPolls) byteArrayOf(-1, 0) else byteArrayOf(0, 0)
        }
        return byteArrayOf(0, 0)
    }
}

class EpdWriterTest {

    private val frame = ByteArray(4000) { (it * 7).toByte() }

    private fun hex(cmd: ByteArray) = cmd.joinToString(" ") { "%02X".format(it) }

    @Test
    fun sendsTheSdkSequence() {
        val display = FakeDisplay()
        val sleeps = mutableListOf<Long>()
        val progress = mutableListOf<Float>()
        val result = EpdWriter(display::transceive, sleep = { sleeps += it }).write(frame, Panel.EPD_2IN13) { progress += it }

        assertEquals(ResultOfEmpty.Success, result)
        val heads = display.sent.map { hex(it.copyOf(minOf(it.size, 3))) }
        assertEquals(listOf("CD 0D", "CD 00 04", "CD 01", "CD 02", "CD 03", "CD 05", "CD 06", "CD 07 00"), heads.take(8))
        assertTrue(heads.drop(8).take(250).all { it == "CD 08 10" })
        assertTrue(display.sent.drop(8).take(250).all { it.size == 19 })
        assertEquals(listOf("CD 18", "CD 09", "CD 0A", "CD 0A", "CD 0A", "CD 0A", "CD 04"), heads.drop(258))
        assertContentEquals(frame, display.received)
        assertEquals(listOf(50L, 20L, 20L, 20L, 20L, 10L), sleeps.take(6))
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun retriesARejectedCommand() {
        val display = FakeDisplay(failOn = { _, n -> if (n == 20 || n == 21) byteArrayOf(1, 0) else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame, Panel.EPD_2IN13)
        assertEquals(ResultOfEmpty.Success, result)
        assertContentEquals(frame, display.received)
        assertEquals(8 + 250 + 7 + 2, display.sent.size)
    }

    @Test
    fun givesUpAfterTenRejections() {
        val display = FakeDisplay(failOn = { cmd, _ -> if (cmd[1] == 0x03.toByte()) byteArrayOf(1, 2) else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame, Panel.EPD_2IN13)
        val error = assertIs<ResultOfEmpty.Failure<WriteError>>(result).value
        assertEquals(WriteError.Rejected("power on", "0102"), error)
        assertEquals(10, display.sent.count { it[1] == 0x03.toByte() })
    }

    @Test
    fun lostTagIsReportedNotThrown() {
        val display = FakeDisplay(failOn = { _, n -> if (n == 100) throw IOException("Tag was lost.") else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame, Panel.EPD_2IN13)
        val error = assertIs<WriteError.ConnectionLost>(assertIs<ResultOfEmpty.Failure<WriteError>>(result).value)
        assertEquals("data row 91", error.step)
    }

    @Test
    fun refreshTimeoutAfterTenSeconds() {
        var now = 0L
        val display = FakeDisplay(busyPolls = Int.MAX_VALUE)
        val writer = EpdWriter(display::transceive, sleep = { now += it }, clock = { now })
        val result = writer.write(frame, Panel.EPD_2IN13)
        assertIs<WriteError.RefreshTimeout>(assertIs<ResultOfEmpty.Failure<WriteError>>(result).value)
    }

    @Test
    fun sendsTheSdkSequenceFor4in2() {
        val panel = Panel.EPD_4IN2
        val frame = ByteArray(panel.frameBytes) { (it * 13).toByte() }
        val display = FakeDisplay(frameBytes = panel.frameBytes)
        val result = EpdWriter(display::transceive, sleep = {}).write(frame, panel)

        assertEquals(ResultOfEmpty.Success, result)
        val heads = display.sent.map { hex(it.copyOf(minOf(it.size, 3))) }
        assertEquals(listOf("CD 0D", "CD 00 0A", "CD 01", "CD 02", "CD 03", "CD 05", "CD 06", "CD 07 00"), heads.take(8))
        assertTrue(heads.drop(8).take(150).all { it == "CD 08 64" })
        assertTrue(display.sent.drop(8).take(150).all { it.size == 103 })
        assertEquals(listOf("CD 18", "CD 09", "CD 0A", "CD 0A", "CD 0A", "CD 0A", "CD 04"), heads.drop(158))
        assertContentEquals(frame, display.received)
    }

    @Test
    fun refreshTimeoutFor4in2AfterTwentySeconds() {
        var now = 0L
        val panel = Panel.EPD_4IN2
        val display = FakeDisplay(busyPolls = 150, frameBytes = panel.frameBytes)
        // 150 busy polls at 100 ms take 15 s: too long for the 2.13 inch, fine for the slower 4.2 inch.
        val result = EpdWriter(display::transceive, sleep = { now += it }, clock = { now }).write(ByteArray(panel.frameBytes), panel)
        assertEquals(ResultOfEmpty.Success, result)
        var now2 = 0L
        val slow = FakeDisplay(busyPolls = Int.MAX_VALUE, frameBytes = panel.frameBytes)
        val timeout = EpdWriter(slow::transceive, sleep = { now2 += it }, clock = { now2 }).write(ByteArray(panel.frameBytes), panel)
        assertIs<WriteError.RefreshTimeout>(assertIs<ResultOfEmpty.Failure<WriteError>>(timeout).value)
        assertTrue(slow.sent.count { it[1] == 0x0A.toByte() } in 195..205)
    }

    @Test
    fun wrongFrameSizeIsRejected() {
        val error = runCatching { EpdWriter({ byteArrayOf(0, 0) }, sleep = {}).write(ByteArray(4000), Panel.EPD_4IN2) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(error)
    }
}
