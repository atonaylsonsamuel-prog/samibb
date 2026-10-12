package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.BuildConfig
import com.samdevelop.samtv.core.model.AccountStatus
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.panel.PanelRules
import com.samdevelop.samtv.domain.usecase.ConnectionStatus
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.PanelText
import com.samdevelop.samtv.ui.components.FocusButton
import com.samdevelop.samtv.ui.theme.SamColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Conta: aparelho, assinatura no painel, lista em uso e versão do app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(onBack: () -> Unit, onLogin: () -> Unit, vm: PanelViewModel = viewModel(factory = PanelViewModel.Factory)) {
    val panel by vm.panel.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var showUpdate by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }

    Column(Modifier.fillMaxSize().background(ScreenBackground)) {
        ScreenHeader("Conta", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard("Aparelho") {
                Text(panel.deviceId, fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Line("Cliente", panel.customer ?: "—")
                Line("Vencimento", PanelText.date(panel.expiresAt))
                Line("Painel", if (panel.reachable) "Conectado" else "Sem conexão" + (panel.error?.let { " ($it)" } ?: ""))
                panel.lastContactAtMillis?.let { Line("Última consulta", fmt.format(Date(it))) }
                panel.message?.let { Line("Aviso", it) }
            }

            InfoCard("Lista em uso") {
                val p = active
                if (p == null) {
                    Text("Nenhuma lista.", color = SamColors.TextSecondary)
                } else {
                    Line("Nome", p.name)
                    Line("Tipo", (if (p.type == SourceType.XTREAM) "Xtream Codes" else "M3U") + if (PanelRules.isLoginSource(p.id)) " · usuário e senha" else " · painel")
                    Line("Servidor", p.address)
                    p.username?.let { Line("Usuário", it) }
                    when (val c = connection) {
                        ConnectionStatus.Checking -> Line("Conexão", "Verificando…")
                        ConnectionStatus.NoSource -> Line("Conexão", "—")
                        is ConnectionStatus.Offline -> Line("Conexão", ErrorMessages.of(c.error))
                        is ConnectionStatus.Online -> {
                            val a = c.auth
                            Line("Conta no servidor", if (a.status == AccountStatus.ACTIVE) "Ativa" else if (a.status == AccountStatus.UNKNOWN) "Conectado" else a.status.name)
                            a.expiresAtEpochSeconds?.let { Line("Vence no servidor", ErrorMessages.formatDate(it)) }
                            if (a.maxConnections != null) Line("Conexões", "${a.activeConnections ?: "?"} em uso de ${a.maxConnections}")
                        }
                    }
                }
            }

            InfoCard("Aplicativo") {
                Line("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Line("Atualização", panel.update?.let { "Disponível: ${it.versionName}" } ?: "Você está na versão mais recente")
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton("Verificar painel", vm::refreshPanel, icon = Icons.Filled.Refresh)
                if (panel.update != null) FocusButton("Atualizar app", { showUpdate = true }, icon = Icons.Filled.SystemUpdate)
                if (panel.allowUserLogin) FocusButton("Entrar com usuário e senha", onLogin, icon = Icons.AutoMirrored.Filled.Login, primary = false)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val info = panel.update
    if (showUpdate && info != null) {
        UpdateDialog(info, update, notice, vm, onDismiss = { showUpdate = false; vm.clearNotice() })
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SamColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = SamColors.PurpleAccent)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SamColors.TextSecondary, modifier = Modifier.width(150.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Entrar só com usuário e senha: o painel descobre o servidor (DNS) certo. */
@Composable
fun LoginScreen(onBack: () -> Unit, onDone: () -> Unit, vm: PanelViewModel = viewModel(factory = PanelViewModel.Factory)) {
    val state by vm.login.collectAsStateWithLifecycle()
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { vm.resetLogin() } }
    LaunchedEffect(state.done) { if (state.done) onDone() }

    fun submit() = vm.doLogin(user, pass)

    Column(Modifier.fillMaxSize().background(ScreenBackground).imePadding()) {
        ScreenHeader("Entrar com usuário e senha", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Use o usuário e a senha que o seu revendedor passou. O servidor é escolhido automaticamente pelo painel.",
                color = SamColors.TextSecondary,
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it }, label = { Text("Usuário") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text("Senha") }, singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showPass) "Ocultar senha" else "Mostrar senha")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = SamColors.Error) }
            Row {
                if (state.running) {
                    CircularProgressIndicator(color = SamColors.PurpleAccent)
                    Spacer(Modifier.width(12.dp))
                    Text("Procurando seu servidor…", color = SamColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
                } else {
                    FocusButton("Entrar", { submit() }, icon = Icons.AutoMirrored.Filled.Login, enabled = user.isNotBlank() && pass.isNotEmpty())
                }
            }
        }
    }
}
