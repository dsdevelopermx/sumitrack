package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.bluetooth.BluetoothTicketPrinter
import com.sumitrack.android.domain.models.TicketData

class FakeBluetoothTicketPrinter : BluetoothTicketPrinter {

    var result: Result<Unit> = Result.success(Unit)
    var printCallCount = 0
        private set

    override suspend fun printTicket(ticket: TicketData): Result<Unit> {
        printCallCount++
        return result
    }
}
