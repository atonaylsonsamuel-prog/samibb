package com.samdevelop.samtv.core.panel

import com.samdevelop.samtv.core.model.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.SecureRandom

/** Armazenamento cifrado de textos (Android: Keystore). */
interface SecretTextStore {
    suspend fun loadText(name: String): String?
    suspend fun saveText(name: String, text: String)
}

/** Onde as fontes do app ficam (Android: Room + credenciais cifradas). */
interface ManagedSourceStore {
    val profiles: Flow<List<ConnectionProfile>>
    suspend fun all(): List<ConnectionProfile>
    suspend fun upsert(source: ManagedSource)
    /** Remoção automática (painel): mantém favoritos e histórico. */
    suspend fun remove(id: String)
    suspend fun activeId(): String?
    suspend fun setActive(id: String?)
}

data class PanelState(
    val phase: AppPhase = AppPhase.STARTING,
    val deviceId: String = "",
    val pairCode: String? = null,
    val customer: String? = null,
    val expiresAt: String? = null,
    val message: String? = null,
    val allowUserLogin: Boolean = false,
    val reachable: Boolean = false,
    val checking: Boolean = false,
    val error: String? = null,
    val lastContactAtMillis: Long? = null,
    /** Atualização mais nova que a instalada (obrigatória ou não). */
    val update: AppUpdateInfo? = null,
)

/**
 * Ligação do app com o painel — mesma regra do app de Windows:
 *  1. gera e guarda (cifrados) o ID do aparelho e o segredo;
 *  2. consulta o painel: pendente → mostra ID + código e consulta a cada 15 s;
 *     ativo → grava as listas como fontes; vencido/bloqueado → o app trava a interface;
 *  3. sem painel, as listas salvas valem por até 7 dias;
 *  4. login opcional só com usuário e senha (o painel descobre o DNS).
 */
class PanelManager(
    private val client: PanelClient,
    private val storage: SecretTextStore,
    private val sources: ManagedSourceStore,
    private val scope: CoroutineScope,
    private val appVersionName: String,
    private val appVersionCode: Int,
    private val deviceModel: String,
    private val platform: String = "android",
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private data class Internal(
        val started: Boolean = false,
        val identity: DeviceIdentity? = null,
        val hello: PanelHello? = null,
        val helloAt: Long? = null,
        val reachable: Boolean = false,
        val checking: Boolean = false,
        val error: String? = null,
        val fatal: Boolean = false,
    )

    private val internal = MutableStateFlow(Internal())
    private val kick = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private var lastAttemptAt = 0L
    private var lastRediscoverAt = 0L
    private var loop: Job? = null

    val state: StateFlow<PanelState> = combine(internal, sources.profiles) { i, profiles ->
        val hello = i.hello
        val usable = panelUsable(i)
        val hasLogin = profiles.any { PanelRules.isLoginSource(it.id) }
        PanelState(
            phase = if (i.fatal) AppPhase.OFFLINE else PanelRules.phase(i.started, hello, i.reachable, usable, hasLogin, appVersionCode),
            deviceId = i.identity?.deviceId.orEmpty(),
            pairCode = hello?.takeIf { it.status == PanelStatus.PENDING }?.code,
            customer = hello?.customer,
            expiresAt = hello?.expiresAt,
            message = hello?.message,
            allowUserLogin = hello?.allowUserLogin == true && hello.status != PanelStatus.BLOCKED,
            reachable = i.reachable,
            checking = i.checking,
            error = i.error,
            lastContactAtMillis = i.helloAt,
            update = PanelRules.updateAvailable(hello, appVersionCode),
        )
    }.stateIn(scope, SharingStarted.Eagerly, PanelState())

    private fun panelUsable(i: Internal): Boolean {
        val at = i.helloAt ?: return false
        return i.reachable || now() - at <= PanelRules.OFFLINE_GRACE_MS
    }

    // ------------------------------------------------------------------ ciclo de vida

    fun start(): Job {
        loop?.let { return it }
        val job = scope.launch {
            if (!loadLocalState()) return@launch
            while (isActive) {
                val next = refreshOnce()
                withTimeoutOrNull(next) { kick.receive() }
            }
        }
        loop = job
        return job
    }

    /** Consulta o painel agora (botões "Verificar painel" / "Recarregar"). */
    fun refreshNow() {
        kick.trySend(Unit)
    }

    /** Ao voltar para o app: consulta se a última tentativa foi há mais de 5 minutos. */
    fun onForeground() {
        if (now() - lastAttemptAt > 5 * 60_000L) refreshNow()
    }

    /** Lê identidade e cache. Retorna false se o armazenamento seguro falhou (não seguimos sem ele). */
    suspend fun loadLocalState(): Boolean {
        val identity = try {
            storage.loadText(KEY_IDENTITY)?.let(::decodeIdentity) ?: newIdentity()
        } catch (e: Exception) {
            internal.value = Internal(
                started = true, fatal = true,
                error = "Não foi possível usar o armazenamento seguro do aparelho para guardar suas credenciais.",
            )
            return false
        }
        val cached = try {
            storage.loadText(KEY_HELLO)?.let(::decodeCache)
        } catch (e: Exception) {
            null
        }
        internal.value = Internal(identity = identity, hello = cached?.first, helloAt = cached?.second)
        if (cached != null) reconcile(cached.first, panelUsable(internal.value))
        internal.update { it.copy(started = true) }
        return true
    }

    /** Uma consulta ao painel. Retorna em quanto tempo consultar de novo. */
    suspend fun refreshOnce(): Long {
        lastAttemptAt = now()
        internal.update { it.copy(checking = true) }
        var identity = internal.value.identity ?: return PanelRules.ERROR_RETRY_MS
        var result = client.hello(identity.deviceId, identity.secret, platform, appVersionName, deviceModel)
        // ID já usado por outro aparelho (colisão rara): gera outro, só se nunca registramos.
        if (result is PanelCall.Fail && result.httpStatus == 401 && !identity.registered) {
            identity = try { newIdentity() } catch (e: Exception) { identity }
            internal.update { it.copy(identity = identity) }
            result = client.hello(identity.deviceId, identity.secret, platform, appVersionName, deviceModel)
        }
        return when (result) {
            is PanelCall.Ok -> {
                val hello = result.value
                if (!identity.registered) {
                    identity = identity.copy(registered = true)
                    runCatching { storage.saveText(KEY_IDENTITY, encodeIdentity(identity)) }
                }
                val at = now()
                runCatching { storage.saveText(KEY_HELLO, encodeCache(hello, at)) }
                reconcile(hello, usable = true)
                internal.update { it.copy(identity = identity, hello = hello, helloAt = at, reachable = true, checking = false, error = null) }
                PanelRules.nextPollMs(hello)
            }
            is PanelCall.Fail -> {
                val current = internal.value
                internal.update { it.copy(reachable = false, checking = false, error = result.message) }
                if (current.hello != null && !panelUsable(internal.value)) reconcile(current.hello, usable = false)
                if (current.hello == null || current.hello.status == PanelStatus.PENDING) PanelRules.PENDING_POLL_MS else PanelRules.ERROR_RETRY_MS
            }
        }
    }

    // ------------------------------------------------------------------ fontes

    /** Deixa as fontes do painel iguais às listas recebidas. Fontes de login são mantidas. */
    private suspend fun reconcile(hello: PanelHello, usable: Boolean) {
        mutex.withLock { reconcileLocked(hello, usable) }
    }

    private suspend fun reconcileLocked(hello: PanelHello, usable: Boolean) {
        val desired = if (usable) PanelRules.sourcesFrom(hello) else emptyList()
        val desiredIds = desired.map { it.profile.id }.toSet()
        for (s in desired) sources.upsert(s)
        val existing = sources.all()
        for (p in existing) {
            if (!PanelRules.isLoginSource(p.id) && p.id !in desiredIds) sources.remove(p.id)
        }
        val remaining = sources.all().map { it.id }
        val active = sources.activeId()
        if (active == null || active !in remaining) {
            sources.setActive(desired.firstOrNull()?.profile?.id ?: remaining.firstOrNull())
        }
    }

    /** Login só com usuário e senha. Retorna null em caso de sucesso, ou a mensagem de erro. */
    suspend fun login(username: String, password: String): String? {
        val user = username.trim()
        if (user.isEmpty() || password.isEmpty()) return "Informe usuário e senha."
        if (internal.value.identity?.registered != true) refreshOnce()
        val identity = internal.value.identity ?: return "Aparelho ainda não registrado no painel."
        return when (val r = client.login(identity.deviceId, identity.secret, user, password)) {
            is PanelCall.Fail -> r.message
            is PanelCall.Ok -> {
                val src = PanelRules.loginSource(user, password, r.value, now()) ?: return "O painel devolveu um servidor inválido."
                mutex.withLock {
                    sources.upsert(src)
                    sources.setActive(src.profile.id)
                }
                null
            }
        }
    }

    /**
     * Fonte de login sem conexão: pergunta ao painel se o DNS mudou (no máximo a cada 30 min).
     * Retorna true se o endereço foi atualizado.
     */
    suspend fun rediscoverLogin(profile: ConnectionProfile, password: String): Boolean {
        if (!PanelRules.isLoginSource(profile.id)) return false
        val user = profile.username ?: return false
        if (now() - lastRediscoverAt < 30 * 60_000L) return false
        lastRediscoverAt = now()
        val identity = internal.value.identity ?: return false
        val r = client.login(identity.deviceId, identity.secret, user, password) as? PanelCall.Ok ?: return false
        val src = PanelRules.loginSource(user, password, r.value, now()) ?: return false
        if (src.profile.address == profile.address) return false
        mutex.withLock {
            // O DNS mudou de id: remove a antiga e ativa a nova.
            if (src.profile.id != profile.id) sources.remove(profile.id)
            sources.upsert(src)
            sources.setActive(src.profile.id)
        }
        return true
    }

    // ------------------------------------------------------------------ persistência

    private suspend fun newIdentity(): DeviceIdentity {
        val id = DeviceIdentity.create(random)
        storage.saveText(KEY_IDENTITY, encodeIdentity(id))
        return id
    }

    companion object {
        const val KEY_IDENTITY = "panel.identity"
        const val KEY_HELLO = "panel.hello"

        fun encodeIdentity(i: DeviceIdentity): String = JsonObject(
            mapOf("id" to JsonPrimitive(i.deviceId), "secret" to JsonPrimitive(i.secret), "registered" to JsonPrimitive(i.registered)),
        ).toString()

        fun decodeIdentity(text: String): DeviceIdentity? = try {
            val o = Json.parseToJsonElement(text) as JsonObject
            val id = o["id"]!!.jsonPrimitive.content
            val secret = o["secret"]!!.jsonPrimitive.content
            if (!DeviceIdentity.isValidId(id) || secret.length < 32) null
            else DeviceIdentity(id, secret, o["registered"]?.jsonPrimitive?.booleanOrNull ?: false)
        } catch (e: Exception) {
            null
        }

        fun encodeCache(hello: PanelHello, at: Long): String =
            JsonObject(mapOf("at" to JsonPrimitive(at), "hello" to JsonPrimitive(PanelClient.encodeHello(hello)))).toString()

        fun decodeCache(text: String): Pair<PanelHello, Long>? = try {
            val o = Json.parseToJsonElement(text) as JsonObject
            val at = o["at"]!!.jsonPrimitive.longOrNull!!
            val hello = PanelClient.decodeHello(o["hello"]!!.jsonPrimitive.contentOrNull!!)!!
            hello to at
        } catch (e: Exception) {
            null
        }
    }
}
