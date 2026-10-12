package com.samdevelop.samtv.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.samdevelop.samtv.core.panel.AppUpdateInfo
import com.samdevelop.samtv.data.remote.OkHttpHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    /** [progress] de 0 a 1, ou null quando o servidor não informa o tamanho. */
    data class Downloading(val info: AppUpdateInfo, val progress: Float?) : UpdateState
    data class Ready(val info: AppUpdateInfo) : UpdateState
    data class Failed(val info: AppUpdateInfo, val message: String) : UpdateState
}

/**
 * Atualização do app publicada no painel:
 *  1. baixa o APK (somente HTTPS, sem redirecionar para HTTP) para o cache privado do app;
 *  2. confere o SHA-256 informado pelo painel, o nome do pacote e a versão;
 *  3. entrega ao instalador do Android, que também exige a MESMA assinatura do app instalado.
 */
class UpdateManager(
    private val context: Context,
    baseOkHttp: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val client = baseOkHttp.newBuilder()
        .followRedirects(true)
        .followSslRedirects(false)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    private val dir get() = File(context.cacheDir, "updates")
    private val apk get() = File(dir, "samtv-update.apk")

    fun download(info: AppUpdateInfo) {
        val current = _state.value
        if (current is UpdateState.Downloading) return
        if (current is UpdateState.Ready && current.info.versionCode == info.versionCode && apk.exists()) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, null)
            _state.value = try {
                fetch(info)
                verify(info)
                UpdateState.Ready(info)
            } catch (e: CancellationException) {
                apk.delete()
                throw e
            } catch (e: UpdateException) {
                apk.delete()
                UpdateState.Failed(info, e.message ?: "Falha na atualização.")
            } catch (e: Exception) {
                apk.delete()
                UpdateState.Failed(info, "Não foi possível baixar a atualização. Verifique a internet e tente de novo.")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    private suspend fun fetch(info: AppUpdateInfo) = withContext(Dispatchers.IO) {
        if (!info.url.startsWith("https://", ignoreCase = true)) throw UpdateException("Endereço de atualização inseguro.")
        dir.mkdirs()
        apk.delete()
        val request = Request.Builder().url(info.url).header("User-Agent", OkHttpHttpClient.USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.request.url.isHttps) throw UpdateException("Endereço de atualização inseguro.")
            if (!response.isSuccessful) throw UpdateException("O servidor da atualização respondeu com erro ${response.code}.")
            val body = response.body ?: throw UpdateException("Arquivo de atualização vazio.")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            if (total != null && total > MAX_APK_BYTES) throw UpdateException("Arquivo de atualização grande demais.")
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            var lastEmit = 0L
            body.byteStream().use { input ->
                apk.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (read > MAX_APK_BYTES) throw UpdateException("Arquivo de atualização grande demais.")
                        if (read - lastEmit > 256 * 1024) {
                            lastEmit = read
                            _state.value = UpdateState.Downloading(info, total?.let { (read.toFloat() / it).coerceIn(0f, 1f) })
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && !sha.equals(info.sha256, ignoreCase = true)) {
                throw UpdateException("O arquivo baixado não confere com o publicado no painel (SHA-256). Tente de novo.")
            }
        }
        Unit
    }

    private fun verify(info: AppUpdateInfo) {
        val pkg = archiveInfo(apk) ?: throw UpdateException("O arquivo baixado não é um APK válido.")
        if (pkg.packageName != context.packageName) {
            throw UpdateException("Este APK é de outro aplicativo (${pkg.packageName}).")
        }
        val code = PackageInfoCompat.getLongVersionCode(pkg)
        val installed = PackageInfoCompat.getLongVersionCode(installedInfo())
        if (code <= installed) throw UpdateException("O APK publicado não é mais novo que a versão instalada.")
        if (code < info.versionCode) throw UpdateException("A versão do APK não confere com a publicada no painel.")
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): PackageInfo? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        else pm.getPackageArchiveInfo(file.path, 0)
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(): PackageInfo {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        else pm.getPackageInfo(context.packageName, 0)
    }

    sealed interface InstallResult {
        data object Started : InstallResult
        /** O usuário precisa permitir "Instalar apps desconhecidos" para o SAM TV e tocar em Instalar de novo. */
        data object NeedsPermission : InstallResult
        data class Error(val message: String) : InstallResult
    }

    fun install(activity: Activity): InstallResult {
        if (_state.value !is UpdateState.Ready || !apk.exists()) return InstallResult.Error("Baixe a atualização primeiro.")
        if (!activity.packageManager.canRequestPackageInstalls()) {
            return try {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
                )
                InstallResult.NeedsPermission
            } catch (e: ActivityNotFoundException) {
                InstallResult.Error("Permita a instalação de apps desconhecidos para o SAM TV nas configurações do aparelho.")
            }
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.startActivity(intent)
            InstallResult.Started
        } catch (e: ActivityNotFoundException) {
            InstallResult.Error("Este aparelho não tem instalador de pacotes disponível.")
        }
    }

    private class UpdateException(message: String) : Exception(message)

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val MAX_APK_BYTES = 300L * 1024 * 1024
    }
}
