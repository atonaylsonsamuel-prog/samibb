package com.samdevelop.samtv.core.panel

import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.core.xtream.arr
import com.samdevelop.samtv.core.xtream.bool
import com.samdevelop.samtv.core.xtream.int
import com.samdevelop.samtv.core.xtream.long
import com.samdevelop.samtv.core.xtream.obj
import com.samdevelop.samtv.core.xtream.objects
import com.samdevelop.samtv.core.xtream.str
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/**
 * Cliente da API do painel (POST JSON em /api/v1/). Só HTTPS, exceto localhost (testes).
 * O segredo do aparelho vai no corpo, nunca na URL.
 */
class PanelClient(baseUrl: String, private val http: HttpClient) {

    private val base: String = baseUrl.trim().trimEnd('/')

    init {
        val uri = URI(base)
        val local = uri.host == "localhost" || uri.host == "127.0.0.1" || uri.host == "10.0.2.2"
        require(uri.scheme == "https" || (uri.scheme == "http" && local)) { "O painel precisa usar HTTPS" }
    }

    private val localHttp = base.startsWith("http://")

    suspend fun hello(deviceId: String, secret: String, platform: String, appVersion: String, model: String): PanelCall<PanelHello> =
        post(
            "device.php",
            mapOf("device_id" to deviceId, "secret" to secret, "platform" to platform, "app_version" to appVersion, "model" to model),
        ) { parseHello(it) }

    suspend fun login(deviceId: String, secret: String, username: String, password: String): PanelCall<PanelLoginOk> =
        post(
            "login.php",
            mapOf("device_id" to deviceId, "secret" to secret, "username" to username, "password" to password),
        ) { o ->
            val server = o.str("server") ?: return@post null
            PanelLoginOk(dnsId = o.long("dns_id") ?: 0, server = server, expiresAt = o.str("expires_at"))
        }

    private suspend fun <T> post(endpoint: String, body: Map<String, String>, parse: (JsonObject) -> T?): PanelCall<T> {
        val json = JsonObject(body.mapValues { JsonPrimitive(it.value) }).toString()
        val request = HttpRequest(
            url = "$base/api/v1/$endpoint",
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            containsCredentials = true,
            allowInsecureHttp = localHttp,
        )
        val response = try {
            http.postJson(request, json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpFailure.Timeout) {
            return PanelCall.Fail(0, "timeout", "O painel demorou para responder.")
        } catch (e: HttpFailure.Tls) {
            return PanelCall.Fail(0, "tls", "Certificado de segurança do painel inválido.")
        } catch (e: Exception) {
            return PanelCall.Fail(0, "network", "Sem conexão com o painel. Verifique a internet.")
        }
        val obj = try {
            Json.parseToJsonElement(response.body) as? JsonObject
        } catch (e: Exception) {
            null
        }
        if (!response.isSuccessful) {
            val code = obj?.str("error") ?: "http_${response.code}"
            val message = obj?.str("message") ?: defaultMessage(code, response.code)
            return PanelCall.Fail(response.code, code, message)
        }
        val value = obj?.let(parse)
            ?: return PanelCall.Fail(response.code, "invalid_response", "O painel respondeu em formato inesperado.")
        return PanelCall.Ok(value)
    }

    companion object {
        fun parseHello(o: JsonObject): PanelHello? {
            val status = when (o.str("status")?.lowercase()) {
                "pending" -> PanelStatus.PENDING
                "active" -> PanelStatus.ACTIVE
                "expired" -> PanelStatus.EXPIRED
                "blocked" -> PanelStatus.BLOCKED
                null -> return null
                else -> PanelStatus.UNKNOWN
            }
            val lists = o.arr("lists")?.objects().orEmpty().mapNotNull { l ->
                val type = when (l.str("type")?.lowercase()) {
                    "xtream" -> PanelListType.XTREAM
                    "m3u" -> PanelListType.M3U
                    else -> return@mapNotNull null
                }
                PanelList(
                    id = l.long("id") ?: return@mapNotNull null,
                    name = l.str("name") ?: "Lista",
                    type = type,
                    server = l.str("server"),
                    username = l.str("username"),
                    password = rawStr(l, "password"),
                    playlistUrl = l.str("playlist_url"),
                    updatedAt = l.str("updated_at"),
                )
            }
            return PanelHello(
                status = status,
                deviceId = o.str("device_id").orEmpty(),
                code = o.str("code"),
                customer = o.str("customer"),
                expiresAt = o.str("expires_at"),
                lists = lists,
                message = o.str("message"),
                allowUserLogin = o.bool("allow_user_login"),
                refreshAfterSeconds = o.int("refresh_after"),
                serverTime = o.str("server_time"),
                update = o.obj("update")?.let(::parseUpdate),
            )
        }

        fun parseUpdate(u: JsonObject): AppUpdateInfo? {
            val code = u.int("version_code") ?: return null
            val url = u.str("url") ?: u.str("apk_url") ?: return null
            if (!url.startsWith("https://", ignoreCase = true)) return null
            val sha = u.str("sha256")?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            return AppUpdateInfo(
                versionCode = code,
                versionName = u.str("version_name") ?: code.toString(),
                url = url,
                sha256 = sha,
                sizeBytes = u.long("size"),
                notes = u.str("notes"),
                mandatory = u.bool("mandatory"),
            )
        }

        /** Serializa no mesmo formato da API, para o cache local (gravado cifrado pelo app). */
        fun encodeHello(h: PanelHello): String {
            fun p(v: String?) = if (v == null) kotlinx.serialization.json.JsonNull else JsonPrimitive(v)
            val lists = kotlinx.serialization.json.JsonArray(
                h.lists.map { l ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(l.id), "name" to p(l.name), "type" to p(l.type.name.lowercase()),
                            "server" to p(l.server), "username" to p(l.username), "password" to p(l.password),
                            "playlist_url" to p(l.playlistUrl), "updated_at" to p(l.updatedAt),
                        ),
                    )
                },
            )
            val update = h.update?.let { u ->
                JsonObject(
                    mapOf(
                        "version_code" to JsonPrimitive(u.versionCode), "version_name" to p(u.versionName), "url" to p(u.url),
                        "sha256" to p(u.sha256), "size" to (u.sizeBytes?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull),
                        "notes" to p(u.notes), "mandatory" to JsonPrimitive(u.mandatory),
                    ),
                )
            } ?: kotlinx.serialization.json.JsonNull
            return JsonObject(
                mapOf(
                    "status" to p(h.status.name.lowercase()), "device_id" to p(h.deviceId), "code" to p(h.code),
                    "customer" to p(h.customer), "expires_at" to p(h.expiresAt), "lists" to lists, "message" to p(h.message),
                    "allow_user_login" to JsonPrimitive(h.allowUserLogin),
                    "refresh_after" to (h.refreshAfterSeconds?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull),
                    "server_time" to p(h.serverTime), "update" to update,
                ),
            ).toString()
        }

        fun decodeHello(json: String): PanelHello? = try {
            (Json.parseToJsonElement(json) as? JsonObject)?.let(::parseHello)
        } catch (e: Exception) {
            null
        }

        /** Senhas podem ter espaços nas pontas: não aparar. */
        private fun rawStr(o: JsonObject, key: String): String? =
            (o[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it.isNotEmpty() }

        private fun defaultMessage(code: String, http: Int): String = when (code) {
            "invalid_credentials" -> "Usuário ou senha incorretos."
            "account_expired" -> "Conta vencida."
            "account_disabled" -> "Conta desativada."
            "feature_disabled" -> "O login por usuário e senha está desativado no painel."
            "device_blocked" -> "Este aparelho está bloqueado."
            "unknown_device" -> "Aparelho ainda não registrado no painel."
            "no_servers", "servers_unavailable" -> "Nenhum servidor disponível no momento."
            "rate_limited" -> "Muitas tentativas. Aguarde um minuto."
            "https_required" -> "O painel exige conexão segura (HTTPS)."
            else -> "Erro $http no painel."
        }
    }
}
