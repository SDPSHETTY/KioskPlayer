package com.esper.kioskplayer

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Enhanced error handling utility with structured logging and retry capabilities
 */
object ErrorHandler {

    private const val TAG = "KioskPlayer.ErrorHandler"

    // Error categories for structured logging
    enum class ErrorCategory {
        CONFIGURATION,
        MEDIA_PLAYBACK,
        NETWORK_STREAM,
        FILE_SYSTEM,
        PERMISSION,
        SYSTEM
    }

    // Structured error information
    data class ErrorInfo(
        val category: ErrorCategory,
        val code: String,
        val message: String,
        val details: Map<String, Any?> = emptyMap(),
        val throwable: Throwable? = null
    )

    /**
     * Logs structured error information with consistent formatting
     */
    fun logError(errorInfo: ErrorInfo) {
        val detailsString = if (errorInfo.details.isNotEmpty()) {
            errorInfo.details.entries.joinToString(" ") { "${it.key}=${it.value}" }
        } else ""

        val logMessage = buildString {
            append("[${errorInfo.category}]")
            append(" code=${errorInfo.code}")
            append(" message=\"${errorInfo.message}\"")
            if (detailsString.isNotEmpty()) {
                append(" details={$detailsString}")
            }
        }

        if (errorInfo.throwable != null) {
            Log.e(TAG, logMessage, errorInfo.throwable)
        } else {
            Log.e(TAG, logMessage)
        }
    }

    /**
     * Logs warning with structured format
     */
    fun logWarning(category: ErrorCategory, code: String, message: String, details: Map<String, Any?> = emptyMap()) {
        val detailsString = if (details.isNotEmpty()) {
            details.entries.joinToString(" ") { "${it.key}=${it.value}" }
        } else ""

        val logMessage = buildString {
            append("[${category}]")
            append(" code=${code}")
            append(" message=\"${message}\"")
            if (detailsString.isNotEmpty()) {
                append(" details={$detailsString}")
            }
        }

        Log.w(TAG, logMessage)
    }

    /**
     * Logs info with structured format
     */
    fun logInfo(category: ErrorCategory, code: String, message: String, details: Map<String, Any?> = emptyMap()) {
        val detailsString = if (details.isNotEmpty()) {
            details.entries.joinToString(" ") { "${it.key}=${it.value}" }
        } else ""

        val logMessage = buildString {
            append("[${category}]")
            append(" code=${code}")
            append(" message=\"${message}\"")
            if (detailsString.isNotEmpty()) {
                append(" details={$detailsString}")
            }
        }

        Log.i(TAG, logMessage)
    }

    /**
     * Executes an operation with exponential backoff retry logic
     * @param maxAttempts Maximum number of attempts
     * @param initialDelayMs Initial delay between attempts
     * @param maxDelayMs Maximum delay between attempts
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param operation Suspending function to execute
     * @return Result of the operation or throws the last exception
     */
    suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        backoffMultiplier: Double = 2.0,
        jitterRange: Long = 500L,
        operation: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return operation(attempt + 1)
            } catch (e: Exception) {
                lastException = e

                // Log retry attempt
                logWarning(
                    ErrorCategory.SYSTEM,
                    "RETRY_ATTEMPT",
                    "Operation failed, retrying",
                    mapOf(
                        "attempt" to attempt + 1,
                        "maxAttempts" to maxAttempts,
                        "nextDelayMs" to currentDelay,
                        "error" to e.message
                    )
                )

                // Don't delay after the last attempt
                if (attempt < maxAttempts - 1) {
                    // Add jitter to prevent thundering herd
                    val jitter = Random.nextLong(-jitterRange, jitterRange)
                    val delayWithJitter = (currentDelay + jitter).coerceAtLeast(0L)

                    delay(delayWithJitter)

                    // Calculate next delay with exponential backoff
                    currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
                }
            }
        }

        // All attempts failed, throw the last exception
        throw lastException ?: RuntimeException("All retry attempts failed")
    }

    /**
     * Creates user-friendly error messages based on error categories
     */
    fun createUserMessage(errorInfo: ErrorInfo): String {
        return when (errorInfo.category) {
            ErrorCategory.CONFIGURATION -> {
                when (errorInfo.code) {
                    "INVALID_CONFIG" -> "Configuration error: ${errorInfo.message}. Check your managed configuration settings."
                    "MISSING_CONFIG" -> "No configuration found. Please ensure managed configuration is properly set up."
                    else -> "Configuration issue: ${errorInfo.message}"
                }
            }
            ErrorCategory.MEDIA_PLAYBACK -> {
                when (errorInfo.code) {
                    "CODEC_ERROR" -> "Video format not supported. Please use MP4, MKV, or WebM files."
                    "NETWORK_ERROR" -> "Stream playback failed. Check network connection and stream URL."
                    "FILE_ERROR" -> "Cannot play media file: ${errorInfo.message}"
                    else -> "Media playback error: ${errorInfo.message}"
                }
            }
            ErrorCategory.NETWORK_STREAM -> {
                when (errorInfo.code) {
                    "CONNECTION_TIMEOUT" -> "Stream connection timed out. Check network and stream URL."
                    "INVALID_URL" -> "Invalid stream URL format: ${errorInfo.details["url"]}"
                    "AUTHENTICATION_FAILED" -> "Stream authentication failed. Check stream credentials."
                    else -> "Network stream error: ${errorInfo.message}"
                }
            }
            ErrorCategory.FILE_SYSTEM -> {
                when (errorInfo.code) {
                    "FILE_NOT_FOUND" -> "Media file not found: ${errorInfo.details["path"]}. Upload the file or update configuration."
                    "PERMISSION_DENIED" -> "Cannot access media files. Check storage permissions."
                    "DIRECTORY_NOT_FOUND" -> "Media directory not found: ${errorInfo.details["path"]}. Create directory or update path."
                    else -> "File system error: ${errorInfo.message}"
                }
            }
            ErrorCategory.PERMISSION -> {
                when (errorInfo.code) {
                    "STORAGE_PERMISSION" -> "Storage permission required to read media files."
                    "SYSTEM_PERMISSION" -> "System permission required: ${errorInfo.message}"
                    else -> "Permission error: ${errorInfo.message}"
                }
            }
            ErrorCategory.SYSTEM -> {
                when (errorInfo.code) {
                    "LOW_MEMORY" -> "System memory low. Close other apps or restart device."
                    "SYSTEM_ERROR" -> "System error occurred: ${errorInfo.message}"
                    else -> "System error: ${errorInfo.message}"
                }
            }
        }
    }

    /**
     * Handles common Android media errors with specific categorization
     */
    fun handleMediaError(error: Throwable): ErrorInfo {
        val message = error.message ?: "Unknown media error"

        return when {
            message.contains("codec", ignoreCase = true) -> ErrorInfo(
                ErrorCategory.MEDIA_PLAYBACK,
                "CODEC_ERROR",
                "Unsupported video format or codec",
                mapOf("originalError" to message),
                error
            )
            message.contains("network", ignoreCase = true) || message.contains("timeout", ignoreCase = true) -> ErrorInfo(
                ErrorCategory.NETWORK_STREAM,
                "NETWORK_ERROR",
                "Network connection issue during playback",
                mapOf("originalError" to message),
                error
            )
            message.contains("file", ignoreCase = true) || message.contains("not found", ignoreCase = true) -> ErrorInfo(
                ErrorCategory.FILE_SYSTEM,
                "FILE_ERROR",
                "Media file cannot be accessed",
                mapOf("originalError" to message),
                error
            )
            message.contains("permission", ignoreCase = true) -> ErrorInfo(
                ErrorCategory.PERMISSION,
                "PERMISSION_DENIED",
                "Permission required to access media",
                mapOf("originalError" to message),
                error
            )
            else -> ErrorInfo(
                ErrorCategory.MEDIA_PLAYBACK,
                "UNKNOWN_ERROR",
                message,
                mapOf("originalError" to message),
                error
            )
        }
    }

    /**
     * Validates configuration and returns structured error if invalid
     */
    fun validateConfiguration(config: KioskConfig): ErrorInfo? {
        // Validate video directory
        if (config.videoDir.isBlank() && config.streamUrl.isBlank()) {
            return ErrorInfo(
                ErrorCategory.CONFIGURATION,
                "MISSING_MEDIA_SOURCE",
                "No video directory or stream URL configured",
                mapOf("videoDir" to config.videoDir, "streamUrl" to config.streamUrl)
            )
        }

        // Validate schedule times if scheduling is enabled
        if (config.scheduleEnabled) {
            if (!isValidTimeFormat(config.scheduleStart)) {
                return ErrorInfo(
                    ErrorCategory.CONFIGURATION,
                    "INVALID_SCHEDULE_START",
                    "Invalid schedule start time format",
                    mapOf("scheduleStart" to config.scheduleStart)
                )
            }
            if (!isValidTimeFormat(config.scheduleEnd)) {
                return ErrorInfo(
                    ErrorCategory.CONFIGURATION,
                    "INVALID_SCHEDULE_END",
                    "Invalid schedule end time format",
                    mapOf("scheduleEnd" to config.scheduleEnd)
                )
            }
        }

        // Validate stream URL format if provided
        if (config.streamUrl.isNotBlank() && !isValidStreamUrl(config.streamUrl)) {
            return ErrorInfo(
                ErrorCategory.CONFIGURATION,
                "INVALID_STREAM_URL",
                "Invalid stream URL format",
                mapOf("streamUrl" to config.streamUrl)
            )
        }

        return null // Configuration is valid
    }

    private fun isValidTimeFormat(time: String): Boolean {
        return try {
            val parts = time.trim().split(':')
            if (parts.size != 2) return false
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            hour in 0..23 && minute in 0..59
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidStreamUrl(url: String): Boolean {
        return try {
            val trimmed = url.trim()
            trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("rtmp://") ||
            trimmed.startsWith("rtsp://")
        } catch (_: Exception) {
            false
        }
    }
}