package com.sumitrack.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class SaleStatusTest {

    @Test
    fun `fromString partial returns PARTIAL`() {
        assertEquals(SaleStatus.PARTIAL, SaleStatus.fromString("partial"))
    }

    @Test
    fun `fromString paid returns PAID`() {
        assertEquals(SaleStatus.PAID, SaleStatus.fromString("paid"))
    }

    @Test
    fun `fromString cancelled returns CANCELLED`() {
        assertEquals(SaleStatus.CANCELLED, SaleStatus.fromString("cancelled"))
    }

    @Test
    fun `fromString pending returns PENDING`() {
        assertEquals(SaleStatus.PENDING, SaleStatus.fromString("pending"))
    }

    @Test
    fun `fromString unknown value falls back to PENDING`() {
        assertEquals(SaleStatus.PENDING, SaleStatus.fromString("unknown"))
        assertEquals(SaleStatus.PENDING, SaleStatus.fromString(""))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(SaleStatus.PARTIAL, SaleStatus.fromString("PARTIAL"))
        assertEquals(SaleStatus.PAID, SaleStatus.fromString("PAID"))
        assertEquals(SaleStatus.CANCELLED, SaleStatus.fromString("CANCELLED"))
    }
}
