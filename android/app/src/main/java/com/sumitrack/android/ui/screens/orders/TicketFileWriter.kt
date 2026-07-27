package com.sumitrack.android.ui.screens.orders

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.sumitrack.android.data.ticket.renderTicketBitmap
import com.sumitrack.android.domain.models.TicketData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

// Interfaz abstraída sobre Context/FileProvider/renderizado — mismo patrón que
// BluetoothTicketPrinter y TransactionRunner: permite que PaymentViewModel sea testeable en JVM
// puro con un fake que solo maneja TicketData/String, sin escribir archivos reales ni tocar
// android.graphics en ningún test.
//
// Devuelve un String (no android.net.Uri): Uri.parse/Uri.EMPTY son stubs de Android sin
// implementación real fuera de un dispositivo/Robolectric — lanzan en un test JVM puro. La
// pantalla (PaymentScreen.kt, nunca testeada por convención de este proyecto) hace Uri.parse(...)
// justo antes de construir el Intent real.
interface TicketFileWriter {
    fun writeToCacheAndGetUri(ticket: TicketData, fileName: String): String
}

class AndroidTicketFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : TicketFileWriter {

    // "La imagen NO se guarda en el almacenamiento del dispositivo" (AC-4) se cumple porque
    // cacheDir es almacenamiento interno privado de la app, no visible al usuario desde una
    // galería/explorador de archivos, y el sistema lo puede purgar automáticamente.
    override fun writeToCacheAndGetUri(ticket: TicketData, fileName: String): String {
        val bitmap: Bitmap = renderTicketBitmap(ticket)
        val dir = File(context.cacheDir, "tickets").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
    }
}
