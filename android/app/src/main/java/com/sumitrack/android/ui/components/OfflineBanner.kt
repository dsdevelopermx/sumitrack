package com.sumitrack.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sumitrack.android.ui.theme.SyncOfflineBanner
import kotlinx.coroutines.delay

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    var collapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3_000)
        collapsed = true
    }
    if (collapsed) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = "Sin internet.",
            modifier = modifier.padding(8.dp),
        )
    } else {
        Surface(modifier = modifier.fillMaxWidth(), color = SyncOfflineBanner) {
            Text("Sin internet. Los cambios se guardarán localmente.", modifier = Modifier.padding(12.dp))
        }
    }
}
