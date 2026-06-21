package it.bigbenmatic.gamelauncher

import android.content.Context

/** Local cache of the last fleet config received, plus diagnostics data shown on the
 * hidden Diagnostics screen (Module 2) so an operator can read the device identity
 * and connection health without ADB. */
class DevicePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("fleet_prefs", Context.MODE_PRIVATE)

    fun getCachedConfigJson(): String? = prefs.getString(KEY_CONFIG_JSON, null)

    fun setCachedConfigJson(json: String) {
        prefs.edit().putString(KEY_CONFIG_JSON, json).apply()
    }

    fun getConfigVersion(): Int = prefs.getInt(KEY_CONFIG_VERSION, -1)

    fun setConfigVersion(version: Int) {
        prefs.edit().putInt(KEY_CONFIG_VERSION, version).apply()
    }

    fun getLastTelemetrySuccessMillis(): Long = prefs.getLong(KEY_LAST_TELEMETRY_OK, 0L)

    fun setLastTelemetrySuccessMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_TELEMETRY_OK, millis).apply()
    }

    fun getLastConnectionStatus(): String = prefs.getString(KEY_LAST_CONN_STATUS, "mai contattato") ?: "mai contattato"

    fun setLastConnectionStatus(status: String) {
        prefs.edit().putString(KEY_LAST_CONN_STATUS, status).apply()
    }

    /** Local-only lifeline Wi-Fi (never sent anywhere): the safety-net network the device
     * falls back to so it can always reach the remote config, even if the venue Wi-Fi changes. */
    fun getLifelineWifi(): WifiNetwork? {
        val ssid = prefs.getString(KEY_WIFI_SSID, null)?.takeIf { it.isNotBlank() } ?: return null
        return WifiNetwork(
            ssid = ssid,
            password = prefs.getString(KEY_WIFI_PASS, null),
            priority = 0,
            hidden = prefs.getBoolean(KEY_WIFI_HIDDEN, false),
        )
    }

    fun setLifelineWifi(ssid: String, password: String, hidden: Boolean) {
        prefs.edit()
            .putString(KEY_WIFI_SSID, ssid)
            .putString(KEY_WIFI_PASS, password)
            .putBoolean(KEY_WIFI_HIDDEN, hidden)
            .apply()
    }

    companion object {
        private const val KEY_CONFIG_JSON = "cached_config_json"
        private const val KEY_CONFIG_VERSION = "cached_config_version"
        private const val KEY_LAST_TELEMETRY_OK = "last_telemetry_success_millis"
        private const val KEY_LAST_CONN_STATUS = "last_connection_status"
        private const val KEY_WIFI_SSID = "lifeline_wifi_ssid"
        private const val KEY_WIFI_PASS = "lifeline_wifi_pass"
        private const val KEY_WIFI_HIDDEN = "lifeline_wifi_hidden"
    }
}
