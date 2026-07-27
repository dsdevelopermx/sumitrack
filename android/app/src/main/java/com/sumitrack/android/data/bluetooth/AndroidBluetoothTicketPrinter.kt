package com.sumitrack.android.data.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import com.sumitrack.android.data.ticket.renderTicketBitmap
import com.sumitrack.android.domain.models.TicketData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidBluetoothTicketPrinter @Inject constructor(
    @ApplicationContext private val context: Context,
) : BluetoothTicketPrinter {

    // Sin selector de impresora (ver "Fuera de alcance" de la historia): autoconecta al primer
    // dispositivo Bluetooth ya emparejado desde los Ajustes del sistema — flujo estándar para
    // impresoras POS de este segmento.
    override suspend fun printTicket(ticket: TicketData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = renderTicketBitmap(ticket)
            val manager = context.getSystemService(BluetoothManager::class.java)
            val adapter = manager?.adapter ?: error("Bluetooth no disponible en este dispositivo")
            if (!adapter.isEnabled) error("Bluetooth deshabilitado")

            val device = adapter.bondedDevices.firstOrNull() ?: error("Ninguna impresora emparejada")
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.use { s ->
                s.connect()
                s.outputStream.write(bitmapToEscPosRaster(bitmap))
                s.outputStream.flush()
            }
        }
    }

    companion object {
        // UUID estándar de Serial Port Profile (SPP) — el mismo que usa prácticamente cualquier
        // impresora térmica Bluetooth "genérica" ESC/POS del mercado; no requiere SDK propietario
        // de ningún fabricante.
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
