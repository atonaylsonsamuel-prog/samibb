package com.samdevelop.samtv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.model.SourceType
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fluxo completo com servidor Xtream simulado. A lista entra como se tivesse vindo do login
 * "usuário e senha" do painel (fonte gerenciada), então o teste não depende do painel real:
 * tela inicial estilo painel → TV ao vivo → busca → favorito → player (com rotação) → Ajustes → remoção.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var fake: FakeXtreamServer
    private val app get() = ApplicationProvider.getApplicationContext<SamTvApplication>()
    private val sourceId = "ltestefluxo01"

    @Before fun setUp() {
        fake = FakeXtreamServer().start()
        runBlocking {
            val repo = app.container.authRepository
            repo.profiles.first().forEach { repo.remove(it.id) }
            val profile = ConnectionProfile(sourceId, "Lista de teste", SourceType.XTREAM, fake.baseUrl, fake.user, true, 1L, 1L)
            repo.upsertManaged(profile, SourceSecrets(password = fake.pass))
            app.container.preferences.setActiveProfile(sourceId)
        }
    }

    @After fun tearDown() {
        runBlocking {
            val repo = app.container.authRepository
            repo.profiles.first().forEach { repo.remove(it.id) }
        }
        fake.shutdown()
    }

    private fun waitText(text: String, substring: Boolean = false, timeout: Long = 15_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeout)

    @Test fun telaInicialCatalogoBuscaFavoritoPlayerERemocao() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Tela inicial estilo painel
        waitText("TV ao vivo", timeout = 30_000)
        compose.onNodeWithText("Filmes").assertExists()
        compose.onNodeWithText("Séries").assertExists()
        compose.onNodeWithText("Trocar lista").assertExists()
        waitText("ID ", substring = true)

        // Rotação/recriação mantém a tela
        scenario.recreate()
        waitText("TV ao vivo", timeout = 15_000)

        // TV ao vivo com o catálogo do servidor simulado
        compose.onAllNodesWithText("TV ao vivo").onFirst().performClick()
        waitText("Canal Exemplo Alfa", substring = true, timeout = 30_000)
        compose.onNodeWithText("Canal Exemplo Gama", substring = true).assertExists()

        // Busca (sem acento/caixa)
        compose.onNodeWithTag("search_field").performTextInput("BETA")
        compose.waitUntilDoesNotExist(hasText("Canal Exemplo Alfa", substring = true), 10_000)
        waitText("Canal Exemplo Beta", substring = true)

        // Favorito
        compose.onNode(hasContentDescription("Adicionar aos favoritos")).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Remover dos favoritos"), 5_000)

        // Player com o clipe de teste
        compose.onNodeWithText("Canal Exemplo Beta", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Voltar"), 10_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Pausar"), 30_000)
        scenario.recreate() // rotação durante a reprodução: o player continua no ViewModel
        compose.waitUntilAtLeastOneExists(hasContentDescription("Voltar"), 10_000)
        compose.onAllNodesWithText("Canal Exemplo Beta", substring = true).onFirst().assertExists()
        Espresso.pressBack() // player → TV ao vivo
        Espresso.pressBack() // TV ao vivo → início

        // Ajustes: remover o login leva de volta à tela do painel (ativação ou sem conexão)
        waitText("Ajustes")
        compose.onNodeWithText("Ajustes").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Remover Lista de teste"), 10_000)
        compose.onNode(hasContentDescription("Remover Lista de teste")).performClick()
        compose.onNodeWithText("Remover").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Ative este aparelho") or hasText("Sem conexão com o painel"), 30_000)

        scenario.close()
    }
}
