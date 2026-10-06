package com.sumitrack.android.domain.format

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun `agrega separador de miles y dos decimales`() {
        assertEquals("$1,250.00", formatMoney(BigDecimal("1250")))
        assertEquals("$1,234,567.89", formatMoney(BigDecimal("1234567.89")))
    }

    @Test
    fun `montos menores a mil no llevan separador`() {
        assertEquals("$0.00", formatMoney(BigDecimal.ZERO))
        assertEquals("$999.99", formatMoney(BigDecimal("999.99")))
    }

    @Test
    fun `redondea a dos decimales con half up`() {
        assertEquals("$10.01", formatMoney(BigDecimal("10.005")))
        assertEquals("$10.00", formatMoney(BigDecimal("10.004")))
    }

    @Test
    fun `completa decimales faltantes`() {
        assertEquals("$300.50", formatMoney(BigDecimal("300.5")))
    }

    @Test
    fun `no depende del locale del dispositivo`() {
        val previous = Locale.getDefault()
        try {
            // En es-ES el separador de miles es "." y el decimal ","; el formato de la app es fijo.
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals("$1,250.00", formatMoney(BigDecimal("1250")))
            Locale.setDefault(Locale.forLanguageTag("de-DE"))
            assertEquals("$1,250.00", formatMoney(BigDecimal("1250")))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `montos negativos conservan el signo del formato anterior`() {
        assertEquals("$-1,250.00", formatMoney(BigDecimal("-1250")))
    }
}
