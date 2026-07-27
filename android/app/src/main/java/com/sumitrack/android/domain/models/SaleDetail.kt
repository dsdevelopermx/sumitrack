package com.sumitrack.android.domain.models

data class SaleDetail(
    val sale: Sale,
    val items: List<SaleItem>,
    val payments: List<Payment>,
    val installments: List<Installment>,
)
