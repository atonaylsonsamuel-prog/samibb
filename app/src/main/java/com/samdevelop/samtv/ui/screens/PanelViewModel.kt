package com.samdevelop.samtv.ui.screens

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samdevelop.samtv.AppContainer
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.panel.AppUpdateInfo
import com.samdevelop.samtv.core.panel.PanelRules
import com.samdevelop.samtv.core.panel.PanelState
import com.samdevelop.samtv.domain.usecase.ConnectionStatus
import com.samdevelop.samtv.domain.usecase.SyncState
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.update.UpdateManager
import com.samdevelop.samtv.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado do login por usuário e senha. */
data class LoginUiState(val running: Boolean = false, val error: String? = null, val done: Boolean = false)

/**
 * Estado do painel, das listas e da atualização do app — usado pela ativação, tela inicial,
 * conta, login e ajustes.
 */
class PanelViewModel(private val c: AppContainer) : ViewModel() {
    val panel: StateFlow<PanelState> = c.panel.state
    val profiles: StateFlow<List<ConnectionProfile>> =
        c.authRepository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active: StateFlow<ConnectionProfile?> = c.session.activeProfile
    val connection: StateFlow<ConnectionStatus> = c.session.connection
    val sync: StateFlow<SyncState> = c.session.sync
    val update: StateFlow<UpdateState> = c.updates.state

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    /** Mensagem curta para o usuário (ex.: resultado da instalação). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun refreshPanel() = c.panel.refreshNow()

    /** "Recarregar": consulta o painel, reconecta e baixa o catálogo da lista em uso de novo. */
    fun reload() {
        c.panel.refreshNow()
        c.session.recheckConnection()
        c.session.refreshCatalog()
    }

    fun select(profileId: String) = viewModelScope.launch { c.authRepository.setActive(profileId) }

    /** Remove uma entrada feita por usuário e senha (as listas do painel só o painel remove). */
    fun removeLogin(profileId: String) = viewModelScope.launch {
        if (PanelRules.isLoginSource(profileId)) c.authRepository.remove(profileId)
    }

    fun doLogin(username: String, password: String) {
        if (_login.value.running) return
        _login.value = LoginUiState(running = true)
        viewModelScope.launch {
            val error = c.panel.login(username, password)
            _login.value = if (error == null) LoginUiState(done = true) else LoginUiState(error = error)
        }
    }

    fun resetLogin() {
        _login.value = LoginUiState()
    }

    fun downloadUpdate(info: AppUpdateInfo) = c.updates.download(info)

    fun cancelUpdate() = c.updates.cancel()

    fun installUpdate(activity: Activity?) {
        if (activity == null) return
        _notice.value = when (val r = c.updates.install(activity)) {
            UpdateManager.InstallResult.Started -> null
            UpdateManager.InstallResult.NeedsPermission ->
                "Permita \"Instalar apps desconhecidos\" para o SAM TV e depois toque em Instalar de novo."
            is UpdateManager.InstallResult.Error -> r.message
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    companion object {
        val Factory = containerFactory { c, _ -> PanelViewModel(c) }
    }
}
