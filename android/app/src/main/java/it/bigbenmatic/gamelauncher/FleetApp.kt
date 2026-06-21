package it.bigbenmatic.gamelauncher

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the long-running fleet-management loops (Modules 1 & 3) for the whole app
 * lifetime, independent of any single Activity instance. Running them here (rather
 * than tied to MainActivity) keeps polling/telemetry alive across configuration
 * changes and matches how this app already runs as the persistent Home launcher.
 */
class FleetApp : Application() {

    lateinit var configRepository: ConfigRepository
    lateinit var telemetryManager: TelemetryManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        telemetryManager = TelemetryManager(this)

        startConfigPollingLoop()
        startTelemetryLoop()
    }

    private fun startConfigPollingLoop() {
        scope.launch {
            while (true) {
                runCatching { configRepository.refresh() }
                val minutes = configRepository.config.value?.pollIntervalMinutes?.takeIf { it > 0 } ?: 15
                delay(minutes * 60_000L)
            }
        }
    }

    private fun startTelemetryLoop() {
        scope.launch {
            var secondsElapsed = 0L
            while (true) {
                delay(HEARTBEAT_TICK_MS)
                secondsElapsed += HEARTBEAT_TICK_MS / 1000

                val config = configRepository.config.value
                val telemetry = config?.telemetry
                if (telemetry?.enabled != true || telemetry.reportUrl.isNullOrBlank()) continue

                val heartbeatEverySeconds = (telemetry.heartbeatIntervalMinutes.takeIf { it > 0 } ?: 5) * 60L
                val reportEverySeconds = (telemetry.reportIntervalMinutes.takeIf { it > 0 } ?: 30) * 60L

                if (secondsElapsed % reportEverySeconds < HEARTBEAT_TICK_MS / 1000) {
                    telemetryManager.enqueueFullReport(this@FleetApp, config)
                } else if (secondsElapsed % heartbeatEverySeconds < HEARTBEAT_TICK_MS / 1000) {
                    telemetryManager.enqueueHeartbeat(config)
                }

                runCatching { telemetryManager.flushQueue(telemetry.reportUrl) }
            }
        }
    }

    companion object {
        private const val HEARTBEAT_TICK_MS = 30_000L
    }
}
