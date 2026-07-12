package com.sumitrack.android.ui.screens.clients

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ClientFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { onSaved() }
    }

    // Evita que back (gesto o botón físico) cancele un guardado en curso a medio hacer.
    BackHandler(enabled = !uiState.isSaving) { onCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Editar cliente" else "Nuevo cliente") },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !uiState.isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nombre o razón social") },
                singleLine = true,
                isError = uiState.nameError,
                supportingText = if (uiState.nameError) {
                    { Text("El nombre es obligatorio") }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.phone,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("Teléfono") },
                singleLine = true,
                isError = uiState.phoneError,
                supportingText = if (uiState.phoneError) {
                    { Text("El teléfono es obligatorio") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            OutlinedTextField(
                value = uiState.rfc,
                onValueChange = viewModel::onRfcChange,
                label = { Text("RFC") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            OutlinedTextField(
                value = uiState.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text("Dirección") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notas") },
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            if (uiState.errorMessage != null && !uiState.nameError && !uiState.phoneError) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.onSaveClick()
                },
                enabled = uiState.isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .padding(bottom = 16.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Guardar")
                }
            }
        }
    }
}
