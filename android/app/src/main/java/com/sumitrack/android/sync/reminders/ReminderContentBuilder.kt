package com.sumitrack.android.sync.reminders

import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import java.math.RoundingMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReminderContent(val title: String, val text: String)

// Lógica pura (sin android.*) — decide SI notificar y con qué texto. El worker solo hace de pegamento
// con Room y NotificationManager. Formato de monto/fecha igual a OrderDetailScreen.
object ReminderContentBuilder {

    private const val GENERIC_TITLE = "Cobro próximo a vencer"
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX"))

    // Devuelve null (no notificar) si la parcialidad ya no está pendiente o su venta ya se cerró:
    // defensa en profundidad para AC-3 si un recordatorio ya encolado llega a ejecutarse. Cliente o
    // venta ausentes (p. ej. el pull aún no las trajo) NO cancelan el aviso — cae al título genérico.
    fun build(
        installment: InstallmentEntity?,
        sale: SaleEntity?,
        clientName: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReminderContent? {
        if (installment == null || installment.status != "pending") return null
        if (sale?.status == "cancelled" || sale?.status == "paid") return null

        val amount = "$${installment.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
        val dueDate = dateFormatter.format(installment.dueDate.atZone(zone))
        return ReminderContent(
            title = clientName?.takeIf { it.isNotBlank() } ?: GENERIC_TITLE,
            text = "$amount · vence el $dueDate",
        )
    }
}
