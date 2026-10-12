package com.samdevelop.samtv.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.samdevelop.samtv.core.model.SourceSecrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Armazenamento das credenciais das fontes (senha Xtream e URL completa da playlist M3U).
 *
 * Estratégia:
 *  - chave AES-256 gerada e mantida no Android Keystore (não exportável; pode ficar em hardware seguro);
 *  - cada segredo é cifrado com AES/GCM (IV aleatório por gravação, autenticação de integridade);
 *  - o texto cifrado é gravado em SharedPreferences privadas, separadas do Room e do DataStore;
 *  - backup em nuvem desativado (ver data_extraction_rules.xml).
 *
 * Limitação (documentada no README): criptografia local reduz a exposição em caso de cópia de
 * arquivos, mas não protege contra um aparelho comprometido (root/malware com acesso ao processo).
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun save(profileId: String, secrets: SourceSecrets) {
        val payload = JsonObject(
            buildMap {
                secrets.password?.let { put("p", JsonPrimitive(it)) }
                secrets.playlistUrl?.let { put("u", JsonPrimitive(it)) }
            },
        ).toString()
        saveText(profileId, payload)
    }

    suspend fun load(profileId: String): SourceSecrets? {
        val text = loadText(profileId) ?: return null
        return try {
            val json = Json.parseToJsonElement(text).jsonObject
            SourceSecrets(
                password = json["p"]?.jsonPrimitive?.contentOrNull,
                playlistUrl = json["u"]?.jsonPrimitive?.contentOrNull,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Texto arbitrário cifrado (identidade do aparelho, cache do painel). */
    suspend fun saveText(name: String, text: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + encrypted
        if (!prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()) {
            throw IllegalStateException("falha ao gravar")
        }
        Unit
    }

    suspend fun loadText(name: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(name, null) ?: return@withContext null
        try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, IV_SIZE)
            val data = blob.copyOfRange(IV_SIZE, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            // Chave invalidada (ex.: restauração de aparelho) ou dado corrompido. Nunca registramos o conteúdo.
            null
        }
    }

    suspend fun delete(profileId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(profileId).commit()
        Unit
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "samtv_source_credentials_v1"
        const val PREFS = "samtv_secure_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
