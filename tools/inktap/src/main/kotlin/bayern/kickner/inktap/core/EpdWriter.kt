package bayern.kickner.inktap.core

import kotnexlib.ResultOfEmpty

/** Why writing to the display failed. Every case means: hold the phone to the display again. */
sealed interface WriteError {
    /** Name of the protocol step that failed, for logs. */
    val step: String

    /** The connection broke, usually because the phone moved or the tag is not a Waveshare display. */
    data class ConnectionLost(override val step: String, val cause: Throwable?) : WriteError

    /** The display answered, but not with "OK" (even after retries). */
    data class Rejected(override val step: String, val answer: String) : WriteError

    /** The image was sent, but the display never reported that the refresh finished. */
    data class RefreshTimeout(override val step: String = "wait for refresh") : WriteError
}

/**
 * Speaks the Waveshare NFC e-paper protocol for the black and white models in [Panel]. The sequence is the same
 * for all of them, only the panel id, the packet size and the number of packets differ.
 *
 * Every command is `0xCD <cmd> [args]` sent as a raw ISO 14443-3A frame, the display answers `00 00` for OK.
 * Sequence and delays come from the official Waveshare Android SDK (`NFC.jar`, class `a`) and match the
 * Proxmark3 implementation (`client/src/cmdhfwaveshare.c`), which retries a command up to 10 times when the
 * answer is not `00 00`. This class does the same.
 *
 * @param transceive Sends one frame and returns the answer. On Android this is `NfcA::transceive`, it throws
 * an `IOException` when the tag is gone.
 * @param sleep Pause in milliseconds, injectable for tests.
 * @param clock Current time in milliseconds, injectable for tests.
 */
class EpdWriter(
    private val transceive: (ByteArray) -> ByteArray,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Writes a frame from [FramePacker.pack] to a display of type [panel] and waits for the refresh.
     *
     * @param onProgress Called with values from `0f` to `1f`.
     */
    fun write(frame: ByteArray, panel: Panel, onProgress: (Float) -> Unit = {}): ResultOfEmpty<WriteError> {
        require(frame.size == panel.frameBytes) { "Frame must have ${panel.frameBytes} bytes for $panel" }

        val setup = listOf(
            Step("init", byteArrayOf(0x0D), 0),
            Step("select panel", byteArrayOf(0x00, panel.panelId), 50),
            Step("normal mode", byteArrayOf(0x01), 20),
            Step("config 1", byteArrayOf(0x02), 20),
            Step("power on", byteArrayOf(0x03), 20),
            Step("config 2", byteArrayOf(0x05), 20),
            Step("load to main", byteArrayOf(0x06), 10),
            Step("data preparation", byteArrayOf(0x07, 0x00), 0),
        )
        for (step in setup) {
            val error = command(step.name, step.payload)
            if (error != null) return ResultOfEmpty.Failure(error)
            if (step.pauseMs > 0) sleep(step.pauseMs)
        }

        val size = panel.packetBytes
        for (row in 0 until panel.packets) {
            val payload = byteArrayOf(0x08, size.toByte()) + frame.copyOfRange(row * size, (row + 1) * size)
            val error = command("data row $row", payload)
            if (error != null) return ResultOfEmpty.Failure(error)
            onProgress((row + 1) * 0.9f / panel.packets)
            sleep(2)
        }

        command("power on 2", byteArrayOf(0x18))?.let { return ResultOfEmpty.Failure(it) }
        sleep(200)
        command("refresh", byteArrayOf(0x09))?.let { return ResultOfEmpty.Failure(it) }
        sleep(300)

        waitUntilReady(panel.refreshTimeoutMs)?.let { return ResultOfEmpty.Failure(it) }
        onProgress(0.98f)
        command("power off", byteArrayOf(0x04))?.let { return ResultOfEmpty.Failure(it) }
        onProgress(1f)
        return ResultOfEmpty.Success
    }

    /** Polls `CD 0A` until the display answers `FF 00` (refresh done) or [timeoutMs] passed. */
    private fun waitUntilReady(timeoutMs: Long): WriteError? {
        val deadline = clock() + timeoutMs
        while (clock() < deadline) {
            val answer = send(byteArrayOf(CMD_PREFIX, 0x0A)).getOrElse {
                return WriteError.ConnectionLost("wait for refresh", it)
            }
            val ready = answer.size >= 2 && answer[0] == 0xFF.toByte() && answer[1] == 0x00.toByte()
            if (ready) return null
            sleep(POLL_INTERVAL_MS)
        }
        return WriteError.RefreshTimeout()
    }

    /** Sends one command, retries while the answer is not `00 00`. Returns null on success. */
    private fun command(name: String, payload: ByteArray): WriteError? {
        var lastAnswer = ByteArray(0)
        repeat(MAX_ATTEMPTS) {
            val answer = send(byteArrayOf(CMD_PREFIX) + payload).getOrElse {
                return WriteError.ConnectionLost(name, it)
            }
            val ok = answer.size >= 2 && answer[0] == 0.toByte() && answer[1] == 0.toByte()
            if (ok) return null
            lastAnswer = answer
        }
        return WriteError.Rejected(name, lastAnswer.toHexString())
    }

    private fun send(frame: ByteArray): Result<ByteArray> = runCatching { transceive(frame) }

    private class Step(val name: String, val payload: ByteArray, val pauseMs: Long)

    private companion object {
        const val CMD_PREFIX = 0xCD.toByte()
        const val MAX_ATTEMPTS = 10
        const val POLL_INTERVAL_MS = 100L
    }
}
