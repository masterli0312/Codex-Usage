package com.codex.quota.security

import android.content.Context
import android.content.SharedPreferences

interface CredentialStore {
    fun storeApiKey(accountId: String, apiKey: String)
    fun getApiKey(accountId: String): String?
    fun storeOAuthTokens(accountId: String, accessToken: String, refreshToken: String, clientId: String)
    fun getRefreshToken(accountId: String): String?
    fun getOAuthClientId(accountId: String): String?
    fun removeApiKey(accountId: String)
    fun clearAll()
}

class EncryptedCredentialStore(
    context: Context,
    private val keystoreManager: KeystoreManager
) : CredentialStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun storeApiKey(accountId: String, apiKey: String) {
        val encrypted = keystoreManager.encrypt(apiKey)
        prefs.edit().putString(KEY_PREFIX + accountId, encrypted)
            .remove(REFRESH_PREFIX + accountId).remove(CLIENT_PREFIX + accountId).apply()
    }

    override fun storeOAuthTokens(accountId: String, accessToken: String, refreshToken: String, clientId: String) {
        val encryptedAccess = keystoreManager.encrypt(accessToken)
        val encryptedRefresh = keystoreManager.encrypt(refreshToken)
        prefs.edit().putString(KEY_PREFIX + accountId, encryptedAccess)
            .putString(REFRESH_PREFIX + accountId, encryptedRefresh)
            .putString(CLIENT_PREFIX + accountId, clientId).apply()
    }

    override fun getRefreshToken(accountId: String): String? =
        prefs.getString(REFRESH_PREFIX + accountId, null)?.let(keystoreManager::decrypt)

    override fun getOAuthClientId(accountId: String): String? = prefs.getString(CLIENT_PREFIX + accountId, null)

    override fun getApiKey(accountId: String): String? {
        val encrypted = prefs.getString(KEY_PREFIX + accountId, null) ?: return null
        return keystoreManager.decrypt(encrypted)
    }

    override fun removeApiKey(accountId: String) {
        prefs.edit().remove(KEY_PREFIX + accountId)
            .remove(REFRESH_PREFIX + accountId).remove(CLIENT_PREFIX + accountId).apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "secure_credentials"
        private const val KEY_PREFIX = "key_acc_"
        private const val REFRESH_PREFIX = "refresh_acc_"
        private const val CLIENT_PREFIX = "oauth_client_acc_"
    }
}
