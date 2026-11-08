package org.devicelink.core.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ControlFrameStreamTest {
    private fun message(id: String) = ControlMessage(1, id, "dl1:test", MessageKind.EVENT, byteArrayOf(1, 2, 3))

    @Test fun readsMultipleFramesAndCleanEof() {
        val out = ByteArrayOutputStream()
        ControlFrameStream.writeFrame(out, message("one"))
        ControlFrameStream.writeFrame(out, message("two"))
        val input = ByteArrayInputStream(out.toByteArray())
        val first = ControlMessageCodec.decode(ControlFrameStream.readFrame(input)!!)
        val second = ControlMessageCodec.decode(ControlFrameStream.readFrame(input)!!)
        assertEquals("one", (first as DecodeResult.Success).message.messageId)
        assertEquals("two", (second as DecodeResult.Success).message.messageId)
        assertNull(ControlFrameStream.readFrame(input))
    }

    @Test fun handlesFragmentedReads() {
        val out = ByteArrayOutputStream()
        ControlFrameStream.writeFrame(out, message("fragmented"))
        val source = ByteArrayInputStream(out.toByteArray())
        val fragmented = object : InputStream() {
            override fun read(): Int = source.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, minOf(len, 2))
        }
        val decoded = ControlMessageCodec.decode(ControlFrameStream.readFrame(fragmented)!!)
        assertEquals("fragmented", (decoded as DecodeResult.Success).message.messageId)
    }

    @Test fun rejectsTruncatedPrefixAndBody() {
        assertThrows(EOFException::class.java) { ControlFrameStream.readFrame(ByteArrayInputStream(byteArrayOf(0, 0))) }
        assertThrows(EOFException::class.java) { ControlFrameStream.readFrame(ByteArrayInputStream(byteArrayOf(0, 0, 0, 18, 1, 2))) }
    }

    @Test fun rejectsInvalidLengthsBeforeBodyAllocation() {
        assertThrows(IllegalArgumentException::class.java) { ControlFrameStream.readFrame(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0))) }
        val huge = byteArrayOf(0x7f, -1, -1, -1)
        assertThrows(IllegalArgumentException::class.java) { ControlFrameStream.readFrame(ByteArrayInputStream(huge)) }
    }
}
