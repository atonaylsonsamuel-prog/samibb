package com.samdevelop.samtv

import android.content.Context
import android.os.Build
import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.panel.PanelClient
import com.samdevelop.samtv.core.panel.PanelManager
import com.samdevelop.samtv.core.panel.PanelRules
import com.samdevelop.samtv.data.panel.KeystoreTextStore
import com.samdevelop.samtv.data.panel.RoomManagedSourceStore
import com.samdevelop.samtv.data.local.AppDatabase
import com.samdevelop.samtv.data.local.AppPreferences
import com.samdevelop.samtv.data.remote.OkHttpHttpClient
import com.samdevelop.samtv.data.repository.AuthenticationRepositoryImpl
import com.samdevelop.samtv.data.repository.CatalogRepositoryImpl
import com.samdevelop.samtv.data.repository.ConnectorProvider
import com.samdevelop.samtv.data.repository.FavoritesRepositoryImpl
import com.samdevelop.samtv.data.repository.HistoryRepositoryImpl
import com.samdevelop.samtv.data.repository.PlaybackSourceProviderImpl
import com.samdevelop.samtv.domain.usecase.ConnectionStatus
import com.samdevelop.samtv.domain.usecase.SessionManager
import com.samdevelop.samtv.player.PlayerFactory
import com.samdevelop.samtv.security.CredentialStore
import com.samdevelop.samtv.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Injeção de dependências manual (sem framework) — o grafo é pequeno e explícito.
 * Uma única instância vive na [SamTvApplication].
 */
class AppContainer(
    context: Context,
    val database: AppDatabase = AppDatabase.build(context),
    baseOkHttp: OkHttpClient = OkHttpHttpClient.defaultClient(),
) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(appContext)
    val credentials = CredentialStore(appContext)
    val httpClient: HttpClient = OkHttpHttpClient(baseOkHttp)

    val connectors = ConnectorProvider(database.profiles(), credentials, httpClient)
    val authRepository = AuthenticationRepositoryImpl(database, preferences, credentials, connectors)
    val catalogRepository = CatalogRepositoryImpl(database, connectors)
    val favoritesRepository = FavoritesRepositoryImpl(database)
    val historyRepository = HistoryRepositoryImpl(database)
    val playbackSourceProvider = PlaybackSourceProviderImpl(connectors, preferences)
    val playerFactory = PlayerFactory(appContext, baseOkHttp)

    val session: SessionManager by lazy { SessionManager(authRepository, catalogRepository, appScope) }

    /** Ligação com o painel (mesmo painel do app de Windows). */
    val panel = PanelManager(
        client = PanelClient(BuildConfig.PANEL_URL, httpClient),
        storage = KeystoreTextStore(credentials),
        sources = RoomManagedSourceStore(authRepository, preferences),
        scope = appScope,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
    )

    val updates = UpdateManager(appContext, baseOkHttp, appScope)

    /** Inicia o painel e, para fontes de login, redescobre o DNS quando a conexão falha. */
    fun start() {
        panel.start()
        appScope.launch {
            session.connection.collect { status ->
                val error = (status as? ConnectionStatus.Offline)?.error ?: return@collect
                if (error !is AppError.Network && error !is AppError.Timeout && error !is AppError.Http) return@collect
                val profile = session.activeProfile.value ?: return@collect
                if (!PanelRules.isLoginSource(profile.id)) return@collect
                val password = credentials.load(profile.id)?.password ?: return@collect
                panel.rediscoverLogin(profile, password)
            }
        }
    }
}
