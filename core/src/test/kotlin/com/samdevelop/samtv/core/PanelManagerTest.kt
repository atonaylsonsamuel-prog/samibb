package com.samdevelop.samtv.core

import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.core.net.HttpResponse
import com.samdevelop.samtv.core.panel.AppPhase
import com.samdevelop.samtv.core.panel.ManagedSource
import com.samdevelop.samtv.core.panel.ManagedSourceStore
import com.samdevelop.samtv.core.panel.PanelClient
import com.samdevelop.samtv.core.panel.PanelManager
import com.samdevelop.samtv.core.panel.PanelRules
import com.samdevelop.samtv.core.panel.PanelState
import com.samdevelop.samtv.core.panel.SecretTextStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelManagerTest {

    private class MemSecrets : SecretTextStore {
        val map = mutableMapOf<String, String>()
        var broken = false
        override suspend fun loadText(name: String): String? { if (broken) error("keystore"); return map[name] }
        override suspend fun saveText(name: String, text: String) { if (broken) error("keystore"); map[name] = text }
    }

    private class MemSources : ManagedSourceStore {
        val secrets = mutableMapOf<String, ManagedSource>()
        override val profiles = MutableStateFlow<List<ConnectionProfile>>(emptyList())
        var active: String? = null
        val removed = mutableListOf<String>()
        override suspend fun all() = profiles.value
        override suspend fun upsert(source: ManagedSource) {
            secrets[source.profile.id] = source
            profiles.value = profiles.value.filter { it.id != source.profile.id } + source.profile
        }
        override suspend fun remove(id: String) {
            removed += id
            secrets.remove(id)
            profiles.value = profiles.value.filter { it.id != id }
        }
        override suspend fun activeId() = active
        override suspend fun setActive(id: String?) { active = id }
    }

    /** Painel simulado: a resposta depende do que o teste definir em [reply]. */
    private class FakePanel : HttpClient {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        var reply: (endpoint: String, body: Map<String, String>) -> HttpResponse = { _, _ -> HttpResponse(500, "") }
        override suspend fun get(request: HttpRequest) = error("GET")
        override suspend fun <T> getLines(request: HttpRequest, onLines: (Sequence<String>) -> T) = error("GET")
        override suspend fun postJson(request: HttpRequest, json: String): HttpResponse {
            val body = Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content }
            val endpoint = request.url.substringAfterLast('/')
            calls += endpoint to body
            return reply(endpoint, body)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000_000_000L
    private val http = FakePanel()
    private val secrets = MemSecrets()
    private val sources = MemSources()

    @After
    fun tearDown() = scope.cancel()

    private fun manager() = PanelManager(
        PanelClient("https://painel.exemplo.com", http), secrets, sources, scope,
        appVersionName = "1.1.0", appVersionCode = 2, deviceModel = "Teste", now = { clock },
    )

    private fun pending(id: String) = """{"status":"pending","device_id":"$id","code":"123456","refresh_after":15,"allow_user_login":true}"""
    private fun active(id: String, lists: String, extra: String = "") =
        """{"status":"active","device_id":"$id","customer":"Ana","expires_at":"2026-12-31 23:59:59","refresh_after":21600,"allow_user_login":true,"lists":[$lists]$extra}"""
    private val listA = """{"id":7,"name":"Principal","type":"xtream","server":"http://dns1.exemplo.com:8080","username":"u","password":"p"}"""
    private val listB = """{"id":8,"name":"Extra","type":"m3u","playlist_url":"https://cdn.exemplo.com/lista.m3u?token=abc"}"""

    private fun PanelState.started() = phase != AppPhase.STARTING

    private suspend fun PanelManager.waitFor(predicate: (PanelState) -> Boolean): PanelState =
        withTimeout(3_000) { state.first(predicate) }

    @Test
    fun primeiroUsoPendenteDepoisAtivo() = runBlocking {
        http.reply = { _, b -> HttpResponse(200, pending(b.getValue("device_id"))) }
        val pm = manager()
        assertTrue(pm.loadLocalState())
        assertEquals(15_000L, pm.refreshOnce())
        val s1 = pm.waitFor { it.phase == AppPhase.PENDING }
        assertEquals("123456", s1.pairCode)
        assertTrue(s1.deviceId.matches(Regex("[2-9A-Z]{4}-[2-9A-Z]{4}-[2-9A-Z]{4}")))
        val sent = http.calls.last().second
        assertEquals("android", sent["platform"]); assertEquals("1.1.0", sent["app_version"]); assertEquals("Teste", sent["model"])
        assertTrue("identidade marcada como registrada", secrets.map[PanelManager.KEY_IDENTITY]!!.contains("\"registered\":true"))
        assertFalse("segredo não aparece no estado", s1.toString().contains(sent.getValue("secret")))

        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), "$listA,$listB")) }
        assertEquals(21_600_000L, pm.refreshOnce())
        val s2 = pm.waitFor { it.phase == AppPhase.READY }
        assertNull(s2.pairCode)
        assertEquals("Ana", s2.customer)
        assertEquals(listOf("p7", "p8"), sources.profiles.value.map { it.id })
        assertEquals("p7", sources.active)
        assertEquals("p", sources.secrets["p7"]!!.secrets.password)
        // O mesmo ID continua sendo usado.
        assertEquals(1, http.calls.map { it.second["device_id"] }.toSet().size)
    }

    @Test
    fun listaRemovidaNoPainelSaiDoApp() = runBlocking {
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), "$listA,$listB")) }
        val pm = manager(); pm.loadLocalState(); pm.refreshOnce()
        sources.active = "p8"
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA)) }
        pm.refreshOnce()
        assertEquals(listOf("p7"), sources.profiles.value.map { it.id })
        assertEquals("p7", sources.active)
    }

    @Test
    fun trocaDeDnsAtualizaAFonte() = runBlocking {
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA)) }
        val pm = manager(); pm.loadLocalState(); pm.refreshOnce()
        val v1 = sources.profiles.value.single().updatedAtMillis
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA.replace("dns1", "dns2"))) }
        pm.refreshOnce()
        val p = sources.profiles.value.single()
        assertEquals("http://dns2.exemplo.com:8080", p.address)
        assertNotEquals(v1, p.updatedAtMillis)
    }

    @Test
    fun semPainelValeSeteDiasDepoisPedeInternet() = runBlocking {
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA)) }
        val pm = manager(); pm.loadLocalState(); pm.refreshOnce()
        http.reply = { _, _ -> throw HttpFailure.Connection("offline") }
        clock += 6L * 24 * 3600 * 1000
        assertEquals(PanelRules.ERROR_RETRY_MS, pm.refreshOnce())
        val s = pm.waitFor { it.started() && !it.reachable && it.error != null }
        assertEquals(AppPhase.READY, s.phase)
        assertEquals("Sem conexão com o painel. Verifique a internet.", s.error)
        assertEquals(1, sources.profiles.value.size)

        clock += 2L * 24 * 3600 * 1000
        pm.refreshOnce()
        pm.waitFor { it.phase == AppPhase.OFFLINE }
        assertTrue(sources.profiles.value.isEmpty())
    }

    @Test
    fun reinicioUsaCacheSemRede() = runBlocking {
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA)) }
        val pm = manager(); pm.loadLocalState(); pm.refreshOnce()
        val id = pm.waitFor { it.phase == AppPhase.READY }.deviceId

        // App reaberto sem internet: identidade e listas vêm do armazenamento cifrado.
        val sources2 = MemSources()
        http.reply = { _, _ -> throw HttpFailure.Timeout() }
        val pm2 = PanelManager(PanelClient("https://painel.exemplo.com", http), secrets, sources2, scope, "1.1.0", 2, "Teste", now = { clock })
        pm2.loadLocalState()
        val s = pm2.waitFor { it.phase == AppPhase.READY }
        assertEquals(id, s.deviceId)
        assertEquals(listOf("p7"), sources2.profiles.value.map { it.id })
    }

    @Test
    fun vencidoEBloqueado() = runBlocking {
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA)) }
        val pm = manager(); pm.loadLocalState(); pm.refreshOnce()
        http.reply = { _, b -> HttpResponse(200, """{"status":"expired","device_id":"${b["device_id"]}","expires_at":"2026-01-01","allow_user_login":true}""") }
        pm.refreshOnce()
        pm.waitFor { it.phase == AppPhase.EXPIRED }
        assertTrue(sources.profiles.value.isEmpty())
        assertEquals(listOf("p7"), sources.removed)

        // Vencido, mas com login por usuário/senha: continua usando o login.
        http.reply = { e, b ->
            if (e == "login.php") HttpResponse(200, """{"status":"ok","dns_id":3,"server":"http://dns3.exemplo.com","expires_at":null}""")
            else HttpResponse(200, """{"status":"expired","device_id":"${b["device_id"]}","allow_user_login":true}""")
        }
        assertNull(pm.login(" cliente ", "senha"))
        val s = pm.waitFor { it.phase == AppPhase.READY }
        assertTrue(s.allowUserLogin)
        val login = sources.profiles.value.single()
        assertTrue(PanelRules.isLoginSource(login.id))
        assertEquals("cliente", login.username)
        assertEquals(login.id, sources.active)
        assertEquals("senha", sources.secrets[login.id]!!.secrets.password)
        assertEquals("cliente", http.calls.last { it.first == "login.php" }.second["username"])

        http.reply = { _, b -> HttpResponse(200, """{"status":"blocked","device_id":"${b["device_id"]}"}""") }
        pm.refreshOnce()
        val blocked = pm.waitFor { it.phase == AppPhase.BLOCKED }
        assertFalse(blocked.allowUserLogin)
    }

    @Test
    fun loginComErroDoPainel() = runBlocking {
        http.reply = { e, b ->
            if (e == "login.php") HttpResponse(401, """{"error":"invalid_credentials","message":"Usuário ou senha incorretos."}""")
            else HttpResponse(200, pending(b.getValue("device_id")))
        }
        val pm = manager(); pm.loadLocalState()
        assertEquals("Informe usuário e senha.", pm.login("  ", "x"))
        assertEquals("Usuário ou senha incorretos.", pm.login("u", "errada"))
        assertEquals("registra o aparelho antes do login", "device.php", http.calls.first().first)
        assertTrue(sources.profiles.value.isEmpty())
    }

    @Test
    fun colisaoDeIdGeraOutro() = runBlocking {
        var first: String? = null
        http.reply = { _, b ->
            val id = b.getValue("device_id")
            if (first == null) first = id
            if (id == first) HttpResponse(401, """{"error":"invalid_device","message":"x"}""") else HttpResponse(200, pending(id))
        }
        val pm = manager(); pm.loadLocalState(); pm.refreshOnce()
        val s = pm.waitFor { it.phase == AppPhase.PENDING }
        assertNotEquals(first, s.deviceId)
        assertTrue(secrets.map[PanelManager.KEY_IDENTITY]!!.contains(s.deviceId))
    }

    @Test
    fun atualizacaoObrigatoria() = runBlocking {
        val upd = ""","update":{"version_code":3,"version_name":"1.2.0","url":"https://painel.exemplo.com/apk/app.apk","mandatory":true}"""
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA, upd)) }
        val pm = manager(); pm.loadLocalState(); pm.refreshOnce()
        val s = pm.waitFor { it.phase == AppPhase.UPDATE_REQUIRED }
        assertEquals("1.2.0", s.update!!.versionName)
    }

    @Test
    fun armazenamentoSeguroIndisponivel() = runBlocking {
        secrets.broken = true
        val pm = manager()
        assertFalse(pm.loadLocalState())
        val s = pm.waitFor { it.phase == AppPhase.OFFLINE }
        assertTrue(s.error!!.contains("armazenamento seguro"))
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun loopConsultaDeNovoAoPedir() = runBlocking {
        http.reply = { _, b -> HttpResponse(200, active(b.getValue("device_id"), listA)) }
        val pm = manager()
        pm.start()
        pm.waitFor { it.phase == AppPhase.READY }
        val before = http.calls.size
        pm.refreshNow()
        withTimeout(3_000) { while (http.calls.size == before) kotlinx.coroutines.delay(10) }
        assertEquals(before + 1, http.calls.size)
    }
}
