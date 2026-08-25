package com.sumitrack.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.ui.theme.Error
import com.sumitrack.android.ui.theme.SyncOk
import com.sumitrack.android.ui.theme.SyncPending

@Composable
fun SyncIcon(status: SyncStatus, onConflictClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    val icon: ImageVector
    val tint: Color
    val description: String

    when (status) {
        SyncStatus.SYNCED -> {
            icon = Icons.Outlined.CloudDone
            tint = SyncOk
            description = "Sincronizado con la nube."
        }
        SyncStatus.PENDING -> {
            icon = Icons.Outlined.Cloud
            tint = SyncPending
            description = "Pendiente de sincronizar."
        }
        SyncStatus.CONFLICT -> {
            // Error (no un token sync-* exclusivo): a diferencia de PENDING (informativo), CONFLICT
            // es genuinamente un estado de error que requiere acción del usuario.
            icon = Icons.Outlined.SyncProblem
            tint = Error
            description = "Conflicto de sincronización."
        }
    }

    if (status == SyncStatus.CONFLICT) {
        // IconButton fuerza el mínimo de 48dp de Material3 para touch targets — un ícono de 20dp
        // clickeable "a pelo" queda muy por debajo del mínimo (Review Finding).
        IconButton(onClick = onConflictClick, modifier = modifier) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = modifier.size(20.dp),
        )
    }
}
