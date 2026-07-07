package com.sumitrack.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sumitrack.android.ui.theme.SyncOk
import com.sumitrack.android.ui.theme.SyncPending
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size

@Composable
fun SyncIcon(isSynced: Boolean, modifier: Modifier = Modifier) {
    val icon: ImageVector
    val tint: androidx.compose.ui.graphics.Color
    val description: String

    if (isSynced) {
        icon = Icons.Outlined.CloudDone
        tint = SyncOk
        description = "Sincronizado con la nube."
    } else {
        icon = Icons.Outlined.Cloud
        tint = SyncPending
        description = "Pendiente de sincronizar."
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(20.dp),
    )
}
