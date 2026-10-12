package com.samdevelop.samtv.core.panel

import com.samdevelop.samtv.core.catalog.StableIds
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.net.UrlRules
import java.security.SecureRandom
import java.util.Base64

/** Fonte pronta para uso, vinda do painel ou do login por usuário/senha. */
data class ManagedSource(val profile: ConnectionProfile, val secrets: SourceSecrets)

/** Fase do app, decidida a partir do painel (mesma regra do app de Windows). */
enum class AppPhase { STARTING, PENDING, READY, EXPIRED, BLOCKED, OFFLINE, UPDATE_REQUIRED }

object PanelRules {
    const val PANEL_PREFIX = "p"
    const val LOGIN_PREFIX = "l"

    /** Sem contato com o painel, as listas salvas continuam valendo por até 7 dias. */
    const val OFFLINE_GRACE_MS = 7L * 24 * 3600 * 1000

    /** Intervalos de consulta ao painel. */
    const val PENDING_POLL_MS = 15_000L
    const val ERROR_RETRY_MS = 60_000L
    const val DEFAULT_REFRESH_S = 6 * 3600

    fun isPanelSource(id: String) = id.startsWith(PANEL_PREFIX)
    fun isLoginSource(id: String) = id.startsWith(LOGIN_PREFIX)

    /**
     * Converte as listas do painel em fontes. Só status ACTIVE entrega listas.
     * A "versão" de cada fonte (updatedAtMillis) é um hash do conteúdo: o conector e o catálogo
     * só são refeitos quando endereço, usuário ou segredo mudam.
     */
    fun sourcesFrom(hello: PanelHello): List<ManagedSource> {
        if (hello.status != PanelStatus.ACTIVE) return emptyList()
        return hello.lists.mapNotNull { l ->
            val id = "$PANEL_PREFIX${l.id}"
            when (l.type) {
                PanelListType.XTREAM -> {
                    val server = l.server ?: return@mapNotNull null
                    val user = l.username ?: return@mapNotNull null
                    val pass = l.password ?: return@mapNotNull null
                    val base = (UrlRules.normalizeXtreamBase(server) as? AppResult.Success)?.value?.value ?: return@mapNotNull null
                    val secrets = SourceSecrets(password = pass)
                    ManagedSource(
                        // O DNS foi cadastrado pelo administrador: ele decide se o servidor usa HTTP.
                        ConnectionProfile(id, l.name, SourceType.XTREAM, base, user, true, 0, versionOf(base, user, pass)),
                        secrets,
                    )
                }
                PanelListType.M3U -> {
                    val url = l.playlistUrl ?: return@mapNotNull null
                    val valid = (UrlRules.validatePlaylistUrl(url) as? AppResult.Success)?.value?.value ?: return@mapNotNull null
                    ManagedSource(
                        ConnectionProfile(id, l.name, SourceType.M3U, UrlRules.displayAddress(valid), null, true, 0, versionOf(valid, "", "")),
                        SourceSecrets(playlistUrl = valid),
                    )
                }
            }
        }
    }

    fun loginSource(username: String, password: String, login: PanelLoginOk, nowMillis: Long): ManagedSource? {
        val base = (UrlRules.normalizeXtreamBase(login.server) as? AppResult.Success)?.value?.value ?: return null
        val id = loginSourceId(username, login.dnsId)
        return ManagedSource(
            ConnectionProfile(id, username, SourceType.XTREAM, base, username, true, nowMillis, versionOf(base, username, password)),
            SourceSecrets(password = password),
        )
    }

    fun loginSourceId(username: String, dnsId: Long): String = LOGIN_PREFIX + StableIds.sha1("$username|$dnsId").take(12)

    /** Hash positivo e estável do conteúdo da fonte. */
    fun versionOf(address: String, user: String, secret: String): Long =
        StableIds.sha1("$address\u0000$user\u0000$secret").take(12).toLong(16)

    /**
     * Fase do app.
     * @param hello última resposta do painel (ou a salva) — null se nunca houve contato
     * @param hasLoginSources fontes de login por usuário/senha continuam valendo com o aparelho vencido
     * @param panelSourcesUsable as listas do painel ainda valem (dentro da tolerância offline)
     */
    fun phase(
        started: Boolean,
        hello: PanelHello?,
        panelReachable: Boolean,
        panelSourcesUsable: Boolean,
        hasLoginSources: Boolean,
        currentVersionCode: Int,
    ): AppPhase {
        if (!started) return AppPhase.STARTING
        if (hello?.status == PanelStatus.BLOCKED) return AppPhase.BLOCKED
        val update = hello?.update
        if (update != null && update.mandatory && update.versionCode > currentVersionCode) return AppPhase.UPDATE_REQUIRED
        val hasPanelSources = panelSourcesUsable && hello != null && sourcesFrom(hello).isNotEmpty()
        if (hasPanelSources || hasLoginSources) return AppPhase.READY
        if (hello?.status == PanelStatus.EXPIRED) return AppPhase.EXPIRED
        // Aparelho ativo, mas sem falar com o painel além da tolerância: pede internet, não o código.
        if (hello?.status == PanelStatus.ACTIVE && !panelReachable) return AppPhase.OFFLINE
        if (hello != null) return AppPhase.PENDING
        return if (panelReachable) AppPhase.PENDING else AppPhase.OFFLINE
    }

    fun updateAvailable(hello: PanelHello?, currentVersionCode: Int): AppUpdateInfo? =
        hello?.update?.takeIf { it.versionCode > currentVersionCode }

    /** Próxima consulta ao painel depois de uma resposta. */
    fun nextPollMs(hello: PanelHello): Long = when (hello.status) {
        PanelStatus.PENDING -> PENDING_POLL_MS
        else -> (hello.refreshAfterSeconds ?: DEFAULT_REFRESH_S).coerceAtLeast(10) * 1000L
    }
}

/** Identidade do aparelho no painel. O segredo nunca é exibido. */
data class DeviceIdentity(val deviceId: String, val secret: String, val registered: Boolean) {
    override fun toString(): String = "DeviceIdentity(deviceId=$deviceId, registered=$registered)"

    companion object {
        private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private val DEVICE_ID = Regex("^[$ALPHABET]{4}-[$ALPHABET]{4}-[$ALPHABET]{4}$")

        fun newDeviceId(random: SecureRandom = SecureRandom()): String {
            val s = CharArray(12) { ALPHABET[random.nextInt(ALPHABET.length)] }.concatToString()
            return "${s.substring(0, 4)}-${s.substring(4, 8)}-${s.substring(8, 12)}"
        }

        /** 36 bytes aleatórios em base64url = 48 caracteres [A-Za-z0-9_-]. */
        fun newSecret(random: SecureRandom = SecureRandom()): String {
            val bytes = ByteArray(36).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun create(random: SecureRandom = SecureRandom()) = DeviceIdentity(newDeviceId(random), newSecret(random), registered = false)

        fun isValidId(id: String) = DEVICE_ID.matches(id)
    }
}
