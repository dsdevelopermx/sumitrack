package com.sumitrack.android.ui.screens.conflict

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// S-15 — dialog/modal (EXPERIENCE.md), no una pantalla llena. El foco de TalkBack se mueve al
// título "Conflicto detectado" al abrirse (AC-3) usando el mismo patrón de FocusRequester ya
// establecido en OrderListScreen.kt. El retorno de foco a la pantalla anterior al cerrar (AC-6) se
// apoya en el comportamiento default de Compose Navigation al hacer popBackStack; no se construyó
// un FocusRequester dedicado por card en las listas — verificación real en TalkBack queda
// pendiente de prueba manual en dispositivo, mismo criterio ya aplicado a otros flujos de foco.
@Composable
fun ConflictScreen(onDismiss: () -> Unit, viewModel: ConflictViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val titleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isResolved) {
        if (uiState.isResolved) onDismiss()
    }

    val entry = uiState.logEntry

    if (uiState.isLoading) {
        // Estado de carga visible (Review Finding: antes no se renderizaba nada mientras cargaba).
        // Sin pedir foco todavía — el título real del conflicto aún no existe en este punto.
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Conflicto detectado") },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
        return
    }

    if (entry == null) {
        // Ya resuelto por otra corrida, o el registro/entry no existe (dato inconsistente) — no hay
        // nada que mostrar, se cierra sin más.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    // El título con el FocusRequester recién se compone en la rama de abajo — pedir el foco aquí,
    // keyed en `entry` (Review Finding: antes se pedía en un LaunchedEffect(Unit) incondicional al
    // tope de la función, que corría en la PRIMERA composición con isLoading=true, antes de que el
    // AlertDialog/título existieran — requestFocus() fallaba siempre y nunca se reintentaba).
    LaunchedEffect(entry) {
        runCatching { titleFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss, // cubre swipe-down y Back (AC-6)
        title = {
            Text(
                text = "Conflicto detectado",
                modifier = Modifier
                    .focusRequester(titleFocusRequester)
                    .focusable(),
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Versión local", style = MaterialTheme.typography.labelLarge)
                ConflictFieldList(entry.localSnapshotJson)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Versión del servidor", style = MaterialTheme.typography.labelLarge)
                ConflictFieldList(entry.serverSnapshotJson)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.resolveKeepLocal() }, enabled = !uiState.isResolving) {
                Text("Usar versión local")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.resolveKeepBoth() }, enabled = !uiState.isResolving) {
                Text("Conservar ambas")
            }
        },
    )
}

// Listado genérico clave:valor — con 9 formas de entidad distintas, un comparador tipado por
// entidad sería scope excesivo para esta historia (ver Dev Notes: "Fuera de alcance").
@Composable
private fun ConflictFieldList(snapshotJson: String) {
    val fields = remember(snapshotJson) { parseFields(snapshotJson) }
    Column {
        fields.forEach { (key, value) ->
            Text("$key: $value", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun parseFields(json: String): List<Pair<String, String>> = runCatching {
    Json.parseToJsonElement(json).jsonObject.map { (key, value) ->
        key to runCatching { value.jsonPrimitive.content }.getOrDefault(value.toString())
    }
}.getOrDefault(emptyList())
