package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.domain.models.TicketData

class FakeTicketFileWriter : TicketFileWriter {

    var lastFileName: String? = null
        private set
    var uriToReturn: String = "content://fake/ticket.png"
    var throwOnWrite: Throwable? = null

    override fun writeToCacheAndGetUri(ticket: TicketData, fileName: String): String {
        throwOnWrite?.let { throw it }
        lastFileName = fileName
        return uriToReturn
    }
}
