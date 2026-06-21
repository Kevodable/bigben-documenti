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

    companion object {
        private const val KEY_CONFIG_JSON = "cached_config_json"
        private const val KEY_CONFIG_VERSION = "cached_config_version"
        private const val KEY_LAST_TELEMETRY_OK = "last_telemetry_success_millis"
        private const val KEY_LAST_CONN_STATUS = "last_connection_status"
    }
}
