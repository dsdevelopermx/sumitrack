package com.sumitrack.android.data.bluetooth

import com.sumitrack.android.domain.models.TicketData

// Abstrae android.bluetooth (y el renderizado a Bitmap, ver AndroidBluetoothTicketPrinter) —
// mismo patrón que TransactionRunner abstrae Room.withTransaction: permite que el ViewModel sea
// testeable en JVM puro con un fake que solo maneja TicketData/Result, sin tocar android.graphics
// ni un adaptador Bluetooth real.
interface BluetoothTicketPrinter {
    suspend fun printTicket(ticket: TicketData): Result<Unit>
}
