package it.bigbenmatic.gamelauncher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.HttpURLConnection
import java.net.URL

/**
 * URL of the static `config.json` published on GitHub Pages (Module 1). Hosted under a
 * dedicated `/launcher/` path so it never collides with the repo's existing root site.
 * Replace with the real GitHub Pages URL for this repo/account once Pages is enabled.
 */
const val FLEET_CONFIG_URL = "https://kevodable.github.io/bigben-documenti/launcher/config.json"

enum class ConnectionStatus { ONLINE, OFFLINE }

/** Downloads and caches the remote fleet config (Module 1). Offline-first: the last
 * successfully parsed config survives app/process restarts and is used immediately
 * while a fresh copy is fetched in the background. */
class ConfigRepository(context: Context) {
    private val appContext = context.applicationContext
    private val devicePrefs = DevicePrefs(appContext)
    private val deviceId = DeviceIdManager.getDeviceId(appContext)

    private val _config = MutableStateFlow<ResolvedConfig?>(loadCached())
    val config: StateFlow<ResolvedConfig?> = _config

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.OFFLINE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private fun loadCached(): ResolvedConfig? {
        val cached = devicePrefs.getCachedConfigJson() ?: return null
        return runCatching { RemoteConfigParser.parse(cached, deviceId) }.getOrNull()
    }

    /** Fetches config.json, applies it only if `configVersion` changed, and persists it
     * for offline use. Safe to call repeatedly from a polling loop. */
    fun refresh() {
        val raw = runCatching { download(FLEET_CONFIG_URL) }.getOrNull()
        if (raw == null) {
            _connectionStatus.value = ConnectionStatus.OFFLINE
            return
        }
        _connectionStatus.value = ConnectionStatus.ONLINE

        val parsed = runCatching { RemoteConfigParser.parse(raw, deviceId) }.getOrNull()
        if (parsed == null) {
            Log.w(TAG, "Config JSON non valido, ignorato")
            return
        }

        val previousVersion = devicePrefs.getConfigVersion()
        if (parsed.configVersion == previousVersion) {
            return // nothing changed, skip re-apply
        }

        devicePrefs.setCachedConfigJson(raw)
        devicePrefs.setConfigVersion(parsed.configVersion)
        _config.value = parsed
        Log.i(TAG, "Config aggiornata: v$previousVersion -> v${parsed.configVersion}")
    }

    private fun download(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "ConfigRepository"
    }
}
