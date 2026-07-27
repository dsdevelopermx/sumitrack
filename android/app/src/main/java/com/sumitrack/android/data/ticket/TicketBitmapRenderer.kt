package com.sumitrack.android.data.ticket

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.sumitrack.android.domain.models.TicketData
import com.sumitrack.android.domain.models.TicketPaymentCondition
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TICKET_WIDTH_PX = 384
private const val LINE_HEIGHT_PX = 28
private const val MARGIN_PX = 12

// Ancho fijo 384px = 58mm a 203dpi, el tamaño más común en impresoras térmicas portátiles de bajo
// costo (ver Dev Notes de la historia). Sin Compose/captureToImage: requeriría un árbol ya
// compuesto e infraestructura de test instrumentado que este proyecto no tiene.
//
// Vive en data/ticket (no en ui/) porque tanto AndroidBluetoothTicketPrinter como
// AndroidTicketFileWriter (ambos en la capa data) lo necesitan para renderizar el Bitmap antes de
// imprimir/escribir — así evitan que PaymentViewModel toque android.graphics directamente,
// dejando esa capa 100% testeable en JVM puro con fakes que solo manejan TicketData/Uri/Result.
fun renderTicketBitmap(ticket: TicketData): Bitmap {
    val lines = buildTicketLines(ticket)
    val height = MARGIN_PX * 2 + lines.size * LINE_HEIGHT_PX
    val bitmap = Bitmap.createBitmap(TICKET_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.MONOSPACE
        textSize = 20f
    }
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, MARGIN_PX.toFloat(), MARGIN_PX + (index + 1) * LINE_HEIGHT_PX.toFloat(), paint)
    }
    return bitmap
}

// Separada de renderTicketBitmap para que su contenido textual (qué línea, en qué orden) sea
// testeable en JVM puro — la única parte de este archivo que no depende de android.graphics.
// También la usa TicketSheet.kt (ui/) para la vista previa en texto, sin tocar el Bitmap.
fun buildTicketLines(ticket: TicketData): List<String> {
    val lines = mutableListOf<String>()
    lines += ticket.fiscal.businessName.ifBlank { "(Sin datos fiscales configurados)" }
    if (ticket.fiscal.rfc.isNotBlank()) lines += "RFC: ${ticket.fiscal.rfc}"
    if (ticket.fiscal.address.isNotBlank()) lines += ticket.fiscal.address
    if (ticket.fiscal.phone.isNotBlank()) lines += "Tel: ${ticket.fiscal.phone}"
    lines += "Folio: ${ticket.folio}"
    lines += "Cliente: ${ticket.clientName.ifBlank { "(sin nombre)" }}"
    lines += "--------------------------------"
    ticket.lineItems.forEach { item ->
        lines += "${item.description} x${item.quantity}"
        lines += "  ${formatTicketAmount(item.unitPrice)} c/u = ${formatTicketAmount(item.subtotal)}"
    }
    lines += "--------------------------------"
    lines += "Subtotal: ${formatTicketAmount(ticket.subtotal)}"
    lines += "Impuestos: ${formatTicketAmount(ticket.tax)}"
    lines += "Total: ${formatTicketAmount(ticket.total)}"
    when (val condition = ticket.paymentCondition) {
        is TicketPaymentCondition.SinglePayment -> lines += "Pago de contado"
        is TicketPaymentCondition.InstallmentPlan -> {
            lines += "Parcialidades:"
            condition.installments.forEach { lines += "  ${formatTicketDate(it.dueDate)}: ${formatTicketAmount(it.amount)}" }
        }
    }
    return lines
}

private fun formatTicketAmount(amount: BigDecimal): String =
    "$${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"

private fun formatTicketDate(instant: Instant): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy").format(instant.atZone(ZoneId.systemDefault()))
