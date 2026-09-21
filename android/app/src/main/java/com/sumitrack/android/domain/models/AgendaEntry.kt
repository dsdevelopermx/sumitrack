package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

// Un cobro programado (parcialidad pendiente) de la Agenda de Cobros (S-10, Historia 5.3).
data class AgendaEntry(
    val installmentId: String,
    val saleId: String,
    val folio: String,
    val clientName: String,
    val amount: BigDecimal,
    val dueDate: Instant,
)
