package com.samdevelop.samtv.core.panel

/*
 * Modelos da API do painel SAM TV (painel/docs/API.md). O mesmo painel atende Windows e Android;
 * o campo "platform" diz qual app está falando.
 */

enum class PanelStatus { PENDING, ACTIVE, EXPIRED, BLOCKED, UNKNOWN }

enum class PanelListType { XTREAM, M3U }

/** Lista cadastrada no painel para este aparelho. Contém credenciais: nunca registrar em log. */
data class PanelList(
    val id: Long,
    val name: String,
    val type: PanelListType,
    val server: String?,
    val username: String?,
    val password: String?,
    val playlistUrl: String?,
    val updatedAt: String?,
) {
    override fun toString(): String = "PanelList(id=$id, name=$name, type=$type)"
}

/**
 * Atualização do app publicada no painel (campo opcional `update` do device.php).
 * O app só oferece quando [versionCode] é maior que o instalado e [url] usa HTTPS.
 */
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    /** SHA-256 do APK em hexadecimal (recomendado). Quando presente, o download é conferido. */
    val sha256: String?,
    val sizeBytes: Long?,
    val notes: String?,
    /** Obrigatória: o app não deixa usar as listas até atualizar. */
    val mandatory: Boolean,
)

data class PanelHello(
    val status: PanelStatus,
    val deviceId: String,
    val code: String?,
    val customer: String?,
    /** ISO 8601 (ou "AAAA-MM-DD HH:MM:SS") ou null = sem vencimento. */
    val expiresAt: String?,
    val lists: List<PanelList>,
    val message: String?,
    val allowUserLogin: Boolean,
    val refreshAfterSeconds: Int?,
    val serverTime: String?,
    val update: AppUpdateInfo?,
)

data class PanelLoginOk(val dnsId: Long, val server: String, val expiresAt: String?)

/** Resultado de uma chamada ao painel. */
sealed interface PanelCall<out T> {
    data class Ok<T>(val value: T) : PanelCall<T>

    /**
     * [httpStatus] 0 = falha de rede. [code] é o código de erro do painel (ex.: invalid_device, rate_limited)
     * ou um código local (timeout, network, tls, invalid_response). [message] já está pronta para o usuário.
     */
    data class Fail(val httpStatus: Int, val code: String, val message: String) : PanelCall<Nothing>
}
