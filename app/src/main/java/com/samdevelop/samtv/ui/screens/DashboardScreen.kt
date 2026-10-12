package com.samdevelop.samtv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.BuildConfig
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.panel.PanelRules
import com.samdevelop.samtv.domain.usecase.ConnectionStatus
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.PanelText
import com.samdevelop.samtv.ui.common.findActivity
import com.samdevelop.samtv.ui.common.rememberIsTv
import com.samdevelop.samtv.ui.components.BrandMark
import com.samdevelop.samtv.ui.components.FocusButton
import com.samdevelop.samtv.ui.components.HeroTile
import com.samdevelop.samtv.ui.components.MenuTile
import com.samdevelop.samtv.ui.components.SideTile
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ações da tela inicial. */
class DashboardActions(
    val onLive: () -> Unit,
    val onMovies: () -> Unit,
    val onSeries: () -> Unit,
    val onAccount: () -> Unit,
    val onSearch: () -> Unit,
    val onFavorites: () -> Unit,
    val onHistory: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * Tela inicial no estilo painel IPTV: TV ao vivo em destaque, Filmes/Séries/Conta/Trocar lista ao lado
 * e uma coluna com Favoritos, Histórico, Ajustes, Recarregar e Sair. Funciona com toque e controle remoto.
 */
@Composable
fun DashboardScreen(actions: DashboardActions, vm: PanelViewModel = viewModel(factory = PanelViewModel.Factory)) {
    val panel by vm.panel.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val tv = rememberIsTv()

    var showLists by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var updateDismissedFor by rememberSaveable { mutableStateOf(0) }
    var showUpdate by remember { mutableStateOf(false) }

    val liveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (tv) runCatching { liveFocus.requestFocus() } }
    BackHandler { confirmExit = true }

    // Atualização opcional: oferece uma vez por versão.
    val available = panel.update
    LaunchedEffect(available?.versionCode) {
        if (available != null && available.versionCode != updateDismissedFor) showUpdate = true
    }

    val vod = active?.type == SourceType.XTREAM
    val vodNote = if (active != null && !vod) "Só em listas Xtream" else null
    val exit = { activity?.finish(); Unit }

    BoxWithConstraints(Modifier.fillMaxSize().background(ScreenBackground).safeDrawingPadding()) {
        val wide = maxWidth > maxHeight
        if (wide) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
                TopBar(panel.expiresAt, available != null, onUpdate = { showUpdate = true }, onSearch = actions.onSearch)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeroTile(
                        "TV ao vivo", Icons.Outlined.LiveTv, actions.onLive,
                        Modifier.weight(1.1f).fillMaxHeight(), subtitle = active?.name, focusRequester = liveFocus,
                    )
                    Column(Modifier.weight(1.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            MenuTile("Filmes", Icons.Outlined.Movie, actions.onMovies, Modifier.weight(1f).fillMaxHeight(), enabled = vod, note = vodNote)
                            MenuTile("Séries", Icons.Outlined.VideoLibrary, actions.onSeries, Modifier.weight(1f).fillMaxHeight(), enabled = vod, note = vodNote)
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            MenuTile("Conta", Icons.Filled.ManageAccounts, actions.onAccount, Modifier.weight(1f).fillMaxHeight(), note = panel.customer)
                            MenuTile(
                                "Trocar lista", Icons.Filled.SwapHoriz, { showLists = true }, Modifier.weight(1f).fillMaxHeight(),
                                note = "${profiles.size} lista(s)",
                            )
                        }
                    }
                    Column(Modifier.weight(0.95f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SideButtons(actions, vm::reload, exit) { Modifier.weight(1f).fillMaxWidth() }
                    }
                }
                Spacer(Modifier.height(10.dp))
                BottomBar(panel.deviceId, active, sync.running, sync.currentKind, connection)
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TopBar(panel.expiresAt, available != null, onUpdate = { showUpdate = true }, onSearch = actions.onSearch)
                HeroTile(
                    "TV ao vivo", Icons.Outlined.LiveTv, actions.onLive,
                    Modifier.fillMaxWidth().height(170.dp), subtitle = active?.name, focusRequester = liveFocus,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuTile("Filmes", Icons.Outlined.Movie, actions.onMovies, Modifier.weight(1f).height(110.dp), enabled = vod, note = vodNote)
                    MenuTile("Séries", Icons.Outlined.VideoLibrary, actions.onSeries, Modifier.weight(1f).height(110.dp), enabled = vod, note = vodNote)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuTile("Conta", Icons.Filled.ManageAccounts, actions.onAccount, Modifier.weight(1f).height(110.dp), note = panel.customer)
                    MenuTile("Trocar lista", Icons.Filled.SwapHoriz, { showLists = true }, Modifier.weight(1f).height(110.dp), note = "${profiles.size} lista(s)")
                }
                SideGrid(actions, vm::reload, exit)
                BottomBar(panel.deviceId, active, sync.running, sync.currentKind, connection)
            }
        }
    }

    if (showLists) {
        ListPickerDialog(profiles, active?.id, onSelect = { vm.select(it); showLists = false }, onDismiss = { showLists = false })
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Sair do SAM TV?") },
            confirmButton = { TextButton(onClick = exit) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancelar") } },
            containerColor = SamColors.Surface,
        )
    }
    if (showUpdate && available != null) {
        UpdateDialog(available, update, notice, vm, onDismiss = {
            showUpdate = false
            updateDismissedFor = available.versionCode
            vm.clearNotice()
        })
    }
}

@Composable
private fun TopBar(expiresAt: String?, hasUpdate: Boolean, onUpdate: () -> Unit, onSearch: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val locale = remember { Locale.forLanguageTag("pt-BR") }
    val time = remember(now) { SimpleDateFormat("HH:mm", locale).format(Date(now)) }
    val day = remember(now) { SimpleDateFormat("EEE, d 'de' MMM", locale).format(Date(now)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandMark(subtitle = true)
        Spacer(Modifier.weight(1f))
        if (hasUpdate) FocusButton("Atualizar app", onUpdate, icon = Icons.Filled.SystemUpdate)
        FocusButton("Buscar", onSearch, icon = Icons.Filled.Search, primary = false)
        Column(horizontalAlignment = Alignment.End) {
            Text(time, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(day, style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary)
            Text(PanelText.expiry(expiresAt), style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary)
        }
    }
}

@Composable
private fun ColumnScope.SideButtons(actions: DashboardActions, onReload: () -> Unit, onExit: () -> Unit, item: ColumnScope.() -> Modifier) {
    SideTile("Favoritos", Icons.Filled.Favorite, actions.onFavorites, item(), accent = SamColors.PurpleAccent)
    SideTile("Histórico", Icons.Filled.History, actions.onHistory, item())
    SideTile("Ajustes", Icons.Filled.Settings, actions.onSettings, item())
    SideTile("Recarregar", Icons.Filled.Refresh, onReload, item())
    SideTile("Sair", Icons.AutoMirrored.Filled.ExitToApp, onExit, item(), accent = SamColors.Error)
}

@Composable
private fun SideGrid(actions: DashboardActions, onReload: () -> Unit, onExit: () -> Unit) {
    val m = Modifier.height(54.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideTile("Favoritos", Icons.Filled.Favorite, actions.onFavorites, m.weight(1f), accent = SamColors.PurpleAccent)
            SideTile("Histórico", Icons.Filled.History, actions.onHistory, m.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideTile("Ajustes", Icons.Filled.Settings, actions.onSettings, m.weight(1f))
            SideTile("Recarregar", Icons.Filled.Refresh, onReload, m.weight(1f))
        }
        SideTile("Sair", Icons.AutoMirrored.Filled.ExitToApp, onExit, m.fillMaxWidth(), accent = SamColors.Error)
    }
}

@Composable
private fun BottomBar(deviceId: String, active: ConnectionProfile?, syncing: Boolean, kind: CatalogKind?, connection: ConnectionStatus) {
    Column(Modifier.fillMaxWidth()) {
        if (syncing) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = SamColors.PurpleAccent)
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val status = when {
                syncing -> when (kind) {
                    CatalogKind.MOVIE -> "Baixando filmes…"
                    CatalogKind.SERIES -> "Baixando séries…"
                    else -> "Baixando canais…"
                }
                connection is ConnectionStatus.Offline -> "Lista sem conexão: ${ErrorMessages.of(connection.error)}"
                active != null -> "Lista: ${active.name}" + if (PanelRules.isLoginSource(active.id)) " (usuário e senha)" else ""
                else -> "Nenhuma lista"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (connection is ConnectionStatus.Offline && !syncing) SamColors.Error else SamColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text("ID $deviceId  ·  v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary)
        }
    }
}

/** Escolha da lista em uso (listas do painel e entradas por usuário/senha). */
@Composable
fun ListPickerDialog(profiles: List<ConnectionProfile>, activeId: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trocar lista") },
        containerColor = SamColors.Surface,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (profiles.isEmpty()) Text("Nenhuma lista disponível.", color = SamColors.TextSecondary)
                profiles.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onSelect(p.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p.id == activeId, onClick = { onSelect(p.id) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (if (p.type == SourceType.XTREAM) "Xtream" else "M3U") +
                                    if (PanelRules.isLoginSource(p.id)) " · usuário e senha" else " · painel",
                                style = MaterialTheme.typography.bodySmall, color = SamColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
