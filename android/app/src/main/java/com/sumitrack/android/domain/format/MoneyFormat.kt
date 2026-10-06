package com.sumitrack.android.domain.format

import java.math.BigDecimal
import java.util.Locale

// Formato único de montos de la app ("$1,250.00", voz de UX): separador de miles, 2 decimales, HALF_UP.
// Locale.US fijo a propósito — con el locale del dispositivo, en es-ES saldría "1.250,00". Reemplaza 13
// copias privadas que no llevaban separador de miles.
fun formatMoney(amount: BigDecimal): String = "$" + String.format(Locale.US, "%,.2f", amount)
