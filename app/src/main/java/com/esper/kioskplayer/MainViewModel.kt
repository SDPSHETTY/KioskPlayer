package com.esper.kioskplayer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    // Configuration polling job
    private var configPollingJob: Job? = null

    // Current configuration state
    private val _currentConfig = MutableStateFlow<KioskConfig?>(null)
    val currentConfig: StateFlow<KioskConfig?> = _currentConfig.asStateFlow()

    // Configuration change events
    private val _configUpdateTrigger = MutableStateFlow(0)
    val configUpdateTrigger: StateFlow<Int> = _configUpdateTrigger.asStateFlow()

    companion object {
        private const val TAG = "KioskPlayerViewModel"
        private const val DEFAULT_HEARTBEAT_SEC = 60
        private const val MIN_HEARTBEAT_SEC = 15
        private const val HEALTH_PREFS = "kioskplayer_health"
    }

    /**
     * Starts the configuration polling coroutine
     */
    fun startConfigurationPolling() {
        Log.d(TAG, "Starting configuration polling")

        // Cancel any existing polling job
        stopConfigurationPolling()

        configPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    // Load and apply new configuration
                    refreshConfiguration(force = false)

                    // Get heartbeat interval from current config
                    val heartbeatSec = _currentConfig.value?.heartbeatSec ?: DEFAULT_HEARTBEAT_SEC
                    val delayMs = (heartbeatSec.coerceAtLeast(MIN_HEARTBEAT_SEC) * 1000L)

                    ErrorHandler.logInfo(
                        ErrorHandler.ErrorCategory.CONFIGURATION,
                        "POLLING_SUCCESS",
                        "Configuration polling completed successfully",
                        mapOf("nextPollInSeconds" to heartbeatSec)
                    )

                    // Wait for the next poll cycle
                    delay(delayMs)
                } catch (e: Exception) {
                    val errorInfo = ErrorHandler.ErrorInfo(
                        ErrorHandler.ErrorCategory.CONFIGURATION,
                        "POLLING_ERROR",
                        "Error during configuration polling",
                        throwable = e
                    )
                    ErrorHandler.logError(errorInfo)

                    // Wait longer before retrying on error
                    delay(30_000L) // 30 seconds
                }
            }
        }
    }

    /**
     * Stops the configuration polling coroutine
     */
    fun stopConfigurationPolling() {
        Log.d(TAG, "Stopping configuration polling")
        configPollingJob?.cancel()
        configPollingJob = null
    }

    /**
     * Forces an immediate configuration refresh
     */
    fun forceConfigurationRefresh() {
        viewModelScope.launch {
            refreshConfiguration(force = true)
        }
    }

    /**
     * Refreshes the configuration and triggers UI update if changed
     */
    private suspend fun refreshConfiguration(force: Boolean) {
        try {
            val newConfig = KioskConfig.fromRestrictions(context)
            val previousConfig = _currentConfig.value

            // Update configuration state
            _currentConfig.value = newConfig

            // Trigger UI update if configuration changed or forced
            if (force || previousConfig != newConfig) {
                _configUpdateTrigger.value = _configUpdateTrigger.value + 1

                ErrorHandler.logInfo(
                    ErrorHandler.ErrorCategory.CONFIGURATION,
                    "CONFIG_UPDATED",
                    "Configuration updated and UI refresh triggered",
                    mapOf(
                        "mode" to newConfig.playMode,
                        "loop" to newConfig.loopMode,
                        "source" to newConfig.sourcePreference,
                        "force" to force,
                        "changed" to (previousConfig != newConfig)
                    )
                )
            }
        } catch (e: Exception) {
            val errorInfo = ErrorHandler.ErrorInfo(
                ErrorHandler.ErrorCategory.CONFIGURATION,
                "REFRESH_FAILED",
                "Failed to refresh configuration from restrictions",
                mapOf("force" to force),
                e
            )
            ErrorHandler.logError(errorInfo)
        }
    }

    /**
     * Records health information to SharedPreferences
     */
    fun recordHealth(state: String, error: String?, mediaCount: Int) {
        viewModelScope.launch {
            try {
                val config = _currentConfig.value ?: return@launch

                val prefs = context.getSharedPreferences(HEALTH_PREFS, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("last_update_ms", System.currentTimeMillis())
                    .putString("state", state)
                    .putString("error", error)
                    .putInt("media_count", mediaCount)
                    .putString("path", config.videoDir)
                    .putString("mode", config.playMode)
                    .putString("loop", config.loopMode)
                    .apply()

                ErrorHandler.logInfo(
                    ErrorHandler.ErrorCategory.SYSTEM,
                    "HEALTH_RECORDED",
                    "Health information recorded successfully",
                    mapOf(
                        "state" to state,
                        "error" to error,
                        "mediaCount" to mediaCount
                    )
                )
            } catch (e: Exception) {
                val errorInfo = ErrorHandler.ErrorInfo(
                    ErrorHandler.ErrorCategory.SYSTEM,
                    "HEALTH_RECORD_FAILED",
                    "Failed to record health information",
                    throwable = e
                )
                ErrorHandler.logError(errorInfo)
            }
        }
    }

    /**
     * Dumps current health information to logs
     */
    fun dumpHealthToLog() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences(HEALTH_PREFS, Context.MODE_PRIVATE)
                val state = prefs.getString("state", "unknown")
                val error = prefs.getString("error", null)
                val mediaCount = prefs.getInt("media_count", 0)
                val lastUpdate = prefs.getLong("last_update_ms", 0)

                ErrorHandler.logInfo(
                    ErrorHandler.ErrorCategory.SYSTEM,
                    "HEALTH_DUMP",
                    "Health information dump",
                    mapOf(
                        "state" to state,
                        "error" to error,
                        "mediaCount" to mediaCount,
                        "lastUpdateMs" to lastUpdate
                    )
                )
            } catch (e: Exception) {
                val errorInfo = ErrorHandler.ErrorInfo(
                    ErrorHandler.ErrorCategory.SYSTEM,
                    "HEALTH_DUMP_FAILED",
                    "Failed to dump health information",
                    throwable = e
                )
                ErrorHandler.logError(errorInfo)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, stopping configuration polling")
        stopConfigurationPolling()
    }

}