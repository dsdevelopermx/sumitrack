package com.sumitrack.android.ui.screens.orders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sumitrack.android.data.ticket.buildTicketLines
import com.sumitrack.android.domain.models.TicketData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketSheet(
    ticketData: TicketData,
    isPrinting: Boolean,
    isSharing: Boolean,
    printError: String?,
    onPrintClick: () -> Unit,
    onPermissionDenied: () -> Unit,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    // BLUETOOTH_CONNECT solo requiere solicitud en tiempo de ejecución en API 31+ (permiso
    // "peligroso"); en API ≤30 ya está concedido en instalación (permisos legacy BLUETOOTH/
    // BLUETOOTH_ADMIN, ver AndroidManifest.xml) — primera vez que este proyecto solicita un
    // permiso peligroso en tiempo de ejecución.
    val requestBluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onPrintClick() else onPermissionDenied() }

    val lines = buildTicketLines(ticketData)

    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Ticket — Folio ${ticketData.folio}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                items(lines) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(16.dp))
            // El Snackbar del Scaffold exterior queda oculto detrás de la ventana propia del
            // ModalBottomSheet (Review Finding del code review de esta historia) — el error de
            // AC-3 se muestra inline, dentro del propio sheet, para garantizar que sea visible
            // mientras el sheet permanece abierto.
            printError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        onPrintClick()
                    }
                },
                enabled = !isPrinting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text("Imprimir vía Bluetooth")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShareClick, enabled = !isSharing, modifier = Modifier.fillMaxWidth()) {
                Text("Compartir")
            }
        }
    }
}
