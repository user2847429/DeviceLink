package org.devicelink.core.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Four-byte big-endian length framing for codec frames. This is not encryption or authentication. */
object ControlFrameStream {
    const val MIN_FRAME_BYTES = 18

    /** Returns null only for clean EOF before the first prefix byte. Truncation throws EOFException. */
    fun readFrame(input: InputStream): ByteArray? {
        val data = DataInputStream(input)
        val first = data.read()
        if (first == -1) return null
        val tail = ByteArray(3)
        data.readFully(tail)
        val length = ((first.toLong() and 255) shl 24) or
            ((tail[0].toLong() and 255) shl 16) or
            ((tail[1].toLong() and 255) shl 8) or (tail[2].toLong() and 255)
        require(length in MIN_FRAME_BYTES.toLong()..ControlMessageCodec.MAX_FRAME_BYTES.toLong()) {
            "Control frame length is outside the permitted range."
        }
        return ByteArray(length.toInt()).also(data::readFully)
    }

    /** Writes one frame. The caller owns stream flushing and lifecycle. */
    fun writeFrame(output: OutputStream, message: ControlMessage) {
        val frame = ControlMessageCodec.encode(message)
        DataOutputStream(output).apply { writeInt(frame.size); write(frame) }
    }
}
