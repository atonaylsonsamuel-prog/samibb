package com.samdevelop.samtv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.samdevelop.samtv.core.panel.AppPhase
import com.samdevelop.samtv.core.panel.PanelState
import com.samdevelop.samtv.ui.screens.PanelGateScreen
import com.samdevelop.samtv.ui.theme.SamTvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Telas do painel exibidas antes de liberar as listas. */
@RunWith(AndroidJUnit4::class)
class PanelScreensTest {
    @get:Rule val compose = createComposeRule()

    private val base = PanelState(deviceId = "AB23-CD45-EF67", reachable = true)

    @Test fun ativacaoMostraIdECodigo() {
        compose.setContent { SamTvTheme { PanelGateScreen(base.copy(phase = AppPhase.PENDING, pairCode = "123456", allowUserLogin = true)) } }
        compose.onNodeWithText("Ative este aparelho").assertExists()
        compose.onNodeWithText("AB23-CD45-EF67").assertExists()
        compose.onNodeWithText("123 456").assertExists()
        compose.onNodeWithText("Entrar com usuário e senha").assertExists()
    }

    @Test fun vencidoMostraData() {
        compose.setContent { SamTvTheme { PanelGateScreen(base.copy(phase = AppPhase.EXPIRED, expiresAt = "2026-01-31 23:59:59")) } }
        compose.onNodeWithText("Assinatura vencida").assertExists()
        compose.onNodeWithText("31/01/2026", substring = true).assertExists()
    }

    @Test fun bloqueadoSemLogin() {
        compose.setContent { SamTvTheme { PanelGateScreen(base.copy(phase = AppPhase.BLOCKED, allowUserLogin = true, message = "Fale com o suporte")) } }
        compose.onNodeWithText("Aparelho bloqueado").assertExists()
        compose.onNodeWithText("Fale com o suporte").assertExists()
        compose.onNodeWithText("Entrar com usuário e senha").assertDoesNotExist()
    }
}
