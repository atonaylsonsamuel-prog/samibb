package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.samdevelop.samtv.BuildConfig
import com.samdevelop.samtv.core.panel.AppUpdateInfo
import com.samdevelop.samtv.ui.common.findActivity
import com.samdevelop.samtv.ui.components.FocusButton
import com.samdevelop.samtv.ui.theme.SamColors
import com.samdevelop.samtv.update.UpdateState

/** Conteúdo da atualização: notas, progresso e botões Baixar / Instalar. */
@Composable
fun UpdateCard(
    info: AppUpdateInfo,
    state: UpdateState,
    notice: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(SamColors.Surface, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Nova versão ${info.versionName}", style = MaterialTheme.typography.titleLarge)
        Text(
            "Instalada: ${BuildConfig.VERSION_NAME}" + (info.sizeBytes?.let { " · Download: ${it / (1024 * 1024)} MB" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary,
        )
        info.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        when (state) {
            is UpdateState.Downloading -> {
                val p = state.progress
                if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SamColors.PurpleAccent)
                else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = SamColors.PurpleAccent)
                Text(if (p == null) "Baixando…" else "Baixando… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                FocusButton("Cancelar", onCancel, icon = Icons.Filled.Close, primary = false)
            }
            is UpdateState.Ready -> {
                Text("Download concluído e conferido.", style = MaterialTheme.typography.bodySmall, color = SamColors.Success)
                FocusButton("Instalar agora", onInstall, icon = Icons.Filled.InstallMobile)
            }
            is UpdateState.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = SamColors.Error)
                FocusButton("Tentar de novo", onDownload, icon = Icons.Filled.Refresh)
            }
            UpdateState.Idle -> FocusButton("Baixar atualização", onDownload, icon = Icons.Filled.Download)
        }
        if (notice != null) Text(notice, style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            "O Android pede sua confirmação para instalar. Seus dados e a ativação são mantidos.",
            style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary,
        )
    }
}

/** Diálogo de atualização opcional (aberto pela tela inicial). */
@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    state: UpdateState,
    notice: String?,
    vm: PanelViewModel,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Depois") } },
        containerColor = SamColors.Surface,
        text = {
            Row {
                UpdateCard(
                    info, state, notice,
                    onDownload = { vm.downloadUpdate(info) },
                    onInstall = { vm.installUpdate(activity) },
                    onCancel = vm::cancelUpdate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
