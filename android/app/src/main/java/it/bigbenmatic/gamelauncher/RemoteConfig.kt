package it.bigbenmatic.gamelauncher

import org.json.JSONArray
import org.json.JSONObject

data class Branding(
    val appTitle: String = "Kids Fun Planet",
    val logoUrl: String? = null,
    val backgroundUrl: String? = null,
    val primaryColor: String? = null,
    val accentColor: String? = null,
    val language: String = "it",
)

data class Layout(
    val columns: Int = 0, // 0 = adaptive (current behaviour)
    val tileSize: String = "large",
    val showLabels: Boolean = true,
)

data class Kiosk(
    val enabled: Boolean = true,
    val exitPin: String? = null,
    val idleTimeoutSeconds: Int = 0,
    val sessionLimitSeconds: Int = 0,
    val maxVolumePercent: Int = 100,
    val brightnessPercent: Int = 100,
    val autoLaunchPackage: String? = null,
)

data class Banner(
    val enabled: Boolean = false,
    val text: String = "",
    val imageUrl: String = "",
)

data class Operational(
    val status: String = "active", // active | maintenance
    val maintenanceMessage: String = "",
    val banner: Banner = Banner(),
)

data class RemoteGame(
    val id: String,
    val packageName: String?,
    val displayName: String?,
    val iconUrl: String?,
    val category: String?,
    val order: Int,
    val visible: Boolean,
    val sessionLimitSeconds: Int,
)

data class UpdateInfo(
    val latestVersionCode: Int = 0,
    val apkUrl: String? = null,
    val silentInstall: Boolean = false,
)

data class WifiNetwork(
    val ssid: String,
    val password: String?,
    val priority: Int = 0,
    val hidden: Boolean = false,
)

data class TelemetryConfig(
    val enabled: Boolean = false,
    val reportUrl: String? = null,
    val reportIntervalMinutes: Int = 30,
    val heartbeatIntervalMinutes: Int = 5,
    val sendGameUsage: Boolean = true,
    val sendDeviceHealth: Boolean = true,
)

data class ResolvedConfig(
    val schemaVersion: Int,
    val configVersion: Int,
    val pollIntervalMinutes: Int,
    val deviceLabel: String?,
    val branding: Branding,
    val layout: Layout,
    val kiosk: Kiosk,
    val operational: Operational,
    val games: List<RemoteGame>,
    val update: UpdateInfo,
    val telemetry: TelemetryConfig,
    val wifiNetworks: List<WifiNetwork>,
)

/** Parses the fleet config.json and resolves the effective settings for [deviceId],
 * applying the documented merge rule: defaults first, then a deep patch of whatever
 * fields the matching entry in `devices` specifies. Games are merged by `id`: the
 * full definition comes from `defaults.games`, per-device entries only patch fields
 * such as `visible`/`order`. */
object RemoteConfigParser {

    fun parse(json: String, deviceId: String): ResolvedConfig {
        val root = JSONObject(json)
        val defaults = root.optJSONObject("defaults") ?: JSONObject()
        val devices = root.optJSONObject("devices")
        val deviceOverride = devices?.optJSONObject(deviceId)

        val branding = parseBranding(
            mergeObjects(defaults.optJSONObject("branding"), deviceOverride?.optJSONObject("branding"))
        )
        val layout = parseLayout(
            mergeObjects(defaults.optJSONObject("layout"), deviceOverride?.optJSONObject("layout"))
        )
        val kiosk = parseKiosk(
            mergeObjects(defaults.optJSONObject("kiosk"), deviceOverride?.optJSONObject("kiosk"))
        )
        val operational = parseOperational(
            mergeObjects(defaults.optJSONObject("operational"), deviceOverride?.optJSONObject("operational"))
        )
        val update = parseUpdate(defaults.optJSONObject("update"))
        val telemetry = parseTelemetry(defaults.optJSONObject("telemetry"))
        val games = mergeGames(defaults.optJSONArray("games"), deviceOverride?.optJSONArray("games"))
        val wifiNetworks = parseWifi(
            mergeObjects(defaults.optJSONObject("network"), deviceOverride?.optJSONObject("network"))
        )

        return ResolvedConfig(
            schemaVersion = root.optInt("schemaVersion", 1),
            configVersion = root.optInt("configVersion", 0),
            pollIntervalMinutes = root.optInt("pollIntervalMinutes", 15),
            deviceLabel = deviceOverride?.optString("label")?.takeIf { it.isNotBlank() },
            branding = branding,
            layout = layout,
            kiosk = kiosk,
            operational = operational,
            games = games,
            update = update,
            telemetry = telemetry,
            wifiNetworks = wifiNetworks,
        )
    }

    private fun parseWifi(o: JSONObject): List<WifiNetwork> {
        val arr = o.optJSONArray("wifiNetworks") ?: return emptyList()
        val result = mutableListOf<WifiNetwork>()
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            val ssid = n.optStringOrNull("ssid") ?: continue
            result.add(
                WifiNetwork(
                    ssid = ssid,
                    password = n.optStringOrNull("password"),
                    priority = n.optInt("priority", 0),
                    hidden = n.optBoolean("hidden", false),
                )
            )
        }
        return result
    }

    /** Shallow merge: every key present in [override] replaces the one in [base]. */
    private fun mergeObjects(base: JSONObject?, override: JSONObject?): JSONObject {
        val result = JSONObject(base?.toString() ?: "{}")
        if (override != null) {
            for (key in override.keys()) {
                result.put(key, override.get(key))
            }
        }
        return result
    }

    private fun parseBranding(o: JSONObject) = Branding(
        appTitle = o.optString("appTitle", "Kids Fun Planet"),
        logoUrl = o.optStringOrNull("logoUrl"),
        backgroundUrl = o.optStringOrNull("backgroundUrl"),
        primaryColor = o.optStringOrNull("primaryColor"),
        accentColor = o.optStringOrNull("accentColor"),
        language = o.optString("language", "it"),
    )

    private fun parseLayout(o: JSONObject) = Layout(
        columns = o.optInt("columns", 0),
        tileSize = o.optString("tileSize", "large"),
        showLabels = o.optBoolean("showLabels", true),
    )

    private fun parseKiosk(o: JSONObject) = Kiosk(
        enabled = o.optBoolean("enabled", true),
        exitPin = o.optStringOrNull("exitPin"),
        idleTimeoutSeconds = o.optInt("idleTimeoutSeconds", 0),
        sessionLimitSeconds = o.optInt("sessionLimitSeconds", 0),
        maxVolumePercent = o.optInt("maxVolumePercent", 100),
        brightnessPercent = o.optInt("brightnessPercent", 100),
        autoLaunchPackage = o.optStringOrNull("autoLaunchPackage"),
    )

    private fun parseOperational(o: JSONObject): Operational {
        val bannerObj = o.optJSONObject("banner")
        val banner = Banner(
            enabled = bannerObj?.optBoolean("enabled", false) ?: false,
            text = bannerObj?.optString("text", "") ?: "",
            imageUrl = bannerObj?.optString("imageUrl", "") ?: "",
        )
        return Operational(
            status = o.optString("status", "active"),
            maintenanceMessage = o.optString("maintenanceMessage", ""),
            banner = banner,
        )
    }

    private fun parseUpdate(o: JSONObject?) = UpdateInfo(
        latestVersionCode = o?.optInt("latestVersionCode", 0) ?: 0,
        apkUrl = o?.optStringOrNull("apkUrl"),
        silentInstall = o?.optBoolean("silentInstall", false) ?: false,
    )

    private fun parseTelemetry(o: JSONObject?) = TelemetryConfig(
        enabled = o?.optBoolean("enabled", false) ?: false,
        reportUrl = o?.optStringOrNull("reportUrl"),
        reportIntervalMinutes = o?.optInt("reportIntervalMinutes", 30) ?: 30,
        heartbeatIntervalMinutes = o?.optInt("heartbeatIntervalMinutes", 5) ?: 5,
        sendGameUsage = o?.optBoolean("sendGameUsage", true) ?: true,
        sendDeviceHealth = o?.optBoolean("sendDeviceHealth", true) ?: true,
    )

    private fun mergeGames(defaultGames: JSONArray?, overrideGames: JSONArray?): List<RemoteGame> {
        val base = linkedMapOf<String, JSONObject>()
        if (defaultGames != null) {
            for (i in 0 until defaultGames.length()) {
                val g = defaultGames.getJSONObject(i)
                val id = g.optString("id")
                if (id.isNotBlank()) base[id] = g
            }
        }
        if (overrideGames != null) {
            for (i in 0 until overrideGames.length()) {
                val patch = overrideGames.getJSONObject(i)
                val id = patch.optString("id")
                if (id.isBlank()) continue
                base[id] = mergeObjects(base[id], patch)
            }
        }
        return base.values.map { g ->
            RemoteGame(
                id = g.optString("id"),
                packageName = g.optStringOrNull("package"),
                displayName = g.optStringOrNull("displayName"),
                iconUrl = g.optStringOrNull("iconUrl"),
                category = g.optStringOrNull("category"),
                order = g.optInt("order", 0),
                visible = g.optBoolean("visible", true),
                sessionLimitSeconds = g.optInt("sessionLimitSeconds", 0),
            )
        }.sortedBy { it.order }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
