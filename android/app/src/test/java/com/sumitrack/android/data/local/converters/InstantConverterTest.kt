package com.sumitrack.android.data.local.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class InstantConverterTest {

    private val converter = InstantConverter()

    @Test
    fun `fromInstant converts to epoch millis`() {
        val instant = Instant.ofEpochMilli(1_000_000_000L)
        assertEquals(1_000_000_000L, converter.fromInstant(instant))
    }

    @Test
    fun `fromInstant returns null for null input`() {
        assertNull(converter.fromInstant(null))
    }

    @Test
    fun `toInstant restores original value`() {
        val original = Instant.ofEpochMilli(1_719_000_000_000L)
        val millis = converter.fromInstant(original)
        assertEquals(original, converter.toInstant(millis))
    }

    @Test
    fun `toInstant returns null for null input`() {
        assertNull(converter.toInstant(null))
    }

    @Test
    fun `round trip preserves millisecond precision`() {
        val instant = Instant.parse("2026-06-28T12:34:56.789Z")
        val restored = converter.toInstant(converter.fromInstant(instant))
        assertEquals(instant, restored)
    }
}
