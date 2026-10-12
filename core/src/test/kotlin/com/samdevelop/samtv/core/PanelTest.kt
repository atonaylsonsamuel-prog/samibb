package com.samdevelop.samtv.core

import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.core.net.HttpResponse
import com.samdevelop.samtv.core.panel.AppPhase
import com.samdevelop.samtv.core.panel.DeviceIdentity
import com.samdevelop.samtv.core.panel.PanelCall
import com.samdevelop.samtv.core.panel.PanelClient
import com.samdevelop.samtv.core.panel.PanelHello
import com.samdevelop.samtv.core.panel.PanelRules
import com.samdevelop.samtv.core.panel.PanelStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom

class PanelTest {

    private class FakePanel : HttpClient {
        val bodies = mutableListOf<Pair<HttpRequest, String>>()
        var next: () -> HttpResponse = { HttpResponse(500, "") }
        override suspend fun get(request: HttpRequest) = error("GET não esperado")
        override suspend fun <T> getLines(request: HttpRequest, onLines: (Sequence<String>) -> T) = error("GET não esperado")
        override suspend fun postJson(request: HttpRequest, json: String): HttpResponse {
            bodies += request to json
            return next()
        }
    }

    private val active = """
        {"status":"active","device_id":"AB23-CD45-EF67","customer":"Cliente","expires_at":"2026-12-31T23:59:59-03:00",
         "message":null,"allow_user_login":true,"refresh_after":21600,"server_time":"2026-10-12T03:00:00-03:00",
         "lists":[
           {"id":7,"name":"Principal","type":"xtream","server":"http://dns.exemplo.com:8080/","username":"u","password":" p 1 ","updated_at":"2026-10-12 02:00:00"},
           {"id":8,"name":"Extra","type":"m3u","playlist_url":"https://cdn.exemplo.com/get.php?username=a&password=b&type=m3u"},
           {"id":9,"name":"Quebrada","type":"xtream","server":"","username":"u","password":"p"},
           {"id":10,"name":"Desconhecida","type":"rtsp"}
         ],
         "update":{"version_code":5,"version_name":"1.2.0","url":"https://painel.exemplo.com/apk/samtv-1.2.0.apk",
                   "sha256":"ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef0123456789","size":12345,"notes":"Melhorias","mandatory":false}}
    """.trimIndent()

    @Test
    fun helloEnviaJsonCorretoEInterpretaListas() = runBlocking {
        val http = FakePanel().apply { next = { HttpResponse(200, active) } }
        val client = PanelClient("https://painel.exemplo.com/", http)
        val r = client.hello("AB23-CD45-EF67", "segredo", "android", "1.1.0", "Moto G")
        val hello = (r as PanelCall.Ok).value
        val (req, body) = http.bodies.single()
        assertEquals("https://painel.exemplo.com/api/v1/device.php", req.url)
        assertFalse("segredo nunca na URL", req.url.contains("segredo"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("android", json["platform"]!!.jsonPrimitive.content)
        assertEquals("segredo", json["secret"]!!.jsonPrimitive.content)

        assertEquals(PanelStatus.ACTIVE, hello.status)
        assertEquals(3, hello.lists.size)
        assertEquals(" p 1 ", hello.lists[0].password)
        assertTrue(hello.allowUserLogin)
        val upd = hello.update!!
        assertEquals(5, upd.versionCode)
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", upd.sha256)

        val sources = PanelRules.sourcesFrom(hello)
        assertEquals(listOf("p7", "p8"), sources.map { it.profile.id })
        val x = sources[0]
        assertEquals(SourceType.XTREAM, x.profile.type)
        assertEquals("http://dns.exemplo.com:8080", x.profile.address)
        assertEquals(" p 1 ", x.secrets.password)
        assertTrue(x.profile.allowInsecureHttp)
        assertFalse("URL da M3U (com credenciais) não vai para o endereço exibido", sources[1].profile.address.contains("password"))
        assertEquals("https://cdn.exemplo.com/get.php?username=a&password=b&type=m3u", sources[1].secrets.playlistUrl)
    }

    @Test
    fun versaoMudaSoQuandoConteudoMuda() {
        val h = PanelClient.decodeHello(active)!!
        val a = PanelRules.sourcesFrom(h)[0].profile.updatedAtMillis
        val renamed = h.copy(lists = h.lists.map { if (it.id == 7L) it.copy(name = "Outro nome") else it })
        assertEquals(a, PanelRules.sourcesFrom(renamed)[0].profile.updatedAtMillis)
        val newPass = h.copy(lists = h.lists.map { if (it.id == 7L) it.copy(password = "nova") else it })
        assertNotEquals(a, PanelRules.sourcesFrom(newPass)[0].profile.updatedAtMillis)
        assertTrue(a > 0)
    }

    @Test
    fun cacheIdaEVolta() {
        val h = PanelClient.decodeHello(active)!!
        val back = PanelClient.decodeHello(PanelClient.encodeHello(h))
        assertEquals(h, back)
    }

    @Test
    fun listasSoQuandoAtivo() {
        val h = PanelClient.decodeHello(active)!!
        assertTrue(PanelRules.sourcesFrom(h.copy(status = PanelStatus.EXPIRED)).isEmpty())
        assertTrue(PanelRules.sourcesFrom(h.copy(status = PanelStatus.BLOCKED)).isEmpty())
        assertTrue(PanelRules.sourcesFrom(h.copy(status = PanelStatus.PENDING)).isEmpty())
    }

    @Test
    fun errosDoPainelENaRede() = runBlocking {
        val http = FakePanel()
        val client = PanelClient("https://painel.exemplo.com", http)
        http.next = { HttpResponse(401, """{"error":"invalid_credentials","message":"Usuário ou senha inválidos."}""") }
        val e1 = client.login("AB23-CD45-EF67", "s", "u", "p") as PanelCall.Fail
        assertEquals(401, e1.httpStatus); assertEquals("invalid_credentials", e1.code); assertEquals("Usuário ou senha inválidos.", e1.message)

        http.next = { HttpResponse(429, "<html>") }
        val e2 = client.login("AB23-CD45-EF67", "s", "u", "p") as PanelCall.Fail
        assertEquals("http_429", e2.code)

        http.next = { throw HttpFailure.Timeout() }
        assertEquals("timeout", (client.login("AB23-CD45-EF67", "s", "u", "p") as PanelCall.Fail).code)
        http.next = { throw HttpFailure.Connection("x") }
        assertEquals(0, (client.hello("AB23-CD45-EF67", "s", "android", "1", "m") as PanelCall.Fail).httpStatus)

        http.next = { HttpResponse(200, """{"foo":1}""") }
        assertEquals("invalid_response", (client.hello("AB23-CD45-EF67", "s", "android", "1", "m") as PanelCall.Fail).code)

        http.next = { HttpResponse(200, """{"status":"ok","dns_id":3,"server":"http://dns2.exemplo.com:80","expires_at":null}""") }
        val ok = (client.login("AB23-CD45-EF67", "s", "cliente", "senha") as PanelCall.Ok).value
        assertEquals(3L, ok.dnsId)
        val src = PanelRules.loginSource("cliente", "senha", ok, 1000)!!
        assertTrue(src.profile.id.startsWith("l"))
        assertEquals(src.profile.id, PanelRules.loginSourceId("cliente", 3))
        assertEquals("senha", src.secrets.password)
    }

    @Test
    fun painelSemHttpsERecusado() {
        try {
            PanelClient("http://painel.exemplo.com", FakePanel())
            fail("deveria recusar HTTP")
        } catch (_: IllegalArgumentException) {
        }
        PanelClient("http://127.0.0.1:8080", FakePanel()) // testes locais
    }

    @Test
    fun atualizacaoSoComHttpsEVersaoMaior() {
        val h = PanelClient.decodeHello(active)!!
        assertEquals(5, PanelRules.updateAvailable(h, 4)!!.versionCode)
        assertNull(PanelRules.updateAvailable(h, 5))
        val insecure = PanelClient.decodeHello(active.replace("https://painel.exemplo.com/apk", "http://painel.exemplo.com/apk"))!!
        assertNull(insecure.update)
        val badSha = PanelClient.decodeHello(active.replace("ABCDEF0123", "XYZ"))!!
        assertNull(badSha.update!!.sha256)
    }

    @Test
    fun fasesDoApp() {
        val h = PanelClient.decodeHello(active)!!
        fun phase(hello: PanelHello?, reachable: Boolean = true, usable: Boolean = true, login: Boolean = false, code: Int = 5) =
            PanelRules.phase(true, hello, reachable, usable, login, code)
        assertEquals(AppPhase.STARTING, PanelRules.phase(false, h, true, true, false, 5))
        assertEquals(AppPhase.READY, phase(h))
        assertEquals(AppPhase.BLOCKED, phase(h.copy(status = PanelStatus.BLOCKED), login = true))
        assertEquals(AppPhase.EXPIRED, phase(h.copy(status = PanelStatus.EXPIRED, lists = emptyList())))
        assertEquals(AppPhase.READY, phase(h.copy(status = PanelStatus.EXPIRED), login = true))
        assertEquals(AppPhase.PENDING, phase(h.copy(status = PanelStatus.PENDING, lists = emptyList())))
        assertEquals(AppPhase.OFFLINE, phase(null, reachable = false))
        assertEquals(AppPhase.PENDING, phase(h, usable = false))
        assertEquals(AppPhase.OFFLINE, phase(h, reachable = false, usable = false))
        assertEquals(AppPhase.READY, phase(h, reachable = false, usable = true))
        val mandatory = h.copy(update = h.update!!.copy(mandatory = true))
        assertEquals(AppPhase.UPDATE_REQUIRED, phase(mandatory, code = 4))
        assertEquals(AppPhase.READY, phase(mandatory, code = 5))
        assertEquals(15_000L, PanelRules.nextPollMs(h.copy(status = PanelStatus.PENDING)))
        assertEquals(21_600_000L, PanelRules.nextPollMs(h))
    }

    @Test
    fun identidadeDoAparelho() {
        val r = SecureRandom()
        val ids = (1..500).map { DeviceIdentity.newDeviceId(r) }
        ids.forEach { assertTrue(it, DeviceIdentity.isValidId(it)) }
        assertTrue(ids.toSet().size > 495)
        val s = DeviceIdentity.newSecret(r)
        assertEquals(48, s.length)
        assertTrue(s.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(DeviceIdentity.create(r).toString().contains(s))
    }
}
