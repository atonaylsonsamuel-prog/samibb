package com.samdevelop.samtv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.BuildConfig
import com.samdevelop.samtv.core.panel.AppPhase
import com.samdevelop.samtv.core.panel.PanelState
import com.samdevelop.samtv.ui.common.PanelText
import com.samdevelop.samtv.ui.common.findActivity
import com.samdevelop.samtv.ui.common.rememberIsTv
import com.samdevelop.samtv.ui.components.BrandMark
import com.samdevelop.samtv.ui.components.FocusButton
import com.samdevelop.samtv.ui.theme.SamColors

val ScreenBackground = Brush.linearGradient(listOf(Color(0xFF070A14), Color(0xFF140A33), Color(0xFF070A14)))

/**
 * Tudo que aparece antes de o app liberar as listas: iniciando, ativação (ID + código),
 * sem conexão, vencido, bloqueado e atualização obrigatória.
 */
@Composable
fun PanelGateScreen(state: PanelState, vm: PanelViewModel = viewModel(factory = PanelViewModel.Factory)) {
    var showLogin by rememberSaveable { mutableStateOf(false) }
    if (showLogin && state.allowUserLogin) {
        BackHandler { showLogin = false }
        LoginScreen(onBack = { showLogin = false }, onDone = { showLogin = false }, vm = vm)
        return
    }
    if (state.phase == AppPhase.STARTING) {
        Box(Modifier.fillMaxSize().background(ScreenBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(subtitle = true)
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = SamColors.PurpleAccent)
            }
        }
        return
    }

    val primary = remember { FocusRequester() }
    val tv = rememberIsTv()
    LaunchedEffect(state.phase) { if (tv) runCatching { primary.requestFocus() } }
    val canLogin = state.allowUserLogin && state.phase != AppPhase.BLOCKED && state.phase != AppPhase.UPDATE_REQUIRED

    GateLayout(deviceId = state.deviceId) {
        when (state.phase) {
            AppPhase.PENDING -> {
                Title("Ative este aparelho")
                Body("Informe o ID e o código abaixo ao seu revendedor ou ative no painel. A liberação chega sozinha em alguns segundos.")
                CodeCard(state.deviceId, state.pairCode)
                Status(state)
                Buttons {
                    FocusButton("Verificar agora", vm::refreshPanel, icon = Icons.Filled.Refresh, focusRequester = primary)
                    if (canLogin) LoginButton { showLogin = true }
                }
            }
            AppPhase.OFFLINE -> {
                GateIcon(Icons.Filled.CloudOff, SamColors.TextSecondary)
                Title("Sem conexão com o painel")
                Body(state.error ?: "Verifique a internet do aparelho e tente de novo.")
                Buttons {
                    FocusButton("Tentar de novo", vm::refreshPanel, icon = Icons.Filled.Refresh, focusRequester = primary)
                }
            }
            AppPhase.EXPIRED -> {
                GateIcon(Icons.Filled.EventBusy, SamColors.Error)
                Title("Assinatura vencida")
                Body("O acesso deste aparelho venceu em ${PanelText.date(state.expiresAt)}. Fale com seu revendedor para renovar.")
                state.message?.let { Body(it) }
                Status(state)
                Buttons {
                    FocusButton("Verificar novamente", vm::refreshPanel, icon = Icons.Filled.Refresh, focusRequester = primary)
                    if (canLogin) LoginButton { showLogin = true }
                }
            }
            AppPhase.BLOCKED -> {
                GateIcon(Icons.Filled.Block, SamColors.Error)
                Title("Aparelho bloqueado")
                Body(state.message ?: "Este aparelho foi bloqueado no painel. Fale com seu revendedor.")
                Buttons {
                    FocusButton("Verificar novamente", vm::refreshPanel, icon = Icons.Filled.Refresh, focusRequester = primary)
                }
            }
            AppPhase.UPDATE_REQUIRED -> {
                val update by vm.update.collectAsStateWithLifecycle()
                val notice by vm.notice.collectAsStateWithLifecycle()
                val activity = LocalContext.current.findActivity()
                GateIcon(Icons.Filled.SystemUpdate, SamColors.PurpleAccent)
                Title("Atualização obrigatória")
                Body("Para continuar usando o SAM TV, instale a nova versão.")
                state.update?.let { info ->
                    UpdateCard(
                        info, update, notice,
                        onDownload = { vm.downloadUpdate(info) },
                        onInstall = { vm.installUpdate(activity) },
                        onCancel = vm::cancelUpdate,
                        modifier = Modifier.widthIn(max = 520.dp),
                    )
                }
            }
            AppPhase.READY, AppPhase.STARTING -> Unit
        }
    }
}

@Composable
private fun GateLayout(deviceId: String, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(ScreenBackground).safeDrawingPadding()) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.Start) {
                    BrandMark(subtitle = true)
                    Spacer(Modifier.height(16.dp))
                    Text("ID do aparelho", color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(deviceId, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Versão ${BuildConfig.VERSION_NAME}", color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Column(
                    Modifier.weight(1.4f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BrandMark(subtitle = true)
                Spacer(Modifier.height(8.dp))
                content()
                Text("ID: $deviceId · v${BuildConfig.VERSION_NAME}", color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LoginButton(onClick: () -> Unit) {
    FocusButton("Entrar com usuário e senha", onClick, icon = Icons.AutoMirrored.Filled.Login, primary = false)
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary)

@Composable
private fun GateIcon(icon: ImageVector, tint: Color) = Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun Status(state: PanelState) {
    val text = when {
        state.checking -> "Consultando o painel…"
        state.error != null -> state.error
        state.reachable -> "Painel conectado. Aguardando ativação."
        else -> null
    }
    if (text != null) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (state.error != null && !state.checking) SamColors.Error else SamColors.TextSecondary)
    }
}

/** ID e código grandes, legíveis de longe na TV. */
@Composable
private fun CodeCard(deviceId: String, code: String?) {
    Row(
        Modifier.fillMaxWidth()
            .background(SamColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, SamColors.Purple, RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("ID do aparelho", color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(deviceId, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
        }
        Column(Modifier.weight(0.7f)) {
            Text("Código", color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(
                code?.chunked(3)?.joinToString(" ") ?: "— — —",
                fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SamColors.PurpleAccent,
            )
        }
    }
}
