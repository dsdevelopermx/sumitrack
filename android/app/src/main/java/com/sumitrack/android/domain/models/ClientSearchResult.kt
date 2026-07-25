package com.sumitrack.android.domain.models

import java.math.BigDecimal

data class ClientSearchResult(
    val id: String,
    val name: String,
    val phone: String,
    val balance: BigDecimal,
)
