package com.samdevelop.samtv.data.panel

import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.panel.ManagedSource
import com.samdevelop.samtv.core.panel.ManagedSourceStore
import com.samdevelop.samtv.core.panel.SecretTextStore
import com.samdevelop.samtv.data.local.AppPreferences
import com.samdevelop.samtv.data.repository.AuthenticationRepositoryImpl
import com.samdevelop.samtv.security.CredentialStore
import kotlinx.coroutines.flow.Flow

/** Identidade do aparelho e cache do painel cifrados com a chave do Android Keystore. */
class KeystoreTextStore(private val store: CredentialStore) : SecretTextStore {
    override suspend fun loadText(name: String): String? = store.loadText(name)
    override suspend fun saveText(name: String, text: String) = store.saveText(name, text)
}

/** Fontes do painel gravadas no Room (dados) + Keystore (senhas e URLs). */
class RoomManagedSourceStore(
    private val auth: AuthenticationRepositoryImpl,
    private val prefs: AppPreferences,
) : ManagedSourceStore {
    override val profiles: Flow<List<ConnectionProfile>> = auth.profiles
    override suspend fun all(): List<ConnectionProfile> = auth.allProfiles()
    override suspend fun upsert(source: ManagedSource) = auth.upsertManaged(source.profile, source.secrets)
    override suspend fun remove(id: String) = auth.removeManaged(id)
    override suspend fun activeId(): String? = prefs.activeProfileIdNow()
    override suspend fun setActive(id: String?) = prefs.setActiveProfile(id)
}
