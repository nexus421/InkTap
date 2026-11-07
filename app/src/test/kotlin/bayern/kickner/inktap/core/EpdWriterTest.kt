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
    val failOn: (ByteArray, Int) -> ByteArray? = { _, _ -> null },
) {
    val sent = mutableListOf<ByteArray>()
    val received = ByteArray(4000)
    private var rows = 0
    private var polls = 0

    fun transceive(cmd: ByteArray): ByteArray {
        sent += cmd
        failOn(cmd, sent.size)?.let { return it }
        if (cmd[1] == 0x08.toByte()) {
            cmd.copyInto(received, rows * 16, 3, 19)
            rows++
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
        val result = EpdWriter(display::transceive, sleep = { sleeps += it }).write(frame) { progress += it }

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
        val result = EpdWriter(display::transceive, sleep = {}).write(frame)
        assertEquals(ResultOfEmpty.Success, result)
        assertContentEquals(frame, display.received)
        assertEquals(8 + 250 + 7 + 2, display.sent.size)
    }

    @Test
    fun givesUpAfterTenRejections() {
        val display = FakeDisplay(failOn = { cmd, _ -> if (cmd[1] == 0x03.toByte()) byteArrayOf(1, 2) else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame)
        val error = assertIs<ResultOfEmpty.Failure<WriteError>>(result).value
        assertEquals(WriteError.Rejected("power on", "0102"), error)
        assertEquals(10, display.sent.count { it[1] == 0x03.toByte() })
    }

    @Test
    fun lostTagIsReportedNotThrown() {
        val display = FakeDisplay(failOn = { _, n -> if (n == 100) throw IOException("Tag was lost.") else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame)
        val error = assertIs<WriteError.ConnectionLost>(assertIs<ResultOfEmpty.Failure<WriteError>>(result).value)
        assertEquals("data row 91", error.step)
    }

    @Test
    fun refreshTimeoutAfterTenSeconds() {
        var now = 0L
        val display = FakeDisplay(busyPolls = Int.MAX_VALUE)
        val writer = EpdWriter(display::transceive, sleep = { now += it }, clock = { now })
        val result = writer.write(frame)
        assertIs<WriteError.RefreshTimeout>(assertIs<ResultOfEmpty.Failure<WriteError>>(result).value)
    }
}
