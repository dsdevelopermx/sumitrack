package com.sumitrack.android.ui.screens.products

import com.sumitrack.android.data.local.TransactionRunner

class FakeTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
