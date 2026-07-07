package com.sumitrack.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusTest {

    @Test
    fun `fromString synced returns SYNCED`() {
        assertEquals(SyncStatus.SYNCED, SyncStatus.fromString("synced"))
    }

    @Test
    fun `fromString conflict returns CONFLICT`() {
        assertEquals(SyncStatus.CONFLICT, SyncStatus.fromString("conflict"))
    }

    @Test
    fun `fromString pending returns PENDING`() {
        assertEquals(SyncStatus.PENDING, SyncStatus.fromString("pending"))
    }

    @Test
    fun `fromString unknown value falls back to PENDING`() {
        assertEquals(SyncStatus.PENDING, SyncStatus.fromString("unknown"))
        assertEquals(SyncStatus.PENDING, SyncStatus.fromString(""))
        assertEquals(SyncStatus.PENDING, SyncStatus.fromString("error"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(SyncStatus.SYNCED, SyncStatus.fromString("SYNCED"))
        assertEquals(SyncStatus.CONFLICT, SyncStatus.fromString("CONFLICT"))
        assertEquals(SyncStatus.PENDING, SyncStatus.fromString("PENDING"))
    }
}
