package com.sumitrack.android.data.local.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class BigDecimalConverterTest {

    private val converter = BigDecimalConverter()

    @Test
    fun `fromBigDecimal returns plain string without scientific notation`() {
        val value = BigDecimal("1234567890.123456")
        val result = converter.fromBigDecimal(value)
        assertEquals("1234567890.123456", result)
    }

    @Test
    fun `fromBigDecimal returns null for null input`() {
        assertNull(converter.fromBigDecimal(null))
    }

    @Test
    fun `toBigDecimal restores original value`() {
        val original = BigDecimal("99999.999999")
        val stored = converter.fromBigDecimal(original)
        val restored = converter.toBigDecimal(stored)
        assertEquals(0, original.compareTo(restored))
    }

    @Test
    fun `toBigDecimal returns null for null input`() {
        assertNull(converter.toBigDecimal(null))
    }

    @Test
    fun `round trip preserves precision for monetary amounts`() {
        val amount = BigDecimal("12345.678900")
        val restored = converter.toBigDecimal(converter.fromBigDecimal(amount))
        assertEquals(0, amount.compareTo(restored))
    }

    @Test
    fun `fromBigDecimal avoids scientific notation for large values`() {
        val large = BigDecimal("1000000.000000")
        val result = converter.fromBigDecimal(large)
        assert(result?.contains('E') == false) { "Should not contain scientific notation: $result" }
    }
}
